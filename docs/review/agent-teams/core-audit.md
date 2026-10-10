# EZMiner harvest-core audit (task t1) — `core/`, `core/founder`, `core/crop`, `thread/`, `mixin/`

Auditor: `audit-tmp-tools-ds` (attempt `217fc992-2308-4d8e-9aa4-220e3dbd9f63`) · read-only audit · no Gradle/Java executed.

**Scope audited (current working tree):**

| Area | Files |
|---|---|
| `core/` | `Manager.java` (700), `BaseOperator.java` (598), `MinerModeState.java` (189), `PlayerManager.java` (105), `MinerConfig.java` (67), `ItemStackKey.java` (52) |
| `core/founder/` | `BasePositionFounder` (515), `ChainPositionFounder` (323), `DeterminingIdentical` (426), `FuzzyChainPositionFounder`, `LogFounder`, `OreFounder`, `GtVeinOreFounder`, `ScreenBlastFounder`, `InverseBlastFounder`, `TunnelFounder`, `PlantingPositionFounder`, `CropFounder`, `BlastPositionFounder`, `NoOpPositionFounder` |
| `core/crop/` | `ICropAdapter`, `VanillaCropAdapter`, `Ic2CropAdapter`, `CropsNHCropAdapter`, `NaturaCropAdapter`, `CropAdapterRegistry` |
| `thread/` | `Pauseable` (157), `SearchWorkerPool` (46), `ParallelTick` (76), `TickEventHandler` (root pkg, 38) |
| `mixin/**` | `early/MixinItemInWorldManager`, `early/MixinGTOreAdapter`, `early/MixinBWOreAdapter`, `early/MixinGTPPOreAdapter`, `early/MixinGuiIngameMenu`, `interfaces/IEZMinerItemInWorldManager`, `Mixins`, `TargetMod`, `MixinCapabilityPlugin`, `ILateMixinPlugin` + `mixins.EZMiner.json` / `mixins.EZMiner.late.json` |
| tests | `src/test/java/.../core/founder/{EncodePosTest, ShellGeometryTest, BfsConsistencyTest}.java` (3 tests) |

**Evidence sources used (in addition to current `src/`):**
`build/rfg/minecraft-src/java/**` (decompiled MCP+Forge 1.7.10 sources are present in this workspace — vanilla claims cite them), `tmp/**` for cross-mod verification. Files outside this scope (`chain/execution/ChunkBlockWriteHelper`, `BlockHarvestActionExecutor`, `ChunkCachedHarvester`, `chain/lifecycle/ChainLifecycleService`, `compat/*`) were read only where the task's checklist explicitly demands it (three harvest paths, metadata zeroing, neighbour notification, drop/XP, logout cleanup) and are labelled as cross-scope; their owning task is t2/t5.

---

## 1. Findings (detailed)

### CORE-01 — `LogFounder.run1` scans ~6.8×10¹³ loop iterations with the default config (tree-felling / logging blast) · **HIGH** · perf/logic

* **File:line** `src/main/java/com/czqwq/EZMiner/core/founder/LogFounder.java:34-65`; defaults `Config.java:80` (`logBigRadius = 1024`), `Config.java:362` (`clientLogBigRadius = 1024`), `Config.java:620-626` (server `getInt("logBigRadius", …, 1024, 8, Integer.MAX_VALUE)`), `Config.java:1176-1182` (client, same); wiring `chain/planning/LegacyFounderPlanningFactory.java:64-65`; client preview also affected `client/render/MinerRenderer.java:219-223`.
* **Evidence (current source):**

```java
// LogFounder.java:36-63
int curRadius = 1; int highRadius = 1; int prevCurRadius = 0; int prevHighRadius = 0;
while (curCount.get() < minerConfig.logBlockLimit) {
    for (int x = center.x - curRadius; x <= center.x + curRadius; x++) {
        for (int y = center.y - highRadius; y <= center.y + highRadius; y++) {
            for (int z = center.z - curRadius; z <= center.z + curRadius; z++) {
                if (Math.abs(x - center.x) <= prevCurRadius && Math.abs(y - center.y) <= prevHighRadius
                    && Math.abs(z - center.z) <= prevCurRadius) continue;   // skip test INSIDE the innermost loop
                ...
                if (checkCanAdd(pos)) addResult(pos);
                if (curCount.get() >= minerConfig.logBlockLimit) return;
                waitUntil();
    ...
    curRadius = Math.min(curRadius + 1, minerConfig.logBigRadius);
    highRadius++;
    if (curRadius >= minerConfig.logBigRadius && highRadius > minerConfig.logBigRadius * 4) break;
}
```

`MinerRenderer.restartViewer` builds the preview config as `new MinerConfig()` and overrides only `bigRadius`/`blockLimit` (:219-221); on the client `MinerConfig`'s field initialisers keep `logBigRadius = Config.clientLogBigRadius = 1024` and `logBlockLimit = 16384` (`MinerConfig.java:10-15`).

* **Quantified cost** (deterministic loop-count computation over the exact source bounds): the loop runs `4·L+1 = 4097` iterations for `L = 1024`, performing **68,302,963,507,203 innermost-body executions** of which **34,405,896,194 are non-skipped positions**, each doing `checkCanAdd` → `blockExists` + `getBlock` + `getBlockMetadata` + `canHarvestBlock`. For comparison the same formula gives 323,715 / 19,362 at `L = 8`. `logCount >= logBlockLimit` (16384) is essentially never reached for a tree (default `logFuzzyEnabled = true` matches only the *sample block class*, `LogFounder.java:78-83`), so the loop always runs to the `break` at `:63`.
* **Concrete failure scenario:** player selects blast sub-mode 4 ("logging") and breaks one log. The founder thread (and, in parallel, the client preview founder enqueued at `MinerRenderer.java:226`) burns a full core for hours instead of seconds; the operator queue keeps receiving "every wood-class block within ±1024 × ±4096 × ±1024" until the player releases the key. `LogFounder.run1` also has **no** `player.isDead/worldObj` guard (unlike `BasePositionFounder.run1SingleThreaded:154`), so a dying/logging-out player does not abort the sweep.
* **Minimal fix sketch:** (a) hoist the skip test out of the innermost loop — iterate only the two new y-shells (`y == center.y ± highRadius`) and, in the single iteration where `curRadius` grows, the two new x-shells; (b) clamp the scan: `highRadius <= max(4·curRadius, 64)` and default `logBigRadius` to a tree-sized value (e.g. 16-32) with the GUI/config max enforced; (c) add the `player == null || player.isDead || player.worldObj == null` guard used by the base class.
* **Confidence:** high on the code + loop-count arithmetic; medium on absolute wall-clock (needs a runtime test).

### CORE-02 — Chain-mode multithreaded BFS marks unloaded-chunk neighbours as visited → permanent under-mine and ST/MT divergence · **MEDIUM** · correctness

* **File:line** `core/founder/ChainPositionFounder.java:282-299` (`collectNeighbours`, `markVisited` at `:294`), worker use `:174` and `:249`; contrast the single-threaded path `:110-115` / `:63-68`; documented contract `core/founder/BasePositionFounder.java:112-114`; the world guard that then rejects them `BasePositionFounder.java:454` (`!player.worldObj.blockExists(...)`).
* **Evidence:**

```java
// ChainPositionFounder.java:293-296  (collecting thread, BEFORE any world read)
if (!markVisited(encodePos(cx, cy, cz))) continue;
out.add(new Vector3i(cx, cy, cz));
// worker lambda :172-180 -> checkCanAddAfterVisited(c) -> BasePositionFounder.checkCanAddImpl :454
if (!player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;   // rejected, but the key is already in the visited set
```

and the constructor contract it violates (`BasePositionFounder.java:112-114`): *"Unloaded-chunk positions are skipped (not added to visited) so they are retried on future ticks after the player naturally loads them."* `BasePositionFounder.tryProcessShellPos:236-238` does honour it (visited only via `addResult` on accept), so the two search families disagree.
* **Concrete failure scenario:** chain-mining a vein whose periphery crosses the currently-loaded chunk boundary (default `bigRadius = 8`, `Config.java:21`, so candidates up to 8 blocks away can sit in an unloaded chunk). Those neighbours are admitted to the visited set, rejected by `blockExists`, and never re-tested even after the chunk loads → the vein keeps "striped residue" at the chunk edge. Because `run1()` dispatches on `minerConfig.blockLimit >= 64 && Config.searchWorkerThreads > 0` (`BasePositionFounder.java:132-139`, defaults 1024 / 3 → MT is the default path), the same operation behaves differently when `searchWorkerThreads` is set to 0.
* **Minimal fix sketch:** move the atomic admission out of `collectNeighbours` into the worker (cheap radius pre-filter on the collector, `markVisited` + world checks in the worker), or have the worker call a new `unmarkVisited(key)` when the rejection reason is "chunk not loaded" — the visited backend would need a `remove`.
* **Confidence:** high (code); medium on user-visible frequency.

### CORE-03 — Batched neighbour notification / water sweep read unguarded neighbours → synchronous chunk load or generation during a harvest batch · **MEDIUM** · threading/perf

* **File:line** callers `core/BaseOperator.java:510-512` and `:592-594`; helper `chain/execution/ChunkBlockWriteHelper.java:240` (`Block nb = world.getBlock(nx, ny, nz);` — no `blockExists`), water sweep `:269-271`; `compat/CoFHWaterBridge.java:47,57,81,87` (same pattern).
* **Vanilla proof:** `World.getBlock` → `getChunkFromChunkCoords` (`build/rfg/minecraft-src/java/net/minecraft/world/World.java:379-403`, and `:383-397` wraps a null chunk into a `ReportedException`), `World.getChunkFromChunkCoords` → `ChunkProviderServer.provideChunk` → `loadChunk` **because `loadChunkOnProvideRequest = true`** (`.../world/gen/ChunkProviderServer.java:48`, `:218-222`, `:117-159` — `ChunkIOExecutor.syncChunkLoad` / `originalLoadChunk` generation on the calling thread). Hodgepodge only disables that flag around random ticks and entity updates (`tmp/Hodgepodge-master/src/main/java/com/mitchej123/hodgepodge/mixins/early/minecraft/chunkloading/MixinWorldServer_PreventChunkLoading.java:31-48`), not around `World.getBlock` called by EZMiner.
* **Concrete failure scenario:** chain/blast whose last removed block sits at the edge of the loaded region → one of its 6 neighbours belongs to an unloaded chunk → `world.getBlock` synchronously loads (or generates) that chunk inside the batch, stalling the server tick and creating an EZMiner-caused chunk load. With `notifyNeighborsOnChainBreak = true` (default, `Config.java:307`) this runs for every batch.
* **Minimal fix sketch:** add `if (!world.blockExists(nx, ny, nz)) continue;` before the `getBlock` at `ChunkBlockWriteHelper.java:240` and in the `CoFHWaterBridge` sweep (the sibling helper already does this: `ChunkBlockWriteHelper.flagNeighbouringLeavesForDecay:328`).
* **Confidence:** high (mechanism); medium on frequency (needs a chunk-border test).

### CORE-04 — `enableBudgetDeadline` defeats the pause contract: the founder resumes world reads while still paused · **MEDIUM** · threading

* **File:line** `thread/Pauseable.java:126-137` (`waitUntil`) and `:80-85` (`consumeBudget`); usage `core/founder/BasePositionFounder.java:157-159` and `:287-289`; default `Config.java:255` (`enableBudgetDeadline = false`).
* **Evidence:**

```java
// Pauseable.java:80-85 (founder branch)
if (workBudget > 0 && --budgetRemaining > 0) return true;
budgetRemaining = workBudget;
waitUntil();                                   // returns early when the 50 ms deadline elapses ...
return !Thread.currentThread().isInterrupted(); // ... and then reports "keep working"
// Pauseable.java:133-135
long remaining = deadlineNanos - System.nanoTime();
if (remaining <= 0) return;                    // still paused here
```

* **Concrete failure scenario:** with `enableBudgetDeadline = true`, a founder that is paused at tick END returns from `waitUntil()` 50 ms later, `consumeBudget()` reports `true`, and the scan continues reading the world outside the server-tick window; every subsequent call also short-circuits because the deadline has already passed (it is refreshed once per radius layer at `:158` / `:288`). This contradicts the documented invariant "founders are paused at tick end so world reads stay inside the tick" (CLAUDE.md "Threading Model & Pause Contract"; `TickEventHandler.java:16-25`).
* **Minimal fix sketch:** make the deadline path report "stop" instead of "continue" — e.g. `waitUntil(); if (paused.get()) return false; return !interrupted;` — so the caller re-enters its outer loop and parks again next tick.
* **Confidence:** high on the code path; medium on runtime observability (config-gated, default off).

### CORE-05 — GT-ore fortune mixins are applied unconditionally while the documented capability gate is dead code · **MEDIUM** · mixin/lifecycle

* **File:line** `src/main/resources/mixins.EZMiner.json:11-17` (all five mixins listed unconditionally, `"required": false`); `mixin/Mixins.java:12-13` (`public enum Mixins { ;` — **no constants**), `:27-47` (`getLateMixins` iterates `values()` → always empty), `:123-127` (`addBytecodeCondition` — the only caller of the capability check, unreachable); `mixin/ILateMixinPlugin.java:20-23`; `mixin/MixinCapabilityPlugin.java:48-49`; config flag `Config.java:220`.
* **Cross-mod check (tmp):** `tmp/GT5-Unofficial-beta2/src/main/java/gregtech/common/ores/{GTOreAdapter,BWOreAdapter,GTPPOreAdapter}.java` exist (lines 38, 32, 22) and the mixin descriptors match exactly (`getOreDrops(Random, OreInfo, boolean, int)` at GTOreAdapter.java:237 / BWOreAdapter.java:165 / GTPPOreAdapter.java:70; `getBigOreDrops(Random, OreDropSystem, OreInfo, int)` at GTOreAdapter.java:313 / BWOreAdapter.java:240 / GTPPOreAdapter.java:95; the injected `fortune > 3` comparison at GTOreAdapter.java:326, BWOreAdapter, GTPPOreAdapter.java:109; the redirected `OreInfo.isNatural` field read at GTOreAdapter.java:250 / BWOreAdapter.java:176 / `OreInfo.java:50`). **However** `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/` has **no `ores` package at all** (directory listing verified) — on that GT5U line the three `@Mixin` target classes do not exist.
* **Concrete failure scenario:** on a GT5U build without the modern ore system, Mixin cannot resolve `gregtech.common.ores.GTOreAdapter` for the three classes listed in `mixins.EZMiner.json`; the config is `required:false`, so the game starts but the three mixins fail to apply (error logs) and the fortune-uncap/placed-ore switches (`FortuneCompatHelper`, `Config.enableUnlimitedOreFortune` / `enableFortuneForPlacedOre`) silently do nothing. The bytecode-shape gate that `MixinCapabilityPlugin`'s javadoc claims protects exactly this case is never invoked (the `Mixins` enum has no entries), so nothing verifies the target shape on any install.
* **Minimal fix sketch:** either populate the late-mixin `Mixins` enum (`addTargetMod(TargetMod.…).addBytecodeCondition("gregtech/common/ores/GTOreAdapter", "getOreDrops", "(Ljava/util/Random;Lgregtech/common/ores/OreInfo;ZI)Ljava/util/ArrayList;")`) and remove the three classes from `mixins.EZMiner.json`, or delete the dead `Mixins`/`MixinCapabilityPlugin` scaffolding and gate the JSON entries by pack version.
* **Confidence:** high on the dead-code claim; medium on Mixin's exact failure mode on the 5.09 line (no runtime available).

### CORE-06 — flag-2 fast path can skip the client block update for not-yet-populated chunks · **LOW-MEDIUM** · visual desync

* **File:line** `mixin/early/MixinItemInWorldManager.java:104-108` (`theWorld.setBlock(x, y, z, Blocks.air, 0, 2)`); vanilla `World.markAndNotifyBlock` `build/rfg/minecraft-src/java/net/minecraft/world/World.java:547-552` (`(flag & 2) != 0 && (chunk == null || chunk.func_150802_k())`), `Chunk.func_150802_k()` `.../world/chunk/Chunk.java:1210-1213` (`field_150815_m && isTerrainPopulated && isLightPopulated`), `field_150815_m` set only in `func_150804_b` (onChunkLoad) `:1202`. The EBS paths do not have this gate — `chain/execution/BlockHarvestActionExecutor.java:213` and `ChunkCachedHarvester.java:206` call `world.markBlockForUpdate(x,y,z)` directly.
* **Concrete failure scenario:** mining in a chunk that is loaded but not yet terrain/light-populated (right after a teleport/world load, or a chunk queued by `enableChainChunkLoading`) → the client never receives `S23PacketBlockChange` for that block and keeps rendering the old block until the chunk is re-meshed. The batch-level compensation `markBlockRangeForRenderUpdate` only fires for regions ≤ 32×32 (`ChunkBlockWriteHelper.java:289-293`) and only when the neighbour-notify sink is enabled.
* **Minimal fix sketch:** after a fast-path batch, call `world.markBlockForUpdate` for each removed position (already done in the EBS variants) or fall back to `setBlock(...,3)` when `!chunk.func_150802_k()`.
* **Confidence:** medium-high (mechanism proven from vanilla source; frequency needs a runtime test).

### CORE-07 — Worker exceptions are silently swallowed: `invokeAll` futures are never inspected, chain worker lambdas have no try/catch · **MEDIUM** · silent failure

* **File:line** `core/founder/ChainPositionFounder.java:169-183` and `:244-256` (task bodies without try/catch), `:185-192` / `:258-265` (`SearchWorkerPool.get().invokeAll(tasks)` — result list discarded); `core/founder/BasePositionFounder.java:306-324` (lambda catches `RuntimeException` only, also discards the futures); `thread/SearchWorkerPool.java:22-24`; `thread/Pauseable.java:139-148` (`finally { stopped.set(true); }` — this part is correct).
* **Concrete failure scenario:** any `Throwable` raised inside a chain worker body — `World.getBlock`'s `ReportedException` for a null chunk (vanilla `World.java:390-397`), a modded block whose `isAir`/`getBlockMetadata` throws, or an `Error` — is captured by the dropped `Future` and never logged. In the chain MT path the affected slice was already admitted to the visited set (`ChainPositionFounder:294`) but is never offered to the operator, so that part of the vein is silently skipped with **no log line and no error report**; in the shell MT path an `Error` leaves `strip.complete == false` and `resumeFrom` unchanged, which is converted into the defensive `MAX_STALLED_SHELL_ATTEMPTS = 20` abort (`BasePositionFounder.java:273, 348-354`).
* **Minimal fix sketch:** keep the futures and inspect them (`for (Future<?> f : futures) try { f.get(); } catch (ExecutionException e) { LOG.error(...) }`), and wrap the chain worker bodies in the same `try/catch (Throwable)` that the shell strip already uses at `:307-313`.
* **Confidence:** high (code); medium on frequency.

### CORE-08 — `ParallelTick` task lists are never cleared across an integrated-server restart; `unPause()` on a stopped founder logs errors · **LOW** · lifecycle

* **File:line** `thread/ParallelTick.java:14` (`preTickTasks` plain list), `:17-32` (unpause loop then `removeIf(stopped)` only at tick END), `:62-75`; `thread/Pauseable.java:87-110` (`pause`/`unPause` log + `errorCount++`, `throw new RuntimeException` when `errorCount > 10`); lifecycle `CommonProxy.java:61-65` (`serverStarting` only rebuilds `PlayerManager`), `:74-76` (`serverStopping` only calls `SearchWorkerPool.stop()`); the holder `EZMiner.java:47` (`public static final ParallelTick parallelTick`) never re-created.
* **Status:** **still present** (reported in `docs/review/full-bug-scan.md:167-180`, item 11). Severity corrected: a stopped task is removed at the *next* tick END (`ParallelTick:20-22`) before the next tick START unpause loop, so a single stale founder can be unpaused at most ~1-2 times → `errorCount > 10` (the crash path) is effectively unreachable. Remaining real effects: `EZMiner.LOG.error("Thread already stopped! …")` noise per world reload, and a stale, still-`started` founder that is resumed by `unPause()` at the first tick START of the new world can run a bounded shell scan against the *previous* world for a few ticks (`SearchWorkerPool.stop()` at `CommonProxy:75` also leaves `SearchWorkerPool.get() == null`, so a stale task that dispatches into `ChainPositionFounder`/`BasePositionFounder` MT reaches `SearchWorkerPool.get().invokeAll(...)` (`:186`, `:259`, `:318`) without a null re-check and dies with an NPE).
* **Minimal fix sketch:** in `serverStopping` clear `EZMiner.parallelTick.preTickTasks`/`normalTasks` (or recreate the singleton in `serverStarting`), and make `pause()`/`unPause()` no-ops when `stopped.get()` instead of counting errors.
* **Confidence:** high (code); medium on the crash-path likelihood.

### CORE-09 — Logout / dimension change / world unload discard the collected drops and accumulated XP · **LOW** · data loss

* **File:line** `chain/lifecycle/ChainLifecycleService.java:58-65` (`stopRuntime` → `mgr.clearDrops()`), `core/Manager.java:439-442` (`clearDrops` → `dropCollector.clear()` + `XPDropHandler.clear(player)`), defaults `Config.java:47` (`dropImmediately = false`), `Config.java:63` (`xpDropMode = 1` delayed); the collect side `Manager.java:254-270`, `:289-315`.
* **Status:** **still present** (reported in `docs/review/full-bug-scan.md:110-118`).
* **Concrete failure scenario:** with delayed drops (default) the collector holds the whole chain's items/XP in memory until the chain ends; if the player logs out, changes dimension, respawns or the world unloads mid-chain (`ChainLifecycleService:33-50`), `clearDrops()` deletes them without spawning anything.
* **Minimal fix sketch:** in `stopRuntime`, call `mgr.flushDrops()` (which already has the respawn/world-spawn fallback chain, `Manager.java:352-417`) before `clearDrops()`, or only clear when `player.worldObj == null`.
* **Confidence:** high.

### CORE-10 — Fast harvest paths bypass spawn/claim protection (no `canMineBlock` / `isBlockProtected`; BreakEvent opt-in) · **LOW** · compatibility/griefing

* **File:line** `mixin/early/MixinItemInWorldManager.java:53-132` (the injected fast path never calls the Forge break hook), `chain/execution/ChainBreakEventHelper.java:32-35` (`fireIfEnabled` returns `null` unless `Config.fireBreakEvent`), `Config.java:159` (`fireBreakEvent = false`); grep over `src/main/java` finds **no** `canMineBlock` / `isBlockProtected` call anywhere.
* **Status:** **still present** (reported in `docs/review/harvest-path-review.md:192-204`).
* **Concrete failure scenario:** chain-mining inside the vanilla world-spawn protection radius (or past a claim mod that cancels `BlockEvent.BreakEvent`) succeeds, because neither `WorldServer.canMineBlock` nor the Forge hook runs on the fast path.
* **Minimal fix sketch:** check `world.canMineBlock(player, x, y, z)` (or `((WorldServer) world).isBlockProtected`) per removed block independently of `fireBreakEvent`.
* **Confidence:** high (code); medium on which installed claim mods depend on it.

### CORE-11 — Air detection is inconsistent between the admission gates and the executors · **LOW** · consistency

* **File:line** gates use identity `block.equals(Blocks.air)`: `BasePositionFounder.java:456`, `ChainPositionFounder.java:306`, `FuzzyChainPositionFounder.java:45`, `LogFounder.java:73`, `OreFounder.java:29`, `GtVeinOreFounder.java:50`, `ScreenBlastFounder.java:29`, `InverseBlastFounder.java:47`, `CropFounder.java:42`; the executors/height-map use the virtual predicate: `BlockHarvestActionExecutor.java:68` (`block.isAir(world,x,y,z)`), `ChunkCachedHarvester.java:123` (`block == Blocks.air`), `ChunkBlockWriteHelper.java:173` (`existing == Blocks.air`) and `:377-379` (`!block.isAir(...)`).
* **Concrete failure scenario:** a modded block that overrides `isAir()` to `true` (air-like placeholder) or a block that *is* `Blocks.air` but returns `false` from `isAir` makes the gate and the executor disagree: the founder enqueues a position the executor refuses (`BlockHarvestActionExecutor.java:68` → `false`, `BaseOperator` "continue"), so the position is counted as a queued candidate and mined as nothing. No crash, no drop loss — a wasted queue slot and a preview outline that never disappears.
* **Minimal fix sketch:** use one predicate everywhere (`block.isAir(world, x, y, z)` in the gates, or `== Blocks.air` in the executors).
* **Confidence:** high (code); low impact.

### CORE-12 — Batched exhaustion overwrites the exhaustion vanilla added inside the same batch · **LOW/INCONCLUSIVE** · behaviour

* **File:line** `core/BaseOperator.java:451-453` + `:514-515` (`exhaustionBefore = getExhaustion(food)` … `setExhaustion(food, exhaustionBefore + harvested * addExhaustion)`), same pattern at `:528-530`/`:596`; helper `chain/execution/ChainHarvestExhaustionStrategy.java:23-48`; the vanilla increment happens inside the block being harvested (`Block.harvestBlock` calls `player.addExhaustion(0.025F)`, and `FoodStats.addExhaustion` decrements `foodLevel` once the level exceeds 4.0).
* **Concrete failure scenario:** the final absolute `setExhaustion` discards the vanilla increments accumulated during the loop while the loop's own increments may already have crossed the 4.0 threshold and decremented `foodLevel`; the next batch starts from a restored value > 4.0, so food can be decremented more times than `blocks × addExhaustion / 4` implies. Net effect is a small, hard-to-predict food-cost skew, not a crash.
* **Minimal fix sketch:** apply the configured exhaustion additively (`player.addExhaustion(harvested * addExhaustion)` after subtracting the vanilla 0.025 × harvested), or clamp `setExhaustion(min(before + n*addExhaustion, 4.0F))`.
* **Confidence:** medium (needs a runtime food-drain test).

---

## 2. Findings summary table

| id | sev | category | file:line | evidence (abridged) | failure scenario | minimal fix | conf |
|---|---|---|---|---|---|---|---|
| CORE-01 | high | perf/logic | `core/founder/LogFounder.java:36-63`; `Config.java:80,362,620-626` | `for x (2R+1) × for y (2H+1) × for z (2R+1)` with the skip test innermost; `R→1024`, `H→4·R` | logging blast: 4.097e3 iterations / 6.83e13 innermost steps / 3.44e10 world lookups; mode effectively never finishes (client preview too: `MinerRenderer.java:219-226`) | hoist the skip test to the new y/x shells; clamp `logBigRadius`/`highRadius`; add the `isDead` guard | high (code), med (wall-clock) |
| CORE-02 | medium | correctness | `core/founder/ChainPositionFounder.java:294` (+`174,249`); `BasePositionFounder.java:112-114,454` | `markVisited(...)` before any world read; `blockExists` rejection afterwards | unloaded-chunk neighbours are lost forever; ST vs MT behaviour differs (`searchWorkerThreads`) | admit in the worker after the loaded check | high |
| CORE-03 | medium | threading/perf | `core/BaseOperator.java:510-512,592-594`; `chain/execution/ChunkBlockWriteHelper.java:240,269-271`; `compat/CoFHWaterBridge.java:47,57,81,87` | `world.getBlock(neighbour)` with no `blockExists`; vanilla `loadChunkOnProvideRequest = true` (`ChunkProviderServer.java:48`) | batch at the loaded-region edge synchronously loads/generates a chunk on the server thread | add `blockExists` guards (mirror `ChunkBlockWriteHelper.java:328`) | high |
| CORE-04 | medium | threading | `thread/Pauseable.java:80-85,126-137`; `BasePositionFounder.java:157-159,287-289` | deadline return path is treated as "keep working" | with `enableBudgetDeadline=true` the founder reads the world after tick end | return "stop" when the deadline fires while `paused` | high |
| CORE-05 | medium | mixin | `resources/mixins.EZMiner.json:11-17`; `mixin/Mixins.java:12-13,27-47,123-127`; `ILateMixinPlugin.java:20-23` | empty `Mixins` enum → gate unreachable; three GT mixes listed unconditionally | apply failure/log noise on GT5U lines lacking `gregtech.common.ores` (tmp 5.09.54.133 has none); no shape check anywhere | wire the enum gate (or delete the scaffolding) | high/med |
| CORE-06 | low-med | visual | `mixin/early/MixinItemInWorldManager.java:104-108`; `World.java:547-552`; `Chunk.java:1202,1210-1213` | flag-2 update gated by `func_150802_k()` | ghost blocks in loaded-but-unpopulated chunks (default path) | `markBlockForUpdate` per removed block, or flag 3 there | med-high |
| CORE-07 | medium | silent failure | `core/founder/ChainPositionFounder.java:169-183,244-256,185-192,258-265`; `BasePositionFounder.java:306-324` | `invokeAll` futures discarded; chain workers have no try/catch | worker `Throwable` silently drops that frontier slice (already marked visited) | inspect futures / wrap worker bodies | high |
| CORE-08 | low | lifecycle | `thread/ParallelTick.java:14,17-32`; `Pauseable.java:87-110`; `CommonProxy.java:61-65,74-76` | lists never cleared on server stop; stale-task unpause errors | error-log spam + stale founder resumed against the old world (median "still present") | clear lists in `serverStopping`; no-op pause on stopped | high |
| CORE-09 | low | data loss | `chain/lifecycle/ChainLifecycleService.java:58-65`; `core/Manager.java:439-442`; `Config.java:47` | `clearDrops()` without `flushDrops()` | logout/dim change/world unload deletes every delayed drop + XP of the running chain | flush before clear | high |
| CORE-10 | low | compat | `mixin/early/MixinItemInWorldManager.java:53-132`; `ChainBreakEventHelper.java:32-35`; `Config.java:159` | no `canMineBlock`/`isBlockProtected` anywhere in `src` | spawn-protected area / claims are chain-mined | query `canMineBlock` per block | high/med |
| CORE-11 | low | consistency | gates `BasePositionFounder.java:456` … vs `BlockHarvestActionExecutor.java:68`, `ChunkCachedHarvester.java:123` | `equals(Blocks.air)` vs `isAir(...)` | modded air-like blocks are queued but never mined | unify the predicate | high |
| CORE-12 | low | behaviour | `core/BaseOperator.java:451-453,514-515,528-530,596`; `ChainHarvestExhaustionStrategy.java:23-48` | absolute `setExhaustion` after a batch that already added vanilla 0.025/block | unpredictable small food-cost skew | additive application / clamp to 4.0 | med |

---

## 3. Re-verified previously reported items (against the CURRENT source)

| # | previously reported (doc) | verdict |
|---|---|---|
| 1 | `consumeBudget()` was a no-op at `searchBudgetPerYield=0` (`docs/review-summary.md` #1) | **fixed** — `Pauseable.java:80` (`if (workBudget > 0 && …)`) → with the default 0 (`Config.java:185`) the pause/interrupt check runs on every call. Verified the positive-budget branch fires on exactly the N-th call (no off-by-one). |
| 2 | shell decomposition missed 4 edge lines (`docs/review-summary.md` #2) | **fixed** — y-faces now use the full z range (`BasePositionFounder.java:189-204`), z-faces exclude x/y edges (`:205-218`); the MT strip variant partitions the same sets (`:370-422`). |
| 3 | visited-set encapsulation / primitive-set NPE (`docs/review-summary.md` #3, #5) | **fixed** — both backends private (`BasePositionFounder.java:54,61`), all access through `markVisited`/`isVisited`/`clearVisited` (`:484-500`); grep confirms no other file touches them. |
| 4 | `Pauseable.run()` without `finally` → `stopped` never set (`docs/review-summary.md` #8) | **fixed** — `Pauseable.java:139-148`. |
| 5 | `encodePos` returned 0 at the world corner and tripped the hand-written set sentinel (`docs/review-summary.md` #8) | **fixed/benign** — the hand-written `LongOpenHashSet` is gone; `encodePos(-30M,0,-30M) == 0` is still produced (`BasePositionFounder.java:506-508`, asserted by `EncodePosTest.java:90-94`) but both backends (`ConcurrentHashMap.newKeySet`, fastutil `LongOpenHashSet`) accept 0 as a key. Bit-packing verified injective over the MC range (x/z 26-bit with +30M bias, y masked 12 bits). |
| 6 | pool workers must never park / `budgetRemaining` race (`docs/review-summary.md` #9) | **fixed** — `Pauseable.java:71-79` returns `!paused.get()` for non-founder threads and touches no budget. |
| 7 | single-threaded BFS frontier used `ConcurrentLinkedQueue` (`docs/review-summary.md` #12) | **fixed** — `ArrayDeque` at `ChainPositionFounder.java:85-86`. |
| 8 | `A1`: founder re-fetched block/meta inside `DeterminingIdentical.identical` (`docs/review/perf-mixin-review.md` A1) | **fixed** — `ChainPositionFounder.java:311-312` calls the 7-arg overload with the pre-fetched `block`/`blockMeta` (`DeterminingIdentical.java:151-181`). `FuzzyChainPositionFounder` needs no lookup (class compare, `:50-51`). |
| 9 | `A3`: no chunk/EBS cache in the founder read path | **still present** (perf only, not a bug) — founders call `world.getBlock`/`getBlockMetadata` per candidate (`BasePositionFounder.java:455-458`, `ChainPositionFounder.java:305-308`). |
| 10 | `harvest-path-review` Bug A — missing neighbour notification (floating water/sand/plants) | **fixed** — `ChunkBlockWriteHelper.notifyBatchNeighborChange:228-294` with dedup, `CoFHWaterBridge`/`BushSupportBridge` bridges (`:257-263`), range re-render (`:291-293`), called from `BaseOperator.java:510-512,592-594` when `Config.notifyNeighborsOnChainBreak` (default true). Note: `BaseOperator`'s *TE/crop* sub-path (`chainExecutor.executeBatch`, `:224`) passes no sink — TE blocks go through vanilla `tryHarvestBlock`, which notifies neighbours itself. |
| 11 | `harvest-path-review` Bug B — `name.0` air+stale-GT-meta blocks | **fixed** — `ChunkBlockWriteHelper.writeAirToEbs:172-185` zeroes metadata on **both** branches (already-air and air-write), mirroring vanilla `Chunk.func_150807_a` (`Chunk.java:654,681`); GE ore containers are forced onto the vanilla TE path (`BlockHarvestActionExecutor.java:80,146,256`, `ChunkCachedHarvester.java:133`, `DeterminingIdentical.isGTTileEntityCarrier:322-329`). No other write path can leave stale meta: the only other removal is `setBlock(...,0,2)` (`MixinItemInWorldManager.java:108`) which goes through `Chunk.func_150807_a` (metadata zeroed) and the vanilla TE path. |
| 12 | `harvest-path-review` C1 — protection bypass | **still present** → CORE-10. |
| 13 | `harvest-path-review` C2 — founder-thread ⇄ server-thread read/write race | **still present (documented/accepted)** — `TickEventHandler.java:16-19` unpauses founders on `ServerTickEvent.START` on the same bus/priority as `BaseOperator.operatorTask` (`BaseOperator.java:109-111`, registered at `:342-344`), so the write phase and the worker reads are not ordered by the code; the worker `!paused.get()` check (`Pauseable.java:78`) bounds but does not remove the window. Needs a runtime/ordering test (see §5). |
| 14 | `harvest-path-review` C3/C4 — `precipitationHeightMap`/skylight stale, empty EBS lingers | **out of file scope** (t2 owns `ChunkBlockWriteHelper`); C4's "empty EBS reused with stale meta" consequence is now neutralised by the metadata zeroing. |
| 15 | `full-bug-scan` #11 — `ParallelTick` lists not cleared; `unPause` on a stopped thread | **still present** → CORE-08 (severity corrected: crash path effectively unreachable). |
| 16 | `full-bug-scan` P1 — logout/world-unload discards delayed drops/XP | **still present** → CORE-09. |
| 17 | `full-bug-scan` "Non-bugs verified": `encodePos`, `ParallelTick` list threading, `SearchWorkerPool` contract, packet IDs | **re-confirmed** — see §4. |
| 18 | `full-bug-scan` P1 — batch path skips neighbour/entity/TE/light updates | **fixed for neighbours/render, still true for light** — `func_147451_t`/relight is not called by the EBS path (only `updateHeightMap`, `ChunkBlockWriteHelper.java:345-393`); the `notifyBatchNeighborChange` range re-render (`:291-293`) only covers ≤32×32 regions. |
| 19 | test existence: AGENTS.md/CLAUDE.md say "**No tests exist**" | **stale docs** — three JUnit tests exist (`src/test/java/.../core/founder/`), `junit:junit:4.13.2` is declared (`dependencies.gradle:48`); see §4/§5. |

---

## 4. Verified OK (do not re-audit)

1. **Three harvest paths & flag semantics** — (1) per-block fast path `MixinItemInWorldManager.java:108` `setBlock(...,2)` (client update, no neighbour notify; light *is* updated because `World.setBlock` calls `func_147451_t`, `World.java:527-529`); (2) batch EBS path `ChunkBlockWriteHelper.writeAirToEbs` + `updateHeightMap` + `markBlockForUpdate` (`ChunkCachedHarvester.java:206`, `BlockHarvestActionExecutor.java:213`); (3) vanilla `tryHarvestBlock` for TE blocks and GT carriers (`BlockHarvestActionExecutor.java:81,147,257`; `ChunkCachedHarvester.java:134`). All mutation is reachable only from `BaseOperator.operatorTask` on `ServerTickEvent.START`.
2. **`isUnbreakable` coverage** — the predicate (`DeterminingIdentical.java:353-357`, creative exempt, `getBlockHardness < 0`) is present in every admission gate **and** before `skipHarvestCheck`: `BasePositionFounder.java:461-462`, `ChainPositionFounder.java:313-314`, `FuzzyChainPositionFounder.java:52-53`, `LogFounder.java:88-89`, `OreFounder.java:35-36`, `GtVeinOreFounder.java:57-58`, `ScreenBlastFounder.java:36-37`, `InverseBlastFounder.java:55-56`, `CropFounder.java:46`, and as the executor safety net `BlockHarvestActionExecutor.java:69,140,254`, `ChunkCachedHarvester.java:124`. `PlantingPositionFounder` deliberately omits it (it places, never breaks).
3. **Drop collection** — O(1) `LinkedHashMap<ItemStackKey,ItemStack>` for NBT-free stacks with copy-on-insert, short list for NBT stacks; `collect` clears the caller's list; `flush` spawns then clears (`ChainDropCollector.java:37-136`, `ItemStackKey.java:20-52`, key = identity item + damage). `Manager.onHarvestDrops:254-270` runs at `LOWEST` and is the single collection point; `flushDrops:352-383` + fallback chain `:389-417` (`tryFlush` refuses unloaded chunks, `ChainDropCollector.java:115-125`). No duplication path found (the flush is only invoked outside `isInOperate()`, `Manager.java:339-350`).
4. **XP** — fast paths compute and either drop at the block (`xpDropMode=0`) or accumulate for the chain-end flush (`=1` default); both entry points no-op on a client world (`XPDropHandler.java:79-115`). The mixin's fast path uses the pre-fired BreakEvent XP when available (`MixinItemInWorldManager.java:122-129`). (Inconsistency noted in §5.)
5. **`encodePos` / visited-set encapsulation / shell enumeration / per-strip resume cursor** — see §3 rows 3-7 and 11; the resume cursor logic is sound: `scanShellStrip:420-421` finalises, `shellPos:434-440` leaves the cursor on a stopping position, `curRadius` is deliberately not advanced while the shell is incomplete (`:338-359`), and `MAX_STALLED_SHELL_ATTEMPTS` (`:273,348-354`) is a defensive guard that cannot normally trigger (the founder parks in `waitUntil` between attempts, so a resumed attempt starts unpaused).
6. **`Pauseable` two-tier contract** apart from CORE-04: founder branch parks (`:82`), worker branch never parks (`:71-79`), `stopped` set in a `finally` (`:139-148`), interrupt observed after `waitUntil` in all shell/tunnel/log loops (`BasePositionFounder.java:166-167`, `LogFounder.java:54-55`, `TunnelFounder.java:39-40`).
7. **`ParallelTick` thread ownership** — `preTickTasks`/`normalTasks` are mutated/iterated on the server thread only (founders never touch them), `addNormalTask`/`processNormalTasks` are lock-guarded (`ParallelTick.java:34-60`); no CME found.
8. **Founder geometry** — `TunnelFounder.getVerticals` always returns exactly 2 basis vectors for every axis (`:58-64`); `BlastPositionFounder` shell-scan total surface is O(R²) ≈ 4.9k positions at the default `bigRadius = 8`; `LogFounder`'s wood/leaf OreDict lookup is memoised per `Block` (`:26,86`) and `OreDictionary.getOreIDs` is null-item safe (`build/rfg/minecraft-src/java/net/minecraftforge/oredict/OreDictionary.java:334-336`).
9. **Crop adapters** — `CropAdapterRegistry.init` order (`:35-44`) cannot shadow Natura: `mods.natura.blocks.crops.CropBlock extends BlockBush`, **not** `BlockCrops` (`tmp/Natura-master/src/main/java/mods/natura/blocks/crops/CropBlock.java:25`), so `VanillaCropAdapter.isCrop` (`instanceof BlockCrops`) never matches it; `NaturaCropAdapter`'s meta semantics match the real block (`getMaxGrowth` `:38-40`, cotton threshold meta 8 and reset to 6 in `onBlockActivated:109-121`, barley hardness `:127-130`); `CropFounder.checkCanAdd` uses `isCrop` while `BaseOperator.shouldHarvest:396-400` filters maturity — consistent by design.
10. **Cached-chain hash consistency** — the pre-calc writes `computeBlockIdHash(Block.getIdFromBlock(sample), dim)` (`chain/planning/ChainPreCalcEngine.java:306`) and `Manager.tryStartCachedChain` compares against the same function (`Manager.java:593`); fuzzy mode uses the class-name assignability check (`Manager.java:582-590`) instead of a hash. No block-ID-range (4096) assumption anywhere; `computeBlockIdHash` is a plain `Objects.hash` (`ChainPreCalcCache.java:53-55`).
11. **Bandit yield** — `Loader.isModLoaded("bandit")` (`Manager.java:69`) matches Bandit's real mod id (`tmp/Bandit-Legacy-master/src/main/kotlin/cn/elytra/mod/bandit/BanditMod.kt:24`), and Bandit really collects `EntityJoinWorldEvent` items inside its own scope (`.../listener/VeinMiningEventListener.kt:43-58`, `mining/HarvestCollector.kt:46`), so the documented "clear `event.drops` would starve Bandit" rationale holds.
12. **GT fortune mixin descriptors** — verified byte-for-byte against `tmp/GT5-Unofficial-beta2` (see CORE-05; all five listed mixins resolve on that tree, `MixinGuiIngameMenu` only acts in a deobfuscated environment, `MixinGuiIngameMenu.java:37`).
13. **Tests** — 3 JUnit 4 tests, pure-logic and Minecraft-free; their inline copies still match production: `EncodePosTest.java:28-30` ≡ `BasePositionFounder.java:507`, `ShellGeometryTest.java:35-81` ≡ `scanShellFaces:177-219`, `BfsConsistencyTest.java:96-98` ≡ `encodePos`. `junit` is declared (`dependencies.gradle:48`) so they compile.

---

## 5. Inconclusive / needs runtime test

* **CORE-01 wall-clock** — the loop counts are exact arithmetic; the resulting seconds (and whether the client preview notices) need a game run.
* **Build/test wiring** — Gradle/Java execution is denied in this sandbox. `build.gradle.kts` only applies the GTNH convention plugin, so whether `./gradlew build` runs `src/test/java` (and whether the tests are in the default source set of that plugin) is unverified. AGENTS.md/CLAUDE.md's "No tests exist" is factually stale regardless.
* **Mixin behaviour when a target class is missing** (CORE-05) — whether Mixin logs-and-skips per mixin or aborts the whole `mixins.EZMiner.json` on the GT5U-5.09 line can only be confirmed by running with that GT build.
* **`func_150802_k()` frequency** (CORE-06) — only observable in a live client at a world/chunk load edge.
* **Founder/server race window** (§3 row 13) — FML orders same-priority listeners by registration order; `TickEventHandler` registers in preInit (`CommonProxy.java:52`) and each `BaseOperator` on chain start (`BaseOperator.java:342-344`), so `TickEventHandler` should run first (unpause *before* the write phase), but confirming the effective order and its impact needs instrumentation.
* **Exhaustion skew** (CORE-12) — needs a food-drain measurement in game.
* **IC2 / CropsNH crop adapters** (`Ic2CropAdapter.java`, `CropsNHCropAdapter.java`) — no IC2 or CropsNH sources exist under `tmp/` (only `CropsNH` as a Gradle-only dependency, `dependencies.gradle:42`), so `crop.harvest(false)`, `card.canBeHarvested(crop)`, `ICropStickTile.hasCrop/canHarvest/doPlayerHarvest(player,false)` cannot be cross-checked: **not-applicable / UNKNOWN**, confirmed by compiling against the real IC2/CropsNH jars and running crop mode on one mature crop of each family.
* **`Manager.onEntityJoinWorld` TiC attribution window** (`Manager.java:289-315`) — the position is derived from the item entity's *floored* coordinates and the window is a synchronous hook replay, so the risk of swallowing an unrelated `EntityItem` at that exact position is small but not zero (also note a cancelled entity with `stack == null || stackSize <= 0` is destroyed without compensation, `:307-308`). Needs a TiC-autosmelt run alongside unrelated item spawns.
* **XP placement inconsistency** — TE blocks go through vanilla `tryHarvestBlock`, so their XP orbs spawn at the block (vanilla `dropXpOnBlockBreak`) instead of the configured `dropToPlayer`/`originPos` position used by `XPDropHandler.flush`; cosmetic, needs a runtime confirmation that both modes can coexist in one chain.

---

## 6. Acceptance answers

**"For the harvest core, can any externally registered block break it — and why?"**
The core is block-class agnostic and EndlessIDs-safe (all ID/meta access goes through `World.getBlock`/`getBlockMetadata` or `ExtendedBlockStorage.getBlockByExtId`/`func_150818_a` — `ChunkBlockWriteHelper.java:160-186,422-427`; no raw byte arrays, no 4096 assumption), so no *registration* property (block ID, extended NEID metadata, TE presence, unlocalised name) breaks it: TE-carrying blocks and the GT ore container classes are routed to the vanilla path (`BlockHarvestActionExecutor.java:80,146,256`; `ChunkCachedHarvester.java:133`), and mixed metadata blocks are handled per metadata by the ore mask (`DeterminingIdentical.java:209-217`). Three *code-level* hooks still let a third-party `Block`/`Material` stop the core:
1. **`block.getMaterial() == null`** → NPE at the first line of every admission gate (`BasePositionFounder.java:456-457` and the seven overrides) — reachable only for a block constructed with a null `Material`.
2. **An exception thrown by a modded block inside the harvest callbacks** — `block.getBlockHardness` (`DeterminingIdentical.java:356`), `block.canHarvestBlock`, `harvestBlock`/`onBlockHarvested`/`onBlockDestroyedByPlayer` (`BlockHarvestActionExecutor.java:201,209,220`; `MixinItemInWorldManager.java:101,111,118`) are invoked without a local try/catch: the founder-thread cases abort the search thread (worker variants are swallowed by the un-inspected futures, CORE-07), and in the server-side batch the caller's `catch (Exception)` (`BaseOperator.java:504-507` etc.) logs and skips **after** the EBS air write has already happened (`writeAirToEbs` precedes `harvestBlock`), i.e. the block is removed with **no drops**.
3. **A block that overrides `isAir()`** — the gate (`equals(Blocks.air)`) and the executor (`isAir(...)`) disagree (CORE-11): the position is queued but never harvested (no damage to the world, a wasted candidate slot).
`block.onNeighborBlockChange` is the one externally-implemented callback the batch path calls directly, and it is protected (`ChunkBlockWriteHelper.java:247-256` logs and continues), so a misbehaving neighbour cannot abort a batch.

**Files created/modified by this audit:** only `docs/review/agent-teams/core-audit.md` (no file under `src/` or `tmp/` was touched; no Gradle/Java was executed).
