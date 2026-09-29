# EZMiner chain subsystem audit (task t2)

Auditor: `audit-core-ds` (agent-teams member) · Date: 2026-07 · **Read-only audit**

Scope: `src/main/java/com/czqwq/EZMiner/chain/**` — `state/`, `lifecycle/`, `planning/`, `execution/`,
`mode/`, `network/`, `watchdog/`, `client/preview/`, plus the chain entry points/wiring in
`core/Manager.java`, `core/PlayerManager.java`, `core/BaseOperator.java`, `network/MainThreadEnforcer.java`
and the `Config` defaults they depend on (cited only as evidence for chain behaviour).

Ground truth used for every "vanilla/Forge behaves like X" claim: the decompiled sources already in the
working tree — `build/rfg/minecraft-src/java/net/minecraft/**` and `.../net/minecraftforge/**`.
Cross-mod claims cite `tmp/` sources.

Method: full read of every file in scope (58 chain files) + call-site greps for every "dead code" claim.
No build/Gradle/java was executed (sandbox rule); no file outside this report was created or modified.

Legend — Severity: **blocker** = crash/corruption/data loss · **high** = wrong behaviour or data loss in a
reachable case · **medium** = wrong behaviour in an opt-in/narrower case or measurable server cost ·
**low** = edge case, maintenance, cosmetic. Confidence: **high** = proven from source, **medium** = proven
mechanism but environment-dependent trigger, **low** = plausible, not fully proven.

---

## 1. Findings table

| # | Sev | Area | One-line problem | Primary citation | Conf |
|---|-----|------|------------------|------------------|------|
| F1 | **high** | lifecycle | In-flight chain drops **and** accumulated XP are destroyed (never flushed) on logout, respawn, dimension change and world unload | `chain/lifecycle/ChainLifecycleService.java:58-65`, `core/Manager.java:439-442,476` | high |
| F2 | medium | watchdog | Default-on tick watchdog (100 ticks = 5 s without a *harvest*) force-cancels legitimate chains whose first block / next block is still being searched or chunk-loaded | `chain/watchdog/ChainWatchdog.java:70-76`, `core/BaseOperator.java:177-183`, `Config.java:233,240` | high |
| F3 | medium | block swap | Block swap calls `Block.harvestBlock` **without** the vanilla `canHarvestBlock` guard and before `breakBlock` — drops for blocks the held tool cannot harvest, inverted harvest/break order | `chain/execution/BlockSwapModeHandler.java:114-119` | high |
| F4 | medium | entry points | The three right-click chain modes (crop, block swap, plant) run on **cancelled** `PlayerInteractEvent`s and never consult `world.canMineBlock` → protection/claim bypass | `core/Manager.java:149-164,177-216,225-250` | high |
| F5 | medium | special modes | Minesweeper/Sudoku handlers copy the entire `loadedTileEntityList` and reflect per TE **on every server tick** while the key is held (no throttle on the scan) | `chain/execution/MinesweeperModeHandler.java:35`, `LootGamesMinesweeperBridge.java:103-107`, `SudokuModeHandler.java:36,48` | high |
| F6 | medium | special modes / cross-mod | Minesweeper/Sudoku helpers mutate **every loaded** LootGames game in the world (not just the player's/nearest one) and are not gated by any config | `LootGamesMinesweeperBridge.java:154-186`, `LootGamesSudokuBridge.java:236-252`, `core/MinerModeState.java:163-167` | high |
| F7 | medium | execution | `enableChainChunkLoading` preloader loads at most **one chunk per shell** and never resumes the shell → loads ~`maxChunkRadius+1` chunks instead of the scanned square | `chain/execution/ChunkPreloader.java:85-124` | high |
| F8 | medium | planning/network | Cached pre-calculation re-sends the whole growing position list S→C every tick → O(n²) bytes per pre-calculation | `chain/planning/ChainPreCalcEngine.java:287-292`, `chain/network/PacketCachedBlockSync.java:54-66` | high |
| F9 | medium | execution / XP | `mergeXPOrbs` merges all XP into one orb; `EntityXPOrb` persists `xpValue` as an NBT **short** → ⩾32768 XP truncates to a negative value on chunk save/reload | `chain/execution/XPDropHandler.java:160-167` + `EntityXPOrb.java:219,229,245` | medium |
| F10 | medium | execution | Batched neighbour notify does an **unguarded** `world.getBlock` before calling `onNeighborBlockChange` directly, bypassing Hodgepodge's chunk-load guard | `chain/execution/ChunkBlockWriteHelper.java:240-248` + Hodgepodge mixin | medium |
| F11 | medium | block swap | `blockSwapRadius` is configurable up to `Integer.MAX_VALUE` and the search is a full O(R³) world-read scan on the server thread inside the interact event | `Config.java:606-612`, `BlockSwapModeHandler.java:159-194` | high |
| F12 | low | network / state | Deferred C→S handlers can re-create a `ChainPlayerState` **after** logout (no online check) → permanently stale map entries | `network/MainThreadEnforcer.java:52-65,71-82`, `chain/state/ChainStateService.java:18-20,28-31` | high |
| F13 | low | execution | Crop/single path applies configured exhaustion even when the harvest failed (batched path does not) | `chain/execution/ChainHarvestExhaustionStrategy.java:23-34` vs `core/BaseOperator.java:514` | high |
| F14 | low | lifecycle | `EZMiner.parallelTick` task lists are never cleared on server start/stop → stale stopped founders survive a JVM-internal world reload | `CommonProxy.java:61-65`, `EZMiner.java:47`, `thread/Pauseable.java:99-110` | medium |
| F15 | low | watchdog | Watchdog progress ticks mix `MinecraftServer.getTickCounter()` with `System.currentTimeMillis()/50` (different bases → spurious timeout) | `chain/watchdog/ChainWatchdog.java:85-95` | medium |
| F16 | low | execution | `ChainDropCollector` merges stack sizes with unchecked `int +=` (overflow wraps negative → malformed `EntityItem`) | `chain/execution/ChainDropCollector.java:68,77` | high (mechanism) |
| F17 | low | dead code | `chain/planning` planner scaffolds, `chain/mode` registries, `ChainRequest`, several executors/helpers and 3 state fields are unreachable (quantified in §4) | see §4 | high |
| F18 | low | network | `PacketChainModeSwitch` is accepted mid-chain and changes the *running* operator's executor selection; mode index bounds are duplicated literals | `chain/network/PacketChainModeSwitch.java:17-23,60-67`, `core/BaseOperator.java:217,436-437` | high |
| F19 | low | planning | `ChainPreCalcEngine.tick` calls `stop(player)` (clears cache + 4 collections + `ConcurrentHashMap.remove`) on every tick for every player in a non-cached mode | `chain/planning/ChainPreCalcEngine.java:78-82,98-111` | high |
| F20 | low | client/preview | `ChainPreviewState.target` is written and never read | `chain/client/preview/ChainPreviewState.java:8`, `client/render/MinerRenderer.java:196,243` | high |

---

## 2. Detailed findings

### F1 — Logout / respawn / dimension change / world unload destroys pending drops and XP (data loss)
Severity **high** · Confidence **high** · Status of known item: **still present** (`docs/review/full-bug-scan.md` #7)

Evidence — `src/main/java/com/czqwq/EZMiner/chain/lifecycle/ChainLifecycleService.java`

```java
22:    public void onPlayerLogout(UUID playerUUID, Map<UUID, Manager> managers) {
23:        EZMiner.chainStateService.onPlayerLogout(playerUUID);
24:        CooldownTracker.clear(playerUUID);
25:        Manager mgr = managers.remove(playerUUID);
26:        if (mgr != null) {
27:            stopRuntime(mgr);
28:            mgr.unRegistry();
29:        }
...
33:    public void onPlayerRespawn(...) { EZMiner.chainStateService.onPlayerRespawn(...); cleanupManagerRuntime(...); ... }
39:    public void onPlayerDimensionChanged(...) { ... cleanupManagerRuntime(...); ... }
45:    public void onWorldUnload(Map<UUID, Manager> managers) { ... for (Manager mgr : managers.values()) stopRuntime(mgr); }
58:    private void stopRuntime(Manager mgr) {
59:        if (mgr.operator != null) { mgr.operator.stopImmediately(); mgr.operator = null; }
63:        mgr.cleanupState();
64:        mgr.clearDrops();          // ← discards, never flushes
65:    }
```

`core/Manager.java:439-442` and `:461-479`:

```java
439:    public void clearDrops() {
440:        dropCollector.clear();
441:        XPDropHandler.clear(player);
442:    }
...
461:    public void cleanupState() {
...
476:        XPDropHandler.clear(player);
```

`core/BaseOperator.java:375-386` (`stopImmediately`) also ends in `cleanupOperatorState()` with no flush, and
`dropImmediately` is **false** by default (`Config.java:47`), so a whole chain's drops accumulate in the
`ChainDropCollector` until the key is released.

Failure scenario: player holds the chain key mining a 1024-block vein (`Config.blockLimit=1024`); the
connection drops (timeout/kick/crash) or the player logs out without the key-release packet reaching the
server. `PlayerManager.onPlayerLogout` (`core/PlayerManager.java:58-67`) → `stopRuntime` → `clearDrops`, i.e.
every mined item and every accumulated XP from the current batch is deleted; nothing is spawned. Same code
path runs on `PlayerRespawnEvent`/`PlayerChangedDimensionEvent` (`core/PlayerManager.java:69-81`) where the
player is still online and the drops could trivially be delivered. (`enableDropFallbackChain=true`,
`Config.java:247`, shows the intended "never lose drops" contract — this path bypasses it entirely.)

Minimal fix sketch: in `stopRuntime`, call `mgr.flushDrops()` (already public, `Manager.java:352`) **before**
`cleanupState()/clearDrops()`; for `onWorldUnload` prefer *not* clearing the collector (the manager is not
removed there, so the batch can flush when the world is loaded again). For logout, removing the manager
after the flush is enough (world/chunk availability handled by the existing fallback chain).

### F2 — Tick watchdog cancels legitimate chains that have not harvested yet
Severity **medium** · Confidence **high**

Evidence — `chain/watchdog/ChainWatchdog.java`

```java
49:    public static void markChainStarted(UUID playerUUID) { ... LAST_PROGRESS_TICK.put(playerUUID, currentServerTick()); }
58:    public static void recordProgress(UUID playerUUID) { ... LAST_PROGRESS_TICK.put(playerUUID, currentServerTick()); }
70:    public static boolean hasTimedOut(UUID playerUUID) {
72:        Long last = LAST_PROGRESS_TICK.get(playerUUID);
73:        if (last == null) return false;
74:        long current = currentServerTick();
75:        return (current - last) >= Config.chainWatchdogTimeoutTicks;
76:    }
```

`core/BaseOperator.java`

```java
177:        if (Config.enableChainWatchdog && ChainWatchdog.hasTimedOut(manager.playerUUID)) {
178:            MessageUtils.serverSendPlayerMessage(new ChatComponentTranslation("ezminer.message.chain.watchdog_timeout"), ...);
181:            unRegistry();
182:            return;
183:        }
185:        if (canBreakPositions.isEmpty()) {
186:            if (planningTask.isStopped()) unRegistry();
187:            return;
188:        }
```

`registry()` arms the timer (`ChainWatchdog.markChainStarted`, `BaseOperator.java:336`) and only
`markHarvested()` (`:403-408`) refreshes it. `enableChainWatchdog=true` and
`chainWatchdogTimeoutTicks=100` (5 s at 20 TPS) are the shipped defaults (`Config.java:233,240`).

Failure scenario: a normal chain whose founder needs >100 ticks before the first candidate arrives — a
big-radius blast / ore / log search (`logBigRadius` is 1024 by default, `Config.java:620-626`) on a lagging
server, a vein whose first candidates are only reached after several shell layers, or an operator that is
waiting in the tool-handoff state (`BaseOperator.java:190-206`, up to
`toolBreakHandoffTimeoutTicks=5` — harmless) while the founder scan stalls. The operator is cancelled with a
"watchdog timeout" message even though it is making progress; the gate duplicates the wall-clock idle timeout
(50 s + 10 s countdown, `Config.java:104,110`) but is checked *before* the `canBreakPositions.isEmpty()`
early-return, so the documented 50 s grace period is never reached.
(Secondary, weaker: `enableChainChunkLoading` also makes progress tick-based, but see F7 — that feature
currently loads almost nothing.)

Minimal fix sketch: arm the watchdog at the first successful harvest instead of at `registry()`
(move `markChainStarted` into `markHarvested`, or store "armed" separately), and/or skip the check while
`canBreakPositions.isEmpty()` and the planning task is not stopped; also refresh progress when the planning
task enqueues new positions.

### F3 — Block swap drops blocks the held tool cannot harvest (and inverts vanilla order)
Severity **medium** · Confidence **high**

Evidence — `chain/execution/BlockSwapModeHandler.java`

```java
114:            // Harvest the original block (fires HarvestDropsEvent → drops collected
115:            // by ChainDropCollector when inOperate is set).
116:            oldBlock.harvestBlock(world, player, pos.x, pos.y, pos.z, oldMeta);
117:
118:            // Replace with the new block
119:            world.setBlock(pos.x, pos.y, pos.z, replacementBlock, replacementMeta, 3);
```

Vanilla — `build/rfg/minecraft-src/java/net/minecraft/server/management/ItemInWorldManager.java`

```java
310:                boolean flag1 = block.canHarvestBlock(thisPlayerMP, l);
322:                flag = this.removeBlock(p_73084_1_, p_73084_2_, p_73084_3_, flag1);
323:                if (flag && flag1)
324:                {
325:                    block.harvestBlock(this.theWorld, this.thisPlayerMP, ...);
326:                }
```

Failure scenario: in block-swap mode a player holds a wooden pickaxe and right-clicks an obsidian (or
diamond-block / GT machine) block: `canHarvestBlock` is false in vanilla, so vanilla gives no drops and no
world change; EZMiner calls `harvestBlock` unconditionally, handing the player the block item, then replaces
it with the held item. Additionally the mod's order is inverted relative to vanilla (`harvestBlock` runs
*while* the block and its TE still exist; vanilla runs `breakBlock` during removal first) — for TE blocks
that implement `breakBlock` for inventory drops and `harvestBlock` for the block item, this is a
behaviour change that vanilla never exercises.

Minimal fix sketch: guard line 116 with `oldBlock.canHarvestBlock(player, oldMeta)
|| WitcheryVampireBridge.canHarvestWithBareHands(player)` (same predicate used by the harvest executors), and
prefer remove-then-harvest (or `world.func_147480_a`/`setBlockToAir` first) to match vanilla ordering.

### F4 — Crop / block-swap / planting modes act on cancelled interactions and skip protection checks
Severity **medium** · Confidence **high**

Evidence — `core/Manager.java`

```java
149:    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
150:    public void onCropRightClick(PlayerInteractEvent event) {
...
177:    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
178:    public void onBlockSwapRightClick(PlayerInteractEvent event) {
...
225:    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
226:    public void onPlantRightClick(PlayerInteractEvent event) {
```

None of the three bodies inspects `event.isCanceled()`; all three end with `event.setCanceled(true)`
(`:163`, `:215`, `:249`). Vanilla honours the cancellation (`ItemInWorldManager.java:386-391` returns
immediately when Forge's `PlayerInteractEvent` is canceled), and region/claim plugins implement their
protection by cancelling exactly this event. `BlockSwapModeHandler.handleSwap` performs the world writes
directly (`BlockSwapModeHandler.java:31-145`) without `world.canMineBlock(...)`/`isBlockProtected`, unlike
vanilla (`WorldServer.canMineBlock` → `isBlockProtected`).

Failure scenario: a claim/protection mod cancels `RIGHT_CLICK_BLOCK` inside a protected region. EZMiner's
`receiveCanceled = true` handler still runs → in block-swap mode it harvests and replaces every matching
block in range; in planting mode it replays `onItemUse` over the whole radius; in crop mode it starts a
crop chain. This is the right-click analogue of `docs/review/harvest-path-review.md` C1.

Minimal fix sketch: return early when `event.isCanceled()` (or downgrade the priority and stop using
`receiveCanceled=true` where vanilla already denies), and add a `world.canMineBlock(player, x, y, z)` check
per modified position in `BlockSwapModeHandler`/`PlantingModeHandler`.

### F5 — Special-mode handlers scan the whole `loadedTileEntityList` every tick
Severity **medium** · Confidence **high**

Evidence — `core/Manager.java:342-345` calls `tickSpecialMode()` every world tick while the key is held;
`chain/execution/MinesweeperModeHandler.java:35` then does `boolean gameActive = bridge.isAnyGameActive(player.worldObj);`
**before** any cooldown gate (the cooldown is only consulted at `:44-47`), and

```java
// chain/execution/LootGamesMinesweeperBridge.java
103:            @SuppressWarnings("unchecked")
104:            List<TileEntity> loadedTileEntities = new ArrayList<>(world.loadedTileEntityList);
105:            for (TileEntity te : loadedTileEntities) {
...
110:                Object game = getGameMethod.invoke(te);
112:                if (!((Boolean) isBoardGeneratedMethod.invoke(game))) continue;
113:                Object stage = getStageMethod.invoke(game);
```

`SudokuModeHandler.tick` does the same twice per tick (`SudokuModeHandler.java:36` and `:48` →
`LootGamesSudokuBridge.isAnyGameActive` / `getBoardFingerprint`, both copying + reflecting over the same list).

Failure scenario: on a GTNH server the loaded-TE list routinely contains tens of thousands of entries; a
player standing in a base holding the chain key in minesweeper/sudoku mode makes the server allocate and
reflect over that whole list 1–2× **per tick** forever (≈20–40 full list traversals/second). This is pure
overhead even when no LootGames board exists.

Minimal fix sketch: memoise "is a board active" on a cooldown (e.g. the existing probe interval, or a
1 s/20-tick cache), and stop iterating once a matching TE was handled.

### F6 — LootGames helpers mutate every loaded board, with no config gate
Severity **medium** · Confidence **high** (cross-mod, §5)

Evidence — `chain/execution/LootGamesMinesweeperBridge.java`

```java
134:            for (TileEntity te : loadedTileEntities) {
136:                if (!msMasterTileClass.isInstance(te)) continue;
...
154:                for (int x = 0; x < size; x++) {
155:                    for (int z = 0; z < size; z++) {
157:                        if (type != bombTypeConstant) continue;
158:                        if (!((Boolean) boardIsHiddenMethod.invoke(board, x, z))) continue;
159:                        if (boardGetMarkMethod.invoke(board, x, z) != noMarkConstant) continue;
...
179:            Object pos2i = pos2iConstructor.newInstance(best.boardX, best.boardZ);
180:            stageSwapFieldMarkMethod.invoke(best.stage, pos2i);   // ← writes the game state
```

The inner loops do not filter by board ownership/proximity to the player — the only distance use is
*choosing* the nearest bomb (`:165-168`) among **all** boards of **all** players in the loaded world.
`chain/execution/LootGamesSudokuBridge.java:236-246` writes the solution straight into the board's
`player` grid of the selected game and then calls `save()` / `checkWin()` / `onLevelSuccessfullyFinished()`.
Gating: `core/MinerModeState.java:163-167` returns `true` for special indices 0/1/2/5 unconditionally —
there is no `Config` switch that a server owner can use to disable minesweeper/sudoku assistance.

Failure scenario: player A walks through a labyrinth/base where player B has an unsolved (or solved) LootGames
board loaded and holds the chain key in minesweeper mode → EZMiner writes marks into B's board each cooldown;
the same for sudoku (fills cells + `save()`). This consumes/alters another player's minigame progress with no
proximity, ownership or opt-in check. (It is the documented purpose of the mode to *assist*, so the issue is
the unbounded scope and the missing server toggle, not the existence of the feature.)

Minimal fix sketch: restrict the scan to the board the player is looking at / standing on (e.g. reuse
`Manager.onBlockBreak`-style ray-trace or a `Config.specialModeMaxDistance` filter), and add a server config
switch per special mode (minesweeper/sudoku/prospect/block swap already has one).

### F7 — `ChunkPreloader` never resumes a chunk shell
Severity **medium** · Confidence **high**

Evidence — `chain/execution/ChunkPreloader.java`

```java
92:        while (chunkRadius <= maxChunkRadius && count < PER_TICK) {
93:            int r = chunkRadius;
94:            boolean anyNew = false;
96:            for (int dx = -r; dx <= r && count < PER_TICK; dx++) {
97:                for (int dz = -r; dz <= r && count < PER_TICK; dz++) {
98:                    if (r > 0 && Math.abs(dx) < r && Math.abs(dz) < r) continue;
...
116:                    count++;
118:            }
119:            chunkRadius++;
```

`PER_TICK = 1` (`:42`) and the loops bail out as soon as `count == PER_TICK`, after which `chunkRadius++`
runs unconditionally: only the **first** cell of each shell is ever visited. Total chunks loaded for a chain
= `maxChunkRadius + 1` (= 2 for the default `bigRadius` of a typical preview/blast chain, at most ~5 for
radius 64) instead of the `(2·maxChunkRadius+1)²` square the class documents (`:79-84`). The loaded-set is
also polluted with the skipped cells (`:103` adds before the disk check), so a later retry cannot reach them
either.

Failure scenario: an operator enables `enableChainChunkLoading` (`Config.java:97`, default false) to make
large veins in previously-explored terrain mineable; the founder still cannot see the vein because the
needed chunks are never loaded, while the log/behaviour suggests the feature is active.

Minimal fix sketch: persist a per-shell cell cursor (or a queue of remaining cells for the current shell) and
only advance `chunkRadius` once the shell has been fully enumerated.

### F8 — Cached pre-calculation re-sends the whole list every tick
Severity **medium** (opt-in) · Confidence **high**

Evidence — `chain/planning/ChainPreCalcEngine.java`

```java
287:            boolean done = frontier.isEmpty() || results.size() >= pConfig.blockLimit;
290:            dirty = true;
291:            EZMiner.network.network
292:                .sendTo(new PacketCachedBlockSync(results, center.x, center.y, center.z, dimension), player);
```

`PacketCachedBlockSync.toBytes` writes every position (`PacketCachedBlockSync.java:60-65`, 12 bytes each).
While the BFS runs, `results` grows to `pConfig.blockLimit` (server default `Config.blockLimit = 1024`,
`Config.java:520-526`; the config file accepts up to `Integer.MAX_VALUE`, and `Manager.receiveClientConfig`
only caps a client value against that same server value, `Manager.java:485`) and the full list is serialised
on **every tick** of the BFS — O(n²) bytes per pre-calculation (for the 1024 default ≈ many MB per
pre-calculation), plus a fresh allocation on the netty write path each tick. Opt-in:
`enableCachedChain=false` by default (`Config.java:89`).

Minimal fix sketch: send incremental deltas (append-only index range since the last packet), or throttle to
e.g. every 5 ticks.

### F9 — Merged XP orb exceeds the NBT short range → negative XP after a chunk save
Severity **medium** · Confidence **medium** (mechanism certain, needs the orb to survive a save)

Evidence — `chain/execution/XPDropHandler.java`

```java
160:        if (mergeIntoOne) {
161:            int total = 0;
162:            for (int v : values) { total += v; }
165:            if (total > 0) {
166:                world.spawnEntityInWorld(new EntityXPOrb(world, x, y, z, total));
167:            }
```

`mergeXPOrbs` is `true` by default (`Config.java:70`) and this is the default `xpDropMode == 1` path
(`Config.java:63`). Vanilla orb persistence — `build/rfg/minecraft-src/java/net/minecraft/entity/item/EntityXPOrb.java`

```java
219:        tagCompound.setShort("Value", (short)this.xpValue);
...
229:        this.xpValue = tagCompund.getShort("Value");
...
245:                entityIn.addExperience(this.xpValue);
```

A single orb with `total >= 32768` (reachable: a 1024-block vein at 4–7 XP/block, or several chains without
releasing the key, since the accumulator is only flushed on key release / `flushDrops`) is written as a
negative short and re-read as a negative value; `onCollideWithPlayer` then *removes* XP from the player.

Note: the previously reported variant of this item (`full-bug-scan.md`, "XPDropHandler.flush merged single orb
can exceed per-orb cap ... losing XP") does **not** reproduce — `EntityXPOrb.xpValue` is an `int` and `addExperience(xpValue)` awards the full
amount in memory; the real defect is the NBT short round-trip.

Minimal fix sketch: split `total` into orbs of ≤ 2477 (vanilla's largest split) or ≤ 32767, or
`clear()` the accumulator when it exceeds the threshold.

### F10 — Unguarded `getBlock` in the neighbour-notify sweep bypasses Hodgepodge's chunk-load guard
Severity **medium** · Confidence **medium**

Evidence — `chain/execution/ChunkBlockWriteHelper.java`

```java
240:                Block nb = world.getBlock(nx, ny, nz);
241:                if (nb == null || nb == Blocks.air) continue;
242:                // Notify one neighbour of the block change. Direct onNeighborBlockChange
243:                // (identical to World.notifyBlockOfNeighborChange's body) so no
244:                // World-layer wrapper (e.g. Hodgepodge MixinWorld_PreventChunkLoading)
245:                // can intercept it, ...
248:                    nb.onNeighborBlockChange(world, nx, ny, nz, rb.oldBlock);
```

The comment is accurate about the *notification* (Hodgepodge only wraps `getBlock` **inside**
`World.notifyBlockOfNeighborChange`), but the wrapper it bypasses is precisely the guard that the preceding
`world.getBlock` needs: `tmp/Hodgepodge-master/src/main/java/com/mitchej123/hodgepodge/mixins/early/minecraft/chunkloading/MixinWorld_PreventChunkLoading.java:45-54`
returns `Blocks.air` when `!blockExists(x, y, z)` "to prevent them from loading chunks". Since EZMiner calls
`getBlock` itself, the guard never applies. `notifyNeighborsOnChainBreak` is `true` by default
(`Config.java:307`), so this runs for every chain batch.

Failure scenario: mining along the loaded-area border (e.g. at the edge of a player's loaded region) — the
6-face sweep resolves neighbours in unloaded chunks; `World.getBlock` → `getChunkFromChunkCoords` →
`ChunkProviderServer.provideChunk` may load/generate the chunk synchronously on the server thread,
contradicting the mod's "never background/off-thread / no unexpected loads" contract and producing a
multi-second MSPT spike on GTNH worldgen.

Minimal fix sketch: `if (!world.blockExists(nx, ny, nz)) continue;` before line 240 (this is exactly what the
existing `flagNeighbouringLeavesForDecay` already does at `:328`).

### F11 — Block-swap search cost scales with an unbounded config radius, on the server thread
Severity **medium** · Confidence **high**

Evidence — `Config.java`

```java
606:        blockSwapRadius = serverConfiguration.getInt(
610:            0,
611:            Integer.MAX_VALUE,
612:            "Maximum radius for block swap mode. ...");
```

`chain/execution/BlockSwapModeHandler.java:159-194` walks every Chebyshev shell up to `maxRadius` with
`world.blockExists` + `world.getBlock` + `DeterminingIdentical.identical(...)` per position (which itself may
touch tile entities) and allocates a `Vector3i` per match. The whole scan runs inside
`Manager.onBlockSwapRightClick` (`core/Manager.java:205-212`) on the server thread, i.e. inside one tick.
The 150 ms debounce only suppresses *identical* click positions (`core/Manager.java:198-203`), so a client
script can alternate between adjacent blocks to bypass it.

Failure scenario: with the default `blockSwapRadius=8` a sparse target costs 17³ ≈ 4.9 k world reads per
right-click (a visible hitch, repeatable at client click rate); with `blockSwapRadius=2000` the same code
attempts ≈ 6.4·10¹⁰ reads and effectively hangs the server tick. `blockSwapLimit` does not bound the scan
(it only caps the matches found).

Minimal fix sketch: clamp the configured radius to a sane maximum at load/apply time (like
`plantRadius`/`plantMaxCount` do at `Config.java:1002-1003`), and/or bound the per-call scan budget with an
equivalent of `Config.breakPerTick` (continue over ticks).

### F12 — Deferred packet handlers can re-create state for a logged-out player
Severity **low** · Confidence **high**

Evidence — `network/MainThreadEnforcer.java:52-65` queues the body on the netty thread; `:71-82` drains it at
the next `ServerTickEvent` START (`core/PlayerManager.java:90-97`). The body resolves the player lazily and
then calls `getOrCreate`:

```java
// chain/network/PacketKeyState.java
40:                EntityPlayerMP player = ctx.getServerHandler().playerEntity;
41:                ChainPlayerState state = EZMiner.chainStateService.getOrCreate(player.getUniqueID());
42:                state.keyPressed = msg.pressed;
// chain/state/ChainStateService.java
18:    public ChainPlayerState getOrCreate(UUID playerUUID) {
19:        return stateMap.computeIfAbsent(playerUUID, ChainPlayerState::new);
20:    }
28:    public void onPlayerLogout(UUID playerUUID) { ChainPlayerState state = stateMap.remove(playerUUID); ... }
```

Failure scenario: the connection drops in the same tick that a key/mode packet was received (packet already
queued, logout processed before the drain) → the deferred body re-inserts a fresh `ChainPlayerState` for an
offline UUID. No later logout event arrives for that UUID, so the entry (a `MinerConfig`, a `MinerModeState`,
a `ChainRuntimeState`) stays in the map forever; repeated logouts under load leak one entry each.
`PacketChainModeSwitch.java:60-63` has the identical pattern.

Minimal fix sketch: check `PlayerManager.instance.managers.containsKey(uuid)` (or
`player.playerNetServerHandler` liveness) inside the deferred body before mutating/re-creating state, and use
`stateMap.get()` instead of `getOrCreate()` on the packet path.

### F13 — Exhaustion is applied for failed harvests on the crop/single path
Severity **low** · Confidence **high**

Evidence — `chain/execution/ChainHarvestExhaustionStrategy.java`

```java
23:    public boolean harvestWithConfiguredExhaustion(EntityPlayerMP player, Vector3i pos, float configuredExhaustion,
24:        ChainActionExecutor actionExecutor) {
25:        FoodStats food = player.getFoodStats();
26:        float exhaustionBefore = getExhaustion(food);
27:        boolean harvested = actionExecutor.execute(pos, player);
28:        try {
29:            FOOD_EXHAUSTION_LEVEL.setFloat(food, exhaustionBefore + configuredExhaustion);
```

`harvested` is ignored. The batched paths do it correctly (`core/BaseOperator.java:514`:
`exhaustionBefore + harvested * addExhaustion`).

Failure scenario: crop mode — a mature crop that passes `shouldHarvest`
(`core/BaseOperator.java:396-400` → `CropAdapterRegistry.isMatureCrop`) but whose adapter refuses the actual
harvest (`core/crop/CropAdapterRegistry.java:82-90` returns `false` when the recognized adapter declines,
e.g. a crop that cannot be re-planted) still charges the player `addExhaustion` for that position, because
`harvestWithConfiguredExhaustion` ignores its own `harvested` flag. A crop chain whose adapter declines on
many positions therefore drains hunger without harvesting anything — the batched block paths never do this
(`core/BaseOperator.java:514` uses `harvested * addExhaustion`).

Minimal fix sketch: `if (harvested) { ...apply... }` (or return early when `!harvested`).

### F14 — ParallelTick task lists are never cleared across a JVM-internal server restart
Severity **low** · Confidence **medium** · Status of known item: **still present** (`full-bug-scan.md` #11)

Evidence — `EZMiner.java:47` (`public static final ParallelTick parallelTick = new ParallelTick();`),
`CommonProxy.java:61-65` (`serverStarting` only re-creates `PlayerManager`), `thread/ParallelTick.java:20-30`
(`removeIf(stopped)` runs only on tick END, `unPause()` on a stopped task at `:27`),
`thread/Pauseable.java:99-110` (`errorCount > 10` → `throw new RuntimeException`).
Returning to the main menu and opening a new world (or `/reload`) keeps the previous founders in
`preTickTasks`; an unPause/pause on a stale stopped thread increments `errorCount` and can throw **on the
server tick thread**.

Minimal fix sketch: clear `parallelTick.preTickTasks`/`normalTasks` in `CommonProxy.serverStopping` (and
defensively in `serverStarting`).

### F15 — Watchdog mixes tick-counter and wall-clock bases
Severity **low** · Confidence **medium** · Status of known item: **still present** (`full-bug-scan.md` #13)

Evidence — `chain/watchdog/ChainWatchdog.java:85-95`

```java
87:            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
88:            if (server != null) { return server.getTickCounter(); }
...
94:        return System.currentTimeMillis() / 50;
```

`markChainStarted`/`recordProgress` can write a small tick counter while `hasTimedOut` reads
`currentTimeMillis()/50` (≈3.5·10¹⁰) whenever the server instance is transiently unavailable → the difference
explodes past `chainWatchdogTimeoutTicks` and the chain is cancelled spuriously. Same class as F2.

Minimal fix sketch: return `-1` when the server is unavailable and make `hasTimedOut` treat a changing base as
"no timeout" (or cache the base chosen at chain start).

### F16 — `ChainDropCollector` int merge overflow
Severity **low** · Confidence **high** (mechanism) — Status of known item: **still present**
(`full-bug-scan.md` #ChainDropCollector)

Evidence — `chain/execution/ChainDropCollector.java:68` (`existing.stackSize += drop.stackSize;`) and `:77`
(same for the NBT list). No cap: with a big enough chain (or repeated chains while the key stays held, since
the collector accumulates until the flush) a single key can exceed `Integer.MAX_VALUE`, wrap negative, and
`flush` (`:96-106`) then spawns an `EntityItem` whose `stackSize <= 0` is skipped (`:98`) — items silently
vanish. Practically unreachable (2.1 G items of one type), so low.

Minimal fix sketch: use a `long` accumulator per key and split into ≤64 item stacks on flush.

### F17 — Dead surface (quantified; see §4)

### F18 — Mode switch is accepted mid-chain and bounds are duplicated literals
Severity **low** · Confidence **high**

Evidence — `chain/network/PacketChainModeSwitch.java`

```java
17:    private static final int MAX_MAIN_MODE = 2;
19:    private static final int MAX_BLAST_MODE = 6;
21:    private static final int MAX_CHAIN_MODE = 3;
23:    private static final int MAX_SPECIAL_MODE = 5;
...
60:            return MainThreadEnforcer.guardedNull(ctx.side, () -> {
62:                ChainPlayerState state = EZMiner.chainStateService.getOrCreate(player.getUniqueID());
63:                state.minerModeState.mainMode = msg.mainMode;
```

The clamps match the current arrays (`core/MinerModeState.java:37-62`), but they are unlinked copies — a new
sub-mode silently makes a legitimately selected mode unreachable (it is clamped away) rather than a wrong
value being rejected. Also nothing prevents a mode change during an active chain, while
`core/BaseOperator.java:217` and `:436-437` select the running executor from `manager.isSpecialCropMode()`
every tick: switching to crop mode mid-blast makes the remaining queue run through
`CropHarvestActionExecutor` (`chain/execution/CropHarvestActionExecutor.java:21-23`), aborting the harvest
(no crash, no world-write hazard — `CropAdapterRegistry` returns false for non-crops).

Minimal fix sketch: derive the bounds from `MAIN_MODES.length`/`BLAST_MODES.length`/…, and latch the mode
per chain (read it in `startChain` and keep it in `BaseOperator`) instead of re-reading it per tick.

### F19 — Pre-calc engine `stop()` runs for every non-cached player every tick
Severity **low** · Confidence **high**

Evidence — `chain/planning/ChainPreCalcEngine.java:78-82` (`if (!modeState.isCachedChainMode()) { stop(player); return; }`)
and `Manager.java:344`/`:348` (called while the key is held **and** on release). `stop` → `clearState()`
(4 collection clears) + `ChainPreCalcCache.remove(uuid)` (a `ConcurrentHashMap.remove`) per tick per player.
No packet is sent thanks to the `dirty` flag (`:101-109`), so this is only wasted work, not traffic. Note the
`stop()` packet-spam item from the previous review is **fixed** (see §3).

### F20 — `ChainPreviewState.target` is dead
Severity **low** · Confidence **high** — written at `MinerRenderer.java:196` (`previewController.setTarget`)
and `:243` (`setTarget(null)`), read nowhere (only `frozen` is read, `MinerRenderer.java:165-166`).

---

## 3. Re-verified previously reported items

| Prev. item (doc) | Current status | Evidence |
|---|---|---|
| `full-bug-scan.md` #9 — `ChainPreCalcEngine` un-bounds-checked `storage[y4]` → AIOOBE | **fixed** | `ChainPreCalcEngine.java:241` `currentEbs = (storage != null && y4 >= 0 && y4 < storage.length) ? storage[y4] : null;` |
| `full-bug-scan.md` #4/#8 + `MainThreadEnforcer` "only logs, never defers" | **fixed** | `network/MainThreadEnforcer.java:52-65` queues into `DEFERRED`; `:71-82` `drainDeferred()`; drained on the server thread from `core/PlayerManager.java:94-96` |
| `full-bug-scan.md` #5 — `PlayerManager.managers` plain `HashMap` | **fixed** | `core/PlayerManager.java:41` `new ConcurrentHashMap<>()` |
| AGENTS.md "ChainPreCalcEngine.stop only pushes the client clear packet when a pre-calculation actually reached the client" | **fixed** | `ChainPreCalcEngine.java:68,101-109` (`dirty` flag) |
| `full-bug-scan.md` #7 — logout/world-unload discards in-flight drops | **still present** | F1 (`ChainLifecycleService.java:63-64`, `Manager.java:439-442,476`) |
| `full-bug-scan.md` #11 — `ParallelTick` tasks not cleared across JVM-internal restarts | **still present** | F14 (`CommonProxy.java:61-65`) |
| `full-bug-scan.md` #12 — `Manager.receiveClientConfig` ignores client `logFuzzyEnabled`/cooldown caps | **still present, by design** | `core/Manager.java:490` forces `Config.logFuzzyEnabled`; `:484-492` caps everything against server limits. Server-authoritative, not a defect. |
| `full-bug-scan.md` #13 — watchdog tick/wall-clock base mixing | **still present** | F15 |
| `full-bug-scan.md` #15 — `CooldownTracker` static `HashMap` | **still present, latent** | `chain/execution/CooldownTracker.java:28`; all current callers are server-thread (`Manager.onBlockBreak:134`, `onCropRightClick:156`, `onBlockSwapRightClick:183`, `onPlantRightClick:232`, `BaseOperator.unRegistry:351`, `ChainLifecycleService.java:24`) so it is safe today. |
| `full-bug-scan.md` — `ChainDropCollector` stack-size overflow | **still present** | F16 |
| `full-bug-scan.md` — `XPDropHandler.flush` merged orb can exceed a per-orb cap | **re-interpreted** | not a cap loss; the real mechanism is the NBT short round-trip — F9 |
| `full-bug-scan.md` #1/#2/#3/#14 — IO-thread mutation of `Config`/`PlayerManager`/inventory by `PacketSaveServerConfig`, `PacketInventorySwap`, `PacketReloadServerConfig`, `PacketMinerConfig` | **out of scope (network/)**, `managers` part fixed | files live in `src/main/java/com/czqwq/EZMiner/network/**`, not in this task's scope; `PacketKeyState`/`PacketChainModeSwitch` (which are in scope) now defer correctly. |
| AGENTS.md / CLAUDE.md — "`chain/mode` package and `chain/planning` planner scaffolds are write-only" | **confirmed still dead**, now quantified | §4 |
| `harvest-path-review.md` C1 — fast harvest skips `canMineBlock`/protection even with `fireBreakEvent=false` | **partially in scope and still present for the chain modes** | `ChainBreakEventHelper.fireIfEnabled` returns `null` when `Config.fireBreakEvent=false` (`Config.java:159`, default false) and no executor consults `world.canMineBlock` (`BlockHarvestActionExecutor.java:87-93`, `ChunkCachedHarvester.java:142-143`); the right-click modes are F4. |
| `harvest-path-review.md` Bug A/B (floating neighbours, stale metadata) | **fixed (verified)** | `ChunkBlockWriteHelper.java:181-185` zeroes metadata after `func_150818_a`; `notifyBatchNeighborChange` (`:228-294`) fires the neighbour notifications with the old block; `Config.notifyNeighborsOnChainBreak=true` (`Config.java:307`) |
| `perf-mixin-review.md` A5 (`ChainPreCalcCache.computeHash` boxing) | **unchanged, non-hot** | `ChainPreCalcCache.java:38-55`, runs only on `start()`/completion — agreed, no action. |

---

## 4. Dead-surface quantification (grep evidence)

Commands run from the repo root with the workspace ripgrep tool (read-only), matching whole-tree
occurrences of each symbol (`src/main/java`):

```
ChainTraverser | ChainBlockMatcher | ChainCandidateFilter
ChainPlanner
\.create\(
chainModeRegistry | chainSubModeRegistry
\.all\(\) | getSubModes
executeBatch | executeWithPreResolved | groupByChunk | isEndlessIDsLoaded
totalSize\(\) | consumedCount\(\) | isInProgress\(\) | getCooldownRemaining | isReady\(\)
previewState | \.target      (restricted to client/render/MinerRenderer.java)
ChainRequest | ChainRuntimeState | ChainClientState
chainClientState\.        (field-usage enumeration)
sessionDimension | previewRenderedCount
```

Proven-unreachable code (declaration site → why nothing calls it):

| Symbol | Declaration | Grep result |
|---|---|---|
| `ChainTraverser` (interface) | `chain/planning/ChainTraverser.java:11` | **only its own file** — zero implementers, zero references |
| `ChainBlockMatcher` (interface) | `chain/planning/ChainBlockMatcher.java:7` | **only its own file** |
| `ChainCandidateFilter` (interface) | `chain/planning/ChainCandidateFilter.java:7` | **only its own file** |
| `ChainPlanner` + `plan()` | `chain/planning/ChainPlanner.java:14,22` | constructed only by `ChainPlanningRuntimeFactory.create()`; `plan()` referenced only in its own body (`ChainPlanner.java:23`) |
| `ChainPlanningStrategy` + `ChainPlanningRuntimeFactory.create(ChainPlanningStrategy)` | `ChainPlanningStrategy.java:11`, `ChainPlanningRuntimeFactory.java:20` | no `.create(` call exists anywhere; `EZMiner.chainPlanningRuntimeFactory` is used **only** for `createFounderForMode` (`BaseOperator.java:95`, `MinerRenderer.java:222`) |
| `ChainModeRegistry`, `ChainSubModeRegistry`, `ChainModeDefinition.addSubMode/getSubModes`, `ChainSubModeDefinition` | `chain/mode/*` | populated in `CommonProxy.java:50-51` but **never read**: `EZMiner.chainModeRegistry`/`chainSubModeRegistry` appear only in `EZMiner.java:50-51` + the two bootstrap calls; `get(...)`/`all()`/`getSubModes()` have no callers |
| `BlockHarvestActionExecutor.executeBatch(List, EntityPlayerMP)` | `chain/execution/BlockHarvestActionExecutor.java:117` | no caller; the only live batch path is `BaseOperator.processBatchWithBatchedExhaustion`/`processBatchChunkCached` (`BaseOperator.java:218-225`) |
| `BlockHarvestActionExecutor.executeWithPreResolved` | `:250` | no caller |
| `ChunkBlockWriteHelper.groupByChunk`, `isEndlessIDsLoaded` | `ChunkBlockWriteHelper.java:120,414` | `groupByChunk` only called from the dead `executeBatch`; `isEndlessIDsLoaded` has zero callers |
| `ChainExecutor.executeBatch(Queue, EntityPlayerMP, int)` | `chain/execution/ChainExecutor.java:21` | only the `(Queue, int, Function)` overload is called (`BaseOperator.java:224`) |
| `CachedPositionsPlanningTask.totalSize()/consumedCount()` | `chain/planning/CachedPositionsPlanningTask.java:72,79` | no callers |
| `ChainPreCalcEngine.isInProgress()` | `chain/planning/ChainPreCalcEngine.java:74` | no callers |
| `MinesweeperModeHandler.isReady()`, `SudokuModeHandler.isReady()`, `ProspectModeHandler.isReady()` | `:56`, `:79`, `:208` | no callers (the cooldown is compared inline in `tick`) |
| `CooldownTracker.getCooldownRemaining(EntityPlayerMP)` | `CooldownTracker.java:47` | no callers (HUD has no cooldown widget) |
| `ChainPreviewState.target` | `chain/client/preview/ChainPreviewState.java:8` | written (`MinerRenderer.java:196,243`), never read (`MinerRenderer.java:165-166` reads only `frozen`) |
| `ChainRequest` (whole class) | `chain/state/ChainRequest.java:10` | never constructed or referenced anywhere (`chainClientState.`-style greps and a `ChainRequest` grep return only its own file) |
| `ChainClientState.previewRenderedCount` | `chain/state/ChainClientState.java:13` | never read; the live counter is `ClientStateContainer.previewRenderedCount` (`:21`, read at `HudRenderer.java:91`) |
| `ChainClientState.sessionDimension` | `chain/state/ChainClientState.java:16` | write-only (`PacketChainStateSync.java:93,97`), no reader anywhere |

`ChainPreviewController` itself is **live** (`MinerRenderer.java:90,102,117,196`), and `ChainExecutionErrorReporter`
+ `ChainHarvestExhaustionStrategy` + `ChainActionExecutor` + `ChainExecutor`(Function overload) are live —
do not delete those.

Obsolete/duplicated behaviour worth mentioning: `ChainSubModeBootstrap` re-registers a subset of the ids already
registered by `ChainModeBootstrap` (both in `CommonProxy.java:50-51`), and both registries are then unused.

---

## 5. Cross-mod answers (tmp/ citations)

**VisualProspecting — API assumptions verified.**
`chain/execution/VisualProspectingBridge.java:41-50` resolves
`com.sinthoras.visualprospecting.VisualProspecting_API$LogicalServer.prospectOreVeinsWithinRadius(int,int,int,int)`,
`sendProspectionResultsToClient(EntityPlayerMP,List,List)`, `OreVeinPosition.getBlockX/getBlockZ/veinType`,
`VeinType.getVeinName`. All five exist with those signatures:
`tmp/VisualProspecting-1.4.8/src/main/java/com/sinthoras/visualprospecting/VisualProspecting_API.java:81-95`
and `.../database/OreVeinPosition.java`. Radius-0 semantics also match the bridge's comment
(`prospectChunkAndNotify` "exactly one vein cell"): `ServerCache.prospectOreBlockRadius(dim,bx,bz,0)` →
`prospectOreBlocks(dim,bx,bz,bx,bz)` → both bounds map through `Utils.mapToCenterOreChunkCoord`
(`.../database/ServerCache.java:56-73`), i.e. exactly one vein centre chunk, and
`prospectOreChunks` skips empty veins (`:44-52`). **One version coupling remains**: EZMiner's
`ProspectModeHandler.gridSnap` (`:151-153`) hard-codes the `EQUAL_SPACING` formula
`chunkCoord - floorMod(chunkCoord,3) + 1`, while VP's `mapToCenterOreChunkCoord` has a second branch for the
legacy "old bugged ore pattern" (`tmp/VisualProspecting-1.4.8/.../Utils.java:61-73`) keyed on
`GTWorldgenerator.oregenPattern`. On a world using the legacy pattern, the spiral probes wrong cells for
negative coordinates; the probe still resolves a vein (VP maps the block to the nearest centre), so the effect
is redundant probes/no-miss rather than a functional break — flagged as **inconclusive** for legacy worlds.

**LootGames — reflection targets verified, semantics reviewed.**
`MSMasterTile` (`tmp/LootGames-master/.../common/block/tile/MSMasterTile.java:7`) extends
`BoardGameMasterTile`, which provides `getGame()`; `GameMineSweeper.isBoardGenerated/getBoard/getStage`
(`.../minigame/minesweeper/GameMineSweeper.java:97`, `:256`), `MSBoard.size/getType/isHidden/getMark`
(`.../minesweeper/MSBoard.java:285,67,59,79`), `Type.BOMB` (`.../minesweeper/Type.java:7-19`),
`Mark.NO_MARK` (`.../minesweeper/Mark.java:7-11`), `Pos2i` ctor and `BlockPos.getX/getY/getZ` (inherited from
`Vector3i`, `.../utils/future/BlockPos.java:19-47`) — all present, so the minesweeper bridge is not silently
dead on this source version. `GameMineSweeper$StageWaiting.swapFieldMark(Pos2i)`
(`GameMineSweeper.java:406-417`) is the game's own mark mutation (it saves and calls `checkWin()`), so F6 is a
real state write, not a no-op: `MSBoard.checkWin()` requires all bombs hidden **and** all safe cells revealed
(`MSBoard.java:106-125`), and LootGames gates reveals on the marked-count matching the clue
(`GameMineSweeper.java:381-387`), i.e. flagging every bomb through EZMiner makes every reveal legal.
Sudoku: `SudokuTile`/`GameSudoku$StageWaiting`/`SPSSyncCell(Pos2i,int)`/`SudokuBoard.player/solution/puzzle`
exist (`.../common/block/tile/SudokuTile.java:7`, `.../common/packet/game/sudoku/SPSSyncCell.java:21`,
`.../minigame/sudoku/SudokuBoard.java:21-24`); the bridge writes the public `player` grid directly instead of
using the public `cSetPlayerValue(Pos2i,int)` (`SudokuBoard.java:274`) — works, but binds to a field rather
than to the API. `tmp/` holds only the master branch, so a GTNH-shipped older LootGames build cannot be
cross-checked here → residual **inconclusive** (the bridge fails *safe*: every lookup is in a try/catch and
logs at debug, `LootGamesMinesweeperBridge.java:82-87`).

**GT5U block swap — accessors verified for 5.09.54.133.**
`GregTechAPI.sBlockMachines` (`tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/api/GregTechAPI.java:176`),
`METATILEENTITIES` (`:95`), `IGregTechTileEntity.getMetaTileID()` (`.../interfaces/tileentity/IGregTechTileEntity.java:39`),
`IMetaTileEntity.getTileEntityBaseType()` returning `byte` (`.../interfaces/metatileentity/IMetaTileEntity.java:59`),
`setInitialValuesAsNBT(NBTTagCompound, short)` (`IGregTechTileEntity.java:131`, implemented at
`.../metatileentity/BaseMetaTileEntity.java:165` and `BaseMetaPipeEntity.java:145`), and `public byte mConnections`
on both `BaseMetaPipeEntity` (`.../metatileentity/BaseMetaPipeEntity.java:63`) and `MetaPipeEntity`
(`.../metatileentity/MetaPipeEntity.java:61`) — so `GT5BlockSwapCompat`'s reflection surface is real.
Remaining risk for F3's TE half: `world.setBlock(..., replacementBlock, replacementMeta, 3)`
(`BlockSwapModeHandler.java:119`) relies on GT's `Block.createTileEntity` for the base meta and then on
`setInitialValuesAsNBT(null,(short)itemDamage)` to rebuild the specific machine
(`CommonBaseMetaTileEntity.java:49-53` shows the `METATILEENTITIES[aID].newMetaEntity(this)` path). If either
step fails, the block is a GT meta block **without** the matching meta-TE; the failure is only logged at debug
(`GT5BlockSwapCompat.java:213-219`) → see **inconclusive** below. Also note the bridge maps GT item damage to
base meta (`getBaseMetaForItem`, `:188-203`) but the swap consumes items by *item + damage, NBT ignored*
(`BlockSwapModeHandler.java:257-260`), so a GT machine item carrying NBT (contents/cover) is treated as an
equal replacement token.

**Hodgepodge** — cited for F10
(`tmp/Hodgepodge-master/src/main/java/com/mitchej123/hodgepodge/mixins/early/minecraft/chunkloading/MixinWorld_PreventChunkLoading.java:45-54`;
the sibling `MixinWorldServer_PreventChunkLoading.java:32,51` only touches tick/chunk-bookkeeping methods, so
it does not guard `World.getBlock`).

**Crop adapter / Natura** are out of scope for this report (compat/ + core/founder), except that
`NaturaSaguaroCompat.cascadeUnsupportedNeighbors` injects positions into
`BaseOperator.canBreakPositions` (`BaseOperator.java:497-500,575-578`) *after* the planning task stopped —
those injected positions are still executed because the drain loop polls the queue first
(`:464`, `:541`), so a stopped/stale planning task cannot strand them. Verified OK.

---

## 6. Verified OK (sub-areas checked and found correct)

1. **State ownership / double bookkeeping.** `Manager` does *not* keep its own `MinerConfig`/`MinerModeState`:
   `Manager.java:117-119` assigns `state.minerConfig` / `state.minerModeState` from
   `ChainStateService.getOrCreate(playerUUID)` (`ChainStateService.java:18-20`), and every mode/config read in
   `Manager`/`BaseOperator` goes through those shared objects (`Manager.java:525-566,665-695`). Packet input
   lands on the same objects (`PacketKeyState.java:41-42`, `PacketChainModeSwitch.java:62-66`). No duplicate
   copies, no per-EntityPlayer keying: `ChainStateService` is `ConcurrentHashMap<UUID,…>` (`:16`) and
   `PlayerManager.managers` is `ConcurrentHashMap<UUID,Manager>` (`PlayerManager.java:41`).
2. **Lifecycle wiring completeness.** Login (`PlayerManager.java:44-55` → `ChainStateService.onPlayerLogin`
   clears runtime), logout (`PlayerManager.java:58-67` → state removal + `CooldownTracker.clear` +
   `ToolSwapServerService.clear`), respawn (`:76-81`), dimension change (`:69-74`), world unload
   (`:83-87`) and per-player handler teardown (`Manager.unRegistry` → Forge-bus unregister,
   `Manager.java:453-459`) are all hooked. Per-player handlers (`MinesweeperModeHandler`, `SudokuModeHandler`,
   `ProspectModeHandler`, `BlockSwapModeHandler`, `PlantingModeHandler`, `ChainPreCalcEngine`,
   `ChainDropCollector`, `ChunkPreloader`, `ChunkCachedHarvester`) are Manager instance fields
   (`Manager.java:93-112`, `BaseOperator.java:84-90`) — no cross-player sharing. The only cleanup defect is
   F1 (what is cleared, not what is missed).
3. **ChainPreCalcEngine bounds + cache isolation.** Sub-chunk index bounded (`:241`), null-chunk guarded
   (`:231-251`), per-player engine + per-UUID cache (`ChainPreCalcCache.java:23`), and the consumer
   re-validates before use: dimension, block-id/class match and `distSq <= bigR²`
   (`Manager.java:569-602`). The result list is an immutable snapshot copy (`:312`, `ChainPreCalcCache.java:75`).
4. **Pre-calc/cache invalidation on world change.** `stop()` removes the cache and clears state
   (`ChainPreCalcEngine.java:98-111`), `cleanupState()` does the same on lifecycle events
   (`Manager.java:462-463`), and a successful cached start consumes the cache
   (`Manager.java:617-618`).
5. **World-write threading.** Every chain world mutation runs on the server thread: the harvest executors are
   only invoked from `BaseOperator.operatorTask` (ServerTickEvent START, `BaseOperator.java:110-232`), the
   right-click modes from `PlayerInteractEvent` (server thread), the pre-calc/tick handlers from
   `Manager.onWorldTick` (`Manager.java:338-350`), and the deferred packets from
   `PlayerManager.onServerTick` (`:90-97`). Founders only read the world and enqueue positions
   (`LegacyFounderPlanningTask.java:20-33`).
6. **Packet server authority.** The two C→S chain packets only write the sender's own
   `ChainPlayerState.keyPressed`/mode indices (`PacketKeyState.java:39-67`,
   `PacketChainModeSwitch.java:60-67`) — clamped to the real mode ranges (`:41-44` vs
   `core/MinerModeState.java:37-62`) — and cannot inject positions/inventories: every world action is
   re-derived from the world at execution time (`BlockHarvestActionExecutor.java:67-71`,
   `ChunkCachedHarvester.java:122-126`, `ChainBreakEventHelper.fireIfEnabled`,
   `DeterminingIdentical.isUnbreakable`). `PacketChainStateSync` is S→C only and carries an ordering guard
   for out-of-order sessions (`:82-90`).
7. **C→S packet thread-safety (chain packets).** Both handlers go through `MainThreadEnforcer.guardedNull`
   and are drained on the server thread (see §3); the deferred-body ordering is FIFO
   (`ConcurrentLinkedQueue`, `MainThreadEnforcer.java:32,74`), so a press/release pair cannot be reordered.
8. **Client-side chain state thread-safety.** `ClientStateContainer` uses `CopyOnWriteArrayList` for the
   minesweeper/sudoku position lists and `volatile` for every scalar/version read by the render thread
   (`client/ClientStateContainer.java:28,34,41,47,53,74,77,94-107`), matching the netty-thread writers in
   `PacketMinesweeperMark.java:56`, `PacketSudokuFill.java:56`, `PacketCachedBlockSync.java:112-118`,
   `PacketBlockSwapResult.java:41-42`.
9. **XP duplication vs vanilla is *not* present.** Vanilla 1.7.10 drops XP in `ItemInWorldManager.tryHarvestBlock`
   (`build/rfg/minecraft-src/.../ItemInWorldManager.java:329-333`), **not** in `Block.harvestBlock`
   (`.../block/Block.java` has no `dropXpOnBlockBreak`/`getExpDrop` call inside `harvestBlock`). The fast paths
   replace that call with `XPDropHandler` (`MixinItemInWorldManager.java:121-130`,
   `BlockHarvestActionExecutor.java:223-230`, `ChunkCachedHarvester.java:216-223`) and the TE paths delegate to
   `tryHarvestBlock` and skip their own XP handling (`BlockHarvestActionExecutor.java:80-82`,
   `ChunkCachedHarvester.java:133-139`) → exactly one XP grant per block.
10. **XP is not lost when `fireBreakEvent=true`.** `BlockEvent.BreakEvent`'s constructor pre-computes
    `exp` (silk-touch and fortune aware) in this Forge build
    (`build/rfg/minecraft-src/java/net/minecraftforge/event/world/BlockEvent.java:83-93`), so
    `handlePreComputedXP(..., breakEvent.getExpToDrop(), ...)` is correct; `getExpToDrop()` returns 0 only
    when the event was canceled (in which case the block is not removed anyway).
11. **ChainDropCollector correctness for the common cases.** Blacklist filtering is applied before
    accumulation (`:53-61`), the event's list is cleared so vanilla cannot also spawn the items (`:84`), NBT
    stacks fall back to content comparison (`:72-82`), and `flush`/`tryFlush` skip empty stacks and unloaded
    chunks (`:96-125`). Only the extreme overflow case F16 remains.
12. **Block metadata / neighbour reaction on the batch paths.** `writeAirToEbs` zeroes the nibble after
    `func_150818_a` (`:181-185`) and `notifyBatchNeighborChange` passes the *old* block (vanilla semantics,
    `:248`), dedupes neighbour coordinates and swallows per-neighbour exceptions so one bad block cannot abort
    a batch (`:239-256`); the leaf-decay flag is wood-gated and O(1) (`:317-335`).
13. **Cached-chain position feeding.** `CachedPositionsPlanningTask.feedTo` respects the per-tick limit and
    `stopped`, and `isStopped()` becomes true once the cursor is exhausted (`:60-91`), so a cached chain
    terminates deterministically; `createCached` replaces the founder task before `registry()` so the legacy
    founder is never scheduled (`BaseOperator.java:100-107,330-345`).
14. **`ChainPreCalcEngine` gating.** The BFS only runs in cached chain modes (`:79-82`), only while the key is
    held (`Manager.java:342-344`), never while a chain is operating (`Manager.java:341`), and is bounded per
    tick (`CHECKS_PER_TICK = 2048`, `:70,198`), by `blockLimit` (`:198`), by `bigRadius` (`:205-212`), skips
    liquids/bedrock/the block under the player (`:256-259`) and honours `enableChainChunkLoading` for
    unloaded-chunk skipping (`:227-229`).

---

## 7. Inconclusive / not provable read-only

1. **GT5 block-swap TE build-out (F3 tail).** Whether `world.setBlock(replacementBlock, baseMeta, 3)` +
   `setInitialValuesAsNBT(null,(short)itemDamage)` always produces a *complete* GT meta-TE depends on
   `BlockMachines.createTileEntity(World,int)` and on `newMetaEntity` for every machine class, which I did not
   exhaustively trace (GT5's `METATILEENTITIES` is populated at runtime). If the TE ends up null/mismatched,
   the position is a GT meta block whose later harvest goes through the TE path
   (`BlockHarvestActionExecutor.java:80-82`) — a plausible NPE/ghost-machine path that needs an in-game test.
2. **LootGames version parity.** `tmp/` contains only `LootGames-master`; the GTNH-shipped 1.7.10 build may
   differ. The bridge resolves everything reflectively inside a single try/catch and disables itself on
   failure (`LootGamesMinesweeperBridge.java:47-88`, `LootGamesSudokuBridge.java:72-113`), so version drift
   degrades to "mode does nothing" rather than a crash — but the F6 behaviour claims are valid only for the
   source shape under `tmp/`.
3. **F9 reachability.** The NBT-short truncation is certain from `EntityXPOrb.java:219`; whether an orb with
   ⩾32768 XP is ever saved before pickup depends on player position/config (`dropToPlayer=false`,
   `Config.dropToPlayer`) and chunk unload timing — an in-game soak test is required to call it "reproduced".
4. **Legacy GT oregen pattern + VP prospect snapping** (§5) — needs a world with
   `GTWorldgenerator.oregenPattern != EQUAL_SPACING`.
5. **Watchdog F2 magnitude.** How often a legitimate chain needs >100 ticks to the first harvest depends on
   server TPS/hardware; the code path and the default-on configuration are proven, the frequency is not.
6. **`Config.prospectMaxScanRadiusChunks`/`sudokuProbeCooldownSeconds` GUI-vs-file drift** — the load path
   clamps them (`Config.java:827-838`, `:807-815`) but I did not audit the OP GUI row wiring (out of scope:
   `client/gui`).

---

## 8. Suggested fix order for the coordinated pass

1. **F1** (data loss on logout/respawn/dim-change) — smallest change, highest impact: flush before clearing.
2. **F2 + F15** (watchdog) — arm on first harvest; single clock base.
3. **F4** (protection bypass) + **F3** (block-swap canHarvest/order) — same file family, one review unit.
4. **F10** (`blockExists` guard, one line) and **F7** (preloader shell cursor) — cheap correctness fixes.
5. **F20/F17** dead-surface deletion (planning scaffolds + `chain/mode` registries + the unreachable methods),
   after the §4 list is confirmed by the integrator.
6. **F5/F6** (special-mode throttling/scope + config gate) and **F8/F9/F11** (bandwidth, XP split, radius
   clamp) as the second pass.
