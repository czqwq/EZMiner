# review-round1.md — independent review of the unified fix (t9)

**Reviewer**: `audit-compat-ds` — task **t11** (review round 1), attempt `2745b741-c2f6-45ad-a6a5-76bb34a5404b`
**Reviewed artifacts**: `docs/review/agent-teams/fix-ledger.md` (t8 contract, 479 lines), `docs/review/agent-teams/fix-report.md` (t9 report, 525 lines), the changed code itself.
**Verification report**: **none exists yet** — no `docs/review/agent-teams/verification*.md` is present in the tree (t10 produced no file), so this review is the first independent code check of the t9 pass.
**Mode**: read-only. Nothing under `src/` or `tmp/` was modified, no code was fixed by me, and `docs/review/agent-teams/review-round1.md` is the only file written.

## 0. VERDICT: **needs_revision** (3 high, 10 medium, 6 low findings)

The implemented half of the pass is largely sound — the previously-unverified high items V11/V31/V32/V41(mixin half)/V61 are real, present, and the documented invariants I could check survived. **But the ledger is not closed**: at least **12 ledger `FIX` rows are absent from the code**, of which **9 are never mentioned in `fix-report.md` at all**, and **two rows (V11b, V37) and one sub-change (V04's per-position half) are reported as done while the code shows no change at all**. The report's status line calls the pass "COMPLETE" and claims "None of these is a silent omission" — that claim is false as written, and `fix-report.md` therefore cannot be used as the closure evidence for t9.

| # | severity | finding (one line) | primary file:line |
|---|---|---|---|
| R1 | **high** | V11b reported implemented, code unchanged: client still re-gates the handoff on its own `Config.enableToolBreakHandoff` | `network/PacketToolBreakHandoff.java:59` |
| R2 | **high** | V41 only half done: `world.canMineBlock` is **not** consulted on the two EBS batch paths, whose comments claim the opposite | `chain/execution/ChunkCachedHarvester.java:141-147`; `chain/execution/BlockHarvestActionExecutor.java:153-160` |
| R3 | **high** | V03b (watchdog) absent and **not mentioned anywhere** in `fix-report.md` | `chain/watchdog/ChainWatchdog.java:49-52,70-76,85-95`; `core/BaseOperator.java:177,336` |
| R4 | medium | V04's per-position `canMineBlock` half claimed but absent (right-click modes still unguarded per position) | `chain/execution/BlockSwapModeHandler.java`, `chain/execution/PlantingModeHandler.java` (no `canMineBlock` anywhere) |
| R5 | medium | V22 absent: HUD-config branch still returns before the release-edge `stopChain()` | `client/KeyListener.java:62-65` vs `:103-105` |
| R6 | medium | V23 absent: no `currentScreen` guard before `event.setCanceled(true)` | `client/KeyListener.java:199-215` |
| R7 | medium | V33 absent: no `Block.removedByPlayer` call in any path | `mixin/early/MixinItemInWorldManager.java:133` |
| R8 | medium | V08 absent: pre-calc still sends the whole growing result list every tick | `chain/planning/ChainPreCalcEngine.java` (no delta/`lastSentSize`) |
| R9 | medium | V15 absent: exhaustion strategy still ignores its `harvested` flag; batched path still writes an absolute value | `chain/execution/ChainHarvestExhaustionStrategy.java:27-33`; `core/BaseOperator.java:514` |
| R10 | medium | V62 absent: `canOperate()` still has no GT branch (dead durability gate on every GT tool) | `core/BaseOperator.java:250` |
| R11 | medium | V64 (T4) absent: durability estimate still misses GT's drop-conversion charge | `compat/GT5ToolDurabilityBridge.java:100-102,112` |
| R12 | medium | V65 absent: GT machine swap still passes `null` NBT | `compat/GT5BlockSwapCompat.java:216` |
| R13 | medium | V39 absent: `ItemFilterExpression` `*` semantics unchanged | `utils/ItemFilterExpression.java:191-239` |
| R14 | medium | V40 absent: `maxFortuneLevel` still has no reader (documented cap unenforced) | `Config.java:311,920`; `config/ConfigValidator.java:79-83` |
| R15 | medium | V12/V13/V29 are deferred under a heading claiming "the ledger deferred them" — the ledger marked all three **FIX**, and only V26's deferral is recorded as captain-accepted | `fix-report.md:435-442`; `fix-ledger.md:125-127,137` |
| R16 | low | V37 claimed fixed ("fixed separately (V37)") but unchanged | `compat/EtFuturumOreCompat.java:27`; `compat/EtFuturumCropCompat.java:41` |
| R17 | low | Report overstates the changed set: `MixinGTOreAdapter.java` was **not** modified yet is listed among files that got the new generation-scope javadoc; there is no consolidated changed-file list | `fix-report.md:384`; mtime 2026-07-09 |
| R18 | low | `AGENTS.md` now contradicts the fixed code: it still describes the bridge as "`instanceof BlockDynamicLiquid` only" | `AGENTS.md:40` vs `compat/CoFHWaterBridge.java:76` |
| R19 | low | Double `BlockEvent.BreakEvent` per block when `fireBreakEvent=true` (fired in the executor and again inside `canBreakAt`) | `chain/execution/BlockHarvestActionExecutor.java:87` + `mixin/early/MixinItemInWorldManager.java:66,107` |
| R20 | low | The new "replaceable cell" water fill destroys non-`BlockBush` plants (vines/reeds) without dropping them (vanilla `func_149813_h` drops the replaced block) | `compat/CoFHWaterBridge.java:62-67,127-130` |
| R21 | low | `LogFounder` comment claims each position is visited "exactly once"; the new-y slab re-walks already-covered y-levels at the new ring (redundant `checkCanAdd` only) | `core/founder/LogFounder.java:44-47,66` |

## 1. Is every ledger FIX item closed in the code? (per-item evidence)

Legend: **OK** = change present and matches the ledger's fix sketch; **PARTIAL** = one half present; **ABSENT** = no code change found; *disclosed* = the report states the deviation; *silent* = the report never mentions the item.

| id | sev | verdict | code evidence (file:line) | report status |
|---|---|---|---|---|
| V01 | high | OK | `chain/lifecycle/ChainLifecycleService.java` (+`core/Manager.canFlushDrops`; flush before clear) | section present |
| V02 | high→med | PARTIAL | throttle cache present: `MinesweeperModeHandler.java:45-59`, `SudokuModeHandler.java:45-59`; mutation-scope half not implemented | disclosed |
| V03a | high | OK | `LogFounder.java:35-78` (shell rewrite + player guard); `Config.clampLogBigRadius` at `Config.java:1079` used at `:624` (server) and `:1253` (client) | section present |
| **V03b** | high→med | **ABSENT** | `ChainWatchdog.java:49-52` (`markChainStarted` still armed at start, called from `BaseOperator.java:336`), `:70-76` (`hasTimedOut` unchanged), `:85-95` (wall-clock fallback still mixed in); `BaseOperator.java:177` still checks timeout before the empty-queue return | **silent — never mentioned** |
| V04 | high | PARTIAL | trigger guard present: `core/Manager.java:154,183,232`; per-position `canMineBlock` in the two mode handlers **absent** (grep for `canMineBlock` returns only `ChainBreakEventHelper.java:106` and the mixin) | report claims the per-position half is covered by V41 (`fix-report.md:234`) — inaccurate |
| V11 | high | OK | 9 fields in `PacketServerConfig.java:148-156` (read) / `:204-212` (write, same order) / `:260-272` (`buildForPlayer`) → `Config.applyServerRuntimeStability` (`Config.java:991`, called at `PacketServerConfig.java:325`); `enableConfigValidation` present in the signature and body (self-found correction verified) | section present |
| **V11b** | high | **ABSENT** | `network/PacketToolBreakHandoff.java:59` still `if (!Config.smartToolSwitchEnabled \|\| !Config.enableToolBreakHandoff) return null;` — file mtime 2026-08-31, i.e. untouched by the pass (fix window 2026-09-29 01:54–02:12) | **section claims the gate was removed** (`fix-report.md:94-103`) |
| V05 | medium | OK | `ChainPositionFounder.java:298` `blockExists` before `:300` `markVisited` (visited-set encapsulation preserved: `BasePositionFounder.java:471-472,484`) | mentioned only in the checklist |
| V06 | medium | OK (alt. impl.) | `Pauseable.java:142-147` `waitUntil` exits only on unpark/interrupt; `consumeBudget` `:70-85` unchanged (two-tier contract intact); `deadlineNanos` now inert | disclosed + captain ruling 3 |
| V07 | medium | OK | `ChunkPreloader.java:51,100-110,132-135` per-shell cursor | not itemised |
| **V08** | medium | **ABSENT** | no `lastSentSize`/`fromIndex` anywhere; `ChainPreCalcEngine` change was the V18 one | **silent** |
| V09 | medium | OK | `XPDropHandler.java:52` `MAX_ORB_VALUE = Short.MAX_VALUE`, split loop at `:183` | not itemised |
| V10 | medium | OK | `ChunkBlockWriteHelper.java:247`; `CoFHWaterBridge.java:58,119,126` | section present |
| V12 | medium | ABSENT | `PacketSaveServerConfig`/`PacketReloadServerConfig` have no `MainThreadEnforcer` (grep: 3 guard sites, none in these) | "deferred", but mislabelled as a ledger DEFER |
| V13 | medium | ABSENT | `MainThreadEnforcer` unchanged; 6 unguarded handlers unchanged | "deferred", mislabelled |
| V14 | medium | OK (partial) | `BlockSwapModeHandler.java:68` `clampBlockSwapRadius`; `Config.clampBlockSwapRadius` `:1094`; harvest guard present | ordering half disclosed |
| **V15** | medium | **ABSENT** | `ChainHarvestExhaustionStrategy.java:27-33` still applies `exhaustionBefore + configuredExhaustion` regardless of `harvested` (the flag is dead); `BaseOperator.java:514` absolute write unchanged | **silent** |
| V17 | medium | OK (partial) | `PacketChainModeSwitch.java:37-40` derived clamps, `:64-67` selectability rejection; `MinerModeState.java:80-108`; `LegacyFounderPlanningFactory.java:41` | latch half disclosed |
| V18 | medium | OK | `ChainPreCalcEngine.java:84` `inProgress \|\| dirty \|\| hasState()`, `:135` | section present |
| V20 | medium | OK | `ParallelTick.clearAllTasks()` (new) + `CommonProxy.serverStarting/serverStopping`; `Pauseable.pause/unPause` `:89-96,103-108` log-and-return | section present |
| **V22** | medium | **ABSENT** | `KeyListener.java:62-65` still returns before the hold-state machine (`:75-108`) whose `:103` is the only release-edge `stopChain()`; file untouched by the pass | **silent** |
| **V23** | medium | **ABSENT** | `KeyListener.java:201` still only checks `Config.blockScrollOnChainKey`; no `currentScreen` guard before the cancel | **silent** |
| V24 | medium | OK | `EZMinerConfigGui.java:667` `updateScrolledPositions()` at the end of `initGui` | section present |
| V24b | medium | OK | `SpaceCalculator.java:129,236` (`lastGeometry`/`indexOffset`), `GradientBlockOutlineRenderer.java:113-118`; `BlockOutlineRenderStrategy` signature unchanged | section present |
| V26 | high | ABSENT | `PacketToolSwapRequest`/`PacketToolSwapFinalize` untouched (mtime 2026-08-31) | disclosed; captain ruling 2 accepts |
| V28 | medium | OK | `Config.clampAddExhaustion` `:1061` used at `:762`, `:1023`, `PacketSaveServerConfig.java:300`; plant clamps `:346-347` = 1..12 / 1..256 | section present |
| V29 | medium | ABSENT | `utils/MessageUtils.java:29-40` still resolves via the overworld list | "deferred", mislabelled |
| V30/V50 | high/med | OK (via V31) | see V31; no `gregtech.common.ores` can remove the runtime gap, which is now documented | sections present |
| V31 | high | OK | 5 files deleted (verified absent by glob: `mixin/` now holds only `early/` 5 classes + `interfaces/`), no reference to any removed symbol (grep → 0), `mixins.EZMiner.json` = 4 common + `client: [MixinGuiIngameMenu]`, `required:false`, no `require` | section present, captain ruling 1 |
| V32 | high | OK | `GT5ToolCompat.java:29,86-93,95-174` (`INIT_LOCK`, publish-after-resolve, brace-balanced); `CommonProxy.preInit` calls `init()` | section present (report flags this as the highest-risk hand-edit — I read the whole file; it is structurally sound) |
| V33 | medium | **ABSENT** | `MixinItemInWorldManager.java:133` still `theWorld.setBlock(x, y, z, Blocks.air, 0, 2)`; `removedByPlayer` appears **nowhere** in `src/` | **silent** |
| V34 | medium | OK | `MixinItemInWorldManager.java:150` `if (removed && !isCreative())`; batch path `BlockHarvestActionExecutor.java:229` | not itemised |
| V35 | medium | OK | `MixinItemInWorldManager.java:77-82` `onBlockStartBreak` replay (see R19 for the TiC double-fire risk) | not itemised |
| **V37** | low | **ABSENT** | `EtFuturumOreCompat.java:27` and `EtFuturumCropCompat.java:41` still set `initialized = true` **before** resolving (`:29-33` / `:43-46`); both files untouched | report says "fixed separately (V37)" (`fix-report.md:166`) — false |
| **V39** | medium | **ABSENT** | `ItemFilterExpression` untouched (no `AnyAtom`); `*` still means "any stack with an OreDictionary entry" | **silent** |
| **V40** | medium | **ABSENT** | `maxFortuneLevel` still has exactly declaration (`Config.java:311`), load (`:920`) and the >100 warning (`ConfigValidator.java:79-83`) — no reader, so the cap is unenforced | **silent** |
| V41 | medium | PARTIAL | mixin paths OK: `ChainBreakEventHelper.canBreakAt` `:104-109` (`canMineBlock` unconditional at `:106`, event conditional at `:81`) reached from `MixinItemInWorldManager.java:66` (called by `execute()` `:93` and `executeWithPreResolved()` `:272`); **batch paths do not call it** → R2 | section absent; deviation note at `:468` |
| V51 | medium | OK | `mixins.EZMiner.json` `client` array (verified by the report's dump and by the file's shape) | section present |
| V53 | low | OK | `PacketCachedBlockSync.java:81` `DecoderException` guard before the loop | section present |
| V58 | low | PARTIAL | `CoFHWaterBridge` javadoc + `CLAUDE.md` corrected; `AGENTS.md:40` **not** corrected for the mechanism (R18); Natura half deferred (disclosed) | section present |
| V59 | low | OK (beyond ledger) | `CoFHWaterBridge.java:76` material-keyed reschedule; ledger marked DEFER — implemented opportunistically and disclosed | disclosed |
| V61 | medium | OK | `CoFHWaterBridge.java:93-98` `isWaterReplaceable` used at `:62` and `:127` | section present |
| V62 | medium | **ABSENT** | `BaseOperator.java:250` gate unchanged (`(getMaxDamage() - getItemDamage()) > 1`); no `GT5ToolCompat.isGTTool` use in `BaseOperator` (grep: only `ToolHarvestEligibility.java:61,108`, `SmartToolSwitchHandler.java:367`) | **silent** |
| V64 | medium | **ABSENT (T4)** | `GT5ToolDurabilityBridge.java:100-102,112` still only `hardness × getToolDamagePerBlockBreak()`; no `getToolDamagePerDropConversion` anywhere (`(?i)dropconversion` → 0 hits) | **silent** |
| V65 | medium | **ABSENT** | `GT5BlockSwapCompat.java:216` still `setInitialValuesMethod.invoke(te, null, (short) itemDamage)` | **silent** |
| V67 | medium | OK | `CommonProxy.java:63` `Loader.isModLoaded("qz_miner")` warning | section present, captain ruling |

**Closure score (of the 35 ledger `FIX` rows):** 21 fully closed, 4 partially closed (V02, V04, V14, V17, V41 — five, with disclosed or undisclosed halves), 13 absent (V03b, V08, V11b, V12, V13, V15, V22, V23, V26, V29, V33, V37, V39, V40, V62, V64, V65 — seventeen rows, of which V12/V13/V26/V29 are *disclosed* deferrals and **V03b, V08, V11b, V15, V22, V23, V33, V37, V39, V40, V62, V64, V65 are not credibly disclosed**).

## 2. Are the implemented changes correct and minimal?

Checked items, with the result:

* **V11 (config parity)** — correct and minimal: `fromBytes`/`toBytes` carry the same nine values in the same order and the same position (after `logFuzzyEnabled`, before the UTF-8 string), `buildForPlayer` fills all nine, and `Config.applyServerRuntimeStability` assigns all nine including `enableConfigValidation` (`Config.java:991-1003`). The constructor was not grown, matching the documented pattern.
* **V31 (mixin gate deletion)** — correct and minimal per the captain's DELETE ruling; verified by glob (`mixin/` = 5 early classes + 1 interface, no `late` json) and by a zero-hit grep for every removed symbol. The removal cannot be compile-verified here (see §5).
* **V32 (GT bridge init)** — correct; the double-checked `init()`/`resolve()` split publishes `initialized` after resolution, killing the half-initialised observation. One stale artefact: the class javadoc at `GT5ToolCompat.java:22-24` still says it "is explicitly initialised from `ClientProxy`" (low; cosmetic).
* **V06 (pause contract)** — the implemented alternative is *the* right call: the ledger's literal suggestion would have ended searches on an ordinary tick-end pause (every founder treats `!consumeBudget()` as "return from `run1`"). The documented pause contract is preserved; `Config.enableBudgetDeadline` is honestly labelled inert in both lang files (`en_US.lang:192`, `zh_CN.lang:192`) with a `docs/todo.md` removal entry (`todo.md:36-40`) — no GUI row was added or removed, so the row-shift invariants are untouched.
* **V41 / V04 (protection gate)** — the `canBreakAt` design is right and matches the captain's split (unconditional `canMineBlock`, conditional event with a `serverutilities` probe). It is **incomplete for the EBS batch paths** (R2) and the per-position half of V04 is missing (R4). The comments asserting that "every path reaches" the gate (`ChunkCachedHarvester.java:143-145`, `BlockHarvestActionExecutor.java:155-157`) are therefore wrong on the code as written.
* **V03a (LogFounder)** — the O(new positions)-per-shell rewrite is present, the player/world guard is present, and the radius is clamped server- and client-side (the ledger's two-part fix, including the captain's client-clamp ruling). Only the "exactly once" comment is inaccurate (R21) and there is redundancy, not incorrectness, because `addResult` → `markVisited` dedups (`BasePositionFounder.java:471-472`).
* **V35 (onBlockStartBreak replay)** — present in the mixin. **Risk I could not settle without a build or a running game**: `getItem().onBlockStartBreak(...)` now runs *in addition to* `TinkersConstructLevelingBridge.fireBeforeBlockBreak(...)`, and `TinkersConstructLevelingBridge`'s own javadoc (`:24-28`) warns that replaying when the item's hook is also invoked doubles XP/autosmelt. TiC's `ToolCore` may itself fire `ActiveToolMod.beforeBlockBreak` from `onBlockStartBreak`; if it does, TiC tools now double-fire the hook. I flag this as an **UNKNOWN worth an in-game test**, not a confirmed defect (the bridge explicitly documents that it mirrors `ToolCore.onBlockStartBreak`'s body rather than calling it).
* **V61/V59 (CoFH water)** — matches the external facts verified in `tmp-world-breakage.md` §3.1: the replaceability predicate mirrors vanilla `func_149809_q`/`func_149807_p`, level 8 + flag 3 matches `func_149813_h` from a source, and the reschedule is material-keyed so it also covers CoFH's `BlockWater extends BlockStaticLiquid` and Forge `BlockFluidClassic`. The one semantic drift this introduced is R20.
* **V05/V07/V09/V10/V17/V18/V20/V24/V24b/V28/V34/V53/V67** — all present, small, and confined to the methods the ledger names. No over-reach seen beyond the two items below.

## 3. Documented invariants (AGENTS.md / CLAUDE.md)

| invariant | status | evidence |
|---|---|---|
| metadata zeroing in `writeAirToEbs` | **preserved** | `ChunkBlockWriteHelper.java:174` (already-air branch) and `:184` (removal branch) |
| `isUnbreakable` predicate placement | **preserved** | still in every gate/executor: `BasePositionFounder.java:461`, `ChainPositionFounder.java:319`, `FuzzyChainPositionFounder.java:52`, `LogFounder.java:142`, `OreFounder.java:35`, `GtVeinOreFounder.java:57`, `InverseBlastFounder.java:55`, `ScreenBlastFounder.java:36`, `CropFounder.java:46`, `BlockHarvestActionExecutor.java:69,140,259`, `ChunkCachedHarvester.java:124` |
| visited-set encapsulation | **preserved** | V05's `blockExists` sits *before* the admission (`ChainPositionFounder.java:298` → `:300`); `markVisited` is still the only write entry point (`BasePositionFounder.java:471-472,484`) |
| `Pauseable` budget/pause contract | **preserved** | `Pauseable.java:70-85` (worker read-only; founder budget+park) and `:142-147` (park until unpark/interrupt) |
| `MainThreadEnforcer` deferral | **unchanged (and that is a finding)** | no new guards; V13/V26 deferred → the 6 unguarded C→S handlers remain (`PacketSaveServerConfig`, `PacketReloadServerConfig`, `PacketMinerConfig`, `PacketRequestClientReload`, `PacketToolSwapRequest`, `PacketToolSwapFinalize`) |
| GUI row-shift guarantees | **preserved** | no row added/removed: the only GUI change is `updateScrolledPositions()` in `initGui` (`EZMinerConfigGui.java:667`) and the label text of an existing row (`lang:192`) |
| no new mixin for core mining | **respected** | no file added under `mixin/`; the only JSON change moves an existing entry into `"client"` (`mixins.EZMiner.json`) |
| server-thread-only world mutation | **preserved for everything implemented**; the *documented* server-thread invariant is still violated by the un-implemented V26 | `ChainBreakEventHelper.canBreakAt` is called from the same server-thread call sites as before; `PacketToolSwapRequest.onMessage` (`:73`) still mutates inventory/ledger on the netty thread |

## 4. Scope compliance (evidence, not trust)

Method: no external programs (git/diff) are available in this sandbox, so I used a recursive file listing with timestamps plus read-tool inspection of the changed files, compared against the files named in `fix-report.md`.

* **Fix window** (the t9 edits, 2026-09-29 01:54–02:12): exactly **45 files** changed, listed in full in §6. Of these, 38 are `.java` under `src/main/java/`, 3 are resources (`mixins.EZMiner.json`, `en_US.lang`, `zh_CN.lang`), and 4 are docs (`AGENTS.md`, `CLAUDE.md`, `docs/todo.md`, and the reports themselves).
* **Nothing under `tmp/` was modified**: the newest `tmp/` file is `tmp/tc-quick2/.../ConfigBlocks.java` at **2026-09-18 01:46**, i.e. 11 days before the pass; no `tmp/` file appears in the window listing.
* **No build script or wrapper was changed**: `gradle/gradle-daemon-jvm.properties` = 2026-03-06, `gradle/wrapper/gradle-wrapper.jar` and `.properties` = 2026-08-25 — all months old. Neither `build.gradle.kts` (2026-08-18) nor `gradle.properties` (2026-04-27) nor `dependencies.gradle` (2026-09-06) is in the window. No `gradle` file is in the window at all.
* **No new files were created** by the pass (the only newly created file in the window is an `.agent-teams` inbox entry, which is team state, not the fix). The 5 reported deletions are genuinely absent: `glob src/main/java/.../mixin/**/*.java` returns only `early/` (5) + `interfaces/` (1), and `glob src/main/resources/*.json` returns only `mixins.EZMiner.json`.
* **No unreported file changed**: every file in the window is either named in `fix-report.md` (in a per-item "Files changed" block or inline) or is the report/ledger/doc the captain scoped. **One reported file did not change**: `network/PacketToolBreakHandoff.java` (V11b) → R1. **One file the report names as changed did not change**: `mixin/early/MixinGTOreAdapter.java` (mtime 2026-07-09) → R17.
* **Limitation I must state**: without `git`/`diff` I cannot prove the *absence of byte-level reformatting* inside the changed files; I can only bound it by reading the edited regions (all hand-formatted consistently with the surrounding style) and by the fact that no unrelated file was touched. Spotless was never run (no build), so **formatting compliance is unverified**.

## 5. Verifiability without a build or a game run — and residual risk

* The code-level facts above **are** verifiable without a build (they are presence/absence and behavioural reads), and that is how this review reached its verdict. The t9 pass is **not** verifiable as *correct-by-construction* from source alone for three classes of change:
  1. **Compile/format risk**: ~38 edited Java files were hand-formatted, `Spotless`/Checkstyle were never run, and the pass itself flags `GT5ToolCompat.resolve()`, `LogFounder` and `SpaceCalculator` as the highest-risk structural edits. I read `GT5ToolCompat` in full (brace-balanced) and the two hot spots around their changed regions; a compiler is still the only authority.
  2. **Behavioural risk under threading/ordering**: V06's inert knob, V02's TTL, V41's per-block `canMineBlock` cost on a 1024-block vein, and the V35 TiC double-fire question all need an in-game check.
  3. **Wire-format risk**: V11 appends 13 values to `PacketServerConfig` (S→C). Both sides ship in one jar, but `acceptableRemoteVersions = "*"` (ledger V52) means a mixed-version pair would mis-decode. This is a pre-existing hazard that V11 makes strictly worse (more bytes to mis-read).
* **Residual risk if no build is ever run**: the deletions in V31 (5 classes) and the packet-field additions in V11 are the two changes most likely to fail a real build in ways a source read cannot see; the un-implemented absences in §1 are *not* build risks — they are simply absent.

## 6. Complete file inventory of the t9 window (mtime-ordered)

`docs/review/agent-teams/fix-ledger.md` (01:52) · `src/.../network/PacketSaveServerConfig.java` (01:54) · `compat/GT5ToolCompat.java` (01:54) · `CommonProxy.java` (01:54) · `thread/ParallelTick.java` (01:54) · `thread/Pauseable.java` (01:55) · `chain/lifecycle/ChainLifecycleService.java` (01:56) · `core/founder/LogFounder.java` (01:57) · `core/founder/ChainPositionFounder.java` (01:57) · `chain/execution/ChunkBlockWriteHelper.java` (01:57) · `compat/CoFHWaterBridge.java` (01:58) · `chain/execution/ChainBreakEventHelper.java` (01:58) · `mixin/early/MixinItemInWorldManager.java` (01:59) · `chain/execution/BlockHarvestActionExecutor.java` (01:59) · `chain/execution/ChunkCachedHarvester.java` (01:59) · `core/Manager.java` (01:59) · `chain/execution/BlockSwapModeHandler.java` (02:00) · `chain/execution/XPDropHandler.java` (02:00) · `chain/execution/ChunkPreloader.java` (02:00) · `client/gui/EZMinerConfigGui.java` (02:00) · `chain/network/PacketCachedBlockSync.java` (02:01) · `chain/execution/MinesweeperModeHandler.java` (02:01) · `chain/execution/SudokuModeHandler.java` (02:01) · `src/main/resources/mixins.EZMiner.json` (02:02) · `mixin/early/MixinBWOreAdapter.java` (02:02) · `mixin/early/MixinGTPPOreAdapter.java` (02:02) · `core/MinerModeState.java` (02:02) · `chain/network/PacketChainModeSwitch.java` (02:03) · `chain/planning/LegacyFounderPlanningFactory.java` (02:03) · `chain/planning/ChainPreCalcEngine.java` (02:03) · `client/render/SpaceCalculator.java` (02:04) · `client/render/GradientBlockOutlineRenderer.java` (02:04) · `CLAUDE.md` (02:05) · `AGENTS.md` (02:05) · `chain/state/ChainClientState.java` (02:05) · `chain/client/preview/ChainPreviewState.java` (02:05) · `chain/client/preview/ChainPreviewController.java` (02:05) · `client/render/MinerRenderer.java` (02:06) · `chain/network/PacketChainStateSync.java` (02:06) · `network/PacketServerConfig.java` (02:06) · `Config.java` (02:10) · `assets/ezminer/lang/en_US.lang` (02:11) · `assets/ezminer/lang/zh_CN.lang` (02:11) · `docs/todo.md` (02:11) · `docs/review/agent-teams/fix-report.md` (02:12).

Files the ledger's file list required but that do **not** appear (consistent with §1's absent rows): `core/BaseOperator.java` (2026-08-31), `chain/watchdog/ChainWatchdog.java`, `chain/execution/ChainHarvestExhaustionStrategy.java`, `client/KeyListener.java`, `utils/ItemFilterExpression.java`, `utils/MessageUtils.java`, `network/PacketToolSwapRequest.java`, `network/PacketToolSwapFinalize.java`, `network/PacketReloadServerConfig.java`, `network/PacketToolBreakHandoff.java`, `compat/GT5ToolDurabilityBridge.java`, `compat/GT5BlockSwapCompat.java`, `compat/EtFuturumOreCompat.java`, `compat/EtFuturumCropCompat.java`, `toolswap/server/ToolSwapServerService.java`.

## 7. Do the DEFER/REJECT decisions hold? (question 6)

* **Honest and acceptable**: V43 (benign predicate mismatch), V48 (lang key exists), V63 (mixin already guards the same path — I re-read `MixinItemInWorldManager.java:109` and agree), V25 (NPE not constructible), V31's rejection of `require = 1` (a hard requirement would turn a benign skip into a startup failure on GT5U 5.09.54.133), and V06's alternative implementation (better than the ledger's suggestion). The two opportunistic implementations (V59, V52-excluded) are disclosed.
* **Acceptable only because the captain ruled**: V26 (high) — the deferral is explicitly recorded as captain-accepted (ruling 2) and it is the only high item left.
* **Not adequately justified**: V12, V13, V29 are ledger `FIX` rows presented under the heading "Deferred by the ledger (unchanged decision)" (`fix-report.md:435`), which is factually wrong — the ledger marked all three `FIX` (`fix-ledger.md:125,126,137`) and no captain ruling is recorded for them (rulings 1–5 cover V31, V67, V41, V03a, V06/`enableBudgetDeadline`, the two self-found defects and the checklist). Either the captain promoted them to DEFER in the amended contract (which the report should say and which I could not verify) or they are unapproved deferrals of medium FIX items → **R15**.
* **Not disclosed at all**: the 9 rows in R3/R5–R14 and the two false claims (R1, R16) — this is the core reason for the verdict.

## 8. What t9 needs to do to close (required fixes)

1. **Either implement or explicitly re-scope** the absent `FIX` rows, with the captain's ruling recorded per row: V03b, V08, V15, V22, V23, V33, V37, V39, V40, V62, V64(T4), V65; and correct the false "implemented" claims for V11b and V04's per-position half.
2. **Close V41's batch-path gap** (R2): call `ChainBreakEventHelper.canBreakAt` (or at minimum `world.canMineBlock`) in `ChunkCachedHarvester.harvestNext` and `BlockHarvestActionExecutor.executeBatch` before the EBS write, or correct the two comments to state that `canMineBlock` is only consulted on the mixin paths.
3. **Fix the documentation drift** (R17/R18): remove the `MixinGTOreAdapter` claim or add the javadoc, and correct `AGENTS.md:40` ("`instanceof BlockDynamicLiquid` only" → material-keyed; scope the "no-op for water" sentence to the flowing replacement).
4. **Retire the double event fire** (R19) and restore the drop for a replaceable plant consumed by the water fill (R20), or document both as accepted.
5. Re-run the pass's own check-list on a machine that can build, and mark `fix-report.md` as a source-read-only claim set — as written it currently misstates its own completeness ("COMPLETE", "None of these is a silent omission").
