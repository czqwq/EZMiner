# review-round3.md — independent review of the t15 repair round (t9 ledger)

**Reviewer**: `audit-compat-ds` — task **t16** (review round 3), attempt `8087f16f-4384-4682-8812-54758b92c3ea`
**Reviewed**: `fix-ledger.md` (t8 contract), `fix-report.md` **§Repair round 2 + §Repair round 3 (t15)** + STATUS CORRECTION, `verification-report.md` (t10), `review-round1.md` (t11), `review-round2.md` (t14), and the code itself.
**Mode**: read-only. No `src/` or `tmp/` file was modified and no code was fixed by me; `docs/review/agent-teams/review-round3.md` is the only file written.
**Round-3 scope** (as scoped by the captain): verify that exactly the four round-2 items (Q1 medium, Q2–Q4 low) are closed in the current source, and that nothing else moved.

## 0. VERDICT: **pass** — no blocker/high/medium finding remains

All four round-2 items are **closed in the code**, verified line by line; the three edited files are structurally sound; the t15 window contains only those three files plus the report; nothing from rounds 1–2 was reopened or refactored; and the four documented deferrals remain explicit rather than silent. The only issues I could raise are non-blocking observations (§6), none of which meets the low-severity bar for a repair cycle.

| round-2 item | severity | state | verification |
|---|---|---|---|
| **Q1** — protection gate sat below the TE-carrier / `removedByPlayer`-override early returns, with a comment claiming unconditional coverage | medium | **CLOSED** | gate hoisted to immediately after `isUnbreakable`, above every branch, at all four sites; all four comments rewritten to match (details below) |
| **Q2** — `executeWithPreResolved` still pre-fired the `BreakEvent` (double event with `fireBreakEvent=true`) | low | **CLOSED** | passes `null` at `:307`; the file now contains exactly **one** `fireIfEnabled*` call (`:189`, the batch EBS path) |
| **Q3** — `damagePerDropConversion` did `getMethod` per invocation on the hot path | low | **CLOSED** | handle resolved once per JVM into a `static volatile Method` against `IToolStats`, with an explicit fallback |
| **Q4** — `fix-report.md` §C count/label wrong + two drifted citations | low | **CLOSED** | §C now "(22 — the previous heading said '(14)', which was wrong)" + a t15 line; V23 → `KeyListener.java:220-223`; TiC skip → `MixinItemInWorldManager.java:88-93` |

## 1. Q1 — gate ordering (verified at all four call sites)

| method | `isUnbreakable` | `canMineBlock` | first escape branch | branch type |
|---|---|---|---|---|
| `BlockHarvestActionExecutor.execute` | `:70` | **`:77`** | `:88` | `hasTileEntity(meta) \|\| isGTTileEntityCarrier` → `tryHarvestBlock` |
| `BlockHarvestActionExecutor.executeBatch` | `:151` | **`:157`** | `:163` (TE) then `:173` (`overridesRemovedByPlayer`) | both escape branches |
| `BlockHarvestActionExecutor.executeWithPreResolved` | `:289` | **`:294`** | `:296` | TE branch |
| `ChunkCachedHarvester.harvestNext` | `:125` | **`:131`** | `:140` (TE) then `:151` (override) | both escape branches |

* The gate precedes **every** removal decision on **every** branch: the only ways out of each method before `canMineBlock` are the null/air check and `isUnbreakable` (`execute:69-70`, `executeBatch:150-151`, `executeWithPreResolved:288-289`, `harvestNext:124-125`) — no mutation can happen before the gate.
* The new placement is *after* `isUnbreakable`, preserving the documented gate order (unbreakable first) from AGENTS.md/CLAUDE.md.
* The four comments were rewritten to describe the new placement and to state the reason (vanilla `ItemInWorldManager.tryHarvestBlock` does not consult `canMineBlock`; vanilla only checks it on the dig packet): `execute:72-76`, `executeBatch:153-156`, `executeWithPreResolved:291-293`, `harvestNext:127-130`. The old "queried UNCONDITIONALLY for every block about to be removed" wording is gone; the two event-site comments now say explicitly that protection is handled once near the top and that these points are reached only by the direct-EBS-write blocks (`BlockHarvestActionExecutor.java:180-187`, `ChunkCachedHarvester.java:159-164`). **No surviving false invariant comment in the four paths.**
* Cross-check against the real vanilla chain (my round-2 evidence, unchanged): `World.canMineBlock` → `WorldProvider.canMineBlock` (`build/rfg/.../WorldProvider.java:538`) → `WorldServer.canMineBlockBody` (`:735-738`) → `MinecraftServer.isBlockProtected`; vanilla consults it only from the dig packet (`NetHandlerPlayServer.java:521,590`) and item classes — so EZMiner's per-block call is the only gate for automated removal, and it now covers the escape branches too.

## 2. Q2 — single `BreakEvent` per block

* `BlockHarvestActionExecutor.java:304-307`: the pre-fire is deleted and the mixin is called with `null`, with a comment naming the two-event history.
* `Select-String fireIfEnabled` over the whole file returns **one** hit — `:189` (`fireIfEnabledOrProtected`) inside `executeBatch`, which is reached only by blocks that take the direct EBS write (the TE/override branches `continue` above it at `:167,:177`).
* The mixin's `ChainBreakEventHelper.canBreakAt` (`MixinItemInWorldManager.java:68`) remains the single fire point for `execute`/`executeWithPreResolved`; `execute()` passes `null` at `:104`. Net effect: one event per block on every path, as the ledger's R19 fix intended.
* Keeping `executeWithPreResolved` public rather than deleting it is a deliberate, documented choice (`:291-293`: "Has no in-tree caller today; kept consistent with the other three paths so it cannot become a bypass") — acceptable API-surface decision, not a finding.

## 3. Q3 — cached drop-conversion handle

* `GT5ToolDurabilityBridge.java:209-224`: `private static volatile Method mDropConversion` + `private static volatile boolean dropConversionResolved`; `dropConversionMethod()` resolves `IToolStats.class.getMethod("getToolDamagePerDropConversion")` once (absent → `null`, `dropConversionResolved = true`), and the javadoc states why the lookup is against the **interface** (one handle serves every implementation) and why an unsynchronised benign race is safe with a volatile field.
* `:235-245` uses the cached handle (`m.invoke(toolStats)`), and falls back to `Math.max(1L, damagePerBlockBreak(toolStats))` when the handle is absent **or** the invocation fails — the conservative direction the item required.
* No per-block reflective lookup remains: the handle resolution is guarded by the flag; the only remaining per-call work is one virtual `invoke` (hot path cost is now one cached reflective call per block, which is what the ledger accepted for GT correctness). `damagePerBlockBreak` (`:182-188`) is a direct interface call.

## 4. Q4 — report bookkeeping

* `fix-report.md:590` — "**Modified in t13** (22 — the previous heading said "(14)", which was wrong)" followed by the counted list; `:594` adds the **t15** list (3 source files + the report).
* `:563` — V23 citation now `client/KeyListener.java:220-223` (matches the code: guard at `:220-223`).
* `:553` — TiC-skip citation now `mixin/early/MixinItemInWorldManager.java:88-93` (matches the code: `!TinkersConstructCompat.isTiCTool(startBreakStack)` inside the condition at `:88-93`).
* The t15 section (§"Repair round 3 (t15) — four scoped items", `:623-632`) states one honest row per Q item with its own evidence.

## 5. Structural sanity on the three edited files

| file | braces | parens | note |
|---|---|---|---|
| `chain/execution/BlockHarvestActionExecutor.java` | 37 / 37 | 124 / 124 | matches the implementer's count |
| `chain/execution/ChunkCachedHarvester.java` | 43 / 43 | 101 / 101 | matches |
| `compat/GT5ToolDurabilityBridge.java` | 53 / 53 | 106 / 106 | matches |

These are raw character counts (braces inside comments/strings are included), so they are a **sanity check, not a parse** — the authority remains a compiler, which nobody has run (§7). Within that limitation, the three files are balanced and the edited regions read as syntactically complete (method bodies closed at `:113`, `:272-273`, `:245-246`).

**Nothing from rounds 1–2 reopened or refactored**: the t15 changes are exactly the hoist + four comment rewrites (`BlockHarvestActionExecutor.java`, `ChunkCachedHarvester.java`), the `null` pass (`:307`), and the cached handle (`GT5ToolDurabilityBridge.java:209-245`). Every other region of those files — TE/override branching, TiC/shears bridges, durability guard, tool damage, drop/XP handling, EBS write, height-map flush — is byte-identical in structure to what I verified in round 2, and no other source file in the tree changed after 18:05.

## 6. Non-blocking observations (recorded, not findings)

1. `executeWithPreResolved` remains dead code kept as public API — deliberate and documented; it is now also gated, so it cannot become a bypass if a caller appears.
2. The hot-path cost of the GT guard is now one cached reflective `invoke` per block; that is the price of the V64 correctness fix and was accepted by the ledger (the gate/guard agreement matters more than the remaining nanos). No further action needed.
3. `dropConversionMethod()`'s flag-then-handle pattern is technically a racy publication, but both fields are `volatile`, a double resolution is idempotent, and the javadoc explains it — acceptable for a read-only handle cache.
4. `docs/todo.md` item 9 still tracks the `Config.enableBudgetDeadline` removal and the two dead `Pauseable` members; that is the scheduled follow-up, not an omission.

## 7. Scope compliance (evidence) and verifiability

**Scope, proven by timestamped listing + read-tool inspection** (no `git` in this sandbox):

* Everything modified after `2026-09-29 18:05` in the workspace, excluding `.agent-teams/`: **5 files** — `BlockHarvestActionExecutor.java` (18:10:06), `ChunkCachedHarvester.java` (18:10:23), `GT5ToolDurabilityBridge.java` (18:10:33), `docs/review/agent-teams/fix-report.md` (18:11:32), and my own `review-round2.md` (18:08:49, written by me as the round-2 deliverable). Each of the three source files is named in the report's §t15 list, and every change in them maps to Q1/Q2/Q3.
* **Nothing under `tmp/` was modified**: newest `tmp/` file is `2026-09-18 01:46` (`tc-quick2/.../BlockCosmeticOpaque.java`); no `tmp/` entry appears in the t15 window.
* **No build script or wrapper changed**: `gradle-wrapper.properties` and `.jar` 2026-08-25, `gradle-daemon-jvm.properties` 2026-03-06, `build.gradle.kts` 2026-08-18, `gradle.properties` 2026-04-27, `dependencies.gradle` 2026-09-06 — all untouched and outside the window.
* **No resource or docs churn**: `lang/*`, `mixins.EZMiner.json` (02:02), `CLAUDE.md` (02:05), `docs/todo.md` (02:11) are unchanged t9 artefacts; only `fix-report.md` changed among the docs.
* **t9 static checks still hold**: the deleted-symbol grep (`MixinCapabilityPlugin|ILateMixinPlugin|TargetMod|enableMixinCapabilityGates|mixins.EZMiner.late|ChainRequest`) over `src/` returns **0 matches**, and `mixins.EZMiner.json` is byte-unchanged since t9 (`required:false`, `mixins` = 4, `client: [MixinGuiIngameMenu]`, `server: []`, no `require`).
* **Unrelated-edit bound**: the three edited files' regions were read; all changes are localised to the four items, with no reformatting of surrounding code. Byte-level absence of reformatting cannot be *proven* without a diff tool, which is the honest limit of this evidence.

**Verifiability / residual risk (stated explicitly, as the acceptance requires)**

* Verifiable **without** a build and verified by reading: every closure claim above (presence/absence and control-flow ordering), the invariant set, the scope set, the static checks.
* **Not** verifiable without a build/run, and therefore still outstanding for the whole pass: compilation and Google-Java-Format/Checkstyle (`spotlessApply build` was never run by anyone); the Mixin annotation processor's refmap output; and, one level further, the runtime application of the three `gregtech.common.ores` mixins and of `RemovedByPlayerBridge`'s reflective probe against real GT/vanilla classes. The `LogFounder` rewritten loop, `PacketCachedBlockSync`'s new `startIndex` wire field and `GT5ToolDurabilityBridge`'s cached handle are the highest-value things a compiler should confirm first.
* **In-game-only residuals** (unchanged, explicitly deferred earlier): **V26** (tool-swap handlers still mutate inventory/ledger on the netty thread) remains the top item and needs a tool-swap test; **V12/V13/V29** (OP-config off-thread handlers, `MainThreadEnforcer` liveness + 6 unguarded C→S handlers, `MessageUtils` overworld-only lookup); **V40** (`maxFortuneLevel` documented "NOT ENFORCED — dead setting", enforced only by a build-validated mixin injection); **V02**'s board-scope half; and the low DEFER batch (V36/V38/V42/V43/V45–V49/V52/V54/V55–V57/V60/V66) plus the scheduled `enableBudgetDeadline` removal.
* **Wire-format note carried forward from round 2** (unchanged judgement): `PacketCachedBlockSync`'s appended `startIndex` is fine for same-jar pairs and a mixed-version pair fails closed with a `DecoderException` thanks to the plausibility guard; record it under ledger **V52** because `acceptableRemoteVersions = "*"` (`EZMiner.java:33`).

## 8. Acceptance evidence for this review task

| acceptance criterion | result | evidence |
|---|---|---|
| Verdict supported by `file:line` evidence for every ledger FIX item and every t9 acceptance criterion | **passed** | §1–§4 give the four round-2 items with exact current lines; rounds 1–2 (this file's parents) carry the per-ledger-row closure tables, which remain valid because no file outside the t15 window changed (mtimes, §7) |
| Scope compliance proven; nothing under `tmp/` modified; no build script or wrapper changed; no unrelated reformatting or drive-by edit | **passed** | §7: 5 files changed after 18:05 (3 source + report + my round-2 review), all attributable; newest `tmp/` file 2026-09-18; gradle/build files months old; byte-level reformatting absence is bounded by reading (stated limit) |
| Each surviving issue recorded with id/severity/problem/file/line/requiredFix; `verdict=pass` only when no blocker/high/medium remains | **passed** | no surviving blocker/high/medium issue: Q1–Q4 all closed (§1–§4); §6 lists four non-findings |
| Review states whether the fix is verifiable without running the game or a build, and the residual risk | **passed** | §7: closure facts are source-verifiable; compilation/Spotless/refmap and the reflective/mixin runtime behaviour remain build-only; in-game residuals enumerated (V26 first) |

**Conclusion**: the t9 + t13 + t15 fix pass now closes every ledger `FIX` row that was implemented by decision, and the four rows I failed it on are resolved in the code rather than in prose. The remaining open items are the explicitly recorded deferrals (V26 highest risk) and the never-run build. **Verdict: pass** — this unblocks t12.
