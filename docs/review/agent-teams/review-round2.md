# review-round2.md — independent review of the t13 repair round (t9 ledger)

**Reviewer**: `audit-compat-ds` — task **t14** (review round 2), attempt `826dcd52-4a89-46f0-aae9-0b472b638c39`
**Reviewed**: `fix-ledger.md` (t8 contract), `fix-report.md` **§Repair round 2 + STATUS CORRECTION** (t9+t13), `verification-report.md` (t10), `review-round1.md` (my t11), and the code itself.
**Mode**: read-only. No `src/` or `tmp/` file was modified, no code was fixed by me; `docs/review/agent-teams/review-round2.md` is the only file written.
**Verification report exists**: yes — `verification-report.md` (t10, 40.6 KB). It is a dependency of this task and its R1–R7 regression list was cross-checked below.

## 0. VERDICT: **needs_revision** (1 medium, 3 low — every round-1 finding is otherwise closed)

The t13 repair is a genuine, substantial repair: of the 21 findings I raised in round 1 (plus t10's R1–R7), **20 are closed in the code**, both false claims (`V11b`, `V37`) are now true, the "COMPLETE" claim is retracted in a STATUS CORRECTION, the 14 silently-absent rows are implemented or explicitly deferred, and the two high regressions from round 1 / t10 (TiC double-fire + AOE recursion; batch paths skipping protection) are fixed. The remaining medium is a **narrow, pre-existing sub-path of the same protection gate** (the TE-carrier / `removedByPlayer`-override early returns sit *above* the new `canMineBlock` check in all four executors) plus a comment that still claims unconditional coverage — the same "false invariant comment" pattern that mattered in round 1.

| id | severity | finding | file:line |
|---|---|---|---|
| **Q1** | **medium** | The new protection gate is *below* the TE-carrier / `removedByPlayer`-override early returns, so those blocks never get `world.canMineBlock` in any path — while the comment asserts it is "queried UNCONDITIONALLY for every block about to be removed" | `BlockHarvestActionExecutor.java:81,150-165,170-176`; `ChunkCachedHarvester.java:134-151,155-159`; `BlockHarvestActionExecutor.java:280` |
| Q2 | low | `executeWithPreResolved` still pre-fires the `BreakEvent` and hands it to the mixin (which fires its own) → the R19 double-event remains on that one path; the path has **no caller** in `src` | `BlockHarvestActionExecutor.java:288-291` |
| Q3 | low | `damagePerDropConversion` resolves `getToolDamagePerDropConversion` by `getMethod` **per invocation** (per block, twice per gate+guard pair) — an uncached reflective lookup on a per-block hot path in a file whose own design caches method handles | `GT5ToolDurabilityBridge.java:195-203` |
| Q4 | low | `fix-report.md` §C says "Modified in t13 (14)" but the list itself contains 23 modified + 1 new file (24 total, matching the mtimes); two cited line ranges drifted (V23 cites `KeyListener.java:206-211`, the guard is at `:220-223`) | `fix-report.md:590`, `:563` |

Everything else in the repair is verified closed — see §1 (round-1 findings), §2 (t10 findings), §3 (per-ledger-row state), §4 (invariants), §5 (scope), §6 (judgement calls the captain asked for: V08 wire change, V40/V02/V12/V13/V26/V29 deferrals, verifiability + residual risk).

## 1. Round-1 (t11) findings — closure check

| id | sev | state | current-source evidence |
|---|---|---|---|
| R1 (TiC double-fire + AOE recursion, = t10 R1) | high | **CLOSED** | `MixinItemInWorldManager.java:88-93` — the vanilla `onBlockStartBreak` replay is skipped for TiC tools (`&& !TinkersConstructCompat.isTiCTool(startBreakStack)`, predicate exists at `TinkersConstructCompat.java:27` and is pre-existing); `TinkersConstructLevelingBridge` therefore stays the single hook path; comment `:80-86` records why. |
| R2 (V41 batch paths skipped protection) | high | **CLOSED for the batch loops** (residual = Q1) | `BlockHarvestActionExecutor.java:176` and `ChunkCachedHarvester.java:159` now call `world.canMineBlock` before the EBS write; the false "every path reaches the mixin" comments are replaced by accurate ones (`:167-175`, `:153-158`). |
| R3 (V03b watchdog absent/undisclosed) | high | **CLOSED** | `ChainWatchdog.java:62-67,73-78` writers refuse a negative tick; `:93-103` `hasTimedOut` refuses to compare against one; `:123-129` returns `-1` instead of `currentTimeMillis()/50`; `BaseOperator.java:352-353` no longer arms at registry (calls `remove`), `:194` refreshes progress when the planner enqueued work, `:424` on harvest. |
| R4 (V04 per-position half claimed, absent) | medium | **CLOSED** | `BlockSwapModeHandler.java:113-116` (`canMineBlock` before the replacement item is consumed); `PlantingModeHandler.java:104-107` (after the shared plantable predicate). |
| R5 (V22 HUD-config early return) | medium | **CLOSED** | `KeyListener.java:65-68` (defensive release when a screen is open) and `:71-81` (release before `HudConfigGui.open()`). |
| R6 (V23 no GUI gate on scroll) | medium | **CLOSED** | `KeyListener.java:220-223` — `currentScreen != null` returns before the cancel. |
| R7 (V33 no `removedByPlayer` anywhere) | medium | **CLOSED** | new `compat/RemovedByPlayerBridge.java` (identity-keyed `ConcurrentHashMap`, `getMethod` probe, declaring-class test at `:69-72`, failure-safe `:73-76`); used at `MixinItemInWorldManager.java:150-158` (raw write kept for non-overriders, documented why: vanilla's default hook = `setBlockToAir` flag 3) and as the vanilla escape in the two batch executors (`BlockHarvestActionExecutor.java:160-165`, `ChunkCachedHarvester.java:145-151`). Vanilla signature matches (`build/rfg/.../Block.java:1659`, 6 args). |
| R8 (V08 O(n²) pre-calc send) | medium | **CLOSED** | `PacketCachedBlockSync.java:47,58-74,82,97,137` (`startIndex` field, both codecs symmetric with the count guard at `:103-108`); client merge with out-of-order fallback `:151-169`; engine sends only the tail and skips empty ticks `ChainPreCalcEngine.java:318-341`, resetting on `clearState` (`:155`) and on a new pre-calculation (`:195`). |
| R9 (V15 exhaustion) | medium | **CLOSED** | `ChainHarvestExhaustionStrategy.java:40` (`if (!harvested) return false;`) and `:46` (clamp to 4.0F); batched clamps at `BaseOperator.java:531-533,615-617`. |
| R10 (V62 dead GT gate) | medium | **CLOSED** | `BaseOperator.java:255-261` — GT branch calling `GT5ToolDurabilityBridge.hasDurabilityReserveForNextBlock`, with the `MetaBaseItem.setMaxDamage(0)` rationale in the comment; import present, `isGTTool` exists (`GT5ToolCompat.java:183`). |
| R11 (V64 T4) | medium | **CLOSED** | `GT5ToolDurabilityBridge.java:121-124` `worstCaseChargeForOneBlock` = block charge **+** drop-conversion term, used by `checkDurability:142`, `hasReserve:162` and `estimateDamage:173` (one shared helper, so gate and guard agree as the ledger required); conservative fallback `:195-203`. Perf note = Q3. |
| R12 (V65 null NBT) | medium | **CLOSED** | `GT5BlockSwapCompat.java:221,228-229` now takes an `ItemStack` and forwards `stack.getTagCompound()`; call site `BlockSwapModeHandler.java:137-141` passes the held stack. |
| R13 (V39 bare `*`) | medium | **CLOSED** | `ItemFilterExpression.java:216` `ANY_NODE`, `:399` atom `"*"` → `ANY_NODE`; ore-name globs unchanged, so `*` inside an ore-name pattern keeps its old meaning. |
| R14 (V40 cap unenforced) | medium | **DEFERRED, explicitly and honestly** | `Config.java:311` now says "**NOT ENFORCED — dead setting, kept for config-file compatibility**"; reason (a new injection into three version-locked ore mixins cannot be validated without a build; a non-applying injection is the very failure mode) is recorded in `fix-report.md:578`. Judged adequate — see §6. |
| R15 (V12/V13/V29 mislabelled) | medium | **CLOSED (documentation)** | `fix-report.md:579,582` now states these are ledger `FIX` rows deferred **with captain acceptance**, not ledger depletions, and names them. |
| R16 (V37 falsely claimed) | low | **CLOSED** | `EtFuturumOreCompat.java:13-15,38,52` and `EtFuturumCropCompat.java:29-31,48,56` — `INIT_LOCK` + `volatile`, resolution inside the lock, `initialized = true` published last. |
| R17 (MixinGTOreAdapter claim + no consolidated list) | low | **CLOSED** | `fix-report.md:580` retracts the claim; §C is the consolidated list. Residual count/label error = Q4. |
| R18 (`AGENTS.md:40` contradicted the code) | low | **CLOSED** | `AGENTS.md` rewritten in t13 (`mtime 18:02:50`): the "no-op" premise is scoped to the **flowing** block, the reschedule is described as **material-keyed**, and the fill test as vanilla's replaceability predicate — matching `CoFHWaterBridge.java:78-80,95-100`. |
| R19 (double `BreakEvent`) | low | **CLOSED for `execute()`**, residual Q2 | `BlockHarvestActionExecutor.java:88-97` no longer pre-fires and passes `null`; the mixin's single `canBreakAt` fires it. `executeWithPreResolved:288-291` still pre-fires (dead path — no caller in `src`). |
| R20 (water fill dropped nothing) | low | **CLOSED** | `CoFHWaterBridge.java:64,130` call `dropReplaced` (`:147-154`) which mirrors vanilla `func_149813_h`'s drop for a non-air replaced cell, is air-guarded and exception-safe; no double drop with `BushSupportBridge` (that bridge requires the plant to still be present — the fill already replaced it). |
| R21 (`LogFounder` comment + decomposition) | low | **CLOSED** | `LogFounder.java:43-50` states the set identity; `:74-85` decompose shell(r)\shell(r-1) into disjoint y-bands + x columns + z columns with explicit bounds `emit(...,zMin,zMax)` (`:107`). I re-derived the cardinalities by hand: r=1 → 36+36 + (3+3+1+1) = 80 = 3·9·3−1 ✓; r=2 → 100+100 + (45+45+27+27) = 344 = 5·17·5−3·9·3 ✓. Bands/columns are disjoint and `addResult`→`markVisited` still dedups (`BasePositionFounder.java:471-472`). |

## 2. t10 (verification-report) findings — closure check

| t10 id | state | evidence |
|---|---|---|
| R1 TiC double-fire / AOE recursion (high) | **CLOSED** | see R1 above (`MixinItemInWorldManager.java:88-93`). |
| R2 batch paths skipped protection (medium) | **CLOSED for the loops**, residual Q1 for the escape branches | `BlockHarvestActionExecutor.java:176`, `ChunkCachedHarvester.java:159`. |
| R4 V11b unchanged (medium) | **CLOSED** | `PacketToolBreakHandoff.java:59-65` — the client handler now gates only on `Config.smartToolSwitchEnabled`, with a comment explaining that the packet's arrival *is* the server's decision. |
| R3 javadoc-only import in a changed file (predicted build blocker) | **CLOSED** | `CoFHWaterBridge.java` no longer imports `BlockDynamicLiquid`; the javadoc `{@link}` uses the fully-qualified name (import list re-checked). |
| R6 LogFounder (medium) | **CLOSED** | see R21 above. |
| R7 `Pauseable.errorCount`/`deadlineNanos` dead (low) | **DEFERRED, tracked** | `Pauseable.java:30-37` field javadoc now states it is inert; `docs/todo.md` item 9 lists the removal. Acceptable for a low item with a scheduled removal. |
| R5 (V33) | **CLOSED** | see R7 (round-1 numbering) above. |

## 3. Every ledger FIX row now has exactly one honest state

Implemented and verified in code (with the evidence in §1/§2): **V01, V02(throttle), V03a, V03b, V04, V05, V06, V07, V08, V09, V10, V11, V11b, V14, V15, V16(merged), V17, V18, V20, V21(small), V22, V23, V24, V24b, V28, V30/V50/V51(V31 group), V31, V32, V33, V34, V35, V37, V39, V41, V53, V58, V59, V61, V62, V64, V65, V67**.

Explicitly deferred with a stated reason (no longer silent): **V12, V13, V26, V29** (captain-accepted; `V26` high = top residual risk, needs an in-game tool-swap test), **V40** (documented dead setting), **V02's board-scope half** (new config field + full GUI/locale wiring; arguably a product decision), **V36, V38, V42, V43, V45–V49, V52, V54, V55–V57, V60, V66** (ledger-DEFER/REJECT low items unchanged), **`Pauseable` dead members** (tracked in `docs/todo.md`).

The two false claims from t9 are retracted in the STATUS CORRECTION (`fix-report.md:6-20`) and replaced by real changes; the `MixinGTOreAdapter` file claim is retracted at `:580`. **No false claim survives in the repair section** (Q4 is a count/label error, not a claim about behaviour).

## 4. Documented invariants (re-checked after t13)

| invariant | state | evidence |
|---|---|---|
| metadata zeroing in `writeAirToEbs` | preserved (untouched by t13) | `ChunkBlockWriteHelper.java:174,184` |
| `isUnbreakable` placement | preserved and still first | `ChunkCachedHarvester.java:125` then TE branch `:134`; `BlockHarvestActionExecutor.java:70,144`; all `checkCanAdd*` gates unchanged |
| visited-set encapsulation | preserved | `BasePositionFounder.java:471-472,484`; `ChainPositionFounder.java:300` |
| `Pauseable` budget/pause contract | preserved | `Pauseable.java:70-85,142-147` (only the field javadoc changed in t13) |
| `MainThreadEnforcer` deferral | unchanged (V13/V26 deferred) | unchanged files |
| GUI row-shift | preserved | no GUI/lang/resource file touched in t13 (`lang` mtime 02:11, `mixins.EZMiner.json` 02:02, `EZMinerConfigGui` 02:00) → no row, key or JSON change to re-validate |
| no new mixin for core mining | respected | t13 added **no** mixin; `RemovedByPlayerBridge` is a pure-Java compat helper |
| server-thread-only world mutation | preserved for everything implemented | all new `canMineBlock`/`removedByPlayer` calls sit on the existing server-thread paths; V26 remains the outstanding threading item (deferred) |
| new helper is failure-safe | verified | `RemovedByPlayerBridge.java:73-76` treats any reflective failure as "no override" = pre-t13 behaviour |

## 5. Scope compliance (evidence, not trust)

Method: no `git`/`diff` in this sandbox → recursive timestamped listing + read-tool inspection, compared against `fix-report.md` §C.

* **t13 window (2026-09-29 17:57:49–18:04:39)**: exactly **24 files** — 21 modified `src/main/java` files + **1 new** (`compat/RemovedByPlayerBridge.java`) + `AGENTS.md` (18:02:50) + `fix-report.md` (18:04:39). All 24 are named in §C (which is why Q4 is only a count/label slip).
* **Nothing under `tmp/` was modified**: newest `tmp/` file is `2026-09-18 01:46` (`tc-quick2/.../BlockCosmeticOpaque.java`); no `tmp/` entry in the window.
* **No build script or wrapper changed**: `gradle/wrapper/gradle-wrapper.properties` 2026-08-25, `build.gradle.kts` 2026-08-18, `gradle.properties` 2026-04-27 — all untouched, none in the window.
* **No resource/format churn**: `lang/en_US.lang` (02:11), `zh_CN.lang` (02:11), `mixins.EZMiner.json` (02:02), `CLAUDE.md` (02:05), `docs/todo.md` (02:11) are all t9 artefacts, unchanged in t13 — so the only user-visible surfaces touched are Java code + `AGENTS.md` + the report.
* **Both t9 static verify checks still hold**: the deleted-symbol grep returns zero (t13 added no reference back), and `mixins.EZMiner.json` is unchanged since t9 (4 common + `client: [MixinGuiIngameMenu]`, `required:false`, no `require`).
* **Limitation**: I cannot prove byte-level absence of reformatting (no diff tool) and Spotless was never run; I bounded it by reading every t13-edited region — all changes are localised edits consistent with the surrounding style.

## 6. The judgement calls the captain asked for

1. **V08's `startIndex` wire change — acceptable, with one caveat.** `PacketCachedBlockSync` gained one `int` before the count (`:82/:97`), and both codecs are symmetric, so same-jar pairs (GTNH ships EZMiner as one artefact) are correct. For a mixed-version pair the old client would read the new `startIndex` as `count`, hit the new plausibility guard (`:103-108`) and throw a `DecoderException` — a clean client disconnect, not silent corruption. Verified `acceptableRemoteVersions = "*"` (`EZMiner.java:33`), so ledger V52's hazard is indeed slightly wider, but the failure mode is contained and the packet is opt-in (`Config.enableCachedChain` default `false`). **I judge it acceptable for this pass**; it should appear in the changelog/ledger V52 entry.
2. **`RemovedByPlayerBridge` on the hot path — acceptable.** The probe is one `ConcurrentHashMap.get` per block (identity-keyed, populated once per block type), the reflective `getMethod` runs only on a miss (`:64-77`), and any failure degrades to the pre-t13 behaviour (`:73-76`). The semantic choice (override → vanilla hook / vanilla escape) is right and documented, including why the hook is not called for every block (default = flag-3 `setBlockToAir`).
3. **`LogFounder.emit` decomposition — correct.** Hand-verified cardinalities and disjointness (R21); every stop path still returns immediately, `logBlockLimit`/pause/interrupt handling preserved (`:107-118`), and the radius is clamped as required (server `Config.java:624`, client `:1253`).
4. **`ChainPreCalcEngine` delta + GT durability branch — correct** (R8/R10/R11), with the two residuals Q1-adjacent (none) and Q3 (uncached reflection).
5. **Deferrals are honest and named**, not silent: V40 is labelled "NOT ENFORCED — dead setting" in the config itself (`Config.java:311`) with the reason; V02's board-scope half, V12/V13/V26/V29 and the low DEFER batch are each listed with reasons (`fix-report.md:578-584`). V26 remains the top residual risk requiring an in-game tool-swap test, as the captain ruled.
6. **Verifiability / residual risk (stated explicitly).** The closure facts above are source-verifiable and were verified by reading; **no build, formatter or Mixin-apply was run by anyone**, so the following remain build-only: the new wire field's remap/ordering, `RemovedByPlayerBridge`'s reflective probe against real GT/vanilla classes, `LogFounder`'s rewritten loop (compilation and Spotless), the `worstCaseChargeForOneBlock` arithmetic, and the whole-mod Checkstyle pass. Highest residual risk order: (a) V26 tool-swap threading (unimplemented by decision, needs in-game), (b) V40/V02 deferrals, (c) the compile/format pass, (d) Q1.

## 7. Required fixes for the next repair (short)

1. **Q1 (medium)** — hoist `if (!world.canMineBlock(player, x, y, z)) return false;/continue;` above the TE-carrier and `removedByPlayer`-override branches in `BlockHarvestActionExecutor.execute`/`executeBatch`/`executeWithPreResolved` and `ChunkCachedHarvester.harvestNext`, so the gate really precedes every removal path; then either keep the "UNCONDITIONALLY for every block about to be removed" comment (it would then be true) or scope it to the branches it covers.
2. **Q2 (low)** — make `executeWithPreResolved` mirror `execute()` (pass `null`, let the mixin fire once) or delete the dead method.
3. **Q3 (low)** — cache `getToolDamagePerDropConversion` per `toolStats.getClass()` (one `ConcurrentHashMap<Class<?>, Method>` or resolve it in the bridge's init) instead of per block.
4. **Q4 (low)** — correct the §C label/count and the two drifted line citations; add the t13 changed-file list to the report's head so scope can be checked without the mtime sweep.
