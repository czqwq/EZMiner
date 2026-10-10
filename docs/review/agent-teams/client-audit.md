# Client-Layer Audit — `audit-client` (task t3, attempt 1)

**Scope**: `src/main/java/com/czqwq/EZMiner/client/**` (ClientProxy, HudRenderer, KeyListener,
SmartToolSwitchHandler, InventoryButtonOverlay, `gui/**` incl. `EZMinerConfigGui` + `gui/sync/`,
`render/**`, ToolEligibility, IngameInfoBridge), `chain/client/**`, `toolswap` **protocol**
(client↔server packets), `network` **client-side** handlers, and
`src/main/resources/assets/ezminer/**` (lang, shader, textures).

**Method**: read-only `read`/`grep`/`glob` over the current tree; no `src/` or `tmp/` file was
modified. The GUI row model was re-derived mechanically from `en_US.lang`/`zh_CN.lang` and
compared against the actual row indices in both tabs. Vanilla 1.7.10 semantics were verified
against the decompiled sources shipped with this checkout
(`build/rfg/mcp_patched_minecraft-sources.jar`) — `net/minecraft/client/settings/KeyBinding.java`,
`resources/I18n.java`, `gui/FontRenderer.java` (temporary extraction dir removed afterwards;
`git status` shows no change under `src/` or `tmp/`).

Severity convention (matches `docs/review/full-bug-scan.md`): **P0** crash/corruption, **P1**
data loss or clearly wrong behaviour, **P2** edge case / cosmetic / maintainability.

---

## 1. Findings table

| id | sev | file:line | one-line problem |
|----|-----|-----------|------------------|
| [C1](#c1) | P1 | `client/KeyListener.java:62-65` | HUD-config key early-returns before the chain-key tracker → chain keeps running + chain key stuck while a GUI is open |
| [C2](#c2) | P1 | `client/KeyListener.java:200-223` | `MouseEvent` cancel is global (not GUI-gated) → scroll wheel switched to EZMiner sub-modes inside inventory/chat/container GUIs |
| [C3](#c3) | P1 | `client/gui/EZMinerConfigGui.java:1488` (+`:1562-1571`) | `initGui` never calls `updateScrolledPositions()` → first-open control Y ≠ every later Y, and the `clientIsOp` branch can NPE |
| [C4](#c4) | P1 | `client/gui/EZMinerConfigGui.java:1279` | lang key `ezminer.config.serverPreviewRadius` does not exist → raw key text shown on the server tab (both locales) |
| [C5](#c5) | P1 | `client/render/GradientBlockOutlineRenderer.java:114` | hard-coded `* 24` indices per block is wrong because `SpaceCalculator` skips fully-enclosed blocks → gradient style draws the wrong subset |
| [C6](#c6) | P2 | `client/render/MinerRenderer.java` (no `onClientStopping` handler) | `RenderCache`'s VAO/VBO/EBO are static and never deleted → leaked on client shutdown / world list |
| [C7](#c7) | P2 | `client/gui/HudConfigGui.java:188-192` | `Config.hudConfigGuiOpen` only cleared by the GUI's own close paths → stuck `true` if the screen is replaced externally |
| [C8](#c8) | P2 | `client/gui/InventoryButtonOverlay.java:164-167` | unconditional `displayGuiScreen(new EZMinerConfigGui())` drops the inventory return target |
| [C9](#c9) | P2 | `client/gui/EZMinerModOptionsScreen.java:103-108` | `factory` not null-checked although the list may contain mods whose factory went away → NPE per row click |
| [C10](#c10) | P2 | `src/main/resources/assets/ezminer/shader/*.glsl` | 3 unused GLSL 330 files referencing a non-existent `sampler2D` uniform; no code references them |
| [C11](#c11) | P2 | `en_US.lang:183,188,203,212` (+zh) | dead lang keys `section.performance` / `section.stability` / `section.prospecting` / `section.planting` |
| [C12](#c12) | P2 | `client/KeyListener.java:43-46` | default mode-switch key `V` collides with vanilla "Toggle Perspective" |
| [C13](#c13) | P2 | `chain/network/PacketChainStateSync.java:101-104` vs `client/HudRenderer.java:89` | two parallel blocked-count fields kept in sync but only one is drawn → latent divergence |
| [C14](#c14) | P2 | `network/PacketOpenHudConfig.java:38-40` | `Minecraft#displayGuiScreen` called from the netty IO thread (guard only handles `side.isServer()`) |
| [C15](#c15) | P2 | `client/SmartToolSwitchHandler.java:248-313` | scroll-wheel tool cycling is unreachable while the chain key is held (C2 consumes the event first) |
| [C16](#c16) | P2 | `client/gui/EZMinerConfigGui.java:1993-1998` | client config values are clamped against a possibly-still-sentinel `runtimeServerMax*`, so locally-saved values can exceed the server cap until the first sync |

---

## 2. Findings — detail

### C1
**HUD-config key early-return strands the chain-key state machine.**
`client/KeyListener.java:61-65`:
```java
        // ===== HUD config key =====
        if (KEY_HUD_CONFIG.isPressed()) {
            HudConfigGui.open();
            return;                       // <-- returns before line 108
        }
```
and `client/KeyListener.java:75-108` is the only place that updates the hold-mode tracker:
```java
        boolean holding = KEY_CHAIN.getIsKeyPressed();
        boolean risingEdge = holding && !wasHoldingChain;
        ...
            if (!holding && wasHoldingChain) {
                stopChain();
            }
        wasHoldingChain = holding;
```
**Failure scenario** (2 steps, no timing needed):
1. Player holds the chain key (`` ` ``) — `startChain()` sent `PacketKeyState(true)` and
   `clientState.chainClientState.keyPressed = true` (`:118-119`). Server-side chain is running.
2. Player taps the HUD-config key / runs `/EZMiner hud config` while still holding the chain key.
   `HudConfigGui.open()` is called and `onInput` returns at `:64` **before `stopChain()`** can run.
   From then on `InputEvent.KeyInputEvent` is not fired while `mc.currentScreen != null`, so the
   release edge is never observed: the server keeps mining (`BaseOperator` keeps harvesting) and
   `KeyListener.wasHoldingChain` stays `true`. The player has to **click the chain key once more**
   after closing the GUI to send `PacketKeyState(false)`.
Additionally, every *other* GUI (inventory `E`, chat `T`, JEI overlay) swallows the release event
the same way, with no GUI-presence check anywhere in this class.

**Minimal fix**: do not early-return — move the HUD-config branch *below* the hold-state machine,
or reset the tracker first:
```java
if (KEY_HUD_CONFIG.isPressed()) {
    if (wasHoldingChain) { stopChain(); wasHoldingChain = false; }
    HudConfigGui.open();
    return;
}
```
plus a defensive `if (mc.currentScreen != null && wasHoldingChain) stopChain();` at the top of
`onInput`.

**Confidence**: high on the mechanism (`InputEvent` is only fired for keyboard/mouse events that
reach `Minecraft.runTick`'s `currentScreen == null` branch); the residual uncertainty is whether
any GTNH-side tweak keeps firing key events with a GUI open, which is why the defensive reset is
part of the fix.

---

### C2
**Chain-key scroll suppression is not gated on "no GUI open".**
`client/KeyListener.java:199-223`:
```java
    @SubscribeEvent
    public void onMouseEvent(MouseEvent event) {
        if (!Config.blockScrollOnChainKey) return;
        if (event.dwheel == 0) return;
        ...
        if (!chainActive) return;
        // Cancel the vanilla MouseEvent to block inventory hotbar scroll.
        event.setCanceled(true);
        ...
        handleSubModeScroll(proxy.clientState.minerModeState);
    }
```
`MouseEvent` is posted from `Minecraft.runTick()` and is registered on `MinecraftForge.EVENT_BUS`
(`:536` of `SmartToolSwitchHandler`, `:262` of `KeyListener`), i.e. it fires regardless of
`mc.currentScreen`.

**Failure scenario**: `Config.blockScrollOnChainKey` is `true` by default (`Config.java:411`).
Hold the chain key and open the inventory (or a chest, or the EZMiner config GUI) — scrolling now
silently advances the EZMiner blast/chain sub-mode and prints
`ezminer.message.subMode` chat lines instead of doing nothing, which changes the mode that the
still-running chain will use for its next harvest batch. In the config GUI it also fights the
GUI's own `handleMouseInput` (`EZMinerConfigGui.java:664-673`) which scrolls its content list —
both run for the same wheel event.

**Minimal fix**: add `if (Minecraft.getMinecraft().currentScreen != null) return;` right after the
`chainActive` check (before `event.setCanceled(true)`).

**Confidence**: high. `Config.blockScrollOnChainKey` default verified in `Config.java:411,1216-1217`.

---

### C3
**`initGui()` builds the scrollable controls at the wrong Y (row top instead of row centre) and the
`clientIsOp` branch is not null-guarded.**

`client/gui/EZMinerConfigGui.java:1471-1488` (the *only* code that centres controls in a row):
```java
            tfClientBigRadius.yPosition = getControlY(0);
            ...
            setScrolledButtonY(BTN_LOG_FUZZY_ENABLED, getControlY(6));
```
versus `client/gui/EZMinerConfigGui.java:1577-1587` (`initClientFields`) / `:1622-1652`
(`initServerFields`) / `:299-407` (buttons), which all use the **row top**:
```java
        tfClientLogBlockLimit = field(fx, contentRowScreenY(5), String.valueOf(Config.clientLogBlockLimit));
```
`updateScrolledPositions()` is called from `handleMouseInput()` (`:671`), both tab cases
(`:758`, `:765`) and the tail of itself — but **never from `initGui()`** (`:240-662`).

**Failure scenario (a) — first-open misalignment.** `contentRowScreenY(6) = 150` while
`getControlY(6) = 177` (row 6 is the 6-line `logFuzzyEnabled` label). On the very first open the
`Tree Felling Fuzzy Match` toggle is drawn 27 px above every subsequent frame; the same holds for
all multi-line rows (server rows 11/12/14-18/21-27/30-32/37-50). Cosmetic but deterministic and it
makes the GUI "jump" the first time the wheel or a tab button is touched.

**Failure scenario (b) — NPE.** `tfServer*` fields are only created when `EZMiner.clientIsOp` is
true at `initGui` time (`:656-658`), but the fields are dereferenced in a branch guarded by the
*current* value of `EZMiner.clientIsOp`, which is written asynchronously by
`PacketOpStatusResponse`/`PacketServerConfig` (`EZMiner.java:55`, `PacketOpStatusResponse.java:36`,
`PacketServerConfig.java:288`). For a player who is OP at open time and is de-opped while the GUI
is open (or re-opens after a config reload where `PacketServerConfig` set it `false`):
```java
        } else if (EZMiner.clientIsOp) {          // :1497 — now false, so this branch is skipped
```
the skip is *safe*; the dangerous direction is `activeTab == TAB_SERVER` + buttons still visible
because `updateTabVisibility()` has not run yet, and `actionPerformed` (`:993-995`) /
`handleRightClickToggle` (`:1012-1026`, which only checks `enabled && visible`) dispatch to a null
field via `applyAndSaveServerConfig()` (`:2003-2080`).

**Minimal fix**: at the end of `initGui()` call `updateScrolledPositions();` instead of only
`updateTabVisibility();` (that also makes `updateScrolledPositions`'s own `clientIsOp` branch
consistent), and null-guard the writes:
```java
if (tfServerBigRadius != null) tfServerBigRadius.yPosition = getControlY(0);
```
**Confidence**: high for (a) — it is a literal code-path difference; medium for (b) — it needs a
mid-session `clientIsOp` flip while the server tab is displayed.

---

### C4
**Missing lang key on the server tab.**
`client/gui/EZMinerConfigGui.java:1275-1277` and `:1753`:
```java
                case 11:
                    return "ezminer.config.serverPreviewRadius";
...
        drawRow(lx, contentRowScreenY(11), lc, "ezminer.config.serverPreviewRadius", tfServerMaxPreviewRadius);
```
`grep` over both lang files finds only `ezminer.config.serverPreviewRadius`-adjacent keys
`ezminer.config.serverPreviewRadius` is absent (the pair that exists is
`ezminer.config.serverPreviewLimit` at `en_US.lang:69` and `zh_CN.lang:69`). A mechanical diff of
the 197 keys in each file → 197 in both, **zero** differences, and a diff of every lang key
referenced from `src/main/java` against `en_US.lang` returns exactly one miss:
`ezminer.config.serverPreviewRadius` (referenced at `EZMinerConfigGui.java:1275`).

**Failure scenario**: opening the server tab as an OP renders the literal string
`ezminer.config.serverPreviewRadius:` above the *Server Max Preview Radius* field, in **both**
locales. The row also gets wrapped (`I18n` returns the key unchanged) so it is drawn as a single
line where the designer intended a two-line label — the row height self-computes from the same
call, so no overlap.

**Minimal fix**: add to both files, next to `serverPreviewLimit`:
```
ezminer.config.serverPreviewRadius=Server Max Preview Radius
ezminer.config.serverPreviewLimit=Server Max Preview Blocks
```
(zh: `服务端最大预览半径`).

**Confidence**: high (mechanical key diff).

---

### C5
**Gradient renderer assumes 24 indices per block; `SpaceCalculator` emits fewer for enclosed blocks.**
`client/render/GradientBlockOutlineRenderer.java:101-121`:
```java
    private static void renderBand(RenderCache cache, List<Integer> blockIndices) {
        ...
                cache.renderRange(rangeStart * 24, rangeCount * 24);
```
but the index stream is *not* uniform per position — `client/render/SpaceCalculator.java:137-165`:
```java
            int kept = 0;
            for (boolean b : edgeKept) { if (b) kept++; }
            // Skip fully-enclosed blocks – but do NOT increment base here;
            // base must only advance when vertices are actually appended.
            if (kept == 0) { continue; }
            ...
            int[] idx = new int[kept * 2];
            ...
            base += 8; // advance only after vertices are appended
```
`positions` (the list the gradient renderer indexes) still contains the enclosed blocks, and any
block with a mined neighbour on one or more sides contributes `< 12` edges (`< 24` indices).

**Failure scenario**: with `Config.renderStyle == 3` (Gradient) and a preview of a real ore vein,
the interior blocks are fully enclosed and contribute **0** indices while still occupying a slot in
`positions`. Every band index after the first such block is offset by at least 24, so the coloured
bands are drawn shifted onto the wrong blocks — visibly wrong colours and, with a large enough
deficit, nothing meaningful at all. The first band is also offset whenever the *first* block in the
list is enclosed. The count is clamped by the GL draw call to the uploaded range, so this is wrong
output, not a crash.

**Minimal fix**: have `SpaceCalculator` expose the per-block index offset (e.g. record
`base` per position into a parallel `int[] blockIndexOffset`, or make `VertexAndIndex` carry it)
and use `renderRange(offsets[i], lengths[i])` in `renderBand`. Cheap alternative: in
`GradientBlockOutlineRenderer`, rebuild the per-block ranges from `cache` metadata instead of
`* 24`.

**Confidence**: high (arithmetic is explicit in both files).

---

### C6
**Static `RenderCache` GL objects are never deleted.**
`client/render/MinerRenderer.java:54`:
```java
    public static final RenderCache renderCache = new RenderCache();
```
`RenderCache.ensureGLInit()` (`:45-69`) does `GL30.glGenVertexArrays()` + two
`GL15.glGenBuffers()`; there is no `delete()`/`glDelete*` anywhere in the class, and
`MinerRenderer.unRegistry()` (`:429-434`) only unregisters event handlers.

**Failure scenario**: every full client restart inside the same JVM keeps 3 GL objects alive until
the context dies with the window; more importantly, when the player leaves a world and the GL
context is *recreated* for a new world (a known 1.7.10 behaviour with some drivers/AMD), the stale
ids are re-used by `glInitialized == true` and the mesh is uploaded into ids belonging to the dead
context — silently drawing nothing.

**Minimal fix**: add `RenderCache.delete()` (`glDeleteBuffers`/`glDeleteVertexArrays`, reset
`glInitialized = false`) and call it from a `FMLNetworkEvent.ClientDisconnectionFromServerEvent`
handler in `MinerRenderer` (a handler already exists for the same purpose in `KeyListener:234`).

**Confidence**: medium (the leak is certain; the context-recreation consequence is
driver-dependent).

---

### C7
**`hudConfigGuiOpen` can stay `true` forever.**
`client/gui/HudConfigGui.java:188-192` sets it, and it is only cleared at `:129` and `:168` — both
inside the screen's own close paths. The only other clear is `KeyListener.java:251` (disconnect).

**Failure scenario**: while the drag overlay is open, any other code calls
`mc.displayGuiScreen(...)` (a server-opened container, a scripted mod, NEI). `GuiScreen.onGuiClosed`
does not touch the flag; afterwards
`HudRenderer` keeps bypassing all three of its gates (`HudRenderer.java:79-83`) because
`inHudConfig == true`, so the EZMiner HUD (and `MinerRenderer`'s preview, via
`Config.isPreviewEnabled()` gating) keeps drawing on top of unrelated GUIs.

**Minimal fix**: set `Config.hudConfigGuiOpen` in `HudConfigGui.initGui()` and clear it in an
override of `onGuiClosed()` (vanilla calls it whenever the screen is replaced), keeping the
existing `closeAndSave()` writes.

**Confidence**: high (the state machine is fully visible in this file).

---

### C8
**Inventory button drops the return target.**
`client/gui/InventoryButtonOverlay.java:162-168`:
```java
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Post event) {
        if (guiOffsets(event.gui) == null) return;
        if (event.button.id != BTN_ID) return;
        Minecraft.getMinecraft().displayGuiScreen(new EZMinerConfigGui());
    }
```
`EZMinerConfigGui` has a working `returnScreen` mechanism (`:33-41`, `:1171-1173`) and the mod-list
path uses it (`EZMinerModListEntryGui.java:44`), but the inventory path passes `null`.

**Failure scenario**: clicking `[EZMiner] Settings` from the inventory and pressing Esc / Save &
Exit returns to the game HUD instead of the inventory, forcing the player to press `E` again. This
is exactly the inconsistency the `returnScreen` field was introduced to remove.

**Minimal fix**: `new EZMinerConfigGui(event.gui)`.

**Confidence**: high.

---

### C9
**`factory` dereferenced without a null check.**
`client/gui/EZMinerModOptionsScreen.java:102-108`:
```java
            IModGuiFactory factory = FMLClientHandler.instance().getGuiFactoryFor(mod);
            Class<? extends GuiScreen> cls = factory.mainConfigGuiClass();
```
The list is built in the constructor with a `factory != null` filter (`:53-60`), but it can become
stale if the factory is unregistered/reloaded later.

**Minimal fix**: `if (factory == null) return;` before `mainConfigGuiClass()`.
**Confidence**: medium (reachability depends on a factory disappearing between construction and
click; the current behaviour is a caught `Exception` + a misleading "Failed to open" chat line).

---

### C10
**Unused shader resources.**
`grep -rn 'ezminer:shader|MinerPreview|ShaderProgram|\.glsl' src/` → **no matches**.
`MinerRenderer` renders exclusively with fixed-function GL (`:414-419` and the four
`BlockOutlineRenderStrategy` implementations). The three files still shipped are:
`shader/MinerPreviewVertex.glsl`, `shader/MinerPreviewGeometry.glsl` (uses
`layout (lines) in` with `max_vertices = 4`, GLSL 330), `shader/MinerPreviewFragment.glsl`.
The fragment shader declares `uniform vec4 lineColor;` and then uses `mix(lineColor.rgb, ...)` —
valid — but the set as a whole targets a programmatic geometry-shader pipeline that no code in the
project builds.

**Minimal fix**: delete `src/main/resources/assets/ezminer/shader/` (dead weight in the jar), or
add a `TODO`/tracking issue if the shader path is still planned.
**Confidence**: high that it is dead; low that it is a defect (severity P2, cleanup only).

---

### C11
**Dead lang keys.** Present in both files (`en_US.lang:183,188,203,212`) and referenced nowhere:

| key | en_US | zh_CN |
|-----|-------|-------|
| `ezminer.gui.section.performance` | 183 | 183 |
| `ezminer.gui.section.stability` | 188 | 188 |
| `ezminer.gui.section.prospecting` | 203 | 203 |
| `ezminer.gui.section.planting` | 212 | 212 |

The server tab actually renders only four headers — Mining, Item Collection (`:1767`), Special
Modes (`:1776`), System (`:1788`) — and the performance/stability/prospecting/planting rows live
inside those sections. **Minimal fix**: delete the four pairs, or add the missing
`drawSectionHeader` calls if the sections were intended.
**Confidence**: high (mechanical: referenced-key set minus lang-key set).

---

### C12
**Default key `V` collides with vanilla Toggle Perspective.**
`client/KeyListener.java:43-46`:
```java
    public static final KeyBinding KEY_MODE_SWITCH = new KeyBinding("key.ezminer.modeSwitch", Keyboard.KEY_V, ...);
```
and `client/KeyListener.java:68-73` handles it in `InputEvent` (only dispatched while no screen is
open), while vanilla `Minecraft.handleKeyInput` also reacts to `V` with no GUI open.

**Failure scenario**: pressing `V` to cycle Blast→Chain→Special publishes a mode-switch packet
**and** toggles the camera to third person (and prints `Main Mode: …`). Since the mode switch is
pushed to the server (`syncModeToServer`, `:176-179`), the side effect is not merely visual.

**Minimal fix**: change the default to `Keyboard.KEY_NONE` (like `KEY_HUD_CONFIG`, `:47-50`) or to
a key not used by vanilla, and add a one-line note to the lang description.
**Confidence**: medium-high (vanilla `V`→perspective is standard 1.7.10; a pack could remap it).

---

### C13
**Redundant, drifting HUD counters.**
`chain/network/PacketChainStateSync.java:101-104`:
```java
                    proxy.clientState.chainClientState.chainedCount = msg.chainedCount;
                    proxy.clientState.chainClientState.elapsedMs = msg.elapsedMs;
                    proxy.clientState.chainedBlockCount = msg.chainedCount;
                    proxy.clientState.chainElapsedMs = msg.elapsedMs;
```
`client/HudRenderer.java:89` draws `state.chainClientState.chainedCount` only; `ClientStateContainer`
`:17,19` documents `chainedBlockCount`/`chainElapsedMs` as the canonical pair and `KeyListener`
`:139` resets only `chainedBlockCount`. Any future writer that updates one pair and not the other
makes the HUD disagree with the frozen/preview HUD (`PacketChainStateSync` is the only writer today,
so this is latent).

**Minimal fix**: delete `ClientStateContainer.chainedBlockCount`/`chainElapsedMs` and read
`chainClientState` everywhere, or make `HudRenderer` read the documented pair.
**Confidence**: high for the duplication, medium that it is currently user-visible (it is not).

---

### C14
**Client-side GUI mutation from the netty IO thread.**
`network/PacketOpenHudConfig.java:34-42`:
```java
        public IMessage onMessage(PacketOpenHudConfig msg, MessageContext ctx) {
            HudConfigGui.open();          // -> Minecraft.getMinecraft().displayGuiScreen(new HudConfigGui())
```
`MainThreadEnforcer` only defers for the **server** side (`MainThreadEnforcer.java:52-65`:
`Config.enableMainThreadGuard && side.isServer() && isOnIoThread()`), so this S→C handler runs on
the netty event loop, and `HudConfigGui.open()` (`:188-192`) touches `displayGuiScreen`, which is
documented as client-thread-only in 1.7.10.

**Failure scenario**: `/EZMiner hud config` (or any S→C send) races the client tick loop; worst
case is an inconsistent screen transition / GL state while the current screen is being replaced.
The same class of issue exists for the other S→C handlers that mutate render-visible state
(`PacketCachedBlockSync:107-119` publishes `cachedPreviewPositions` before bumping
`cachedPreviewVersion`; `PacketChainStateSync:79-121` may call `proxy.minerRenderer.freeze()` /
`unfreeze()` on the IO thread).

**Minimal fix**: add a client twin of `MainThreadEnforcer` (a queue drained from
`TickEvent.ClientTickEvent`) and route these handlers through it; or open the GUI via
`Minecraft.getMinecraft().addScheduledTask(...)`.
**Confidence**: medium-high (mechanism is certain; the concrete failure is a race, so the visible
symptom is intermittent). Cross-audit note: `docs/review/full-bug-scan.md` covered the server-side
direction; this finding is the client-side counterpart.

---

### C15
**Tool-wheel cycling is dead while chaining.**
`client/SmartToolSwitchHandler.java:270-286` requires `event.dwheel != 0` and an uncancelled
`MouseEvent`; but when `Config.blockScrollOnChainKey` (default `true`) is on and the chain key is
held, `KeyListener.onMouseEvent` (`:200-223`) sets `event.setCanceled(true)` first. `KeyListener`
is registered before `SmartToolSwitchHandler` in `ClientProxy.preInit` (`:31-35`), and
`EventBus.post` stops delivering cancelled events to subsequent listeners when the event is
cancelable and no lower-priority listener requested it.

**Failure scenario**: a player running a long chain cannot scroll-switch to their second-best tool;
attempting it silently changes the *sub-mode* instead (C2 makes this worse inside GUIs).
**Minimal fix**: let `SmartToolSwitchHandler` check `event.isCanceled()` and, if so, use the raw
`Mouse.getEventDWheel()` path, or have `KeyListener` skip the cancel when
`SmartToolSwitchHandler` reports a valid target with ≥2 candidates.
**Confidence**: medium (depends on `EventBus` cancellation semantics for this exact case; the
listener ordering is verified in `ClientProxy.java:31-35` and `SmartToolSwitchHandler.java:536`).

---

### C16
**Client cap clamping can be a no-op before the first server sync.**
`client/gui/EZMinerConfigGui.java:1984-2001` (`applyAndSaveClientConfig`) and
`Config.java:1088-1097`:
```java
        Config.clampClientMiningToServerCaps();     // min(x, runtimeServerMax*)
```
`runtimeServerMax*` starts at `Integer.MAX_VALUE` and is only lowered by `PacketServerConfig`
(`Config.java:1058-1069` resets it to the sentinel on disconnect). So a value typed in the GUI and
saved before the first sync is persisted to `EZMiner.cfg` unclamped.

**Mitigation already present**: the *authoritative* path is safe —
`Config.buildClientMinerConfigForSync()` (`:1071-1082`) re-caps with `Math.min(client*, runtime*)`,
and the server caps again via `Manager.receiveClientConfig`. Authoritative server state is
therefore **not** writable from the client, which is the invariant the task asked for.
**Minimal fix**: also clamp against `Config.bigRadius`/`blockLimit`/… (the synced local copy of the
server config) as a fallback when the sentinel is still set — the same rule
`ClientCapSyncTarget.effectiveServerMax` already applies (`:59-61`).
**Confidence**: high for the code path, low for user impact (a few seconds until the sync lands).

---

## 3. Re-verified known items

| item from `docs/review/*` | current status |
|---|---|
| `full-bug-scan.md` §P0-1/2/3 — `PacketSaveServerConfig` / `PacketReloadServerConfig` / `PacketMinerConfig` handlers run off-thread | **still present**, server-side; outside this task's scope (see `network/PacketSaveServerConfig.java:277-354`, `PacketReloadServerConfig.java:35-51`, `PacketMinerConfig.java:55-64`). The client half is now C14. |
| `full-bug-scan.md` §P0-4 — `PacketInventorySwap` writes the inventory on the IO thread | **the packet is gone**; replaced by `PacketToolSwapRequest` (`network/PacketToolSwapRequest.java:61-127`), which performs the same off-thread inventory mutation (`inv.swapInventorySlotsAtomically` at `:112`, `syncInventoryDifference` at `:118`) and still does not call `MainThreadEnforcer`. **Regression-equivalent, still present** (server-audit scope). |
| `full-bug-scan.md` §P1-2 — `MainThreadEnforcer` a no-op | **fixed**: `MainThreadEnforcer.java:52-65` now queues onto `DEFERRED` and `drainDeferred()` (`:71-82`) is drained per server tick. Confirmed for `side.isServer()` only (see C14). |
| `full-bug-scan.md` §P1-2 — `PlayerManager.managers` plain `HashMap` | **fixed** per `AGENTS.md` (now `ConcurrentHashMap`); not re-verified here (server scope). |
| `full-bug-scan.md` §P2-14 — `PacketServerConfig` client handler writes `Config` statics off-thread | **still present** (`network/PacketServerConfig.java:255-288`); this is one of the writes that can flip `EZMiner.clientIsOp` and thereby arm C3(b). |
| `review-summary.md` §15 — GUI dead constant / stale lang / bus rows removed; rows 34→33 | **superseded**: the GUI now has `MAX_CONTENT_ROWS = 23` (client) and `SERVER_CONTENT_ROWS = 51` (server) — audited in §4. |
| `decouple-api-review.md` B4 note — "`isPreviewEnabled` consumed by `HudRenderer:79`, `MinerRenderer:121`, `KeyListener:130`" | **still accurate** (`HudRenderer.java:79`, `MinerRenderer.java:123`, `KeyListener.java:130`). |
| `docs/review-summary.md` §6 — old server fields reset by GUI saves | **not re-audited** (server-audit scope); the client half (`applyAndSaveServerConfig`, `:2003-2080`) still sends **all** fields, so the finding is unchanged. |
| `docs/review-summary.md` §3 — `SpaceCalculator` rewritten with a reused `boolean[12]` edge mask + probe | **present and correct** for the mesh itself (`SpaceCalculator.java:111-136`); it introduced C5 for the gradient consumer by making per-block index counts non-uniform. |

---

## 4. Verified OK

### 4.1 Dedicated-server safety
* `EZMiner.java:43` uses `@SidedProxy(clientSide = "com.czqwq.EZMiner.ClientProxy", serverSide = "com.czqwq.EZMiner.CommonProxy")`; `client/` classes are only touched through `ClientProxy` (`ClientProxy.java:15-36`), which is never loaded on a dedicated server.
* Every common-code reference to a client-only type is **runtime-guarded**, so no `NoClassDefFoundError` on a dedicated server: `EZMiner.proxy instanceof ClientProxy` (`chain/network/PacketChainStateSync.java:79`, `PacketCachedBlockSync.java:107`, `PacketMinesweeperMark.java:54`, `PacketMinesweeperClear.java:37`, `PacketSudokuFill.java:54`, `PacketSudokuClear.java:34`, `PacketProspectState.java:41`, `PacketBlockSwapResult.java:39`, `PacketBlockSwapClear.java:29`).
* `network/PacketToolBreakHandoff.java:33-53` and `PacketToolSwapResult.java:19-57` are deliberately free of `@SideOnly` fields and only annotate `onMessage` (`:57`, `:46`), so `NetworkMain.registry()` can load them on the server. Verified by reading both files end to end.
* `client/gui/InventoryButtonOverlay.java:248` resolves ServerUtilities reflectively (`Class.forName` inside `try/catch (Throwable)`, `:254-256`) — a missing GuiSidebar cannot break the class.
* `client/IngameInfoBridge.java:41-52` gates on `Loader.isModLoaded("InGameInfoXML")` + `Class.forName` in a `try/catch`; `hideHud`/`restoreHud` are idempotent (`:68`, `:91`), and `restoreHud()` is called on release (`KeyListener.java:160`) and on disconnect (`:255`).
* `utils/MessageUtils.java` imports `net.minecraft.client.Minecraft` **without** `@SideOnly(CLIENT)` because `serverSendPlayerMessage` (`:29-40`) is a genuine server-side entry point (used by `chain/execution/*`, `core/BaseOperator.java:161`). Safe: the client-only reference is confined to `printSelfMessage`, which is only called from `KeyListener.java:70,172` (client-only class). Verified against the 1.7.10 sources (`I18n.java` is `@SideOnly(CLIENT)`, so **no** common class may import it) — `utils/TimeFormatUtils.java:3` does import `I18n`, but it is consumed from `HudRenderer` (client) on the client path and via `formatElapsedServer` → `StatCollector` (`:47-48`, used at `core/BaseOperator.java:359`) on the server path. Both are safe; **residual fragility**: neither utility carries `@SideOnly(CLIENT)`/`@SideOnly(SERVER)` annotations, so a future server-side call to `formatElapsed` would `NoClassDefFoundError`.

### 4.2 Client/server authority
* Client values are preference **caps**, never authoritative: `Config.buildClientMinerConfigForSync()` (`Config.java:1071-1082`) applies `Math.min(client*, runtimeServerMax*)` before the packet is even constructed, `clampClientMiningToServerCaps`/`clampClientPreviewToServerCaps` (`:1088-1103`) clamp the local statics, and `EZMinerConfigGui.applyAndSaveClientConfig` (`:1984-2001`) sends only that capped projection. The 1.7.10 server remains the authority (`Manager.receiveClientConfig`, out of scope).
* The OP-only server tab cannot be reached without `EZMiner.clientIsOp` (`EZMinerConfigGui.java:278-287`, `:436`, `:656-658`), and server writes only ever travel as `PacketSaveServerConfig` (`:2004-2079`) — a C→S request, not a direct mutation.

### 4.3 GUI row-shift checklist (the contract from `CLAUDE.md`)
Row model re-derived mechanically from both lang files and the `getContentRowHeight` formula
(`ROW_H = 20`, `EXTRA_LINE_SPACING = 2`, `FONT_HEIGHT = 9` → `+11` per extra line).

| checklist item | result |
|---|---|
| `MAX_CONTENT_ROWS` vs actual client rows | **OK** — 23 (`:104`); `getRowLabelKey` client switch covers 0-22 (`:1200-1245`), all 23 drawn (`:1690-1729`) |
| `SERVER_CONTENT_ROWS` vs actual server rows | **OK** — 51 (`:106`); `getRowLabelKey` server switch covers 0-50 (`:1252-1356`), all 51 drawn (`:1738-1807`) |
| `isSectionBreak()` on the **client** tab | **OK** — `3,6,8,15,20` (`:1409`) matches exactly the five `drawSectionHeader` calls after rows 3/6/8/15/20 (`:1696,1702,1707,1717,1727`) |
| `isSectionBreak()` on the **server** tab | **OK** — `3,6,21,27,36,50` (`:1413`) matches the four headers after rows 21/27/36 (`:1767,1776,1788`) plus the two unlabelled sub-group gaps after rows 3 and 6 (`:1744`, `:1749`), which are also consumed by header Y at `:1767` |
| every section header Y uses `getContentRowHeight(prevRow)`, never `ROW_H` | **OK** — all eight headers: `:1696,1702,1707,1717,1727` (client, `getContentRowHeight(3/6/8/15/20)`) and `:1767,1776,1788` (server, `getContentRowHeight(21/27/36)`). The first header on each tab is `contentRowScreenY(0) - 10` (`:1689`, `:1737`) and lives in the `TOP_PAD = 12` band. No `+ ROW_H + 4` pattern remains anywhere. |
| header/next-row separation | **OK** for both locales. Each header is at `rowTop(prev) + contentHeight(prev) + 4` and the next row starts at `rowTop(prev) + contentHeight(prev) + SECTION_GAP(18)`, so the 4+10 px header always sits inside the gap (`gap = 18 > 14`) regardless of actual glyph wrapping; the separator-to-title distance is ≥ 4 px worse case. |
| multi-line label heights | **OK** — `getLabelRenderLines` uses the same `splitLabel(resolveNewlines(I18n.format(key)))` as `drawRow`, and `contentRowScreenY`/`recalcTotalContentH` both go through `getRowHeight`, so measured height and drawn text cannot disagree (even for a label that wraps at `LABEL_MAX_WIDTH`). |
| `\n` handling for MC 1.7.10 lang files | **OK** — `resolveNewlines` (`:1364-1366`) converts the literal `\n` the 1.7.10 `.lang` parser stores; `isMultiLineLabel` (`:1369-1372`) deliberately checks the *unconverted* string, which is the same source the draw path converts. |
| sync-icon vs scrollbar collision | **OK** — `SYNC_BTN_X = 162 + 100 + 4 = 266` (`:99-128`), right edge `280`; track at `GUI_W - SCROLLBAR_W - 2 = 284` (`:1847`). Full-width option buttons end at `guiLeft + 274` (`bw = 290 - 20 - 4 - 2 = 264`, `:297`). |
| `updateScrolledPositions` row consistency | **OK** — covers client rows 0-22 (`:1467-1491`) and server rows 0-50 (`:1499-1555`) one-to-one, incl. `BTN_LOG_FUZZY_ENABLED` row 6, `BTN_SERVER_LOG_FUZZY_ENABLED` row 6, `BTN_SERVER_NOTIFY_NEIGHBORS` row 50 |
| `initGui` button rows | **OK** — every `newOptionButton(id, row, …)` row equals the row drawn and the row repositioned by `updateScrolledPositions`; `initClientFields`/`initServerFields` field rows likewise (see C3 for the *Y*, which is a different invariant) |
| `actionPerformed` per row | **OK** — all 34 option + 6 action cases present (`:771-995`), plus the sync-icon default branch (`:998-1000`) |
| `updateTabVisibility` | **OK** — client content set (`:1865-1875`), server content set (`:1878-1900`), sync icons (`:1903-1909`), action strips (`:1876-1877,1901`) |
| `applyAndSaveClientConfig` / `applyAndSaveServerConfig` | **OK** — all 11 client fields and all 28 server fields + 12 named packet fields are read (`:1985-1996`, `:2004-2078`) |
| `clientFieldFor` | **OK** — all 10 `ClientCapSyncTarget` constants have a case (`:1958-1981`); no default-returning target |
| `ClientCapSyncTarget.row()` | **OK** — `{0,1,2,3,7,8,21,22,4,5}` (`ClientCapSyncTarget.java:22-33`) all equal the client-tab rows drawn for those keys |
| `mouseClicked` / `keyTyped` forwarding | **OK** — all 11 client fields + all 27 server fields forwarded, guarded by tab and `clientIsOp` (`:1068-1157`) |
| lang keys in both files | **197 = 197**, byte-identical key sets (see C4 for the single *code→lang* miss and C11 for the four stale keys) |

### 4.4 Render path
* `MinerRenderer.drainQueue` rebuilds the mesh **only** when `spaceCalc.hasChange` (`:267-273`), and `SpaceCalculator.add` is the only writer of that flag (`:115-120`); duplicates return early, so a wave of out-of-render-distance positions no longer triggers a rebuild — the documented optimisation is intact.
* Edge mask + probe are allocation-free and correct: `edgeKept` is a reused `boolean[12]`, `probePos` a reused `Vector3i` (`SpaceCalculator.java:111-113`), and the neighbour tables are resolved once at class load from the *same* `COMPLETE_EDGES` list (`:89-102`), so the index mapping cannot drift.
* Geometry of the 12 edges is correct: front `z=1` = vertices `{0,1,2,3}`, back `z=0` = `{4,5,6,7}` (`:23-27`); every `DIR_EDGES` entry is exactly the four edges of that face (`:53-70`), and 12 unique edges are produced by the removed-cube face decomposition (`:30-34`).
* `base` only advances after vertices are appended (`:141-164`), so enclosed blocks cannot desynchronise the *vertex* stream.
* GL state restore: native/modern/rainbow/gradient all wrap their changes in `glPushAttrib(GL_ALL_ATTRIB_BITS)`/`glPopAttrib` (`NativeBlockOutlineRenderer.java:25,37`; `ModernBlockOutlineRenderer.java:29,48`; `RainbowBlockOutlineRenderer.java:48,67`; `GradientBlockOutlineRenderer.java:69,94`), and `MinerRenderer.doRender`/`doRenderMinesweeper`/`doRenderSudoku` pair `glPushMatrix`/`glPopMatrix` (`:414,419; :315,328; :363,377`). No unbalanced state.
* `RenderCache.updateData` grows the staging buffers only when the data no longer fits (`:76-78`, `:90-92`) and uses `glBufferData` (orphaning) rather than `glBufferSubData`, so no stale tail is drawn; `renderRange` bails on `count <= 0` and on `vao == 0` (`:125-127`).
* Everything on this path is called from `RenderWorldLastEvent`/`RenderGameOverlayEvent` handlers registered on the Forge bus — i.e. the render thread. The only cross-thread inputs are the `volatile` fields and `CopyOnWriteArrayList`s in `ClientStateContainer` (`:17-100`), which is the right shape (the *ordering* concern is C14).

### 4.5 KeyListener / input semantics
* `KeyBinding.isPressed()` in 1.7.10 turns out **not** to be a raw "is down" test: `isPressed()` returns `false` immediately when `pressTime == 0`, otherwise decrements and returns `true` (`KeyBinding.java:103-114` of the decompiled sources), and `onTick` bumps `pressTime` (`:28-39`). Because FML's `Keyboard.poll` path calls `onTick` **before** the key event, the counter produced by a single physical press is consumed by exactly one `isPressed()` call — so `KeyListener.java:62` (`KEY_HUD_CONFIG`) and `:68` (`KEY_MODE_SWITCH`) fire **once per press**, not once per tick. The earlier concern about a 20×/s mode-switch spam is **not** a defect. Same for `SmartToolSwitchHandler.java:72` (`getIsKeyPressed()`, explicitly the continuous form, correctly paired with a `wasHolding` rising-edge guard at `:73`).
* `event instanceof InputEvent.MouseInputEvent` gating of `handleSubModeScroll` (`KeyListener.java:89`, `:99`) matches the double-`@SubscribeEvent` registration on both buses (`:262-265`) — no duplicate sub-mode steps per wheel tick when `blockScrollOnChainKey` is off (the Forge `MouseEvent` handler returns early at `:201` in that case).
* Disconnect cleanup is complete and idempotent: `onClientDisconnect` (`:234-256`) resets `keyPressed`, `inOperate`, `wasHoldingChain`, `chainToggled`, all four cooldown stamps, both marker lists, the renderer freeze, `hudConfigGuiOpen`, the server runtime overrides, and the IGI HUD.
* `SmartToolSwitchHandler.onClientDisconnect` (`:523-529`) resets the toggle, temp-disable and tracking. `restoreSwapAndClear` only clears the cached slot lists so the next tick rebuilds them from live inventory (`:144-155` guard), which correctly survives the server's asynchronous `PacketToolSwapFinalize` restore.

### 4.6 HUD
* No unbounded per-frame allocation in the animation: `drawRainbowBounceHeader` (`:255-283`) and `drawWaveHighlightHeader` (`:304-338`) allocate only per-character `String.valueOf`/concatenations for a 7-character brand (14 small strings/frame, short-lived), and the three `I18n.format` calls per frame are unavoidable for localisation. No growing collections, no per-frame `new ArrayList`/`StringBuilder` accumulation.
* The HUD is correctly gated on `Config.isPreviewEnabled() && mc.currentScreen == null && state.chainClientState.keyPressed` (`:79-83`) with the intentional `hudConfigGuiOpen` bypass for the drag overlay.
* IGI hiding is only armed when the HUD will really be shown (`KeyListener.java:130` requires `Config.suppressIngameInfoHud && Config.isPreviewEnabled()`), matching the `HudRenderer` gate, and is restored on both release and disconnect.

---

## 5. Inconclusive

1. **`InventoryButtonOverlay` SU-sidebar geometry** (`:120-142`, `:243-279`). The reflection is defensive (`getFieldMcpSrg`/`getDeclaredFieldMcpSrg` try MCP then SRG, `:67-98`), the bounds are sanity-checked (`:267-273`), and `getSuArea` null-falls back to the panel bottom. I could **not** verify `serverutils.client.gui.GuiSidebar`'s real `xPosition/yPosition/width/height` semantics (no ServerUtilities sources in `tmp/` or the workspace), so whether `suArea.y + suArea.height` is the visual bottom of the sidebar or of its bounding box is unverified. No defect claimed.
2. **`V` key conflict (C12)** — I did not verify whether this GTNH pack re-binds vanilla Toggle Perspective away from `V`. The EZMiner half of the collision is certain; the vanilla half is a standard-1.7.10 assumption.
3. **`EventBus` cancellation delivery for C15** — I verified the registration order (`ClientProxy.java:31-35`) and that `KeyListener` cancels the event, but the exact "cancelled cancelable event is not delivered to later listeners unless `receiveCanceled()`" rule was not re-read from the Forge sources in this checkout.
4. **`clientIsOp` mid-session flip reachability (C3b)** — `PacketOpStatusResponse.java:36` and `PacketServerConfig.java:288` both write it, but whether a *de-op while the config GUI is open* is ever sent by this server build (vs. only on the next `/EZMiner` command) was not traced into the permission package (server scope).
5. **GL object deletion on context loss (C6)** — whether this sandbox's target driver/GTNH build ever recreates the client GL context without a JVM restart was not determined.
6. **ModularUI2 / EnderIO / Forestry / Et-Futurum client-side behaviour** — `tmp/` contains no client-side GUI/render sources for these mods that EZMiner's own GUI/render code depends on; EZMiner's client layer references none of them (no `modularui`/`enderio`/`forestry`/`etfuturum` imports anywhere under `src/main/java/com/czqwq/EZMiner/client/`). The only cross-mod *client* surfaces are ServerUtilities (`InventoryButtonOverlay`), InGame Info XML (`IngameInfoBridge`), GregTech (`GT5ToolCompat`, toolboxes) and Visual Prospecting (`HudRenderer.java:204`) — all guarded by `Loader`/`Class.forName`/`isModLoaded`. See inconclusive #1 for the one item I could not close.
7. **`Config.hudConfigShowOtherHuds`** is read at `HudConfigGui.java:64` and declared at `Config.java:348`/`:1204`, but it has **no GUI row and no lang key** — it is file-only today. Whether that is intentional was not determined (not a defect).

---

## 6. Scope / process notes

* Files read for this audit: all 27 files under `client/**` and `chain/client/**`, plus
  `ClientProxy.java`, `CommonProxy.java`, `EZMiner.java`, `Config.java` (client/authority
  regions), the client-relevant packet classes (`PacketOpStatusResponse`, `PacketOpenHudConfig`,
  `PacketToolBreakHandoff`, `PacketToolSwapResult`, `PacketToolSwapRequest`,
  `PacketToolSwapFinalize`, `PacketServerConfig`), `toolswap/server/**` (protocol only),
  `network/MainThreadEnforcer.java`, both lang files, all three `.glsl` files, and the decompiled
  1.7.10 `KeyBinding`/`I18n`/`FontRenderer` from `build/rfg/`.
* No file outside this report was created or modified. The one temporary artifact
  (`tmp/_mcp_extract/`, used to read the vanilla sources) was deleted before finishing;
  `git status --porcelain` reports only pre-existing unrelated entries
  (`gradle/gradle-daemon-jvm.properties`, `gradle/wrapper/*`, and the untracked `.agent-teams/`).
* No Gradle/java execution was attempted (sandbox constraint); the build is therefore not part of
  the evidence for this report — every claim is source-level with `file:line`.
