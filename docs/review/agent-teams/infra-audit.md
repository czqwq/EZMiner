# EZMiner infrastructure audit — Config / network / command / permission / api / utils / toolswap / entrypoints

- **Task**: t4 [audit-infra] (team `ezminer-audit`), attempt `51dad08f-9f29-42ff-90c2-db493312b6ed`
- **Auditor**: audit-chain-ds
- **Mode**: read-only. No file under `src/` or `tmp/` was modified; no Gradle/java was executed (sandbox denies external programs). Only this report was created.
- **Method**: full read of the in-scope sources + `grep` proof for every dead-code / no-live-caller claim; cross-check against the review corpus (`docs/review-summary.md`, `docs/review/decouple-api-review.md`, `docs/review/full-bug-scan.md`, `docs/review/perf-mixin-review.md`, `docs/review/agent-teams/client-audit.md`) and against third-party sources under `tmp/`.
- **Line numbers**: current working tree at audit time (spotless-formatted, `Config.java` = 1913 lines, `PacketServerConfig.java` = 293, `PacketSaveServerConfig.java` = 360, `EZMinerConfigGui.java` = 2206, `NetworkMain.java` = 114).
- **Not re-verified / out of scope here**: world-mutation semantics of the harvest paths (t1), chain state machine internals (t2), client render output (t3), compat bridge behaviour (t5), `tmp/` block registration cross-check (t6/t7). Where a finding touches those areas only the packet/config side is claimed.

---

## 0. Executive summary

| # | Severity | One-line |
|---|---|---|
| F1 | **high** | 9 server config fields are GUI-editable and persisted but **never synced** → OP “Save” silently overwrites the server's real values with the client's local file values; the GUI also *displays* the wrong state |
| F2 | **high** | `PacketToolSwapRequest` / `PacketToolSwapFinalize` mutate live player inventory + a plain `HashMap` ledger **on the netty IO thread**, contradicting their own “must run on the server main thread” contracts → item duplication/loss and ledger corruption risk |
| F3 | **medium** | `PacketSaveServerConfig` / `PacketReloadServerConfig` (OP-only) run `Config` mutation + disk I/O + player iteration on the netty thread; two concurrent OP saves race on the same Forge `Configuration` object and file |
| F4 | **medium** | 2 of 8 C→S handlers use `MainThreadEnforcer`; deferred work has no player-liveness guard → state can be re-created for a disconnected player (leak) |
| F5 | **medium** | `addExhaustion` is accepted unvalidated (NaN/±∞) through the save packet; `plantRadius`/`plantMaxCount` clamps disagree between the save path and the load path |
| F6 | **medium** | `maxFortuneLevel` is a dead field: declared/loaded/saved/documented, but **no live reader** (grep proof) — the Fortune cap it advertises is unenforced |
| F7 | **medium** | The documented “mixin capability gate” does not exist: `Mixins` enum is empty, no mixin config registers `MixinCapabilityPlugin` → `enableMixinCapabilityGates` is write-only |
| F8 | **medium** | Mixin registration inconsistency: `MixinGuiIngameMenu` (client-only target) sits in the config's **common** list while `"client": []` is empty; the late config declares no mixins |
| F9 | **medium** | Packet ids are registration-order dependent and the mod declares `acceptableRemoteVersions = "*"` with no id/version guard |
| F10 | **medium** | `MessageUtils.serverSendPlayerMessage` only scans the **overworld** player list → every chain chat message is dropped for players in the Nether/End |
| F11 | **medium** | `ItemFilterExpression`: `*` matches only ore-registered items (silent no-op for “destroy everything”); malformed input disables filtering entirely (fail-open) |
| F12 | **low-med** | `PacketCachedBlockSync.fromBytes` trusts the wire `count` (no remaining-bytes/limit check) |
| F13 | **low-med** | S→C handlers run on the client netty thread and mutate client tick state (`suitableSlots` `ArrayList`, non-volatile `founder`) |
| F14 | **low** | `FileReadUtils` + `MatrixUtils` are dead code (grep proof); `FileReadUtils.LOG` is the **root** logger |
| F15 | **low** | `TimeFormatUtils` caches locale detection in one static shared by client and server paths; server path always uses the vanilla `StatCollector` fallback |
| F16 | **low** | `PacketSaveServerConfig`: legacy 24-param constructor silently substitutes defaults for 6 fields; the 30-param tail has adjacent booleans (transposition is compiler-legal) |
| F17 | **low** | `PacketChainModeSwitch` clamps to array lengths, not to the config/VP-conditional selectable set |
| F18 | **low** | `EZMinerAPI` does not enforce its documented server-thread rule; “read-only” queries create state for arbitrary UUIDs |
| F19 | **low** | Permission-policy inconsistencies: `PacketReloadServerConfig` uses `canCommandSenderUseCommand(2,…)` while everything else uses `OpPermissionChecker`; EZMiner ignores server-rank permission systems (ServerUtilities) |

Config coverage headline (full matrix in §2): **55** server config fields. **52** have load+save; **3** (`enableUnlimitedOreFortune`, `maxFortuneLevel`, `enableFortuneForPlacedOre`) are load-only (no GUI row, never written back). **46** are synced to the client. **9** are saved + GUI-editable but never synced (**F1**). **1** (`enableMixinCapabilityGates`) is load+save but has no GUI row, no sync, no packet field and no live consumer (**F7**).

---

## 1. Findings

### F1 — 9 server config fields are GUI-editable + persisted but never synced: OP “Save” silently resets them (high, confidence high)

**Files/lines**

- `network/PacketServerConfig.java:38-69` — the packet declares fields for the “P1-1 fix” set, but has **no** field for the stability/tool-handoff group.
- `network/PacketSaveServerConfig.java:66-75` — the save packet *does* carry them:
  ```java
  // Stability settings
  public boolean enableChainWatchdog;
  public boolean enableDropFallbackChain;
  public boolean enableMainThreadGuard;
  public boolean enableBudgetDeadline;
  public boolean enableConfigValidation;
  public boolean enableSafeReflection;
  public int chainWatchdogTimeoutTicks;
  public boolean enableToolBreakHandoff;
  public int toolBreakHandoffTimeoutTicks;
  ```
- `client/gui/EZMinerConfigGui.java:2049-2060` — the GUI builds those fields from the **client's local** `Config.*` statics:
  ```java
  packet.enableChainWatchdog = Config.enableChainWatchdog;
  packet.enableDropFallbackChain = Config.enableDropFallbackChain;
  ...
  packet.toolBreakHandoffTimeoutTicks = parseI(tfToolBreakHandoffTimeoutTicks, Config.toolBreakHandoffTimeoutTicks, 1);
  ```
- `client/gui/EZMinerConfigGui.java:559-614`, `1645-1652` — the same client-local statics initialize the toggle buttons and text fields for those rows.
- `Config.java:985-1019` (`applyServerRuntimeConfig`) — the client-side apply path has **no** parameters for these 9 fields (24 params: cachedBreakPerTick … notifyNeighborsOnChainBreak).
- `network/PacketSaveServerConfig.java:326-334` — the server applies them to the globals and `Config.saveServerConfig()` (line 345) persists them.

**Failure scenario (dedicated server, GTNH)**

1. Server admin sets `enableChainWatchdog=true, chainWatchdogTimeoutTicks=100, enableToolBreakHandoff=true, enableMainThreadGuard=true, …` in `<game_root>/EZMiner/EZMiner_Server.cfg`.
2. Client config `Config.load()` also runs `loadServerOnlyInternal()` on the **client**, from the client's own `.minecraft/EZMiner/EZMiner_Server.cfg` (defaults; `CommonProxy.java:47` calls `Config.init(client, server)` on both sides). `PacketServerConfig` never overwrites them.
3. OP opens the server tab → the watchdog/handoff/stability rows show the *client's* defaults (stale display), then presses **Save** → `applyAndSaveServerConfig()` (line 2003) sends the client's values → `PacketSaveServerConfig.Handler:326-334` overwrites the server globals → `saveServerConfig()` writes them to the server file.
4. Net effect: the OP's save silently disables the watchdog, the budget deadline, config validation, safe reflection, the main-thread guard and the tool-break handoff (and resets both timeouts), with **no** user-visible warning. `enableToolBreakHandoff` additionally splits brain between server and client: the server decides to send `PacketToolBreakHandoff` (`Config.enableToolBreakHandoff`, `core/BaseOperator.java:269`) while the client handler checks its own local copy (`network/PacketToolBreakHandoff.java:59`), so a mismatch makes the server wait out `toolBreakHandoffTimeoutTicks` and then cancel the chain while the client ignores every handoff packet.

**Minimal fix**

Add the 9 fields to `PacketServerConfig` (public fields + `toBytes`/`fromBytes` + `buildForPlayer` assignment) and the 9 parameters to `Config.applyServerRuntimeConfig` (or a new `applyServerRuntimeStability(...)` following the existing `applyServerRuntimePerformance` pattern at `Config.java:972-977`), then call it from `PacketServerConfig.Handler`. No constructor growth is needed (the documented named-field pattern). This closes the “19 legacy fields” leftover recorded in `docs/review-summary.md:69` for the subset that is still unsynced.

**Confidence**: high (all five steps verified by reading; no writer of those client statics exists other than the client's own config file and the GUI).

---

### F2 — tool-swap handlers mutate live inventory and the ledger on the netty IO thread (high, confidence high)

**Files/lines**

- `toolswap/server/ToolSwapInventoryPort.java:9-12`:
  ```java
  * Mirrors Qz-Miner's {@code AutoToolSwapInventoryPort}: the server is the only
  * owner of physical inventory mutations during a chain. Implementations must run
  * on the server main thread.
  ```
- `toolswap/server/ToolSwapServerService.java:22-29` — `private static final Map<UUID, ToolSwapServerLedger> LEDGERS = new HashMap<>();` and `/** Records a borrow swap before it is applied. Call on the server thread. */`
- `toolswap/server/ToolSwapServerLedger.java:20` — `private final ArrayDeque<SwapRecord> records = new ArrayDeque<>();` (not thread-safe).
- `network/PacketToolSwapRequest.java:63-119` — the handler (registered `Side.SERVER`, `NetworkMain.java:93-94`) runs on the netty IO thread and:
  ```java
  108  ItemStack aStack = inv.readInventorySlot(anchor);
  109  ItemStack bStack = inv.readInventorySlot(candidate);
  110  ToolSwapServerService.recordSwap(player, anchor, candidate, aStack, bStack);
  112  inv.swapInventorySlotsAtomically(anchor, candidate);
  118  inv.syncInventoryDifference();
  ```
- `network/PacketToolSwapFinalize.java:30-35` — same, calls `ToolSwapServerService.finalize(player)` → `ToolSwapServerLedger.restoreAll` → `MinecraftToolSwapInventoryPort.swapInventorySlotsAtomically` (`toolswap/server/MinecraftToolSwapInventoryPort.java:50-57`) directly on `player.inventory.mainInventory[]`.
- Contrast: the two chain packets *do* use the guard — `chain/network/PacketKeyState.java:39`, `chain/network/PacketChainModeSwitch.java:60` (`MainThreadEnforcer.guardedNull`).

**Failure scenario**

- **Concurrency/duplication**: `PacketToolSwapRequest` (netty) swaps slots while the server thread runs `ContainerInventory.detectAndSendChanges()` / `EntityPlayerMP.onUpdate()` on the same `InventoryPlayer`. A partial window sync can leave the *server* inventory swapped but the client mirrored state only half-updated; combined with `syncInventoryDifference()` (`sendContainerToPlayer`) issued from the netty thread and the client's own hotbar changes, the player can observe a duplicated tool in the hotbar (server slot A holds tool X while the last full-window snapshot the client applied still shows X in both A and B). The fingerprint guard in `ToolSwapServerLedger.SwapRecord.matchesPostSwap` (`:117-126`) then no longer matches, so `restoreAll` marks `conflict` and leaves the swapped layout in place — the borrow becomes permanent.
- **HashMap corruption**: `ToolSwapServerService.LEDGERS` is a plain `HashMap` mutated from netty threads *and* from the server thread (`chain/lifecycle/ChainLifecycleService.java:30/36/42` call `clear`, `core/BaseOperator.java:372/385` call `finalize`). Two concurrent requests (e.g. two candidates sent back-to-back by `PacketToolBreakHandoff.Handler:120-125` + `SmartToolSwitchHandler:218`) can interleave `computeIfAbsent`/`remove` → lost/duplicated ledger entries or a corrupted bucket chain; the `ArrayDeque` in the ledger can throw `NoSuchElementException`/`ConcurrentModificationException`.
- **Stale container state**: `player.openContainer != player.inventoryContainer` and `player.inventory.getItemStack()` are read on the netty thread (`PacketToolSwapRequest.java:68-69`, `:95`) — the authoritative owner of that state is the server thread.

**Minimal fix**

Wrap both handlers' bodies in `MainThreadEnforcer.guardedNull(ctx.side, …)` (the existing primitive) — the inventory/ledger mutation then runs on the server thread in the next tick; alternatively make `ToolSwapServerService` use `ConcurrentHashMap` **and** defer the inventory mutation. Guarding is sufficient and matches the documented contract without new machinery.

**Confidence**: high (contract text + both call paths read; the only server-thread callers are `BaseOperator.finalize`).

---

### F3 — OP config handlers do blocking config I/O and player iteration on the netty thread (medium, confidence high)

**Files/lines**

- `network/PacketSaveServerConfig.java:284-356` — `onMessage` immediately: OP check → 50 global `Config.*` writes → `Config.saveServerConfig()` (Forge `Configuration.save()` = file write) → iteration of `PlayerManager.instance.managers` + 3 `sendTo` per player. No `MainThreadEnforcer` anywhere in the file.
- `network/PacketReloadServerConfig.java:35-51` — same shape, but calls `Config.load()` (line 39) → `serverConfiguration.load()` (disk read, `Config.java:512`) → `loadServerOnlyInternal()` (mutates ~50 globals) → `ConfigValidator.validate()` (`Config.java:506-508`) → broadcast loop. Also no guard.
- `Config.java:53` (`MainThreadEnforcer` javadoc): *“Handlers that touch world / per-player state must run on the server thread.”*

**Failure scenario**

- Two OPs press Save/Reload simultaneously: each request is handled on a *different* netty event-loop thread → two threads mutate the same `Configuration` properties map (`serverConfiguration.get(...).set(...)`) and write the same file concurrently (`Configuration.save()` uses a `PrintWriter` over the same path). Result: an interleaved/truncated `EZMiner_Server.cfg`, or a `ConcurrentModificationException` inside Forge's config save, or a value from one save lost.
- While the netty thread rewrites the 50 globals, the server tick reads them (`Config.breakPerTick` in `BaseOperator`, `Config.enableChainChunkLoading`, `Config.notifyNeighborsOnChainBreak`, …) → readers can observe a mixed configuration (e.g. `bigRadius` from the new value with `blockLimit` still old) for one tick. `double addExhaustion` is not even guaranteed to be read atomically.
- File I/O on a netty event-loop thread also stalls every other connection sharing that loop.

**Minimal fix**

Route both handlers through `MainThreadEnforcer.guardedNull(ctx.side, …)`; additionally make the OP check re-run inside the deferred body (the player may have lost OP or logged out during the wait).

**Confidence**: high.

---

### F4 — MainThreadEnforcer is applied to only 2 of 8 C→S handlers; deferred work has no liveness guard (medium, confidence medium-high)

**Files/lines**

- Guard users: `chain/network/PacketKeyState.java:39`, `chain/network/PacketChainModeSwitch.java:60`.
- Guard missing (C→S, server-side mutation): `network/PacketMinerConfig.java:54-64`, `network/PacketSaveServerConfig.java:284`, `network/PacketReloadServerConfig.java:35`, `network/PacketRequestClientReload.java:31`, `network/PacketToolSwapRequest.java:64`, `network/PacketToolSwapFinalize.java:30`. Only `PacketOpStatusRequest`/`PacketOpStatusResponse` are side-effect-free.
- `network/MainThreadEnforcer.java:32` — `private static final Queue<Runnable> DEFERRED = new ConcurrentLinkedQueue<>();`; `:52-65` captures the body closure; `:71-82` drains with no player-liveness check.
- `chain/state/ChainStateService.java:18-19` — `getOrCreate` = `stateMap.computeIfAbsent(playerUUID, ChainPlayerState::new)`; `:28-31` `onPlayerLogout` = `stateMap.remove(playerUUID)`.
- `core/PlayerManager.java:92-95` — `drainDeferred()` runs once per server tick.

**Failure scenario (key press + immediate disconnect)**

1. Client sends `PacketKeyState(true)`; the netty thread defers the body into `DEFERRED` (`MainThreadEnforcer.java:54`).
2. The player disconnects before the next server tick. `ChainLifecycleService.onPlayerLogout` (`chain/lifecycle/ChainLifecycleService.java:22-31`) removes the `Manager` and `ChainStateService.onPlayerLogout` removes the state.
3. The next tick drains the stale body: `PacketKeyState.Handler` takes `ctx.getServerHandler().playerEntity` (a disconnected `EntityPlayerMP`) and calls `EZMiner.chainStateService.getOrCreate(uuid)` (`PacketKeyState.java:41`) → the state for the offline player is **re-created after cleanup** and never removed again (it is only removed on the next logout of the same UUID). `state.keyPressed = true` is left behind; `network.sendTo(..., player)` on a closed channel logs an FML send failure.
4. `EZMinerAPI.isActive(uuid)` (public API) then reports `true` for a player who is not online.

The same closure-capture pattern applies to the missing guards in F2/F3 — those are *worse* because they mutate inventory/config immediately rather than being deferred.

**Minimal fix**

In `MainThreadEnforcer.guardedNull`, capture the owning `EntityPlayerMP` (or a `BooleanSupplier` liveness predicate) and skip the body when the player is gone; and add the guard to `PacketMinerConfig`, `PacketSaveServerConfig`, `PacketReloadServerConfig`, `PacketRequestClientReload`, `PacketToolSwapRequest`, `PacketToolSwapFinalize`. Note `isOnIoThread()` (`MainThreadEnforcer.java:37-41`) detects the netty thread **by name substring** — a fragile heuristic that silently degrades to the legacy unsafe behaviour if the pool is ever renamed; an explicit `Thread.currentThread() instanceof`/`@SideOnly`-free server-thread marker would be stronger.

**Confidence**: medium-high (code paths certain; the exact disconnect-vs-drain ordering depends on FML event order, which I could not execute in this sandbox).

---

### F5 — unvalidated / inconsistently clamped values in `PacketSaveServerConfig` (medium, confidence high)

**Files/lines**

- `network/PacketSaveServerConfig.java:297` — `Config.addExhaustion = msg.addExhaustion;` — **no clamp**, while every sibling is clamped (lines 294-341). The load path clamps via Forge (`Config.java:761-770`, range `-Double.MAX_VALUE … Double.MAX_VALUE`, which does not reject NaN/±∞ either).
- `network/PacketSaveServerConfig.java:340-341` vs `Config.java:840-863` and `Config.java:1002-1003`:
  ```java
  Config.plantRadius = Math.max(1, Math.min(64, msg.plantRadius));
  Config.plantMaxCount = Math.max(1, Math.min(1024, msg.plantMaxCount));
  ```
  vs load/apply: `plantRadius` → 1..12, `plantMaxCount` → 1..256.

**Failure scenarios**

- A hand-crafted (or future GUI) `PacketSaveServerConfig` with `addExhaustion = NaN`: any OP (or anyone who gets the packet through, see F19) sets the global to NaN. Every chained block then calls `player.addExhaustion(NaN)` → `FoodStats.foodExhaustionLevel` becomes NaN forever; `foodExhaustionLevel > 4.0F` is false for NaN, so **no player ever loses saturation/hunger again**, and `-Inf` has the same effect. The corrupt value is persisted to the server file (`Config.java:1578-1586` writes it back with no validation) and survives restarts (`Double.parseDouble("NaN")` succeeds).
- `plantRadius=64` accepted by the GUI/save path is reduced to 12 on the next `Config.load()` and is reported as 12 to every other client (`applyServerRuntimeConfig:1002` clamps to 12), so the OP's own session behaves differently from everybody else's with no diagnostic.

**Minimal fix**

Clamp `addExhaustion` (`Math.max(-1000.0, Math.min(1000.0, x))` plus `if (Double.isNaN(x) || Double.isInfinite(x)) reject`) and align the plant clamps with the single source of truth used by `loadServerInternal`/`applyServerRuntimeConfig` (1..12 / 1..256).

**Confidence**: high.

---

### F6 — `maxFortuneLevel` is a dead config field (medium, confidence high)

**Files/lines / grep proof**

- Declared `Config.java:312-313`; loaded `Config.java:919-929`; saved `Config.java` (property written in `saveServerConfig`, no separate line) — declared/loaded/saved per §2 matrix.
- Grep across `src/main/java` for `maxFortuneLevel` returns exactly 3 files: `Config.java:313/919/…`, `config/ConfigValidator.java:79-83`, and the field's own javadoc reference. **No consumer** — in particular the fortune mixins only call `FortuneCompatHelper` (`mixin/early/MixinGTOreAdapter.java:23,32`, `MixinBWOreAdapter.java:23,32`, `MixinGTPPOreAdapter.java:23`), and `utils/FortuneCompatHelper.java:10-16` never reads `maxFortuneLevel`.
- Documentation promises the cap: `Config.java:312` *“Max Fortune level for GT/BW ores (clamped to 255)”*, `Config.java:924-929` *“Maximum Fortune enchantment level that GT / BartWorks ores will respond to …”*.

**Failure scenario**

An admin sets `maxFortuneLevel=3` or `maxFortuneLevel=50` believing it caps drop multiplication; the fortune uncap then honours **any** enchantment level present on the tool (a modded/pack item with Fortune 200 yields the full uncapped multiplication). The only effect of the field is a log warning when >100 (`ConfigValidator.java:79-83`) — i.e. the safety cap the validator itself warns about is not implemented.

**Minimal fix**

Either enforce it where the cap check is bypassed (pass `Config.maxFortuneLevel` into the uncapped roll inside the three mixins, or clamp the effective level in `FortuneCompatHelper`), or remove the field/GUI-free config entry and its javadoc promise. Given “no new mixins for core functionality” the cleanest is a `FortuneCompatHelper.clampFortuneLevel(int)` used by all three mixins.

**Confidence**: high for deadness, high for the consequence (the grep is exhaustive over `src/main/java`).

---

### F7 — the documented mixin capability gate does not exist (medium, confidence high)

**Files/lines / grep proof**

- `Config.java:215-220` documents `enableMixinCapabilityGates`: *“mixins are only applied when the target class bytecode shape matches expectations — preventing crashes on GTNH version mismatches”*.
- `mixin/Mixins.java:12-13` — `public enum Mixins { ; }` → **zero constants**; therefore `getLateMixins(...)` (`:27-47`) always returns an empty list, and `MixinClass.addBytecodeCondition(...)` (`:123-127`) is never invoked (grep for `addBytecodeCondition` matches only `Mixins.java:123` and two javadoc hits).
- `mixin/MixinCapabilityPlugin.java:48-49` is the only reader of `enableMixinCapabilityGates` (grep), and it is reachable only from `Mixins.addBytecodeCondition` → unreachable.
- `mixin/MixinCapabilityPlugin.java:19-24` states it is *not* an `IMixinConfigPlugin` and that “the capability gate is applied through the late-mixin loader path”; that path (`mixin/ILateMixinPlugin.java:21-23`) returns `Mixins.getLateMixins(...)` = always empty.
- Neither `src/main/resources/mixins.EZMiner.json` nor `mixins.EZMiner.late.json` contains a `plugin` key (grep for `"plugin"` finds nothing outside `gradle.properties` comments) and the late config is empty apart from boilerplate.

**Failure scenario**

On a GTNH version whose `GTOreAdapter`/`BWOreAdapter` bytecode differs from the expected shape, the gate that was supposed to skip the mixin does not run: `mixins.EZMiner.json` unconditionally lists `MixinGTOreAdapter`, `MixinBWOreAdapter`, `MixinGTPPOreAdapter`, so the injection is applied and a signature/shape change becomes a hard `MixinApplyError`/`NoSuchMethodError` at startup instead of a graceful skip. Also, `enableMixinCapabilityGates` is a server-config field whose value has no effect anywhere (a silent no-op knob).

**Minimal fix**

Either (a) register the gate where Mixin actually looks for it (implement `IMixinConfigPlugin`/`MixinConfigPlugin` and add `"plugin": "com.czqwq.EZMiner.mixin.MixinCapabilityPlugin"` to the JSON, adjusting the class to the fork's plugin interface), or (b) delete `enableMixinCapabilityGates`, `Mixins`, `MixinCapabilityPlugin`, `ILateMixinPlugin`, `TargetMod` and the empty late config so the documentation matches reality. (b) is the honest option while `Mixins` is empty.

**Confidence**: high for deadness; the crash consequence is the documented purpose of the flag.

---

### F8 — mixin registration: client-only mixin in the common list, empty `client` array, empty late config (medium, confidence medium)

**Files/lines**

- `src/main/resources/mixins.EZMiner.json`:
  ```json
  "package": "com.czqwq.EZMiner.mixin.early",
  "mixins": [ "MixinGTOreAdapter", "MixinBWOreAdapter", "MixinGTPPOreAdapter", "MixinItemInWorldManager", "MixinGuiIngameMenu" ],
  "client": [],
  "server": []
  ```
- `src/main/java/com/czqwq/EZMiner/mixin/early/MixinGuiIngameMenu.java:3-4,30-31,38` — `@Mixin(GuiIngameMenu.class)` on `net.minecraft.client.gui.GuiButton`/`GuiIngameMenu`, and the body constructs `EZMinerModOptionsScreen` (a `@SideOnly(CLIENT)` GUI, `client/gui/EZMinerModOptionsScreen.java`).
- `src/main/resources/mixins.EZMiner.late.json` — no `mixins` array at all (the file only carries `required/minVersion/package/refmap/target/compatibilityLevel`), while `mixin/ILateMixinPlugin.java:16-23` claims `"mixins.EZMiner.late.json"` as its config.

**Failure scenario**

On a dedicated server the 1.7.10 server jar has no `net.minecraft.client.gui.*` classes, so Mixin cannot resolve `MixinGuiIngameMenu`'s target while loading `mixins.EZMiner.json`. Best case (config `required:false`, Mixin 0.8.5-GTNH) the single mixin is logged as errored and skipped; the risk is that the whole config — which also carries the fast-harvest (`MixinItemInWorldManager`) and fortune mixins — is disabled for that environment. Either way, the registration is inconsistent with the config's own `client`/`server` sections: a client-only mixin belongs in `"client": []`, not in `"mixins": []`. All five mixins sit in one config, so a per-mixin failure and a per-config failure have very different blast radii.

**Minimal fix**

Move `MixinGuiIngameMenu` into `"client": ["MixinGuiIngameMenu"]`; keep the four environment-neutral mixins in `"mixins"`. Separately, either list the (currently non-existent) late mixins in `mixins.EZMiner.late.json` or delete the empty late config + `ILateMixinPlugin` (see F7).

**Confidence**: medium (the failure mode depends on the GTNH Mixin fork's per-mixin vs per-config error handling, which I cannot execute here; the registration inconsistency itself is certain).

---

### F9 — packet ids are order-dependent and the mod accepts every remote version (medium, confidence high)

**Files/lines**

- `network/NetworkMain.java:18` — `private int packetId = 0;` and `:20-113` register 26 entries with `packetId++` in source order (ids 0…25). Any insertion in the middle shifts every later id — including the OP-only pair (`PacketSaveServerConfig` = 15, `PacketReloadServerConfig` = 16) and the tool-swap pair (21/22).
- `EZMiner.java:33` — `acceptableRemoteVersions = "*"`; there is no `NetworkRegistry`/`SimpleNetworkWrapper` version check anywhere (grep: only `NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL)` at `NetworkMain.java:17`).
- `network/MainThreadEnforcer.java` / packet `fromBytes` implementations read fixed field sequences with no id echo.

**Failure scenario**

Client at EZMiner commit N+1 (a packet registered before index 6, say) joins a server still at commit N — FML permits it because of `acceptableRemoteVersions = "*"`. The client now sends id 6 for `PacketKeyState` (server expects `PacketSaveServerConfig`… or vice versa). The server decodes a 1-byte boolean payload as a 50-field config save: `readInt` on a 1-byte buffer throws `IndexOutOfBoundsException` inside the netty decoder → FML logs a decode error and drops the connection. The OP-gated config path is *not* exploitable for privilege escalation (the handler re-checks `OpPermissionChecker.isOp` at `PacketSaveServerConfig.java:287`), but the failure is a hard-to-diagnose “random disconnect after an update”.

**Minimal fix**

Keep the ids stable by never reordering the existing registrations (append only) and add an explicit protocol guard: register the channel through `NetworkRegistry.newSimpleChannel(name, version, clientAccepted, serverAccepted)` (or verify a `PROTOCOL_VERSION` field on a handshake packet) so a mismatch produces FML's version error instead of a silent id remap. At minimum, document the append-only rule next to `packetId` (the checklists in `AGENTS.md`/`CLAUDE.md` do not mention it).

**Confidence**: high for the mechanism (ids are positional and no version guard exists).

---

### F10 — player chat messages are dropped for anyone not in the overworld (medium, confidence high)

**Files/lines**

- `utils/MessageUtils.java:29-40`:
  ```java
  List<EntityPlayer> players = FMLCommonHandler.instance()
      .getMinecraftServerInstance()
      .getEntityWorld().playerEntities;      // worldServers[0] == overworld
  for (EntityPlayer player : new ArrayList<>(players)) { if (player.getUniqueID().equals(playerUUID)) { … } }
  ```
- All 13 live call sites go through this method: `chain/execution/ChainExecutionErrorReporter.java:20`, `BlockSwapModeHandler.java:44/51/68/137/141`, `ProspectModeHandler.java:97`, `PlantingModeHandler.java:216`, `LootGamesSudokuBridge.java:255`, `LootGamesMinesweeperBridge.java:188`, `core/BaseOperator.java:161/169/178/355`.

**Failure scenario**

A player chain-mines in the Nether. `BaseOperator` fires the 50-second idle countdown (`core/BaseOperator.java:158-172`), the watchdog message (`:178-180`), the chain-done summary (`:355-361`) and every block-swap error message. Each call looks up the player in the **overworld** `playerEntities` list — the player is registered in the Nether world's list, so no match, no message (`MessageUtils.java:33-39` returns without doing anything). The player gets a silently cancelled chain with no explanation; on GTNH this is exactly where long GT-vein chains pass the chunk boundary.

**Minimal fix**

Resolve the player via the server's player list instead of a world: `MinecraftServer.getServer().getConfigurationManager().func_152612_a(name)`/`getPlayerByUUID` or iterate `getConfigurationManager().playerEntityList` (all dimensions) and match the UUID. Add a null-guard for a logged-off player.

**Confidence**: high (`MinecraftServer.getEntityWorld()` returns the overworld in 1.7.10; the world-specific `playerEntities` list is the one searched).

---

### F11 — `ItemFilterExpression` semantics: `*` is not “everything”, malformed input fails open (medium, confidence high)

**Files/lines**

- `utils/ItemFilterExpression.java:191-202` — `OrePatternAtom.matches`:
  ```java
  int[] ids = OreDictionary.getOreIDs(stack);
  if (ids.length == 0) return false;      // item with no ore-dict entry never matches
  ```
- `utils/ItemFilterExpression.java:216-239` — `compileGlob("*")` → `parts.length == 2`, `startsWith == false`, `endsWith == false` → `name -> true` (matches every *ore name*).
- `utils/ItemFilterExpression.java:110-126` — malformed / trailing-garbage expressions return `NEVER_MATCHES` with only a `LOG.warn`.
- `chain/execution/ChainDropCollector.java:53-61` — the filter is a **destroy list**: matching stacks are dropped without ever entering the collector (`if (blacklist != null && blacklist.matches(drop)) continue;`).

**Failure scenarios**

- An admin writes `blacklistExpression = *` intending “destroy everything (I am testing / I want no drops)”: items that have no OreDictionary entry (most modded junk, e.g. Natura/witchery drops, GT dusts without an `ore*` entry) are **kept**, so drops still appear — a silent no-op that is invisible without reading the log.
- `blacklistExpression = 123:5:6` (typo) → malformed → `NEVER_MATCHES` → **nothing** is destroyed: the intended cobblestone filter silently stops working (fail-open). Because the expression is a destroy list, fail-open is “safe” for item loss but produces the exact lag the feature exists to prevent.
- A 4097-char expression (the GUI caps at 512 via `EZMinerConfigGui.java:1632`, the packet allows up to 32 767) is silently disabled (`:112-118`) with only a warning.

**Minimal fix**

Make `*` (and a pattern with no ore-name characters) match *any* stack: add an `AnyAtom` when the atom is exactly `*`, and convert “expression present but malformed” into a startup/config warning that is also surfaced to the OP GUI, or document the current behaviour next to the config comment (`Config.java:48-57`) explicitly (“`*` matches any stack that has an OreDictionary entry”).

**Confidence**: high (parser + matcher read line by line; the 64-level nesting cap at `:67/:318/:330` and the 4096-char cap do correctly bound recursion and length, so there is no depth bomb — see §4).

---

### F12 — `PacketCachedBlockSync` trusts the wire count (low-medium, confidence high)

**Files/lines**

- `chain/network/PacketCachedBlockSync.java:74-79`:
  ```java
  int count = buf.readInt();
  List<Vector3i> list = new ArrayList<>(Math.min(count, 4096));
  for (int i = 0; i < count; i++) { list.add(new Vector3i(buf.readInt(), buf.readInt(), buf.readInt())); }
  ```
  `Math.min` bounds only the initial capacity — the loop runs `count` times, with no comparison against `buf.readableBytes()`.

**Failure scenario**

A malformed/desynced stream (or a hostile server, or after the id drift of F9) with `count = Integer.MAX_VALUE` makes the handler allocate a `Vector3i` per iteration until `readInt()` runs off the end of the buffer → `IndexOutOfBoundsException` in the netty decoder → client disconnect with a decode error (bounded, because the first read past the end throws, so no OOM). A legitimate sender whose payload was truncated produces the same hard failure instead of a graceful “ignore stale preview”.

**Minimal fix**

`if (count < 0 || count > (buf.readableBytes() / 12)) throw new DecoderException(...)` (or clamp to a sane max such as `blockLimit`) before allocating/looping.

**Confidence**: high.

---

### F13 — S→C client handlers write client tick state from the netty thread (low-medium, confidence medium)

**Files/lines**

- `chain/network/PacketChainStateSync.java:108-121` — on the client netty thread: `proxy.minerRenderer.freeze()/unfreeze()` and `proxy.smartToolSwitchHandler.restoreAfterChainEnd()`.
- `client/SmartToolSwitchHandler.java:502-508` — `restoreSwapAndClear()` does `suitableSlots.clear(); scrollSlots.clear();` on `private final List<Integer> suitableSlots = new ArrayList<>();` (`:57-59`), while the client tick iterates/indexes the same list (`:144-207`, `:269-…`) → `ConcurrentModificationException` / `IndexOutOfBoundsException` is possible on chain end.
- `client/render/MinerRenderer.java:73` — `private BasePositionFounder founder = null;` is written by `freeze()`/`unfreeze()` (netty thread, `:101-119`) and read/written by `onRenderWorldLast` (`:140-146`, `:223`) — non-volatile, so a `freeze()` originating from the packet can be lost (or an `interrupt()`-then-reassign race leaves the search running).
- Mitigations already present (so this is not a GL crash): `foundQueue` is a `LinkedBlockingQueue` (`:74`) and the cached-preview fields are `volatile` (`client/ClientStateContainer.java:94,100`).

**Failure scenario**

Chain ends while the player is cycling tools with the scroll wheel: `PacketChainStateSync` (sent by `BaseOperator.unRegistry`, server thread) is handled on the netty thread and clears `suitableSlots` mid-iteration in `SmartToolSwitchHandler.onClientTick` → client crash report instead of a no-op.

**Minimal fix**

For every S→C handler that touches client state, schedule the body onto the client thread (`Minecraft.getMinecraft().addScheduledTask(...)` / a small client-side `ClientThreadEnforcer` mirroring `MainThreadEnforcer`) or make the two lists a copy-on-write/concurrent structure. `founder` should at least be `volatile`.

**Confidence**: medium (needs an in-game reproduction; the fact that handlers run on the netty thread and the fields are shared is certain).

---

### F14 — dead utility classes; `FileReadUtils` logs to the root logger (low, confidence high)

**Files/lines / grep proof**

- `grep` for `FileReadUtils` over `src/main/java` matches only its own file (`utils/FileReadUtils.java:12,21`) — no caller.
- `grep` for `MatrixUtils` matches only `utils/MatrixUtils.java:19` (class declaration) — no caller (a `@SideOnly(CLIENT)` 62-line render helper).
- `utils/FileReadUtils.java:14` — `public static final Logger LOG = LogManager.getLogger();` — the **root** logger (the arg-less overload), not `EZMiner`; any failure there bypasses the mod's logger (and the Hodgepodge filter installed in `EZMiner.java:77-90` which targets specific loggers).

**Failure scenario / impact**

Dead code (no user-visible effect today), but a trap: the next contributor extending `FileReadUtils` will log to the root logger, and `MatrixUtils.captureMatrices()` duplicates rendering helpers that `client/render/*` implements independently (divergence risk, e.g. a second matrix-capture implementation with different conventions).

**Minimal fix**

Delete both classes (or make `FileReadUtils.LOG` an `EZMiner` logger if it is to be kept).

**Confidence**: high (exhaustive grep).

---

### F15 — `TimeFormatUtils` locale handling is shared across client/server and degrades on dedicated servers (low, confidence medium)

**Files/lines**

- `utils/TimeFormatUtils.java:114-127`:
  ```java
  private static Boolean chineseLocale = null;
  private static boolean isChineseLocale(boolean client) {
      if (chineseLocale == null) { chineseLocale = translate("ezminer.hud.time.second", client).equals("秒"); }
      return chineseLocale;
  }
  ```
  One static decides the spacing for **both** the client HUD path (`formatElapsed` → `I18n`) and the server chat path (`formatElapsedServer` → `StatCollector`, used by `core/BaseOperator.java:359`).
- `utils/TimeFormatUtils.java:110-112` — the server branch uses `StatCollector.translateToLocal`.

**Failure scenario**

A Chinese-locale player on a dedicated server: the server-side chain-done message is built with `StatCollector`, which on 1.7.10 resolves against the server's client-less `StringTranslate` (vanilla en_US fallback) → the unit labels come back in English; whichever side calls `isChineseLocale` first also fixes the spacing for the other side for the rest of the JVM's life, so an integrated server can render `10秒 0毫秒`-style mixed spacing if the server path initialised the cache first. Purely cosmetic.

**Minimal fix**

Cache the detection per side (two statics or a keyed map) and prefer `ChatComponentTranslation` with placeholder arguments for the server path so the client localises the units itself (the `Format` needed for a duration is more work; per-side caching alone removes the cross-contamination).

**Confidence**: medium (1.7.10 `StatCollector` behaviour on a dedicated server is inferred, not executed here).

---

### F16 — `PacketSaveServerConfig` constructor/field layout hazards (low, confidence high)

**Files/lines**

- `network/PacketSaveServerConfig.java:89-96` — the current 30-param constructor; its last two parameters are adjacent booleans:
  ```java
  int blockSwapLimit, boolean enableBlockSwapMode, boolean fireBreakEvent
  ```
  (and `boolean dropImmediately, double addExhaustion, boolean dropToPlayer` earlier) — a transposition compiles silently, which is precisely the reason `CLAUDE.md` says new fields must be appended as named public fields. The only caller today is correct: `EZMinerConfigGui.java:2004-2040` passes `blockSwapLimit, enableBlockSwapMode, fireBreakEvent` in the right order (`:2038-2040`).
- `network/PacketSaveServerConfig.java:131-169` — the retained 24-param “binary compatibility” constructor delegates with **hardcoded** defaults for the six missing fields:
  ```java
  chainCooldownTicks, 1, true, 8, 1024, false, false   // cachedBreakPerTick, mergeXPOrbs, blockSwapRadius, blockSwapLimit, enableBlockSwapMode, fireBreakEvent
  ```
  A caller that used this signature would silently reset the server's cached break rate, block-swap limits and `fireBreakEvent` to those constants.
- `grep` for `new PacketSaveServerConfig(` finds only the GUI construction — the 24-param constructor has no live caller (dead but a loaded gun).

**Failure scenario**

An external/downstream caller (or a future refactor) compiled against the legacy signature jumps through the delegating constructor and writes `cachedBreakPerTick=1, mergeXPOrbs=true, blockSwapRadius=8, blockSwapLimit=1024, enableBlockSwapMode=false, fireBreakEvent=false` over the real server config.

**Minimal fix**

Delete the legacy constructor (nothing calls it — F16 grep proof), or have it delegate to the real values instead of constants, and consider making the 30-param constructor package-private with a `Builder`/named-field factory as the only public entry point.

**Confidence**: high.

---

### F17 — mode packet clamps ignore conditional mode availability (low, confidence medium)

**Files/lines**

- `chain/network/PacketChainModeSwitch.java:17-23` — `MAX_BLAST_MODE = 6`, `MAX_CHAIN_MODE = 3`, `MAX_SPECIAL_MODE = 5`, with *“keep in sync with MinerModeState…length - 1”* comments; `:40-45` clamps the incoming ints to those.
- `core/MinerModeState.java:121` — the **selectable** chain-mode count is `Config.enableCachedChain ? CHAIN_MODES.length : 2`; `:163-167` — special modes 3 (prospect) and 4 (block swap) are conditional on VP availability / `Config.enableBlockSwapMode`; `:185-187` — `currentSpecialMode()` resets an invisible index to 0.
- `core/Manager.java:536-537`, `:544-545` — the server-side *behaviour* gates (`isBlockSwapMode`, prospect) do check config/VP, so this is **not** an admin-switch bypass.
- `chain/planning/LegacyFounderPlanningFactory.java:36` — `if (modeState.chainMode == 1 || modeState.chainMode == 3)` selects the **fuzzy** founder, and this check is *not* hidden behind `isCachedChainMode()`.

**Failure scenario**

`enableCachedChain=false` on the server. A crafted `PacketChainModeSwitch(main=1, chain=3)` passes the clamp; `MinerModeState.isCachedChainMode()` (`:153-155`) correctly refuses the cached path, but `LegacyFounderPlanningFactory:36` sees `chainMode == 3` and uses the fuzzy founder — a mode the admin cannot select through the GUI (the client's cycle skips it via `getChainModeCount()`), so the server silently runs a mode combination the client's preview does not describe. No privilege gain (fuzziness/adjacency is not a capability), but it is unaudited mode drift.

**Minimal fix**

Clamp against the effective counts (`MinerModeState.getChainModeCount()` and the `isSpecialModeVisible` predicate) rather than array lengths, or reject the switch outright when the requested mode is not currently selectable.

**Confidence**: medium (the packet/clamp/MinerModeState/legacy-factory chain is verified; whether a mismatched mode is user-visible depends on the preview integration owned by t2/t3).

---

### F18 — `EZMinerAPI` does not enforce its server-thread contract; read-only queries create state (low, confidence high)

**Files/lines**

- `api/EZMinerAPI.java:24-31` — the documented contract: *“All methods in this class that mutate server state (setChainKeyHeld, setMainMode, setMode, startChain) must be called on the server's main thread. isActive and isKeyHeld are read-only and may be called from any thread.”*
- `api/EZMinerAPI.java:48-56` — `stateOf(UUID)` = `EZMiner.chainStateService.getOrCreate(player)`; it is called by `setChainKeyHeld` (`:71`), `setMode` (`:132`), **and** by the “read-only” `isKeyHeld`/`isOperateActive`/`isActive` (`:147/153/159`).
- `chain/state/ChainStateService.java:16-19` — `ConcurrentHashMap` + `computeIfAbsent` (so no map corruption) but the entry is **created** on read and only removed in `onPlayerLogout` (`:28-31`).
- `api/EZMinerAPI.java:68-86` — no thread assertion/defers; `EZMiner.network.network.sendTo(...)` is executed on the caller's thread (`:76`, `:80-83`).
- `chain/state/ChainPlayerState.java:17-18` — `keyPressed` and `session` are `volatile`, so the cross-thread *visibility* of the read-only queries is fine; what is not fine is that a query **inserts** state (`stateMap.computeIfAbsent`) and that the mutators perform `sendTo` on the caller's thread (the packet path serialises the same writes, proving the intended writer is the server thread).

**Failure scenarios**

- A foreign mod polls `EZMinerAPI.isActive(uuid)` (explicitly documented as thread-safe) for a UUID that never logged in, or after the player logged out: `getOrCreate` inserts a permanent `ChainPlayerState` into `stateMap` → unbounded growth (and `isActive` can then report true from the stale `PacketKeyState` deferral of F4). The fields themselves are `volatile` (`chain/state/ChainPlayerState.java:17-18`), so this is a lifecycle/leak issue, not a visibility issue.
- A foreign mod calls `setChainKeyHeld(uuid, true)` from its own tick/thread: it mutates state and performs `sendTo` off-thread, and can start a chain through the next `BreakEvent` with no serialisation against the server tick.

**Minimal fix**

Make the read-only queries non-mutating (`stateMap.get(...)` without `computeIfAbsent`), and either assert `FMLCommonHandler.instance().getEffectiveSide()`/server-thread and log a warning, or route server-side mutators through the same deferral primitive as the packets.

**Confidence**: high for the code paths; the growth impact requires a caller that passes unknown UUIDs (documented as a supported read pattern).

---

### F19 — permission-policy inconsistencies, incl. ServerUtilities interaction (low, confidence medium)

**Files/lines**

- `permission/OpPermissionChecker.java:32-63` — policy: vanilla ops list level ≥2 → integrated-server owner with cheats → `ServerOwnerWhitelist` name bypass; javadoc (`:10-11`) says it *“correctly excludes LAN guests”* relative to `canCommandSenderUseCommand(2, "EZMiner")`.
- `network/PacketReloadServerConfig.java:37-38` — *“if (!player.canCommandSenderUseCommand(2, "EZMiner")) return null;”* — i.e. the packet path uses exactly the API that `OpPermissionChecker` was written to replace, so a LAN guest with `commandsAllowedForAll` can force a full config reload + broadcast (F3 makes that an off-thread, blocking, all-player operation). `PacketSaveServerConfig.java:287` and `PacketOpStatusRequest.java:41` use `OpPermissionChecker`; `command/ReloadConfigCommand.java:38/47-59` uses `getRequiredPermissionLevel()==0` + the internal `OpPermissionChecker` re-check (`:96-100`).
- `permission/ServerOwnerWhitelist.java:48-49/74-76/86-93` — `toLowerCase()` without `Locale.ROOT` (default-locale Turkish-`I` hazard); `:249` in the command prints the stored name via `ChatComponentText("  §e" + name)` (values come from the file/console, so this is a formatting curiosity, not injection).
- Cross-mod — `tmp/ServerUtilities-master/src/mixins/java/serverutils/mixins/early/minecraft/MixinCommandHandler.java:60-69` registers a permission node per command: `node = (container == null ? "command" : "command." + modid) + "." + commandName`, lowercased; `:43-58` **replaces** the `canCommandSenderUseCommand(sender)` result with `((ICommandWithPermission) command).serverutilities$hasPermission(player)`; `tmp/ServerUtilities-master/src/mixins/java/serverutils/mixins/early/minecraft/MixinICommand.java:45-51`:
  ```java
  Event.Result result = Ranks.INSTANCE.getPermissionResult(player, serverutilities$getPermissionNode(), true);
  if (result == Event.Result.DEFAULT) return canCommandSenderUseCommand(player);
  return result == Event.Result.ALLOW;
  ```
  `tmp/ServerUtilities-master/src/main/java/serverutils/ranks/ServerUtilitiesPermissionHandler.java:39-59` confirms ranks override, with `DefaultPermissionHandler` (vanilla op) as fallback.

**Consequences**

- `/EZMiner reloadConfig` on an SU server: the node is `command.ezminer.ezminer`. With no explicit rank entry the result is `DEFAULT` → falls back to EZMiner's own override, which returns **true for every player** (required level 0) — harmless only because `ReloadConfigCommand.java:96-100` re-checks `OpPermissionChecker` internally. Any *future* OP-gated sub-command that forgets the internal re-check would be open to everyone on an SU server. Conversely, an SU rank granted `ALLOW` on that node bypasses EZMiner's vanilla-op requirement at the command layer but still fails the internal `OpPermissionChecker.isOp` check (`permission/OpPermissionChecker.java:32-63` never consults `PermissionAPI`) → rank-based admins cannot use EZMiner's OP features at all, while `PacketSaveServerConfig`/`PacketReloadServerConfig` (the GUI paths) disagree with `PacketReloadServerConfig`'s own check.
- A LAN guest on an integrated server can trigger the reload packet (policy gap).

**Minimal fix**

Use `OpPermissionChecker.isOp` in `PacketReloadServerConfig.Handler` (one-line change, consistent with the save packet); document (or optionally consult) ServerUtilities' `PermissionAPI` so a rank can be granted EZMiner settings access; use `toLowerCase(Locale.ROOT)` in the whitelist.

**Confidence**: medium (the SU mixins are read, but their end-to-end effect could not be executed; the source-level claims are direct quotes).

---

## 2. Coverage matrices

### 2.1 Server config field × sync-step matrix

Legend — **D**eclared (`Config.java` field), **L**oaded (`loadServerOnlyInternal`), **S**aved (`saveServerConfig`), **Y** Synced (`PacketServerConfig` field + `toBytes`/`fromBytes` + handler apply), **P** Persisted by `PacketSaveServerConfig` (+ handler assignment), **G** GUI row + lang key, **C**onsumer exists. `—` = absent. “via” names the apply function for the synced group: `L` = `applyServerRuntimeLimits` (941-964), `F` = `applyServerRuntimePerformance` (972-977), `G` = `applyServerRuntimeConfig` (985-1019), `D` = direct assignment in `PacketServerConfig.Handler` (286-288).

| # | Field | D | L | S | Y | P | G (row) | Note |
|---|---|---|---|---|---|---|---|---|
| 1 | `bigRadius` | 21 | 513 | 1272 | ✓ L | ✓ 290 | 0 | |
| 2 | `blockLimit` | 22 | 520 | 1281 | ✓ L | ✓ 291 | 1 | |
| 3 | `smallRadius` | 23 | 527 | 1290 | ✓ L | ✓ 292 | 2 | |
| 4 | `tunnelWidth` | 24 | 535 | 1299 | ✓ L | ✓ 293 | 3 | |
| 5 | `addExhaustion` | 25 | 761 | 1578 | ✓ G | ✓ 297 | 10 | **no clamp in P (F5)** |
| 6 | `dropToPlayer` | 26 | 771 | 1587 | ✓ G | ✓ 298 | 22 | |
| 7 | `serverUsePreview` | 27 | 778 | 1594 | ✓ L (allowPreview) | ✓ 299 | 13 | |
| 8 | `serverMaxPreviewBigRadius` | 28 | 783 | 1601 | ✓ L | ✓ 300 | 11 | |
| 9 | `serverMaxPreviewBlockLimit` | 29 | 790 | 1610 | ✓ L | ✓ 301 | 12 | |
| 10 | `breakPerTick` | 34 | 542 | 1308 | ✓ L | ✓ 294 | 7 | |
| 11 | `cachedBreakPerTick` | 39 | 550 | 1317 | ✓ F+G | ✓ 295 | 8 | |
| 12 | `crazyMode` | 45 | 558 | 1326 | ✓ G | ✓ 310 | 17 | |
| 13 | `dropImmediately` | 47 | 565 | 1333 | ✓ G | ✓ 296 | 23 | |
| 14 | `blacklistExpression` | 57 | 572 | 1340 | ✓ D | ✓ 342 | 24 | GUI caps 512 chars (`:1632`) |
| 15 | `xpDropMode` | 63 | 584 | 1352 | ✓ G | ✓ 317 | 25 | |
| 16 | `mergeXPOrbs` | 70 | 592 | 1361 | ✓ G | ✓ 318 | 26 | |
| 17 | `enableBlockSwapMode` | 73 | 600 | 1375 | ✓ L | ✓ 321 | 30 | |
| 18 | `blockSwapRadius` | 75 | 606 | 1382 | ✓ L | ✓ 319 | 28 | |
| 19 | `blockSwapLimit` | 77 | 613 | 1391 | ✓ L | ✓ 320 | 29 | |
| 20 | `logBigRadius` | 80 | 620 | 1400 | ✓ L | ✓ 335 | 4 | |
| 21 | `logBlockLimit` | 82 | 627 | 1409 | ✓ L | ✓ 336 | 5 | |
| 22 | `logFuzzyEnabled` | 87 | 634 | 1418 | ✓ D 286 | ✓ 337 | 6 | |
| 23 | `enableCachedChain` | 89 | 640 | 1368 | ✓ G | ✓ 304 | 14 | |
| 24 | `enableChainChunkLoading` | 97 | 648 | 1553 | ✓ G | ✓ 307 | 15 | |
| 25 | `chainIdleTimeoutSeconds` | 104 | 656 | 1560 | ✓ G | ✓ 311 | 19 | |
| 26 | `chainIdleCountdownSeconds` | 110 | 664 | 1569 | ✓ G | ✓ 313 | 20 | |
| 27 | `minesweeperProbeCooldownSeconds` | 115 | 797 | 1619 | ✓ G | ✓ 302 | 35 | |
| 28 | `sudokuProbeCooldownSeconds` | 120 | 807 | 1628 | ✓ G | ✓ 303 | 36 | |
| 29 | `prospectProbeIntervalSeconds` | 126 | 817 | 1637 | ✓ G | ✓ 338 | 31 | |
| 30 | `prospectMaxScanRadiusChunks` | 134 | 827 | 1646 | ✓ G | ✓ 339 | 32 | |
| 31 | `plantRadius` | 139 | 840 | 1656 | ✓ G | ✓ 340 | 33 | **clamp mismatch 12 vs 64 (F5)** |
| 32 | `plantMaxCount` | 144 | 852 | 1665 | ✓ G | ✓ 341 | 34 | **clamp mismatch 256 vs 1024 (F5)** |
| 33 | `stopOnUnbreakable` | 151 | 864 | 1674 | ✓ G | ✓ 315 | 18 | |
| 34 | `fireBreakEvent` | 159 | 871 | 1681 | ✓ G | ✓ 322 | 49 | |
| 35 | `chainCooldownTicks` | 166 | 879 | 1688 | ✓ G | ✓ 316 | 21 | |
| 36 | `searchWorkerThreads` | 174 | 671 | 1425 | ✓ G | ✓ 305 | 9 | |
| 37 | `searchBudgetPerYield` | 185 | 680 | 1434 | ✓ F | ✓ 323 | 37 | |
| 38 | `useDualFrontierBfs` | 193 | 690 | 1443 | ✓ F | ✓ 324 | 38 | |
| 39 | `usePrimitiveVisitedSet` | 201 | 696 | 1450 | ✓ F | ✓ 325 | 39 | |
| 40 | `enableConfigValidation` | 206 | 702 | 1457 | **—** | ✓ 330 | 43 | **F1** |
| 41 | `enableSafeReflection` | 213 | 707 | 1464 | **—** | ✓ 331 | 44 | **F1** |
| 42 | `enableMixinCapabilityGates` | 220 | 712 | 1471 | **—** | **—** | **no row** | **F7** (also no live consumer) |
| 43 | `enableMainThreadGuard` | 226 | 718 | 1478 | **—** | ✓ 328 | 41 | **F1** |
| 44 | `enableChainWatchdog` | 233 | 723 | 1485 | **—** | ✓ 326 | 40 | **F1** |
| 45 | `chainWatchdogTimeoutTicks` | 240 | 728 | 1492 **& 1697 (dup)** | **—** | ✓ 332 | 45 | **F1** |
| 46 | `enableDropFallbackChain` | 247 | 736 | 1501 | **—** | ✓ 327 | 27 | **F1** |
| 47 | `enableBudgetDeadline` | 255 | 742 | 1508 | **—** | ✓ 329 | 42 | **F1** |
| 48 | `enableToolBreakHandoff` | 261 | 748 | 1515 **& 1706 (dup)** | **—** | ✓ 333 | 46 | **F1** (+client-side gate split) |
| 49 | `toolBreakHandoffTimeoutTicks` | 264 | 754 | 1522 **& 1713 (dup)** | **—** | ✓ 334 | 47 | **F1** |
| 50 | `suppressHodgepodgeWarnings` | 272 | 912 | 1531 | ✓ G | ✓ 306 | 48 | live-read by the log filter |
| 51 | `useChunkCachedHarvest` | 291 | 898 | 1538 | ✓ G | ✓ 308 | 16 | |
| 52 | `notifyNeighborsOnChainBreak` | 307 | 905 | 1545 | ✓ G | ✓ 309 | 50 | |
| 53 | `enableUnlimitedOreFortune` | 310 | 889 | **—** | **—** | **—** | **no row** | load-only; live consumer (`FortuneCompatHelper`) |
| 54 | `maxFortuneLevel` | 313 | 919 | **—** | **—** | **—** | **no row** | **F6** dead |
| 55 | `enableFortuneForPlacedOre` | 327 | 930 | **—** | **—** | **—** | **no row** | load-only; live consumer (`FortuneCompatHelper`) |

Totals: declared 55/55; loaded 55/55; saved 52/55; synced 46/55; save-packet 51/55; GUI rows 51/55 (rows 0-50, `SERVER_CONTENT_ROWS = 51`, `EZMinerConfigGui.java:106`).

Notes verified while building the matrix:

- `saveServerConfig()` writes three properties **twice** (`chainWatchdogTimeoutTicks` 1492 & 1697, `enableToolBreakHandoff` 1515 & 1706, `toolBreakHandoffTimeoutTicks` 1522 & 1713). Harmless (same value, same property object) but it is dead code in the writer and a sign the writer is maintained by copy-paste.
- Row↔field mapping is consistent across `initServerFields` (1590-1653), `getRowLabelKey` (1250-1360), `drawServerTab` (1737-1808), `updateScrolledPositions` (1499-1555), `isSectionBreak`, `actionPerformed` (860-995), `mouseClicked` (1080-1108), `keyTyped` (1129-1157) and `updateTabVisibility` (1878-1900): all 27 server text fields and all 22 server toggles are present in the field-related lists. The 4 section-header lang keys `ezminer.gui.section.{performance,stability,prospecting,planting}` exist in both lang files but are no longer referenced by `drawServerTab` (dead lang entries).
- Lang coverage: a repo-wide diff of the 189 distinct `ezminer.*` keys used in `src/main/java` against `en_US.lang`/`zh_CN.lang` (197 keys each) shows **no missing key** (the only apparent gap is the concatenation `"ezminer.command.active_mode.desc." + mode`, which resolves to `.0`/`.1`).

### 2.2 Registered packet matrix (id, direction, handler thread/guard, validation)

| id | class | dir | handler side guard | thread guard | input validation |
|---|---|---|---|---|---|
| 0 | `PacketMinerConfig` | C→S | `ctx.side.isServer()` (54) | **none** | per-field caps in `Manager.receiveClientConfig:484-492` ✓ |
| 1 | `PacketMinerConfig` | S→C | `ctx.side.isClient()` | n/a | — |
| 2 | `PacketHudPos` | S→C | `isClient()` (38) | n/a | unbounded x/y → written to the client config file (`Config.saveHudPos`) |
| 3 | `PacketOpenHudConfig` | S→C | `@SideOnly(CLIENT)` on the method (37-39) | n/a | empty payload |
| 4 | `PacketServerConfig` | S→C | `isClient()` (242) | n/a | full 51-field decode; `isOp` bit trusted client-side for GUI visibility |
| 5 | `PacketReloadClientConfig` | S→C | `isClient()` (23) | n/a | empty |
| 6 | `PacketKeyState` | C→S | `isServer()` (38) | **`guardedNull`** (39) | boolean only |
| 7 | `PacketChainModeSwitch` | C→S | `isServer()` (59) | **`guardedNull`** (60) | clamped to array bounds (F17) |
| 8 | `PacketChainStateSync` | S→C | `proxy instanceof ClientProxy` (79) | none (F13) | session sanity checks (83-90) ✓ |
| 9 | `PacketCachedBlockSync` | S→C | `instanceof ClientProxy` (107) | none (F13) | **count unvalidated (F12)** |
| 10/11 | `PacketMinesweeperMark/Clear` | S→C | client-only usage | none | coords |
| 12/13 | `PacketSudokuFill/Clear` | S→C | client-only usage | none | coords |
| 14 | `PacketProspectState` | S→C | `instanceof ClientProxy` (41) | none | cooldown long |
| 15 | `PacketSaveServerConfig` | C→S | `isServer()` (285) | **none (F3)** | OP check ✓; 49/50 values clamped, `addExhaustion` not (**F5**) |
| 16 | `PacketReloadServerConfig` | C→S | `isServer()` (36) | **none (F3)** | `canCommandSenderUseCommand(2,…)` — **policy mismatch (F19)** |
| 17 | `PacketRequestClientReload` | C→S | `isServer()` (32) | **none (F3/F4)** | **no permission check at all** (by design? it only echoes limits, but it does so for any player, any rate) |
| 18 | `PacketBlockSwapResult` | S→C | client-only usage | none | — |
| 19 | `PacketBlockSwapClear` | S→C | client-only usage | none | — |
| 20 | `PacketToolBreakHandoff` | S→C | `@SideOnly(CLIENT)` method (57) | none | client applies it only if its **local** `enableToolBreakHandoff` (59) |
| 21 | `PacketToolSwapRequest` | C→S | `isServer()` (65) | **none (F2)** | slot/eligibility checked (71-95) ✓ |
| 22 | `PacketToolSwapFinalize` | C→S | `isServer()` (31) | **none (F2)** | no-op payload |
| 23 | `PacketToolSwapResult` | S→C | `@SideOnly(CLIENT)` (46) | none | slot bounds checked (51-52) ✓ |
| 24 | `PacketOpStatusRequest` | C→S | `isServer()` (39) | none needed | returns the requester's own OP bit |
| 25 | `PacketOpStatusResponse` | S→C | `isClient()` (35) | none | writes `EZMiner.clientIsOp` (**volatile** ✓ `EZMiner.java:55`) |

Registration ids are unique and contiguous (0-25, 26 registrations, `packetId` unresettable, `NetworkMain.java:18`) — **F9** is about reordering/version drift, not about current duplicates. Server→client handlers do not need `MainThreadEnforcer` (guarded on `side.isServer()`, `MainThreadEnforcer.java:53`), but on the client they still run on the netty thread (**F13**).

---

## 3. Re-verified previously reported items

| Previously reported | Current status |
|---|---|
| `docs/review-summary.md:21` (#4) — 3 new performance fields not synced → OP save resets them | **Fixed** for `searchBudgetPerYield`/`useDualFrontierBfs`/`usePrimitiveVisitedSet`: fields `PacketServerConfig.java:35-37`, `buildForPlayer:204-206`, encode/decode `:106-108`/`:153-155`, apply `Config.applyServerRuntimePerformance:972-977` via `PacketServerConfig.Handler:257-260`. Still open for the 9 fields of **F1** (the summary's own §6 leftover). |
| `docs/review-summary.md:30` (#13) — `PacketSaveServerConfig` had 34 positional params with 3 adjacent trailing booleans | **Partially fixed**: the constructor is back to 30 params (`PacketSaveServerConfig.java:89-96`) with new fields as named public fields (58-85) — matches the `CLAUDE.md` pattern. Residual hazard: adjacent booleans remain in the tail, and the retained 24-param overload injects hardcoded defaults (**F16**). |
| `docs/review-summary.md:69` §6 — 19 legacy server fields still have the GUI-reset problem | **Substantially fixed** — 46 of 55 fields are now synced; the 9 remaining are **F1**. |
| `docs/review/decouple-api-review.md:121` — the 5 stability flags “all have real consumers” | **Wrong for one**: `enableMixinCapabilityGates`'s only nominal consumer (`MixinCapabilityPlugin:49`) is unreachable because `Mixins` is empty (**F7**). The other four flags are live (`enableBudgetDeadline`/`enableMainThreadGuard` → `MainThreadEnforcer:53`, `enableSafeReflection` → `SafeReflection:43`, `suppressHodgepodgeWarnings` → `EZMiner:83`, `enableConfigValidation` → `ConfigValidator:29`). |
| `docs/review/perf-mixin-review.md:161` — “the existing capability gating via `Mixins.java` + `MixinCapabilityPlugin.targetHasMethod`” | **Not present in the current source**: `Mixins.java:12-13` is an empty enum (**F7**). |
| `AGENTS.md:24` — mixin list including `mixin/ILateMixinPlugin` | `ILateMixinPlugin` exists (`mixin/ILateMixinPlugin.java:13`) but yields no mixins (empty `Mixins`); the late JSON lists none (**F8**). |
| `CLAUDE.md` “Network Packet Registration” — “All packets registered in `NetworkMain.registry()` with auto-incrementing IDs” | Confirmed (`NetworkMain.java:18-113`), including the added tool-swap/OP/vanish-era packets not listed in the doc’s enumeration (21-25). |
| `AGENTS.md` “Public API for other mods” — server methods “must run on server thread”, queries callable from any thread | Doc-only; not enforced (**F18**). |
| `AGENTS.md` “Network threading” — `MainThreadEnforcer.guardedNull` “now actually defers server-mutating handlers”; `PlayerManager.managers` is a `ConcurrentHashMap` | Deferral is real (`MainThreadEnforcer.java:52-65`, `PlayerManager.java:95`), but it is wired to only 2 of the 8 mutating C→S handlers (**F2/F3/F4**). |

---

## 4. Verified-OK (checked, no finding)

1. **`PacketSaveServerConfig` wire format** — `toBytes` (`:227-279`) and `fromBytes` (`:172-224`) are field-for-field identical in order and type (51 values, `blacklistExpression` last with the null-guarded UTF8 write at `:278`); the 30-param constructor keeps `blacklistExpression` as a named field, so no field is dropped or mis-ordered today (**F16** is about the latent hazard only).
2. **`PacketServerConfig` wire format / handler argument order** — `toBytes` (`:140-184`) vs `fromBytes` (`:92-137`) match; `buildForPlayer` (`:190-236`) fills every field; `Handler` (`:243-285`) passes `applyServerRuntimeConfig`'s 24 arguments in exactly the declared order (verified pairwise against `Config.java:985-993`).
3. **`PacketChainStateSync` wire format** — the optional session block is written and read under the same `hasSession` guard (`:63-73` vs `:44-59`), and the client rejects out-of-order/older sessions (`:83-90`).
4. **`ItemFilterExpression` robustness** — no depth bomb: every recursive descent passes through the `!` (`:317-321`) or `(` (`:329-337`) branch, both gated by `MAX_NESTING = 64`; input length is capped at 4096 (`:112-118`); the parser is a single pass with no regex, and out-of-range atoms compile to `NEVER_NODE` (`:375/381`); numeric parsing rejects non-ASCII digits (`:388-396`); the compile cache is an identity-keyed single volatile entry (`:91-101`) and is safe for concurrent use. The 64-level cap plus 4 stack frames per level is far below a `StackOverflowError`.
5. **Glob matching correctness (single/double wildcard)** — `*a*`/`a*`/`*a` map to `contains/startsWith/endsWith` (`:226-239`), and the multi-wildcard infix scan refuses overlaps with the trailing anchor (`:248-262`): `*ab*b` does not match `ab` (verified by hand-tracing `idx`/`limit`).
6. **`ToolHarvestEligibility`** — GT tools are routed to `GT5ToolCompat.canGTToolMineBlock` before `ForgeHooks` (`:61-63`, guarding the documented “GT reports a class-agnostic harvest level” problem), TiC tools fall back to `getDigSpeed > 1.0F` (`:76-79`), and every failure path is fail-closed (`:56`, `:82-84`). Cross-checked against `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/HarvestTool.java:39-52` (harvest level read from the `InfiTool.HarvestLevel` NBT) and `:69-70` (`block.getHarvestLevel(meta) > tags.getInteger("HarvestLevel")` → 0.1× speed) — so the `getDigSpeed > 1.0F` signal is exactly TiC's own “can mine” signal, including with IguanaTweaks' rewritten level scale (`tmp/IguanaTweaksTConstruct-master/src/main/java/iguanaman/iguanatweakstconstruct/harvestlevels/HarvestLevelTweaks.java:151-169`, `leveling/LevelingLogic.java:113-121`).
7. **TiC NBT contract** — `TinkersConstructCompat` uses `InfiTool`/`Damage`/`TotalDurability`/`Broken`/`Unbreaking`, all confirmed in `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/crafting/ToolBuilder.java:222-264` (creation) and `tconstruct/library/tools/AbilityHelper.java:378-383` (damage/breakage), and the `Unbreaking >= 10` “unbreakable” threshold matches `tconstruct/library/tools/ToolCore.java:400` (`reinforced > 9` → “tool.unbreakable”).
8. **`Config.saveServerConfig`/`saveClientConfig` coverage of the fields the OP packets can set** — every field written by `PacketSaveServerConfig.Handler` (290-342) has a matching writer in `saveServerConfig` (verified field by field; the only unpersisted server fields are the three fortune ones of **F6**/matrix rows 53-55, which no packet can set).
9. **Server-authority clamping of client-sent mining limits** — `Manager.receiveClientConfig:484-492` caps radius/limit against the server values and forcibly rewrites `logFuzzyEnabled`/`addExhaustion` from server config, so `PacketMinerConfig` cannot widen the search. `OpPermissionChecker` is re-checked *inside* `PacketSaveServerConfig.Handler:287` (not just in the GUI) and the client cannot set `isOp` (the field is written by `PacketServerConfig.buildForPlayer` only).
10. **No server-side info leak in the OP packets** — `PacketSaveServerConfig`/`PacketReloadServerConfig` return nothing to a non-OP (`:287` / `:38` before any work); `PacketOpStatusResponse` only carries the requester's own OP bit; the server-config packet is sent by the server on login/request, never on a client's behalf for another player.
11. **`Config.applyServerRuntimeLimits`/`applyServerRuntimePerformance`/`applyServerRuntimeConfig`** all clamp on the client side before storing (`:945-963`, `:974-976`, `:994-1018`) — a hostile *server* cannot inject an absurd radius into the client's config-derived caps.
12. **Log4j Hodgepodge filter** — installed unconditionally at `postInit` (`EZMiner.java:75-95`) and reads `Config.suppressHodgepodgeWarnings` per event (`:83`), so the documented “no restart required” behaviour holds, including after a synced value change (matrix row 50).
13. **Lang/i18n coverage** — 189 referenced keys vs 197 defined in `en_US.lang` and `zh_CN.lang`: no missing key, and all player-facing chat lines use `ChatComponentTranslation` with `MessageUtils` carrying the component (not a raw string), so no untranslated key can reach a player (`utils/MessageUtils.java:29-40`).
14. **Command parsing/injection** — `/EZMiner` sub-command dispatch is a chain of `equalsIgnoreCase` comparisons (`command/ReloadConfigCommand.java:90-256`), `active_mode`/`hud pos` parse ints in `try/catch` with a usage fallback (`:139-149`, `:180-187`), unknown sub-commands fall through to `sendUsage` (`:256`), and the whitelist commands only accept a player **name** that is stored verbatim and used in log lines/`ChatComponentText` — no command/format-string injection and no path component.
15. **`Config.load()` ordering** — server load → runtime cap mirror (server side only, `:479-493`) → client load → clamps → validation; `runtimeServerMax*` are only mirrored on the server side, so a dedicated client starts from `Integer.MAX_VALUE` sentinels and the GUI's `serverValueForDisplay` (`EZMinerConfigGui.java:2128-2130`) correctly falls back to the local file until the first `PacketServerConfig` arrives.

---

## 5. Inconclusive / could not verify in this sandbox

1. **Is `/EZMiner` case-sensitive?** `ReloadConfigCommand.getCommandName()` returns `"EZMiner"` (`command/ReloadConfigCommand.java:27`) and every usage string plus `permission/ServerOwnerWhitelist.java:19` alternates between `/EZMiner` and `/ezminer`. Whether vanilla 1.7.10's `CommandHandler` lookup map is case-insensitive could not be established from the sources available here (no decompiled `CommandHandler` in the workspace, and no `tmp/` copy of vanilla). If it is case-sensitive, `/ezminer …` fails and only `/EZMiner …` works (and ServerUtilities' derived node `command.ezminer.ezminer` is lowercased regardless — `tmp/ServerUtilities-master/src/mixins/.../MixinCommandHandler.java:66`). **Needs one in-game check.**
2. **Exact Mixin failure mode for the client-only mixin on a dedicated server** (F8): whether the GTNH fork drops only `MixinGuiIngameMenu` or marks the whole `mixins.EZMiner.json` config as errored (which would also disable the fast-harvest and fortune mixins) depends on the fork's error handling and could not be executed. The registration inconsistency itself is certain.
3. **Disconnect-vs-drain ordering for `MainThreadEnforcer.DEFERRED`** (F4): the re-created-state leak requires the deferred body to run after `ChainLifecycleService.onPlayerLogout`; the FML event order for logout vs the next server tick was not executed.
4. **Client-side CME reproduction** (F13) and the **item-duplication reproduction** (F2) need a running client/server; both are argued from thread ownership, not from an observed crash.
5. **ServerUtilities end-to-end effect** (F19): the mixins and the rank handler were read (`MixinCommandHandler`, `MixinICommand`, `ServerUtilitiesPermissionHandler`), but whether a GTNH pack actually grants/denies `command.ezminer.ezminer` by default could not be run.
6. **FTB-Ultimine cross-check**: `tmp/FTB-Ultimine-main` is a **multi-loader modern** project (`common/`, `fabric/`, `neoforge/` — see `tmp/FTB-Ultimine-main/common/src/main/java/dev/ftb/mods/ftbultimine/`), not a 1.7.10 source, so its tool-selection/durability code cannot be compared 1:1 with EZMiner's `ToolHarvestEligibility`/`SmartToolSwitchHandler` (the in-source reference at `Config.java:452` is conceptual). Not audited beyond that.
7. **`TimeFormatUtils` on a dedicated server** (F15): the 1.7.10 `StatCollector`/`StringTranslate` server-side resolution is inferred, not executed.
8. **Mixin/tool compat bridges** (`GT5ToolCompat`, `GT5ToolDurabilityBridge`, server-side `MinecraftToolSwapInventoryPort` behaviour with GT Toolboxes) belong to t5/t7; only their *contract* usage from the in-scope packets/utils was checked here.

---

## 6. Cross-mod citations used

| Claim | Citation |
|---|---|
| ServerUtilities replaces each command's permission check with a rank-based node derived from the mod id + command name | `tmp/ServerUtilities-master/src/mixins/java/serverutils/mixins/early/minecraft/MixinCommandHandler.java:32-69` |
| The replacement falls back to `canCommandSenderUseCommand(player)` when the rank result is `DEFAULT` | `tmp/ServerUtilities-master/src/mixins/java/serverutils/mixins/early/minecraft/MixinICommand.java:44-52` |
| Rank handler overrides vanilla with `DefaultPermissionHandler` as fallback | `tmp/ServerUtilities-master/src/main/java/serverutils/ranks/ServerUtilitiesPermissionHandler.java:39-59` |
| Permission API entry point | `tmp/ServerUtilities-master/src/main/java/serverutils/lib/util/permission/PermissionAPI.java:63-77` |
| TiC harvest level and dig speed semantics (NBT `HarvestLevel`, 0.1× when insufficient) | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/HarvestTool.java:39-52,69-70` |
| TiC tool NBT creation (keys used by `TinkersConstructCompat`) | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/crafting/ToolBuilder.java:222-264` |
| TiC damage/breakage/repair keys | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/AbilityHelper.java:378-383,490-497` |
| TiC “unbreakable” = reinforced > 9 | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/ToolCore.java:400` |
| IguanaTweaks rewrites the harvest-level scale of blocks, materials and tools (so a hardcoded numeric level would be wrong) | `tmp/IguanaTweaksTConstruct-master/src/main/java/iguanaman/iguanatweakstconstruct/harvestlevels/HarvestLevelTweaks.java:28-36,151-169` |
| IguanaTweaks mutates `InfiTool.HarvestLevel` on the tool itself (TiC reads the same tag → EZMiner's `getDigSpeed` signal stays valid) | `tmp/IguanaTweaksTConstruct-master/src/main/java/iguanaman/iguanatweakstconstruct/leveling/LevelingLogic.java:113-121`, `leveling/modifiers/ModMiningLevelBoost.java:33,45,90` |
| FTB-Ultimine present but modern-only (`common`/`fabric`/`neoforge`) — not comparable | `tmp/FTB-Ultimine-main/{common,fabric,neoforge}/`, `tmp/FTB-Ultimine-main/common/src/main/java/dev/ftb/mods/ftbultimine/FTBUltimine.java` (path listing) |

---

## 7. Minimal fix list for the fix pass (priority order)

1. **F1** — add the 9 stability/tool-handoff fields to `PacketServerConfig` + `Config.applyServerRuntimeConfig` (or a new grouped apply). Highest user-visible payoff: stops OP saves from destroying server settings and fixes the tool-handoff client/server split-brain.
2. **F2** — wrap `PacketToolSwapRequest`/`PacketToolSwapFinalize` handlers in `MainThreadEnforcer.guardedNull` and switch `ToolSwapServerService.LEDGERS` to a concurrent map.
3. **F3** — wrap `PacketSaveServerConfig`/`PacketReloadServerConfig` (re-check OP inside the deferred body).
4. **F4** — add a liveness check to `guardedNull` and guard the remaining mutating C→S handlers.
5. **F5** — clamp/validate `addExhaustion`; align the plant clamps with the load path.
6. **F10** — resolve the player through the server player list in `MessageUtils`.
7. **F6/F7/F14** — either implement or delete the dead knobs/code (`maxFortuneLevel`, `enableMixinCapabilityGates` + `Mixins`/`MixinCapabilityPlugin`/late JSON, `FileReadUtils`, `MatrixUtils`).
8. **F8** — move `MixinGuiIngameMenu` into the JSON `client` array.
9. **F9** — version guard for the channel, and document append-only ids.
10. **F11/F12/F13/F16/F17/F18/F19** — the lower-severity fixes listed in their sections.
