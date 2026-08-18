# EZMiner Harvest / World-Mutation Path Review

Root package: `com.czqwq.EZMiner` · review of the fast-harvest / direct-EBS world-mutation path.
Decompiled vanilla confirmed against `build/rfg/minecraft-src/java/net/minecraft/...`.

---

## 0. The three mutation paths (map of the code)

1. **Single-block fast path** — `BlockHarvestActionExecutor.execute()` (line 50) →
   `IEZMinerItemInWorldManager.ezminer$tryHarvestBlockFast()`
   (`mixin/early/MixinItemInWorldManager.java:108`) which uses
   `theWorld.setBlock(x, y, z, Blocks.air, 0, 2)` — **flag = 2**.
2. **Batch fast path (chunk-cached)** — `*BlockHarvestActionExecutor.executeBatch()`
   (lines 92–214) and `ChunkCachedHarvester.harvestNext()` (lines 78–200). These do
   **NOT** call `World.setBlock` at all. They write air directly into the
   `ExtendedBlockStorage` via `ChunkBlockWriteHelper.writeAirToEbs()`
   (`ChunkBlockWriteHelper.java:150–165`, the actual write is
   `ebs.func_150818_a(...)` at line 163), then update the server height map
   (`updateHeightMap`, lines 175–223) and call
   `world.markBlockForUpdate(x, y, z)` (e.g. `ChunkCachedHarvester.java:177`).
3. **Vanilla/TE path** — `player.theItemInWorldManager.tryHarvestBlock(...)` only for
   blocks where `block.hasTileEntity(meta)` is true
   (`BlockHarvestActionExecutor.java:63,119`; `ChunkCachedHarvester.java:110`).

### What vanilla `setBlock(flags=3)` does that paths 1 & 2 skip

`World.setBlock` (`World.java:490–544`):
- `chunk.func_150807_a(...)` (`Chunk.java:610–729`): on top of writing the EBS, it
  fires `block.onBlockPreDestroy`, `block.breakBlock`, clears a stale TileEntity,
  **updates the skylight/block light** (`relightBlock`, `propagateSkylightOcclusion`,
  `generateSkylightMap`), fires `block.onBlockAdded`, and **zeroes the metadata** via
  `setExtBlockMetadata(..., meta)` (line 654 & 681).
- `markAndNotifyBlock` (`World.java:547–563`):
  - `(flag & 2)` → `markBlockForUpdate` → client `S23PacketBlockChange`.
  - `(flag & 1)` → `notifyBlockChange` → `notifyBlocksOfNeighborChange` → the **6
    neighbors' `onNeighborBlockChange`** (`World.java:695–741`).
- `func_147451_t(x,y,z)` (line 528) → `updateLightByType` sky + block light.

Path 1 (`setBlock …,2`) keeps the `func_150807_a` internals and light update, but has
**bit 1 clear** → no neighbor notification.
Paths 2 (EBS direct) keep almost nothing: no light update, no `breakBlock` /
`onBlockPreDestroy` / `onBlockAdded`, **no metadata clearing**, only a hand-rolled
height map + `markBlockForUpdate`.

The vanilla block-break entry (`ItemInWorldManager.tryHarvestBlock` → `destroyBlock`)
uses **flag 3** (`World.func_147480_a` line 672: `setBlock(x,y,z,air,0,3)`), so it
gets both client update **and** the neighbor notify.

---

## 1. Bug A — Water / grass / sand blocks left floating after chain-mining

### Verdict
**Yes — the missing neighbor notification is the root cause.** The direct EBS write
(and even the flag=2 single path) never fires `Block.onNeighborBlockChange` on the 6
neighbours that the removed position used to support / be adjacent to. Vanilla gets
this from `(flag & 1)` → `notifyBlocksOfNeighborChange`.

### Exactly what must fire
- **Water flowing down:** `BlockLiquid.onNeighborBlockChange` → `func_149805_n`
  schedules the flow tick (`BlockLiquid.java:538–541`). A water source sitting above
  the mined block only moves because its own `onNeighborBlockChange` fires — triggered
  when the block under/next to it changes and neighbours are notified.
- **Sand / gravel falling:** `BlockFalling.onNeighborBlockChange` →
  `scheduleBlockUpdate` → `updateTick` → `func_149830_m` spawns the falling entity
  (`BlockFalling.java:38–84`). Same trigger requirement.
- **Torches / rails / levers / etc. popping off:** the host block being removed must
  notify them (`BlockTorch.onNeighborBlockChange:169`, `BlockRailBase:145`, ...).
- **Grass:** a grass block whose support/lighting changed relies on
  `BlockGrass.updateTick`/neighbor-driven updates.
- **Scheduled ticking:** most of the above is *scheduled* by `scheduleBlockUpdate`
  inside `onNeighborBlockChange` — so the single missing upstream call is the
  neighbour notification; the per-block scheduled tick then happens naturally.

The exact vanilla call chain that is missing in the fast paths:

```
World.notifyBlocksOfNeighborChange(x,y,z, oldBlock)   // = (flag & 1) in markAndNotifyBlock
  → notifyBlockOfNeighborChange ×6
    → Block.onNeighborBlockChange(world, nx,ny,nz, oldBlock)
```

`oldBlock` must be the block that was removed (air after removal would still work for
most vanilla handlers, but vanilla passes the *old* block — `markAndNotifyBlock` line
556 `notifyBlockChange(x,y,z, oldBlock)`).

### Why the current code can't fix this
- `ChunkCachedHarvester.java:177` and `BlockHarvestActionExecutor.java:184` call only
  `world.markBlockForUpdate(x,y,z)`. That is `(flag & 2)` only — it **never** touches
  `notifyBlocksOfNeighborChange` / `onNeighborBlockChange` (`World.java:684–690` just
  iterates `worldAccesses` and sends the client packet).
- `MixinItemInWorldManager.java:108` uses `setBlock(...,2)` precisely to skip the
  neighbour notify — matching comment at lines 104–107. Same for the batch path.

### Batch-friendly fix that preserves the perf win
Do the neighbour notifications **once per batch, deduplicated**, on the server thread
after the EBS writes, and schedule light updates at chunk granularity:

1. Record the `oldBlock` for every removed position (you already have `block` captured
   in the loop before removal — `ChunkCachedHarvester.java:104`).
2. Collect a set of distinct **neighbour** positions (the ≤6 offsets around each
   removed block), plus a per-position `oldBlock`. Attempting `notifyBlocksOfNeighborChange`
   per removed block is 6 world lookups/block — instead call
   `world.notifyBlockOfNeighborChange(nx,ny,nz, oldBlockOfRemoved)` once per distinct
   neighbour coordinate. This is O(unique neighbours) instead of O(6×blocks).
3. After the whole batch, run `world.func_147451_t(x,y,z)` (checkLight) on the touched
   columns, or better `world.markBlockRangeForRenderUpdate(x1,y1,z1,x2,y2,z2)` for the
   chunk's AABB so the client relights properly.
4. Only do this for blocks whose neighbours can actually react (water/sand/attached
   blocks). Mining pure stone/ore veins in a solid body has no reacting neighbour, so
   a cheap pre-check (is any neighbour liquid, falling block, or an attachable-attached
   block) keeps the fast path fast for the common case. At minimum, gating on
   `Config.fireBreakEvent`-independent always-notify is acceptable — the dedup keeps
   it near-constant.

Alternative simplest correct fix: keep the EBS write for ID/meta, but call
`world.func_147450_b(x,y,z, oldBlock)` (the batch schedule-update API) per removed
block. It still marks neighbours dirty — acceptable if bounded per tick (you already
cap `breakPerTick`).

---

## 2. Bug B — GT ore shows `name.0` / stale, wrong-name block after mining

### Verdict
Two concrete, verifiable defects in the mod path, both strongest for **GTNH's modern
`GTBlockOre`** (large-vein ores whose identity lives entirely in **block metadata** and
which have **no TileEntity** → they take the EBS fast path, NOT the TE vanilla path).

### Mechanism 1 — `writeAirToEbs` never clears the metadata nibble (primary)
`ChunkBlockWriteHelper.writeAirToEbs` (`ChunkBlockWriteHelper.java:150–165`) calls only
`ebs.func_150818_a(lx,ly,lz, Blocks.air)`. `func_150818_a`
(`ExtendedBlockStorage.java:61–108`) overwrites the **block ID** arrays (LSB + zeroes
MSB when fitting ≤255), but it **does not touch `blockMetadataArray`**. Compare vanilla
`Chunk.func_150807_a`, which always calls
`extendedblockstorage.setExtBlockMetadata(..., metadata)` (`Chunk.java:654, 681`),
zeroing the metadata when the block becomes air.

Consequence: after EBS chain-mining,
`world.getBlockMetadata(x,y,z)` returns the **old GT ore metadata with an air block**.
- `markBlockForUpdate` → server `PlayerManager` → `S23PacketBlockChange` serializes the
  *live* metadata (`PlayerManager.java:570`; `S23PacketBlockChange.java:29–30` read
  `getBlock` + `getBlockMetadata`), so the stale GT meta is transmitted with the air
  block. Clients apply `air + staleMeta`.
- The stale nibble **persists into the saved chunk** (metadata is saved with the air
  block), so on chunk unload/reload the mismatch reappears.
- GT ores are metadata-driven: `DeterminingIdentical.isGTLargeVeinOre` itself notes
  small ores live at `meta >= 16000` and legacy ore variety is stored in a TileEntity
  (`DeterminingIdentical.java:283–331`) — i.e. a *substantial* portion of the ore's
  state is in metadata / a companion TE. A remaining air block with a bogus meta is
  exactly what surfaces as a wrong/unlocalized `name.0` on the client's block-name
  lookup (which indexes GT ore names by meta).

### Mechanism 2 — `breakBlock` / `onBlockPreDestroy` / `onBlockAdded` never fire (contributor)
For `hasTileEntity(meta)==false` GT ores the batch path skips `onBlockPreDestroy`,
`breakBlock`, and `onBlockAdded` (`Chunk.func_150807_a:650–725`). The fast path fires
only `onBlockHarvested` (`ChunkCachedHarvester.java:166`) and
`onBlockDestroyedByPlayer` (`:172`). Ore/TE subsystems that clean up a companion
`TileEntity` or update a per-ore counter in `breakBlock` are left in an inconsistent
state, which can leave a stale client-side ore rendering.

### Mechanism 3 — weaker, "sometimes" factor
`markBlockForUpdate` sends one `S23PacketBlockChange` per position with no
chunk-unload guard and is delivered through the per-chunk update queue; for very large
same-tick batches the queue coalesces and a client already mid-`func_150807_a` at the
same coordinate can momentarily render the pre-write state — combined with mechanism 1
this yields an intermittent "old ore stays visible" symptom.

### Fixes
- In `ChunkBlockWriteHelper.writeAirToEbs`, zero the metadata:
  `ebs.setExtBlockMetadata(lx, ly, lz, 0)` immediately after `func_150818_a`
  (mirrors `Chunk.func_150807_a:654/681`). This is cheap (one nibble write, no refcount /
  neighbour cost) and removes the stale-meta S23 + save corruption for every mined
  block, not just GT ores.
- After each batch, additionally call `world.markBlockRangeForRenderUpdate` for the
  touched region (rather than only per-block `markBlockForUpdate`) so clients rebuild
  the affected chunk meshes — fixes "sometimes not update client-side".
- For metadata-carrying blocks (classify via `DeterminingIdentical.isGTLargeVeinOre`,
  or simply always) that have a companion TE, prefer the vanilla `tryHarvestBlock` path
  (like the existing `hasTileEntity` check at `ChunkCachedHarvester.java:110`) so
  `breakBlock`/TE cleanup runs. Better: extend the "must use vanilla path" condition to
  include GT ore classes instead of only `hasTileEntity`.
- `func_150818_a`'s `blockRefCount`/`tickRefCount` are correct; if `ebs.isEmpty()` after
  removal, drop the now-empty EBS from `chunk.getBlockStorageArray()` (vanilla does) to
  avoid leaving an empty sub-chunk on disk.

---

## 3. Bug C — Other concrete bugs in this path

### C1. Spawn protection / claim protection bypass (HIGH)
Vanilla break protection lives in `WorldServer.canMineBlock` → `canMineBlockBody` →
`mcServer.isBlockProtected` (`WorldServer.java:735–738`), plus Forge
`ForgeHooks.onBlockBreakEvent` (`ItemInWorldManager.java:285`) which protection/claim
mods subscribe to. **Neither runs on the fast paths**:
- `BlockHarvestActionExecutor` / `ChunkCachedHarvester` never call `world.canMineBlock`
  or `isBlockProtected`.
- `ChainBreakEventHelper.fireIfEnabled` (`ChainBreakEventHelper.java:32–35`) returns
  `null` and fires nothing when `Config.fireBreakEvent` is **off (default)**.
Result: EZMiner can chain-mine inside the vanilla world-spawn protected zone (and past
claim mods' cancellations). Fix: consult `world.canMineBlock(player,x,y,z)` once per
removed block in the batch (cheap), and always honour protection even when
`fireBreakEvent` is off.

### C2. Founder-thread ⇄ server-thread data race on the world (MEDIUM)
All world mutation happens on the server thread (`TickEvent.ServerTickEvent.START` →
`BaseOperator.operatorTask`, `BaseOperator.java:104–233`). The founders are
**unpaused at the same tick START** and run on worker threads (`BasePositionFounder`,
`ChainPositionFounder`), reading the same `Chunk`/`ExtendedBlockStorage` arrays via
`worldObj.getBlock / blockExists` (e.g. `BasePositionFounder.java:340–344`,
`ChainPositionFounder.java:293–298`) **while** the server thread is writing them with
`writeAirToEbs`. `Chunk`/`ExtendedBlockStorage` arrays are plain, unsynchronized
arrays. The `!paused.get()` worker check (`Pauseable.java:78`) bounds but does not
eliminate the window (both are inside the same tick). A founder can observe a torn
half-written block/metadata, enqueue a phantom position, and the operator then wastes
a slot on an already-air block. `updateHeightMap` (`ChunkBlockWriteHelper.java:175–223`)
also reads `chunk.getBlockStorageArray()` under the same race. Mitigation: have the
operator run its write phase at tick **END** (after founders are paused) or guard the
loop with `world.blockExists` + re-read `getBlock` right before writing (the batch code
already re-reads at `ChunkCachedHarvester.java:104`, which reduces but doesn't fix the
founder side).

### C3. `updateHeightMap` leaves `precipitationHeightMap` and skylight stale (LOW/MED)
`updateHeightMap` only rewrites `Chunk.heightMap` downward from the old height
(`ChunkBlockWriteHelper.java:190–219`); it does not update `Chunk.precipitationHeightMap`
(`Chunk.func_150807_a:614` does) nor the skylight arrays (vanilla does in
`func_150807_a:683–707`). Minor — `markBlockForUpdate` triggers client relight, but
server-side per-chunk light values used by other systems (and `getHeightValue`) stay
stale until the next full chunk remesh. Consider `world.func_147451_t` on the batch.

### C4. Empty EBS sub-chunk lingers (LOW)
After `writeAirToEbs` drains an EBS to `blockRefCount == 0`, the sub-chunk object is
left in `chunk.storageArrays`. `ebs.isEmpty()` returns true so save skips it, but the
object and its `blockMetadataArray` (with stale GT metas) remain in memory and on the
next placed block get reused with leftover metadata — see Bug B mechanism 1. Clearing
metadata (B fix) plus dropping the empty EBS closes this.

### C5. Single-path flag mismatch (consistency, part of Bug A/B)
`BlockHarvestActionExecutor.execute()` (flag=2 through the mixin) and the batch paths
(EBS direct) diverge: the former keeps `func_150807_a` (light + metadata clearing) and
skips only neighbour notify; the latter skips nearly everything. Both are inconsistent
with each other and with vanilla (flag 3). The batch path is the more aggressive and is
the source of B/1 and A. Unifying on one helper (EBS write + metadata clear + batched
neighbour notify + range update) removes the divergence.

---

## Summary of recommended changes

| # | Area | Fix | Files / lines |
|---|------|-----|---------------|
| 1 | Bug A | After each batch, notify distinct neighbour positions (`world.notifyBlockOfNeighborChange` with old block) and `markBlockRangeForRenderUpdate`; or `func_147450_b` per removed block | `ChunkCachedHarvester.java:177`, `BlockHarvestActionExecutor.java:184`, `ChunkBlockWriteHelper.java` |
| 2 | Bug B | `writeAirToEbs` → `ebs.setExtBlockMetadata(lx,ly,lz,0)` after `func_150818_a`; route metadata/TE-carrying GT ores through the vanilla path; add range render update | `ChunkBlockWriteHelper.java:163` |
| 3 | Bug C1 | Call `world.canMineBlock(player,x,y,z)` per removed block; honour protection even with `fireBreakEvent` off | `BlockHarvestActionExecutor.java:127+`, `ChunkCachedHarvester.java:119+` |
| 4 | Bug C2 | Run writes at tick END (after founders pause) or guard with re-read + `blockExists` | `BaseOperator.java`, `ChunkBlockWriteHelper.java` |
| 5 | Bug C3/C4 | `func_147451_t` on batch; drop empty EBS; clear stale meta | `ChunkBlockWriteHelper.java` |
