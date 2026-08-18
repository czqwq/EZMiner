# Full Codebase Bug Scan — EZMiner

Scope: broad whole-codebase hunt over `src/main/java` (168 files), prioritizing
threading/network/chain/core. Every finding cites concrete file:line + reasoning.
Severity: **P0** crash/corruption, **P1** data loss / wrong behavior, **P2** edge case.

---

## P0 — Server-side packet handlers run on the netty IO thread and mutate server-thread state

**Root systemic defect.** In Forge 1.7.10, `SimpleNetworkWrapper` dispatches
server-side `IMessageHandler`s on the netty event-loop (IO) thread, *not* the
server main thread. `MainThreadEnforcer.guardedNull()` documents this but **only
logs a warning and runs the body immediately anyway** — it never actually defers
to the server thread:

- `network/MainThreadEnforcer.java:50-67` — `guardedNull` checks the thread name,
  logs "the handler will run on the IO thread", and calls `body.run()` on the IO thread.

Only two handlers even call `guardedNull` (`PacketKeyState:39`, `PacketChainModeSwitch:60`).
Every other C→S handler runs raw on the IO thread and mutates server-owned state:

1. **`network/PacketSaveServerConfig.java:277-354` (Handler)** — Mutates the global
   `Config.*` statics, writes the config to disk (`Config.saveServerConfig()` at :340),
   iterates `PlayerManager.instance.managers` (a plain `HashMap`, `PlayerManager.java:31`),
   and calls `mgr.pConfig.updateFrom(new MinerConfig())` on every player. All off-thread,
   concurrent with the server tick. **P0** — HashMap corruption / CME, torn config reads,
   disk I/O on the IO thread. Not OP-gated against thread placement.

2. **`network/PacketReloadServerConfig.java:35-51` (Handler)** — Same pattern:
   `Config.load()` + iterate `managers.values()` + `mgr.pConfig.updateFrom(...)` on the
   IO thread. **P0**.

3. **`network/PacketMinerConfig.java:55-64` (Handler)** — `PlayerManager.instance.managers.get(...)`
   then `mgr.receiveClientConfig(...)` (mutates per-player `MinerConfig`) on the IO thread.
   **P0/P1**.

4. **`network/PacketInventorySwap.java:50-71` (Handler)** — Directly writes
   `player.inventory.mainInventory[a/b]`, `markDirty()`, `detectAndSendChanges()` on the
   IO thread. Vanilla inventory structures are not thread-safe; a malicious client can also
   spam this unthrottled. **P0** (crash/corruption risk).

5. **`chain/network/PacketKeyState.java:39-67`** and
   **`chain/network/PacketChainModeSwitch.java:60-66`** — call `guardedNull`, but since
   `guardedNull` does not defer, they still touch `ChainPlayerState`, `PlayerManager.instance.managers`,
   and `Manager.resendMinesweeperMarks/resendSudokuFills` on the IO thread. Config default
   `enableMainThreadGuard=true` gives a false sense of safety. **P1**.

**Fix:** implement real deferral (queue work onto the server thread the way
`BaseOperator` uses the tick bus), or at minimum make handler bodies execute through
`MinecraftServer.addScheduledTask`. Make `managers` a `ConcurrentHashMap`.

---

## P1 — `PlayerManager.managers` is a non-thread-safe `HashMap` read off-thread

- `core/PlayerManager.java:31` — `public final Map<UUID, Manager> managers = new HashMap<>();`
  Written on the server thread (login/logout events, :39/:55) but read from the netty IO
  thread by `PacketKeyState:46-47`, `PacketChainModeSwitch` (indirect), `PacketMinerConfig:57`,
  `PacketSaveServerConfig:344`, `PacketReloadServerConfig:41`. Concurrent `get`+`put`/`remove`
  on a `HashMap` can corrupt the table (infinite loop in resize) → **P1** (potentially P0).

---

## P1 — `MainThreadEnforcer` is a no-op that advertises protection

Even when `Config.enableMainThreadGuard = true` (default), `guardedNull` never defers; it
merely logs. The whole class's contract ("defers execution if called from a netty IO thread",
`network/MainThreadEnforcer.java:22-23`) is unimplemented. Any handler relying on it (the only
two that do) is still running off-thread. **P1** (design bug masking the P0 above).

---

## P1 — `ChunkBlockWriteHelper` fast path skips `World.setBlock` neighbor/entity notifications

- `chain/execution/ChunkBlockWriteHelper.java:150-165` (`writeAirToEbs`), and all callers
  `ChunkCachedHarvester.harvestNext` (`ChunkCachedHarvester.java:169`),
  `BlockHarvestActionExecutor.executeBatch` (`BlockHarvestActionExecutor.java:179`).
- Writes air directly into `ExtendedBlockStorage` and calls only `world.markBlockForUpdate`
  (`ChunkCachedHarvester.java:177`, `BlockHarvestActionExecutor.java:184`). This **skips
  `Block.onNeighborBlockChange`, tile-entity break, entity-collision, light propagation, and
  block physics**. Redstone, falling sand/gravel, water-height updates and `TileEntity` cleanup
  on the *neighbors* of mined blocks will not fire (only `onBlockHarvested`/`onBlockDestroyedByPlayer`
  /`harvestBlock` are invoked manually). Controller explicitly documents this is
  "caller's responsibility" (`ChunkBlockWriteHelper.java:48-58`) but it is the caller (BaseOperator/
  harvest path) — leading to **stale redstone / dangling tile-entity / non-collapsing sand**.
  **P1** (wrong behavior; user-visible when `useChunkCachedHarvest=true`, default off).
  With default (`useChunkCachedHarvest=false`) the vanilla-path branch still skips neighbor
  notifications via flag=2 `setBlock`, but that is the safer documented path.

---

## P1 — `PacketMinerConfig` drops the `addExhaustion` / cooldown echo and cross-field caps

- `network/PacketMinerConfig.java:28-49` `fromBytes/toBytes` only transfer
  radius/blockLimit/smallRadius/tunnelWidth/useChainDoneMessage/logBigRadius/logBlockLimit/
  logFuzzyEnabled — but `MinerConfig.addExhaustion` is server-authoritative and
  `MinerConfig.updateFrom` copies it (`core/MinerConfig.java:64`). Because the client never
  sends `addExhaustion`, `receiveClientConfig` (`Manager.java:483-493`) forcibly uses
  `Config.addExhaustion` (line 492) — OK — but the echo packet (`PacketMinerConfig` returned at
  `PacketMinerConfig.java:63`) carries a config whose `addExhaustion` was never serialized, so
  the client sees default. **P2** (cosmetic on this field). More important: `receiveClientConfig`
  does **not cap `addExhaustion`** (it hard-assigns server value, safe) but it also does **not
  validate `useChainDoneMessage`/`logFuzzyEnabled`** against limits and silently overwrites
  `logFuzzyEnabled = Config.logFuzzyEnabled` (Manager.java:490), ignoring the client's choice.
  **P2**.

---

## P1 (data loss) — Logout path removes the Manager but does not flush collected drops

- `chain/lifecycle/ChainLifecycleService.onPlayerLogout` (`ChainLifecycleService.java:21-29`):
  calls `stopRuntime(mgr)` → `stopImmediately()` + `mgr.cleanupState()` + `mgr.clearDrops()`.
  `clearDrops` (Manager.java:439-442) **discards** the `ChainDropCollector` contents and all
  accumulated XP without spawning them. If a player logs out (or the server unloads the world)
  mid-chain with a large delayed-drop batch (`dropImmediately=false`), every mined item/XP from
  the active batch is silently deleted. `onWorldUnload` (`ChainLifecycleService.java:41-46`)
  does the same. **P1** — real item loss. Should flush to the player's feet / world before clearing.

---

## P1 — `Manager.onHarvestDrops` clears `event.drops` but the collector's post-flush logic can double-spawn with `dropImmediately`

- `core/Manager.java:254-270` — On `dropImmediately=true`, `flushCollectedDrops()` is called
  inside the `HarvestDropsEvent` handler. `flush` spawns `EntityItem`s at the player's position
  while the event is still being processed, then `event.drops` was already cleared by `collect`
  (`ChainDropCollector.collect` clears the list at `ChainDropCollector.java:84`). This can race with
  the same event's vanilla spawning of other drops. Additionally, calling `flushCollectedDrops()`
  (Manager.java:318-334) re-enters during the event dispatch; if two drops for the same tick arrive
  in separate `HarvestDropsEvent`s, the first flush may spawn items into a chunk and the second
  re-collect — generally benign but the repeated `flush` inside event handling is fragile. **P2**.

---

## P2 — `ChainDropCollector` integer overflow on merged stack sizes

- `chain/execution/ChainDropCollector.java:68,77` — `existing.stackSize += drop.stackSize` with no
  cap. If cumulative drops of one `ItemStackKey`/NBT-identical stack exceed `Integer.MAX_VALUE`
  (huge veins / cobblestone with meta 0 merging), `stackSize` wraps negative; `EntityItem` spawned
  with a negative/very large stack (`flush`, :97-104) is malformed. Extreme edge, but a guarded
  `long` sum + split-into-64 would be correct. **P2**.

---

## P2 — `ChainPreCalcEngine` un-bounds-checked sub-chunk index → AIOOBE

- `chain/planning/ChainPreCalcEngine.java:226` — `currentChunk.getBlockStorageArray()[y4]` with
  `y4 = cy >> 4` where `cy` can be `< 0` or `> 255` (from `center.y ± bigR`). No bounds check
  (unlike `ChunkBlockWriteHelper.getEbs` which checks `y4 < 0 || y4 >= storage.length` at
  `ChunkBlockWriteHelper.java:254-255`). Throws `ArrayIndexOutOfBoundsException`, caught by the
  outer try (`ChainPreCalcEngine.java:304-307`) which calls `stop(player)` — cancelling the whole
  pre-calc whenever a candidate with out-of-range Y is reached. **P2** (functionality loss,
  not crash).

---

## P2 — `ChainPreCalcEngine` uses `world.getChunkFromChunkCoords` that can return null with chunk-loading enabled

- `chain/planning/ChainPreCalcEngine.java:220` — when `enableChainChunkLoading=true`, the
  `blockExists` guard at :215 is skipped, so `getChunkFromChunkCoords` may return `null` for a
  chunk still loading. Handled by the `currentChunk != null` guard at :226/:234 (nbBlock null →
  continue), but a partially-initialized chunk image read off-thread is possible. Mitigated by the
  outer try/catch. **P2**.

---

## P2 — `ParallelTick` pre-tick list is never cleared between server restarts (same JVM)

- `thread/ParallelTick.java:16,57-60` — `preTickTasks` is a field on the single `EZMiner.parallelTick`
  singleton, cleared only by `processPreTickTasks(false)` removing `stopped` tasks (:21-24).
  `CommonProxy.serverStarting` (`CommonProxy.java:61-65`) only reinstantiates `PlayerManager`; it
  does **not** clear `preTickTasks`/`normalTasks`. On an integrated-server restart (`/reload`,
  returning to menu and opening a new world) stale founder `Pauseable`s remain. When a stale
  already-started-but-stopped founder is reached in `processPreTickTasks(true)`, `unPause()`
  hits `if (stopped.get())` and increments `errorCount` (`Pauseable.java:99-110`); after 10 such
  hits it `throw new RuntimeException(...)` **on the server tick thread** (`Pauseable.java:104`).
  Normally each tick-END removes stopped tasks so errorCount stays low, but a world swap that
  never runs a tick-END before the founder is re-encountered can trip it. **P2** (fragile; could
  become a crash). Fix: clear `parallelTick.preTickTasks/normalTasks` on server stop and on
  `serverStarting`.

---

## P2 — `ChainWatchdog` fallback tick source mixes wall-clock with tick counter

- `chain/watchdog/ChainWatchdog.java:86-94` — `currentServerTick()` returns the server tick counter
  when available, else `System.currentTimeMillis()/50`. `markChainStarted`/`recordProgress`/`hasTimedOut`
  can mix the two bases if `MinecraftServer` is transiently null (e.g., during shutdown), producing a
  spurious watchdog timeout or a no-timeout. Minor, but the mixing of bases across calls is incoherent.
  **P2**.

---

## P2 — `BaseOperator` idle-timeout arithmetic can underflow `countdownSec` to large positives

- `core/BaseOperator.java:153` — `countdownSec = Config.chainIdleCountdownSeconds - (int)((now-countdownStartTime)/1000)`.
  If the client clock... actually wall-clock always advances; the concern is a **server freeze** where
  `(now - countdownStartTime)/1000 >> chainIdleCountdownSeconds`, making `countdownSec` very negative;
  `< 0` triggers cancel (`:154-160`), which is the intended fallback. But because `countdownStartTime`
  is only set on the first idle tick (`:149-152`), and `lastHarvestedTime` resets on every harvest
  (`markHarvested`, :367-372), a chain that stalls at a chunk boundary for longer than the sum still
  cancels correctly. The real edge: `countdownSec` can be `0` exactly → `<= 0` cancels immediately even
  though the countdown just began. Minor. **P2**.

---

## P2 — Client-side packet handlers mutate `Config` statics from the netty thread

- `network/PacketServerConfig.java:237-287` (Handler, `ctx.side.isClient()`): writes
  `Config.runtimeServer*`, `Config.logFuzzyEnabled`, `Config.blacklistExpression`, `EZMiner.clientIsOp`
  from the client netty thread while the client render thread reads the same statics (HUD/preview).
  Not synchronized; most are plain even non-volatile fields except `clientIsOp` (volatile,
  `EZMiner.java:50`). Read-mostly but a torn `double` (`addExhaustion` etc.) is possible. **P2**.

---

## P2 — `CooldownTracker` static `HashMap` not concurrency-safe (latent)

- `chain/execution/CooldownTracker.java:28` — `new HashMap<>()`; currently touched only on the server
  thread (`BaseOperator.unRegistry`, `Manager` events, `ChainLifecycleService.onPlayerLogout`), so safe
  today. If the IO-thread packet handlers (see P0) ever call `isOnCooldown` (they are in the start chain
  path indirectly), it becomes a race. Recommend `ConcurrentHashMap`. **P2** (latent).

---

## P2 — `ConfigValidator` claims to clamp but doesn't

- `config/ConfigValidator.java:67-69` — "searchWorkerThreads exceeds maximum (8). Clamping to 8."
  but `Config.searchWorkerThreads` is never reassigned; it's a warn-only validator. Misleading log only;
  not a correctness bug. **P2** (cosmetic).

---

## P2 — `XPDropHandler.flush` merged single orb can exceed per-orb cap

- `chain/execution/XPDropHandler.java:160-167` — `total` sums all accumulated XP into a single
  `EntityXPOrb`. Vanilla XP orbs carry a per-orb `xpValue` (15 max in vanilla; some mods raise it);
  an enormous vein merging into one orb can exceed the displayed/split cap, losing XP. Not bounded or
  chunk-split. **P2** (edge; large GTNH veins).

---

## P2 — `BaseOperator.unRegistry` not idempotent re cooldown/message; `stopImmediately` + tick can double-send

- `core/BaseOperator.java:314-338` (`unRegistry`) vs `:341-350` (`stopImmediately`) vs
  `chain/lifecycle/ChainLifecycleService.java:54-61`. A chain that hits an idle-timeout in `operatorTask`
  (unRegistry) while the lifecycle simultaneously issues `stopImmediately` (dimension change mid-batch)
  can run both paths in the *same* server thread across two tick/event frames, sending the "done" message
  and recording a cooldown twice. `unRegistry` guards nothing; no `closed` flag. **P2**.

---

## Non-bugs verified (reported-for-completeness / no action)

- `BasePositionFounder.encodePos` packing (`BasePositionFounder.java:391-393`): 26/12/26-bit split with
  +30M bias and `y & 0xFFF` mask is correct; no collision for any in-range MC coord. Not a bug.
- `ParallelTick.processPreTickTasks` list iteration: add/remove/iterate all on the server thread (founders
  never modify `preTickTasks` themselves) — no concurrent-modification on the server side.
- `SearchWorkerPool`: `invokeAll` bounded batches + `consumeBudget()` worker no-park path
  (`Pauseable.java:70-85`) matches the documented two-tier contract. `budgetRemaining` non-thread-safe is
  intentional (founder-thread only).
- Network packet ID increments are linear and unique in `NetworkMain.registry()` — no collisions.

---

## Top findings summary (ranked)

1. **P0** `PacketSaveServerConfig.Handler` mutates `Config` statics + `managers` (HashMap) + disk I/O on netty IO thread (`PacketSaveServerConfig.java:277-354`).
2. **P0** `PacketInventorySwap.Handler` writes player inventory + `markDirty`/`detectAndSendChanges` on IO thread (`PacketInventorySwap.java:50-71`).
3. **P0** `PacketReloadServerConfig.Handler` reloads config + re-caps all players on IO thread (`PacketReloadServerConfig.java:35-51`).
4. **P1** `MainThreadEnforcer.guardedNull` only logs, never defers — all handlers effectively off-thread (`MainThreadEnforcer.java:50-67`).
5. **P1** `PlayerManager.managers` plain `HashMap` read off-thread by packets (`PlayerManager.java:31`).
6. **P1** `ChunkBlockWriteHelper` fast path skips neighbor/entity/TE/light updates (`ChunkBlockWriteHelper.java:150-165`).
7. **P1** Logout/world-unload discards in-flight delayed drops/XP (`ChainLifecycleService.java:24-27,41-46` + `Manager.clearDrops`).
8. **P1** `PacketKeyState`/`PacketChainModeSwitch` handlers still run off-thread despite `guardedNull`.
9. **P2** `ChainPreCalcEngine` un-bounds-checked `storage[y4]` AIOOBE cancels precalc (`ChainPreCalcEngine.java:226`).
10. **P2** `ChainDropCollector` `stackSize` merge overflow (`ChainDropCollector.java:68,77`).
11. **P2** `ParallelTick` pre-tick tasks never cleared across JVM-internal server restarts; `unPause` on stopped thread can throw after 10 hits (`ParallelTick.java:16`, `Pauseable.java:104`).
12. **P2** `Manager.receiveClientConfig` ignores client `logFuzzyEnabled`/cooldown caps (`Manager.java:483-493`).
13. **P2** `ChainWatchdog` mixes tick-counter and wall-clock bases (`ChainWatchdog.java:86-94`).
14. **P2** `PacketServerConfig` client handler writes `Config` statics off-thread (`PacketServerConfig.java:237-287`).
15. **P2** `CooldownTracker` static `HashMap` latent race (`CooldownTracker.java:28`).
