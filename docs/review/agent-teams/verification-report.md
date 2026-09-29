# verification-report.md — t10 independent verification of the t9 fix pass

**Author**: `audit-infra-ds` — task `t10`, attempt 1, attempt id `655ecf28-6177-4d14-9925-3a43bb139aee`.
**Kind**: verification. **Contract**: `docs/review/agent-teams/fix-ledger.md` (t8) + the captain's t9 attempt-2 rulings.
**Independence statement**: I did **not** treat `docs/review/agent-teams/fix-report.md` as evidence. I read the
**current source** first (grep + full-file reads), decided each item from the code alone, and only then compared
against the report's claims — which is how `V11b` and `V33` were caught (see §5). Every `file:line` below is from
the tree as it stands now.

**Method**: read-only (`read`/`grep`/`glob` + PowerShell `Select-String`/`Get-Content` file reads). No file outside
`docs/review/agent-teams/` was written; `src/` and `tmp/` were not modified (§7 proves it). No fix was applied.

**Headline**: **24 of 48 ledger `FIX` items are closed, 6 are partially closed, and 18 are not closed** — of which
only 4 (`V12`, `V13`, `V26`, `V29`) are the deferrals t9 declared and the captain accepted. **14 ledger `FIX` items
are neither implemented nor listed as deferred anywhere in `fix-report.md`** (`V03b`, `V08`, `V11b`, `V15`, `V16`,
`V22`, `V23`, `V33`, `V37`, `V39`, `V40`, `V62`, `V64`, `V65`), the report's "Status: COMPLETE" notwithstanding.
One **new high-severity behavioural regression** was introduced by `V35`'s implementation (`R1`, §6).

---

## 1. Verdict summary

| verdict | count | ids |
|---|---|---|
| **closed** | 24 | V01, V04, V05, V06*, V07, V09, V10, V11, V14, V17, V18, V20, V21(small), V24, V24b, V28, V31, V32, V34, V51, V53, V59, V61, V67 |
| **partially closed** | 6 | V02, V03a, V30*, V35, V41, V58 |
| **not closed — declared deferral (captain-accepted)** | 4 | V12, V13, V26, V29 |
| **not closed — undeclared omission** | 14 | V03b, V08, V11b, V15, V16, V22, V23, V33, V37, V39, V40, V62, V64, V65 |
| **total ledger `FIX` items re-checked** | **48** | |

`*` `V06` is closed **as the captain's ruled variant** (the ledger's own sketch was shown non-implementable and the
knob is now labelled inert); `V30` is closed **as ruled** (the captain replaced "add `require = 1` + `@Pseudo`" with
"document the generation scope"); neither is a defect, but neither matches the ledger text.

**Report-claim integrity**: `fix-report.md`'s per-item claims are accurate for every item it has a section for,
**except `V11b`**, which it claims to have changed and did not (§5, R4). Its "COMPLETE" status is not accurate: 14
`FIX` items have no section, no deferred-table row and no re-decided row.

---

## 2. Build / test log (verbatim)

**No build could be run in this session.** Three distinct, reproducible obstacles — not a single spawn denial, which
corrects the diagnosis recorded in `fix-report.md` §Build attempt log:

| # | command | exit | output (verbatim tail) |
|---|---|---|---|
| 1 | `.\gradlew.bat build --offline` | `1` | `Exception in thread "main" java.io.FileNotFoundException: D:\gradle_cache\wrapper\dists\gradle-9.4.0-bin\lcvyxq3t37f6mx9miaydrrgs\gradle-9.4.0-bin.zip.lck (拒绝访问。)` … `at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:2)` |
| 2 | `& 'C:\Users\37593\.gradle\wrapper\dists\gradle-9.6.0-bin\42k10rwplmzkhuboz9kdazi7s\gradle-9.6.0\bin\gradle.bat' build --offline` | (no code) | `ResourceUnavailable: 程序'gradle.bat'运行失败： 拒绝访问。在 行:1 字符:129` |
| 3 | write probe `D:\gradle_cache\__dsh_probe.tmp` | — | `DENIED: Access to the path 'D:\gradle_cache\__dsh_probe.tmp' is denied.` |

Root causes, all verified:

1. **The wrapper did spawn** (contrary to the t9 record's `ResourceUnavailable: 程序'gradlew.bat'运行失败`): `java`
   ran and the failure is inside the wrapper. `gradle/wrapper/gradle-wrapper.properties` pins
   `distributionUrl=…gradle-9.4.0-bin.zip`, which is **not installed** in `GRADLE_USER_HOME`, so the wrapper tried to
   download/extract it and could not create its lock file.
2. **`GRADLE_USER_HOME=D:\gradle_cache` is outside the writable workspace** and is read-only to this sandbox (probe
   3). Every Gradle invocation needs to write locks/daemon/caches there, so **no Gradle task can run regardless of
   distribution** — including the cached 9.6.0 one, which additionally lies outside the workspace and is denied at
   spawn (attempt 2).
3. `services.gradle.org` is unreachable, so the 9.4.0 download could not succeed even with write access.

**Escalation not attempted.** The t10 prompt allowed one `sandbox_permissions=danger-full-access` retry, but this
session states approval prompts are disabled and rejects escalations automatically; the contract objective also
records that the user rejected that exact escalation for t9. I therefore did **not** set `sandbox_permissions` and
did **not** re-run the denied spawn a third way. **No build or test was run, and none is claimed.**

**Strongest available substitute** (§7): 41 changed files swept for unused imports, all changed symbols and call sites
cross-checked, all deleted symbols grepped for dangling references, config/packet/lang/GUI wiring checked field by
field. One likely `spotlessCheck`/Checkstyle failure was found this way (`R3`).

---

## 3. Item-by-item verification

`evidence` = what I read in the current tree; `claim` = what the ledger's `minimal fix` required.

| ledger id | ledger claim (abridged) | verdict | independent code evidence | note |
|---|---|---|---|---|
| **V01** | flush drops/XP before clearing on stopRuntime; do not clear on world unload | **closed** | `chain/lifecycle/ChainLifecycleService.java:83-94` (`if (!cleanupRuntime) return; if (mgr.canFlushDrops()) { mgr.flushDrops(); } mgr.cleanupState(); mgr.clearDrops();`), `:51` `stopRuntime(mgr, false)` in `onWorldUnload`, `core/Manager.java:364` `canFlushDrops()` | matches the ledger exactly; flush precedes both clearing steps |
| **V02** | (1) TTL-cache `isAnyGameActive` (2) bound boards by distance (3) optional per-mode switch | **partial** | (1) `MinesweeperModeHandler.java:43,52-56,68` + `SudokuModeHandler.java:45,54-56,68` (`GAME_ACTIVE_TTL_MS = 500L`); (2)/(3) absent — no proximity filter, no `Config.specialMode*` field (`grep specialMode` in `Config.java` → none) | t9 declares (2)/(3) deferred with a product-decision reason; the ledger's mutation-scope concern is **not** closed |
| **V03a** | hoist the skip test (O(new positions)) **and** clamp `logBigRadius`/`highRadius` **and** add the player guard | **partial** | clamp ✓ `Config.java:1079` (`clampLogBigRadius`, used at `:624` server, `:1253` client, both defaults 64); guard ✓ `core/founder/LogFounder.java:48`; enumeration ✗ see R6 — `LogFounder.java:66` emits `emit(-curRadius, curRadius, -highRadius, highRadius, MODE_FULL_Z)` = the **whole box of shell r**, and `MODE_INNER_Z` derives z from x (`:104-105`), so the two "boundary slab" emits are strict subsets of it | outcome (mode usable) achieved by the clamp alone; the algorithmic half and its comment are wrong (R6) |
| **V03b** | arm the watchdog on first harvest; `-1` sentinel for an unavailable server; refresh from the planner | **not closed** | `core/BaseOperator.java:336` still `ChainWatchdog.markChainStarted(manager.playerUUID)` at registry; `chain/watchdog/ChainWatchdog.java:72` `Long last = LAST_PROGRESS_TICK.get(...)` with no sentinel branch; no `recordProgress` call from the planning path | **undeclared omission** (no section, no deferred row) |
| **V04** | `if (event.isCanceled()) return;` first in all three handlers; keep `receiveCanceled` | **closed** | `core/Manager.java:154`, `:183`, `:232`; `receiveCanceled = true` still present at `:151` etc.; comment at `:147-153` explains the discriminator | verified statement order: the guard is the first statement of each body |
| **V05** | move the loaded-chunk test onto the collecting thread before `markVisited` | **closed** | `core/founder/ChainPositionFounder.java:298` `if (!player.worldObj.blockExists(cx, cy, cz)) continue;` immediately above `:300` `if (!markVisited(encodePos(cx, cy, cz))) continue;` | admission still only via `markVisited` (encapsulation intact) |
| **V06** | make the deadline path report "stop" | **closed (ruled variant)** | `thread/Pauseable.java:80-84` — `consumeBudget()` is back to the original body (no `paused` read); `:87-112` `pause`/`unPause` now debug-return when `stopped`; `:135-137` `setDeadlineNanos` retained; `deadlineNanos` is written at `:137` and read nowhere (grep: 3 hits, all in this file) | t9 substituted "deadline is inert" for the ledger sketch and documented why; captain ruling 3 accepted it; labels verified in **both** locales (`en_US.lang:192`, `zh_CN.lang:192`) and `docs/todo.md` exists |
| **V07** | persist a per-shell cell cursor in `ChunkPreloader` | **closed** | `chain/execution/ChunkPreloader.java:51` `shellCursor`, `:107-110` resume, `:132-135` advance only when exhausted, `:146` reset | matches the ledger |
| **V08** | append-only deltas (`lastSentSize` + `fromIndex`) | **not closed** | grep `lastSentSize|fromIndex` over `PacketCachedBlockSync.java` → **no hits**; the file's only change is the V53 count guard (`:80-84`) | **undeclared omission** |
| **V09** | split merged XP into ≤-representable orbs | **closed** | `chain/execution/XPDropHandler.java:182-186` `while (total > 0) { chunk = min(total, MAX_ORB_VALUE); … }`, constant declared **once** at `:52` | also confirms the self-found "duplicate declaration" defect is absent |
| **V10** | `blockExists` guard before the unguarded neighbour reads | **closed** | `chain/execution/ChunkBlockWriteHelper.java:247`; `compat/CoFHWaterBridge.java:58`, `:76` (and the sweep at `:119`, `:126`) | guard precedes every previously unguarded `world.getBlock` in those methods |
| **V11** | add the 9 fields to `PacketServerConfig` (fields + both codecs + `buildForPlayer`) and a new apply method | **closed** | fields `network/PacketServerConfig.java:73-81`; `fromBytes` `:148-156`; `toBytes` `:204-212`; `buildForPlayer` `:265-273`; call `:325-334`; `Config.applyServerRuntimeStability` `Config.java:991-1004` assigns **all 9** | codec order is byte-for-byte identical (bool,bool,bool,bool,bool,bool,int,bool,int) and both sit between `logFuzzyEnabled` and the `blacklistExpression` UTF8 string (`:157` / `:213`) → no wire asymmetry |
| **V11b** | remove the client's second gate on `enableToolBreakHandoff` | **not closed** | `network/PacketToolBreakHandoff.java:59` still reads `if (!Config.smartToolSwitchEnabled \|\| !Config.enableToolBreakHandoff) return null;` | **t9 claims this was changed — it was not** (R4). The split-brain the ledger described is still live |
| **V12** | guard the OP config handlers | **not closed (declared)** | `network/PacketSaveServerConfig.java` / `PacketReloadServerConfig.java` — grep `MainThreadEnforcer` → no hits | declared deferral, captain-accepted |
| **V13** | `MainThreadEnforcer` liveness + the 6 unguarded C→S handlers | **not closed (declared)** | still exactly 3 `MainThreadEnforcer.` references in `src` (`chain/network/PacketKeyState.java:39`, `PacketChainModeSwitch.java:60`, `core/PlayerManager.java:95`) | declared deferral |
| **V14** | guard the swap `harvestBlock` with `canHarvestBlock`; clamp the scan radius | **closed** | `chain/execution/BlockSwapModeHandler.java:124` `if (oldBlock.canHarvestBlock(player, oldMeta) \|\| WitcheryVampireBridge.canHarvestWithBareHands(player))`, import `:22`; `Config.clampBlockSwapRadius` `Config.java:1094`, used `:604` | the ordering half stays deferred (declared deviation) |
| **V15** | (a) skip exhaustion when nothing was harvested (b) additive/exact exhaustion | **not closed** | `chain/execution/ChainHarvestExhaustionStrategy.java:27-29` — `boolean harvested = …; FOOD_EXHAUSTION_LEVEL.setFloat(food, exhaustionBefore + configuredExhaustion); return harvested;` (no `if (harvested)`); `core/BaseOperator.java` untouched (mtime) | **undeclared omission** |
| **V16** | see V15 | **not closed** | same evidence as V15 | **undeclared omission** |
| **V17** | derive the clamps from `MinerModeState`; reject unselectable modes | **closed** | `core/MinerModeState.java:79-82` (`MAX_*_INDEX = *ARRAY.length - 1`), `:89`, `:97-104`; `chain/network/PacketChainModeSwitch.java:37-40` clamps, `:64-66` rejects with `return` inside the lambda | lambda is a `Runnable` → bare `return` is valid |
| **V18** | only `stop()` when there is state | **closed** | `chain/planning/ChainPreCalcEngine.java:84` `if (inProgress \|\| dirty \|\| hasState())`, `:135` `hasState()` | — |
| **V20** | clear stale founders on both server boundaries; no-op `pause`/`unPause` when stopped | **closed** | `thread/ParallelTick.java:87-93` `clearAllTasks()` (takes `normalTaskLock`); `CommonProxy.java:86`, `:104`; `Pauseable.java:89-96`, `:103-108` | `errorCount` is now never incremented → dead field (R7) |
| **V21** (small) | remove the write-only fields/class | **closed** | grep `setTarget(`, `previewState.target`, `chainClientState.previewRenderedCount`, `chainClientState.sessionDimension`, `ChainRequest` over `src/main/java` → **no code hits**; the only textual mentions are the new explanatory javadoc (`chain/state/ChainClientState.java:9-13`) | no dangling references |
| **V22** | do not early-return past the hold-state machine | **not closed** | grep `currentScreen` over `client/KeyListener.java` → **no hits**; the file is not in the 24 h change set | **undeclared omission** |
| **V23** | GUI-gate the scroll suppression | **not closed** | same as V22 | **undeclared omission** |
| **V24** | call `updateScrolledPositions()` at the end of `initGui` | **closed** | `client/gui/EZMinerConfigGui.java:655-658` create the fields, `:660-661` recalc/visibility, `:667` `updateScrolledPositions();`, `:668` closes `initGui` | it is the last statement of the method |
| **V24b** | per-block index ranges for the gradient renderer | **closed** | `client/render/SpaceCalculator.java:126-130` (`ThreadLocal` + `lastGeometry()`), `:150-151`, `:172-173`, `:194-195`, `:200-204`, `:211-248` (4-arg ctor, `indexOffset`/`indexCount` with `*24` fallback); `GradientBlockOutlineRenderer.java:113-134` merges exact contiguous ranges | only one `new VertexAndIndex` call site (`SpaceCalculator.java:200`, 4-arg) → no arity mismatch; `MinerRenderer` only reads the fields |
| **V26** | wrap both tool-swap handlers in `guardedNull`; `ConcurrentHashMap` ledger | **not closed (declared)** | grep `MainThreadEnforcer` over `network/PacketToolSwapRequest.java` and `PacketToolSwapFinalize.java` → **no hits** | declared, captain-accepted top residual risk; requires an in-game tool-swap test |
| **V28** | validate `addExhaustion`; align the plant clamps | **closed** | `Config.java:762` load through `clampAddExhaustion`, `:1023` apply path, `:1032-1033` plant clamps `1..12`/`1..256`; `network/PacketSaveServerConfig.java:300` `Config.addExhaustion = Config.clampAddExhaustion(msg.addExhaustion);`, `:346-347` the same clamps | three paths now agree (load / apply / save) |
| **V29** | resolve messages via the server player list | **not closed (declared)** | `utils/MessageUtils.java` untouched (mtime) | declared deferral |
| **V30** | `require = 1` + `@Pseudo`/string targets | **closed (as ruled)** | `MixinGTOreAdapter.java:16` `@Mixin(value = GTOreAdapter.class, remap = false)` (no `@Pseudo`, no `targets =`), no `require` in any of the three mixins (grep `require` → javadoc/comment hits only); generation scope documented at `MixinGTPPOreAdapter.java:14-26`, `MixinBWOreAdapter.java:25` | captain ruling replaced the ledger's prescription; the residual runtime risk (GT5U shape drift = silent no-op) is unchanged and is now documented |
| **V31** | delete the dead gate (option b) | **closed** | §7.2: all five symbols grep to **0** under `src/main/java`, the five files/configs are gone, `Config` load/save blocks removed | see §7.2 for the mechanical proof |
| **V32** | call `GT5ToolCompat.init()` from `CommonProxy`; make init race-safe | **closed** | `CommonProxy.java:56-58` (`if (Mods.GregTech.isModLoaded()) GT5ToolCompat.init();`, imports `:8`, `:21` used); `compat/GT5ToolCompat.java:31` `private static volatile boolean initialized`, `:86-93` double-checked lock, `:95` `resolve()` | publish-after-resolve is sound (monitor release publishes `initialized` after `resolve()`) |
| **V33** | call `block.removedByPlayer(...)` in the fast path | **not closed** | grep `removedByPlayer` over `src/main/java` → **no hits**; `mixin/early/MixinItemInWorldManager.java:133` still `theWorld.setBlock(x, y, z, Blocks.air, 0, 2)` | **undeclared omission**; the GT powder-barrel / non-TE `removedByPlayer` loss (compat-audit C4) is untouched |
| **V34** | `if (removed && !isCreative())` around the XP block | **closed** | `MixinItemInWorldManager.java:150` (+ comment `:147-149`); `chain/execution/BlockHarvestActionExecutor.java:229` uses the `isCreative` local from `:121` | — |
| **V35** | call `Item.onBlockStartBreak` first and honour `true`; keep the bridges; cover the batch paths | **partial + regression** | fast path ✓ `MixinItemInWorldManager.java:77-82` (`return true` on a true hook) — but it now runs **in addition to** the TiC bridge at `:90-96` (R1); batch paths ✗ `BlockHarvestActionExecutor.java:~179+`, `ChunkCachedHarvester.java:146-160` still only replay `TinkersConstructLevelingBridge` (grep `onBlockStartBreak` there → comments only) | R1 is the highest-value finding of this report |
| **V37** | publish `initialized` after resolving in both EFR bridges | **not closed** | `compat/EtFuturumOreCompat.java:27` `initialized = true;` then `:29-35` resolution; `compat/EtFuturumCropCompat.java:41` then `:43-46`; both files untouched (mtime) | **undeclared omission** (the same defect t9 fixed in `GT5ToolCompat` — fixed there, missed here) |
| **V39** | make a bare `*` match any stack | **not closed** | grep `AnyAtom|anyAtom` over `utils/ItemFilterExpression.java` → **no hits**; file untouched | **undeclared omission** |
| **V40** | enforce `maxFortuneLevel` (or remove the field) | **not closed** | `Config.java:311` field, `:920-921` load; `config/ConfigValidator.java:79-83` warns; grep `clampFortuneLevel` → **no hits**; `utils/FortuneCompatHelper.java` untouched and still reads only the two booleans | **undeclared omission** (the unbounded-uncap hazard stands) |
| **V41** | unconditional `canMineBlock` for every removed block in **all three** paths; event only when config/protection | **partial** | path 1 ✓ `MixinItemInWorldManager.java:66` → `ChainBreakEventHelper.canBreakAt` (`:104-108`: `canMineBlock` + conditional event); policy ✓ `:38`, `:79-83`, `:107`; paths 2/3 ✗ `BlockHarvestActionExecutor.java:158-160` and `ChunkCachedHarvester.java:146` call only `fireIfEnabledOrProtected`, and neither writes through the mixin | the comment at `BlockHarvestActionExecutor.java:155-157` asserts the opposite (R2) |
| **V51** | move `MixinGuiIngameMenu` to the `client` array | **closed** | `src/main/resources/mixins.EZMiner.json:11-19` — `mixins` = 4 environment-neutral entries, `"client": ["MixinGuiIngameMenu"]`, `"server": []`, `"required": false` at `:2` | — |
| **V53** | reject an over-large `count` in `fromBytes` | **closed** | `chain/network/PacketCachedBlockSync.java:20` import, `:80-84` `count < 0 \|\| count > buf.readableBytes() / 12` → `DecoderException` before allocation/loop | `DecoderException` is unchecked → no signature change needed |
| **V58** | compat documentation corrections | **partial** | ✓ `CoFHWaterBridge.java:13-30` (hierarchy corrected), `AGENTS.md`, `CLAUDE.md` (both re-written, verified in the fresh instruction text); ✗ `compat/NaturaSaguaroCompat.java` javadoc unchanged (mtime) | the Natura half is a declared deferral |
| **V59** | reschedule by material, not `instanceof BlockDynamicLiquid` | **closed** | `CoFHWaterBridge.java:70-79` (`neighbour.getMaterial() == Material.water && world.blockExists(...) && world.getBlock(...) == neighbour`) | class javadoc updated too |
| **V61** | replace `below == Blocks.air` with the vanilla replaceability test | **closed** | `CoFHWaterBridge.java:93-98` `isWaterReplaceable` (not water/lava && `!material.blocksMovement()`), used at `:62` and `:127` | mirrors `func_149809_q`/`func_149807_p` |
| **V62** | give `canOperate()` a GT branch computing real GT durability | **not closed** | `core/BaseOperator.java:240-260` — the TiC branch is at `:247-248`, the GT toolbox branches at `:310`/`:316`, **no GT tool durability branch**; `BaseOperator.java` is not in the change set | **undeclared omission** (the GT gate stays dead) |
| **V64** | add GT's second damage charge to the bridge estimate | **not closed** | grep `getToolDamagePerDropConversion` over `src/main/java` → **no hits**; `compat/GT5ToolDurabilityBridge.java` untouched | **undeclared omission** |
| **V65** | pass the replacement NBT into `initGTMetaTileEntity` | **not closed** | `compat/GT5BlockSwapCompat.java:210` still `initGTMetaTileEntity(World, int, int, int, int)`, called from `chain/execution/BlockSwapModeHandler.java:133` with `replacementItemDamage` only | **undeclared omission** |
| **V67** | start-up collision warning, no behaviour change | **closed** | `CommonProxy.java:63-69` (`Loader.isModLoaded("qz_miner")` → one `LOG.warn` naming `~`) | `Loader` import used (`:14`); default key unchanged |

---

## 4. t9 acceptance criteria, re-checked mechanically

| # | t9 criterion | result | evidence |
|---|---|---|---|
| 1 | Two runnable static checks: grep proving the V31 deletion complete; dump of `mixins.EZMiner.json` | **pass** | §7.1 count `0`; §7.2 JSON dump — `"required": false`, four common + `client: [MixinGuiIngameMenu]`, no `require`, no `plugin` |
| 2 | `tmp/` never modified | **pass** | `Get-ChildItem -Recurse -File tmp \| Where LastWriteTime -gt (Get-Date).AddDays(-2)` → **0** |
| 3 | No build claim in either direction | **pass (at t9 time); now superseded by facts** | t9 correctly refused to claim a build. I additionally established *why* no build can run (§2) and that the t9 denial marker was at the wrapper, not the spawn |
| 4 | Hand-format to Google-Java-Format | **not verifiable here — one probable failure** | no formatter available; the static sweep found `CoFHWaterBridge.java:4` as a javadoc-only import → `spotlessCheck` (part of `build`) will fail unless `spotlessApply` runs first, and Checkstyle `UnusedImports` may fail even after (R3) |
| 5 | Preserve the documented invariants | **pass for the ones I probed; one false comment** | metadata zeroing `ChunkBlockWriteHelper` unchanged (not in change set); `isUnbreakable` present in `BlockHarvestActionExecutor.java:69,140` and `executeWithPreResolved`; visited set still only via `markVisited` (`ChainPositionFounder.java:300`); `func_150818_a`/`getBlockByExtId` untouched; pause contract strengthened (`Pauseable.java:82-84`); **but** the V41 invariant comment is false (R2) |
| 6 | Self-found defects recorded with file:line | **pass** | `enableConfigValidation` is in `Config.applyServerRuntimeStability` (`:993` param, `:999` assignment) and passed at `PacketServerConfig.java:330`; exactly one `MAX_ORB_VALUE` (`XPDropHandler.java:52`) |
| 7 | Deleted classes unreferenced | **pass** | §7.1 (`0` hits) and §7.3 (no `EZMiner.late`, no `enableMixinCapabilityGates`, no `ChainRequest`, no `setTarget(` anywhere) |
| 8 | 9 synced fields wired end-to-end | **pass** | §7.4: each name present in `Config` (declaration + load + save) and in `PacketServerConfig` (field + `fromBytes` + `toBytes` + `buildForPlayer` + call); the deleted `enableMixinCapabilityGates` is absent from all of them |
| 9 | Both lang files updated for the new labels / no locale drift | **pass** | `en_US.lang:192` and `zh_CN.lang:192` carry the INERT/DEPRECATED label; key sets are **197 = 197 with zero asymmetry**; no lang key mentions mixin/capability |
| 10 | GUI row-shift invariants untouched | **pass** | no config field with a GUI row was added or removed: `MAX_CONTENT_ROWS = 23` / `SERVER_CONTENT_ROWS = 51` (`EZMinerConfigGui.java:104,106`) unchanged, and the deleted knob had no row (grep `enableMixinCapabilityGates` → 0 in both `src/main/java` and both lang files) |

---

## 5. Report-claim integrity (independent of the code verdicts)

| report claim | reality | severity |
|---|---|---|
| "V11b — `if (!Config.smartToolSwitchEnabled \|\| !Config.enableToolBreakHandoff) return null;` → `if (!Config.smartToolSwitchEnabled) return null;`" | **Not in the tree.** `PacketToolBreakHandoff.java:59` is unchanged. | medium — a claimed high-severity sync fix does not exist |
| "Status: COMPLETE" | 14 ledger `FIX` items are neither implemented nor declared deferred (`V03b`, `V08`, `V15`, `V16`, `V22`, `V23`, `V33`, `V37`, `V39`, `V40`, `V62`, `V64`, `V65`, plus `V11b` above). | medium — the completeness claim is wrong |
| "Build attempt 1: `.\gradlew.bat …` **DENIED at spawn** (`程序'gradlew.bat'运行失败`)" | The wrapper **did** spawn here; the failure was an read-only `GRADLE_USER_HOME` lock file (§2). | low — diagnosis only, but it matters for the user's local retry (a writable `GRADLE_USER_HOME` may let the build run) |
| "V24b … the 2-arg constructor is retained for any other caller" | Confirmed (only `SpaceCalculator.java:200` constructs it) and the fallback `*24` path still works (`:236-248`). | accurate |
| "V31 … no `require` added anywhere" | Confirmed. | accurate |
| "V01 … `clearDrops()` is kept after the flush" | Confirmed (`ChainLifecycleService.java:93-94`). | accurate |

---

## 6. Regression list (each with a minimal required fix)

### R1 — **high** — V35's new `Item.onBlockStartBreak` call double-fires and re-enables TiC AOE recursion
`src/main/java/com/czqwq/EZMiner/mixin/early/MixinItemInWorldManager.java:77-82` now calls
`startBreakStack.getItem().onBlockStartBreak(startBreakStack, x, y, z, thisPlayerMP)` and returns `true` on a true
hook. For Tinkers' Construct tools this is harmful:
* `tconstruct.library.tools.HarvestTool.onBlockStartBreak` (`tmp/TinkersConstruct-master/.../HarvestTool.java:33-36`)
  → `ToolCore.onBlockStartBreak` (`.../ToolCore.java:473-482`) **loops `TConstructRegistry.activeModifiers` and calls
  `beforeBlockBreak`** — the very list that `TinkersConstructLevelingBridge.fireBeforeBlockBreak`
  (`compat/TinkersConstructLevelingBridge.java:120-132`) replays 8 lines later at the mixin's `:90-96`. Result:
  every chained block fires IguanaTweaks' XP/autosmelt/lapis hooks **twice** (double tool XP on chain mining).
* `tconstruct.items.tools.LumberAxe.onBlockStartBreak` (`tmp/TinkersConstruct-master/.../LumberAxe.java:96-117`)
  spawns `new TreeChopTask(this, stack, new ChunkPosition(x, y, z), player, 128)`, registers it on the FML tick bus
  and returns `true`; `Scythe.java:138` and `AOEHarvestTool.java:28` are the same shape. EZMiner's mixin now invokes
  that **once per chained block**, i.e. the AOE recursion `TinkersConstructLevelingBridge.java:24-28` was written to
  avoid is back, at chain scale.
**Minimal required fix**: guard the new vanilla replay so it does not run for TiC tools — e.g.
```java
if (startBreakStack != null && !TinkersConstructCompat.isTiCTool(startBreakStack)
    && startBreakStack.getItem() != null
    && startBreakStack.getItem().onBlockStartBreak(startBreakStack, x, y, z, thisPlayerMP)) {
    return true;
}
```
(or `!(stack.getItem() instanceof tconstruct.library.tools.ToolCore)`, which is class-load-safe behind the existing
`Loader.isModLoaded("TConstruct")` guard). Then the TiC bridge stays the single hook path and GT/other items keep the
newly restored vanilla behaviour. **Also** decide the batch-path half of V35 (`BlockHarvestActionExecutor` /
`ChunkCachedHarvester`) so all three paths behave alike.

### R2 — **medium** — the V41 protection gate does not cover the batch paths, and the comment says it does
`src/main/java/com/czqwq/EZMiner/chain/execution/BlockHarvestActionExecutor.java:155-157`:
> "The unconditional World.canMineBlock half of the protection gate lives in the fast-path mixin, which every path reaches before mutating anything."

That is false: `executeBatch` (`:153-160`) reaches `ChunkBlockWriteHelper.writeAirToEbs` (`:~206`) without ever
calling the mixin, and `chain/execution/ChunkCachedHarvester.java:146` has the same shape. So with
`useChunkCachedHarvest=true` the captain's V41 ruling ("unconditional `canMineBlock` for every removed block in all
three paths") is not satisfied and the ServerUtilities/protection coverage is still absent on those paths.
**Minimal required fix**: in both loops, before mutating, `if (!ChainBreakEventHelper.canBreakAt(world, player, x, y, z)) continue;`
(or at minimum `if (!world.canMineBlock(player, x, y, z)) continue;` if the event cost is unwanted there), and correct
the comment.

### R3 — **low (likely build blocker)** — javadoc-only import in a changed file
`src/main/java/com/czqwq/EZMiner/compat/CoFHWaterBridge.java:4` `import net.minecraft.block.BlockDynamicLiquid;` — the
name appears only inside javadoc `{@link}` text (`:16`, `:23`) and comments (`:64`, `:71`, `:72`, `:83`); no code
line uses it. javac accepts it, but `spotlessCheck` (part of `build`) fails until `spotlessApply` silently removes
it, and Checkstyle's `UnusedImports` (if the GTNH convention enables it) fails even afterwards.
**Minimal required fix**: delete the import and reference the type fully qualified in the javadoc
(`{@link net.minecraft.block.BlockDynamicLiquid}`).

### R4 — **medium** — `V11b` claimed but absent
See §3 (`PacketToolBreakHandoff.java:59`). **Minimal required fix**: either apply the one-line change the report
describes, or correct the report; the underlying split-brain (server sends on its `Config`, client discards on its
own) stays until then. Note `V11` now syncs the value, so the window is only pre-first-sync — but the report's claim
is still false.

### R5 — **medium** — `V33` silently dropped
No `removedByPlayer` anywhere in `src/main/java`; `MixinItemInWorldManager.java:133` still does the raw
`setBlock(..., air, 0, 2)`. **Minimal required fix**: as the ledger says (fast path only), call
`block.removedByPlayer(theWorld, thisPlayerMP, x, y, z, canHarvest)` and `markBlockForUpdate` instead of the raw
write — or record it as an explicit deferral with the reason (compat-audit C4: GT `BlockReinforced` meta 5 powder
barrel and any other non-TE override are skipped).

### R6 — **medium** — `LogFounder`'s enumeration does not implement (and its comment claims) the O(new) shell walk
`src/main/java/com/czqwq/EZMiner/core/founder/LogFounder.java:66` emits the **full box** of the current shell
(`emit(-curRadius, curRadius, -highRadius, highRadius, MODE_FULL_Z)`), so the two "boundary slab" emits at `:70-72`
are strict subsets of it, and `MODE_INNER_Z` (`:104-105`) derives `zMin/zMax` from the x range (`xMin+1 … xMax-1`),
which yields an interior rectangle rather than the intended z-boundary columns. Consequences: (a) the per-shell cost
is the whole box, not the new positions — at the new max (`logBigRadius = 64`, `highRadius = 4r`) that is
Σ(2r+1)²(8r+1) ≈ **1.3e8** loop iterations with a `Vector3i` allocation each, i.e. the ledger's algorithmic half of
V03a is not achieved (only the clamp makes the mode usable); (b) the comment at `:44-47` and `:89-94` is wrong; (c)
the "obvious" optimisation the comment invites (restricting slab #1 to the new y-band) would introduce **scan gaps**,
because the ring emits as written do not cover `x ∈ [-r+1..r-1]` at `z = ±(r-1)` (and miss the `y = 0` layer for
`r = 1`). **Minimal required fix**: give `emit` explicit `zMin`/`zMax` parameters (instead of deriving them from
`xMin`/`xMax`), then emit (i) the new y-bands `[prevHigh+1 .. high]` and `[-high .. -prevHigh-1]` over the full
current x/z extent, (ii) the `x = ±curRadius` columns at `|y| ≤ prevHigh`, and (iii) the `z = ±curRadius` columns at
`x ∈ [-curRadius+1 .. curRadius-1]`, `|y| ≤ prevHigh` — and handle the `r = 1` `y = 0` layer (or simply keep the
full-box emit and delete the O(new) claim from the comment).

### R7 — **low** — declared dead members
`thread/Pauseable.java:18` `public int errorCount = 0;` now has no writer (grep: declaration only) and
`deadlineNanos` is write-only (`:37`, `:137`). Both are declared in `fix-report.md` and scheduled in `docs/todo.md`.
**Minimal required fix**: delete when `Config.enableBudgetDeadline` is removed (already tracked).

---

## 7. Static-check log (verbatim commands and observed results)

### 7.1 Contract verify command 1 — V31 deletion proof
```
Get-ChildItem -Recurse -File src/main/java -Include *.java |
  Select-String -Pattern 'enableMixinCapabilityGates|MixinCapabilityPlugin|ILateMixinPlugin|TargetMod|fortuneOverrideEnabled' |
  Measure-Object | Select-Object -ExpandProperty Count
```
→ **`0`**. Independently, `Test-Path` on the five deleted paths returns `False` for
`mixin/Mixins.java`, `mixin/MixinCapabilityPlugin.java`, `mixin/ILateMixinPlugin.java`, `mixin/TargetMod.java`,
`src/main/resources/mixins.EZMiner.late.json`; `src/main/java/com/czqwq/EZMiner/mixin/` now contains only
`early/` (5 classes) and `interfaces/` (1); `src/main/resources/` contains only `mixins.EZMiner.json`.

### 7.2 Contract verify command 2 — mixin JSON (dumped and inspected)
```json
{ "required": false, "minVersion": "0.8.5-GTNH",
  "mixinextras": { "minVersion": "0.5.0" },
  "package": "com.czqwq.EZMiner.mixin.early",
  "refmap": "mixins.EZMiner.refmap.json", "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [ "MixinGTOreAdapter", "MixinBWOreAdapter", "MixinGTPPOreAdapter", "MixinItemInWorldManager" ],
  "client": [ "MixinGuiIngameMenu" ], "server": [] }
```
`required: false` ✓ · `MixinGuiIngameMenu` in `client` ✓ · no per-injection `require` (grep `require` over
`mixin/early` → javadoc/comment hits only) ✓ · no `"plugin"` key ✓.

### 7.3 Contract verify command 2 — tmp untouched
```
Get-ChildItem -Recurse -File tmp | Where-Object { $_.LastWriteTime -gt (Get-Date).AddDays(-2) } | Measure-Object
```
→ **`0`**.

### 7.4 Compile-consistency sweep (41 changed files, `src/main/java` + `src/main/resources`)
| check | method | result |
|---|---|---|
| dangling reference to a deleted/renamed symbol | grep `EZMiner.late`, `\bMixins\b`, `ChainRequest`, `enableMixinCapabilityGates`, `setTarget(`, `previewState.target`, `chainClientState.previewRenderedCount`, `chainClientState.sessionDimension` | **clean** (only 4 KDoc mentions of "mixins.EZMiner.json" and the explanatory `ChainClientState` javadoc) |
| removed `ChainPreviewController.setTarget` call sites | grep `setTarget(` | **0 hits** |
| removed `ChainClientState` fields' readers | grep `previewRenderedCount`, `sessionDimension` | only the live owners (`ClientStateContainer`, `PacketChainStateSync`) + javadoc |
| changed signature: `VertexAndIndex` | grep `new VertexAndIndex` | one call site, 4-arg ctor exists (`SpaceCalculator.java:200,225`); 2-arg ctor retained and `*24` fallback present |
| changed signature: `ChainLifecycleService.stopRuntime` | grep `stopRuntime` | both call sites match the 2-arg form (`:62`, `:51`) |
| new methods used | `canFlushDrops`, `clearAllTasks`, `hasState`, `canBreakAt`, `fireIfEnabledOrProtected`, `lastGeometry`, `indexOffset/indexCount`, `clampAddExhaustion`, `clampLogBigRadius`, `clampBlockSwapRadius`, `applyServerRuntimeStability`, `resolve()` | all have ≥1 call site inside the tree |
| unused imports (41 files, comment/javadoc lines excluded) | per-import simple-name scan | exactly **one** hit: `CoFHWaterBridge.java:4` → R3 |
| config field parity for the 9 synced names | grep each name in `Config.java` | declaration + load + save + apply for all nine (`enableChainWatchdog` 226/724/1560/995; `enableDropFallbackChain` 240/737/1576/996; `enableMainThreadGuard` 219/719/1553/997; `enableBudgetDeadline` 253/743/1583/998; `enableConfigValidation` 206/709/1539/999; `enableSafeReflection` 213/714/1546/1000; `chainWatchdogTimeoutTicks` 224/729/1567/1001; `enableToolBreakHandoff` 259/749/1590/1002; `toolBreakHandoffTimeoutTicks` 262/755/1597/1003) |
| packet codec symmetry | read `PacketServerConfig.java:135-158` vs `:161-214` | identical order, both closed by the `blacklistExpression` UTF8 string → no decode mismatch |
| lang parity | key-set diff of `en_US.lang` / `zh_CN.lang` | **197 = 197**, zero keys on either side alone; no lang key references mixins/capability |
| GUI row drift | `MAX_CONTENT_ROWS` / `SERVER_CONTENT_ROWS` + grep for the removed knob in `src` and lang | unchanged (23 / 51); no remaining reference anywhere |
| thread-affinity invariants | grep `MainThreadEnforcer` (3 hits, unchanged) and the new `canBreakAt` call sites | no **new** off-thread mutation was introduced; the V12/V13/V26 sites are still unguarded exactly as before (declared deferrals) |
| packet id / handler consistency | `NetworkMain` not in the change set; no packet class was added/removed | unchanged |

---

## 8. Repair list for the lead (ordered, no re-derivation needed)

1. **R1** (high) — `MixinItemInWorldManager.java:77-82`: skip the new `onBlockStartBreak` for TiC tools (and decide the V35 batch-path half).
2. **R2** (medium) — `BlockHarvestActionExecutor.java:153-160` + `ChunkCachedHarvester.java:146`: add the unconditional `canMineBlock`/`canBreakAt` gate; fix the comment at `:155-157`.
3. **R4** (medium) — `PacketToolBreakHandoff.java:59`: apply the claimed one-line change or retract it.
4. **R6** (medium) — `LogFounder.java:66-72,104-105`: correct the shell decomposition (or drop the O(new) claim).
5. **R5** (medium) — `V33`: implement `removedByPlayer` or record the deferral.
6. **R3** (low, build) — `CoFHWaterBridge.java:4`: remove the javadoc-only import.
7. **Undeclared ledger `FIX` omissions to either implement or explicitly defer** (each with the ledger's own fix sketch): `V03b`, `V08`, `V15`, `V16`, `V22`, `V23`, `V37`, `V39`, `V40`, `V62`, `V64`, `V65`.
8. **R7** (low) — bump `docs/todo.md` to also cover `Pauseable.errorCount` (it already covers `enableBudgetDeadline`).
9. **Carry-forward** — `V02` mutation scope, `V12`/`V13`/`V26`/`V29` (declared), `V26`'s in-game tool-swap test, and the compat-audit C1/C2 GT5U-generation residual risk (`V30` as ruled) remain open by decision.

---

## 9. Limits of this verification

1. **No compiler, formatter or Checkstyle ran** (§2). Every "closed" verdict is a source-level judgement; the
   non-obvious risks (brace balance in `GT5ToolCompat.resolve()`, `LogFounder.emit`, `SpaceCalculator`'s new arrays)
   were checked by reading the whole edited region, not by compiling.
2. **R6's cost arithmetic** is hand-derived (Σ(2r+1)²(8r+1) at `r ≤ 64`), not profiled; the *correctness* of the
   enumeration is unaffected (the redundant emits are harmless and `isVisited` dedups).
3. **R1's in-game impact** (double IguanaTweaks XP; TreeChopTask per chained block) is derived from the TiC sources
   and the call order in the mixin; the observable magnitude needs a GTNH client with TiC + IguanaTweaks.
4. **V41's ServerUtilities coverage** is only source-level: whether SU's handler actually cancels EZMiner's
   `ForgeHooks.onBlockBreakEvent` (fired with the player's game type, `ChainBreakEventHelper.java:82`) in a real
   claimed chunk is an in-game check.
5. **A local Gradle result for this working tree was not supplied**, so §2's "no build possible" stands as the
   strongest statement available. If one is supplied later it should be pasted into §2 and the `R3` prediction
   re-checked first, because it is the one item this verification predicts will fail.
6. `docs/review/agent-teams/fix-ledger.md`'s row for `V03b` was truncated in my extraction (2000-char line cap); I
   verified the item from its `files` column (`ChainWatchdog`, `BaseOperator`) and the ledger's summary text, both
   of which name the exact behaviours I checked.
7. **Independence note for t12**: `docs/review/agent-teams/review-round1.md` appeared in the tree at 02:21:36 while
   this verification was in progress (another member's t11 review of the same fix). I did **not** read it, so the
   verdicts above are derived only from the current source, the ledger and the tests named in §7; integration should
   cross-check the two documents rather than treat either as derived from the other.
