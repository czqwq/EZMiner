# EZMiner — Performance & Mixin Review (Task 4)

Scope: server-side search/retrieval hotspots and legitimate mixin opportunities for
huge-orebody chain mining (blockLimit up to 4096+), 1.7.10 Forge GTNH
(EndlessIDs + Hodgepodge present).

All findings cite the actual source under `src/main/java/com/czqwq/EZMiner/`.

---

## A) Hot methods measurable from the code (ranked by expected win)

### A1 — `DeterminingIdentical.identical()` state check wastes `getBlock`+`getBlockMetadata`
`core/founder/BasePositionFounder.checkCanAddImpl` (lines 338-350) calls, for every
candidate:
1. `player.worldObj.blockExists(x,y,z)` (line 340)
2. `player.worldObj.getBlock(x,y,z)` (line 341)
3. `player.worldObj.getBlockMetadata(x,y,z)` (line 344)
4. `block.canHarvestBlock(player, blockMeta)` (line 349)

`core/founder/ChainPositionFounder.checkCanAddImpl` (lines 292-306) additionally calls
`DeterminingIdentical.identical(...)` (line 302) which re-does its OWN `world.getBlock`
+ `world.getBlockMetadata` inside `DeterminingIdentical.identical(Block,..,Vector3i,..)`
(`core/founder/DeterminingIdentical.java` lines 178-182) — **after the founder already
fetched the block and meta.**

Net effect per surviving candidate in chain mode: **2× `getBlock` + 2×
`getBlockMetadata`** (one pair in the founder, one duplicate pair re-fetched inside
`DeterminingIdentical`). Each `World.getBlock` is `World → ChunkProviderServer →
ChunkStorage LongHashMap → Chunk → ExtendedBlockStorage[CY>>4] → block array`
(~5 call layers + a hash lookup). At 4096 candidates that is ~8192 full block reads
that could be ~4096 EBS-direct reads.

**Fix (no mixin):** pass the already-fetched `Block block`+`meta` into
`identical(sBlock, sMeta, sTile, tBlock, tMeta, pos, player)` (the overload at
DeterminingIdentical line 185 already exists and avoids re-lookup). The founder's
`checkCanAddImpl` should build the 8-arg overload call directly instead of the
position-only overload. **Zero-risk, pure event-layer, biggest per-candidate saving.**

### A2 — Boxed `Vector3i` allocation + queue offer per survivor in the hot path
`BasePositionFounder.addResult` (lines 356-360) does `positions.offer(pos)` with the
already-allocated `Vector3i`; but `ChainPositionFounder.doSingleThreadedSearch*`
(lines 64, 111) **allocate a new `Vector3i candidate` per neighbour before
`checkCanAdd`**, and `collectNeighbours` (lines 274-289) allocates `up to 124
Vector3i per BFS node` in the multi-threaded path (line 285), even though most are
discarded by the visited/bounds filter. The queue type is `LinkedBlockingQueue<Vector3i>`
— each `offer` allocates a node object. `encodePos` (line 391) already exists and would
let the visited set do pre-filtering cheaply before any `Vector3i` allocation.

Wins: (a) skip allocation until a candidate survives `isVisited` + `blockExists` checks;
(b) consider a primitive `long`-keyed work queue instead of `LinkedBlockingQueue<Vector3i>`
for the operator→founder handoff.

### A3 — Chunk/EBS not cached in `BasePositionFounder`/DQ founder read path
`ChainPositionFounder.checkCanAddImpl` (importantly the fuzzy variant too,
`FuzzyChainPositionFounder.checkCanAddImpl` lines 41-55) does `world.getBlock` /
`world.getBlockMetadata` **per candidate with no chunk locality cache**. Contrast:
`chain/planning/ChainPreCalcEngine.tickBfs` already caches `Chunk` + `ExtendedBlockStorage`
(lines 180-234) and `chain/execution/ChunkCachedHarvester` caches them per batch
(lines 59-64, 86-101). The background founder threads are the one hot path that **does
not** do chunk/EBS caching — they rebuild the whole `World→Chunk→EBS` chain per block.
BFS-flood results are heavily locality-grouped, so a `(chunkX,chunkZ,y4)→EBS` cache
like the precalc engine's would collapse ~4096 full lookups to ~(chunks×subchunks).

### A4 — Per-shell-layer `waitUntil()` + `budgetRemaining` reset (default budget 0)
`BasePositionFounder.run1SingleThreaded` / `run1MultiThreaded` call `waitUntil()`
**once per radius layer** (lines 165, 268), and with `Config.searchBudgetPerYield = 0`
(`Config.java` line 185 default) `consumeBudget()` in `Pauseable` (lines 70-85) parks on
**every** position check — a `LockSupport.parkNanos` + `System.nanoTime` + atomics
(`paused.get()`) per candidate. Raising `searchBudgetPerYield` reduces park checks;
the tradeoff is coarser pause latency. Should be a measured config knob, not default-0.

### A5 — `ChainPreCalcCache.computeHash` re-boxes via `Objects.hash` on a hot stop path
Minor: `chain/planning/ChainPreCalcCache.java` lines 38-55 use `Objects.hash(...)`
(individual `Integer`/`Class` boxing) — only runs once per `start()`/completion so it
is **not** hot; listed for completeness, no action needed.

### A6 — `OreFounder`-style founders re-fetch on `canHarvestBlock`
`GtVeinOreFounder.checkCanAdd` (lines 44-60) and `DeterminingIdentical.isGTLargeVeinOre`
(DeterminingIdentical lines 296-315) may call `world.getTileEntity(x,y,z)` for legacy GT
ore tiles on top of the block read. Only for legacy GT ore paths; secondary.

---

## B) What a MIXIN could legitimately accelerate (that events alone cannot)

The mod already uses a fast-harvest mixin **`MixinItemInWorldManager`** (`mixin/early/`,
`ezminer$tryHarvestBlockFast`, line 53) which avoids neighbor notifications (`setBlock
flag=2`, line 108) but still calls `World.setBlock` and thus `World.getBlock` +
`getBlockMetadata` + `Chunk.getBlock` internally. The dedicated
`ChunkCachedHarvester`/`ChunkBlockWriteHelper` path by-passes `setBlock` entirely by
writing air straight into `ExtendedBlockStorage` (ChunkBlockWriteHelper.writeAirToEbs,
line 150). The remaining hot read side (founders) is pure event-layer.

Legitimate mixin targets, ranked by expected win:

### B1 — `Chunk.getBlock(int x, int y, int z)` fast-path redirect (read side)
Target `net.minecraft.world.chunk.Chunk#getBlock`. Vanilla `Chunk.getBlock` resolves
`x&15, y&15, z&15`, gets `storage[y>>4]`, and calls `getBlockByExtId`. That's already
thin — the real cost is the caller's `World.getBlock → chunk provider → LongHashMap →
Chunk` chain that the mod repeats per candidate. A mixin cannot remove the caller's own
`World`→`Chunk` lookup. **Expected saving: small** — the biggest win is the caller-side
chunk/EBS cache (A3), which is already implemented for precalc and harvest, only missing
in the founder read path. A `Chunk.getBlock` redirect only helps if the mod stops at
`getBlockByExtId` (which EndlessIDs already `@Overwrite`s) — see B4.

### B2 — `Chunk.setBlockIDWithMetadata` / `World.isBlockLoaded` skip during fast writes
`World.setBlock` calls `isValid`/`isBlockLoaded` + neighborhood notifications. The mod
already avoids this via `func_150818_a` direct EBS writes. A mixin that made
`World.setBlock` with flag=2 skip `isBlockLoaded` re-checks would only matter if the mod
reverted to the `setBlock`-based path (the `MixinItemInWorldManager` path). **Not needed**
once the EBS fast path is the only non-TE path. Config-flag class.

### B3 — Lighting skip during fast batch writes
`ExtendedBlockStorage.func_150818_a` → `BlockLitRedstone`/`Block.scheduleBlockUpdate`
or light-recompute paths are avoided because the batch helper groups writes and updates
only the height map (ChunkBlockWriteHelper.updateHeightMap, lines 175-223), never
recomputing full chunk light. A mixin that forcibly skipped per-block light updates in
`func_150818_a` while EZMiner is batching would cut remaining per-block overhead, but the
height-map-only approach already avoids the costly light pass. **Config-flag class;
moderate expected saving only if light skips prove necessary.** (Deprecated light paths
in 1.7.10 mean correct light requires a `generateSkylightMap`/`propagateSkylightOcclusion`
recompute at batch end — risky.)

### B4 — Mixin the *founder-side* lookup instead of `Chunk.getBlock`
The highest-value, lowest-risk mixin: because `EndlessIDs` already `@Overwrite`s
`ExtendedBlockStorage.getBlockByExtId` (and `func_150818_a`) to handle extended ID arrays,
the mod can already call `getBlockByExtId` safely **without any new mixin** — which
`ChainPreCalcEngine` and `ChunkCachedHarvester` already do. Extending that same
chunk/EBS-cached `getBlockByExtId` read to the **founder threads** (A3) captures nearly
all the expected win **with zero new mixins**. Only if the founder must read a block
id+meta in one shot would a `Chunk.getBlock`+`getExtBlockMetadata` redirect be worth it
(~1 layer cut + 1 array-read), and only then behind a config flag.

### B5 — `ItemInWorldManager.tryHarvestBlock` → direct EBS-air (already done in the mod)
Already covered by `MixinItemInWorldManager` (fast harvest) plus the pure-Java
`ChunkBlockWriteHelper.writeAirToEbs`. **No new mixin needed**; the existing mixin should
be kept as the TE-blocks-only fallback and the non-TE path routed through
`ChunkCachedHarvester.executeBatch` (BlockHarvestActionExecutor.executeBatch, line 92)
which already groups by chunk.

---

## C) Safety on 1.7.10 GTNH + config-flag classification

**SAFE (no mixin or safe because EndlessIDs/Hodgepodge already coexist):**
- A1 (deduplicate the `getBlock`/`getBlockMetadata` pair in the founder hot path — pure
  event-layer, zero mixin). **Recommended first.**
- A2 (skip `Vector3i` allocation until a candidate survives visited/loaded checks).
- A3 (add chunk/EBS caching to founder read paths, mirroring `ChainPreCalcEngine` lines
  180-234). **Recommended.** Uses `getBlockByExtId` which EndlessIDs already patches.
- A4 (tune `searchBudgetPerYield`; non-zero default) — config knob.
- B5 (confirm non-TE harvest goes through `ChunkCachedHarvester.executeBatch`).

**MIXIN — safe but gated behind config flags (require restart like the fortune mixins),**
because they modify core Minecraft classes and interact with EndlessIDs/Hodgepodge mixins
that target the same classes:
- B1 (`Chunk.getBlock` redirect) and B3 (light skip) both touch classes EndlessIDs and
  Hodgepodge also mixin. On GTNH these mods are present and their mixins must win priority;
  EZMiner mixins must be **conditional** (`mixins.EZMiner.json` is `required: false`,
  and the existing capability gating via `Mixins.java` + `MixinCapabilityPlugin.targetHasMethod`
  should gate the new ones on the observed bytecode shape). Put new perf mixins behind a
  `Config` flag with **game-restart** semantics (same as `fortuneOverrideEnabled`), **not**
  hot-reload.

**Config-flag classification:**
- `enableChainChunkLoading` (Config.java line 97, default false): when true the founder
  loads chunks from disk → keep behind flag, currently only the precalc path honours it;
  the founder threads deliberately do NOT load chunks (BasePositionFounder constructor
  comment lines 99-114).
- `usePrimitiveVisitedSet` (line 201, default true) and `useDualFrontierBfs` (line 193,
  default false): these switch visited-set backend and BFS queue strategy — operator
  choice, keep as flags. `useDualFrontierBfs` default false means the PriorityQueue
  O(log n) path (A2/A3 apply to it) is the default.
- Any B1/B3 mixin: new flags, restart-only.

**Where to be maximally cautious:** GT ore reads go through tile entities
(`DeterminingIdentical` uses `TileEntityOres.mMetaData` reflection, lines 194-203) — any
"fast block read" mixin must **not** bypass tile-entity reads for GT/BW ores; a
block-id-only fast read would silently mis-identify GT ore veins. The pure-Java fix in A1
is the safe primary move for exactly this reason.

---

## Recommendation summary (ranked by expected win vs risk)

1. **A1** — founder passes pre-fetched block/meta into `DeterminingIdentical.identical`
   (event layer, zero risk, ~2× fewer full block reads per candidate).
2. **A3** — mirror the precalc/harvest chunk+EBS cache into founder read paths using
   EndlessIDs-safe `getBlockByExtId` (event layer, zero new mixin).
3. **A2** — delay `Vector3i` allocation until a candidate survives visited/loaded checks.
4. **A4** — raise default `searchBudgetPerYield` (config).
5. **B1/B3** — only as config-gated, restart-only, capability-gated mixins; skip unless
   profiling shows the remaining `World→Chunk→EBS` lookup or light pass is measurably hot.
6. **B5** — keep existing `MixinItemInWorldManager` but route non-TE harvests through
   `ChunkCachedHarvester.executeBatch`.
