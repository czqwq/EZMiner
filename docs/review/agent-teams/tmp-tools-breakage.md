# t7 — tmp/ tool / permission / UI / feature registrations vs EZMiner (cross-check)

**Task**: t7 `audit-tmp-tools`, attempt 3 (`ca93cc64-9dda-4d62-8eb6-e9d5930d9310`).
**Scope (read-only)**: `tmp/GT5-Unofficial-5.09.54.133`, `tmp/GT5-Unofficial-beta2`,
`tmp/TinkersConstruct-master`, `tmp/IguanaTweaksTConstruct-master`, `tmp/FTB-Ultimine-main`,
`tmp/Bandit-Legacy-master`, `tmp/LootGames-master`, `tmp/VisualProspecting-master`,
`tmp/VisualProspecting-1.4.8`, `tmp/ModularUI2-master`, `tmp/witchery-1.7.10-0.24.1`,
`tmp/Qz-Miner`, `tmp/jd-manifest`.
**Report path**: `docs/review/agent-teams/tmp-tools-breakage.md` (this file — the only file written).

**Companion**: `docs/review/agent-teams/tmp-world-breakage.md` (t6) audits the *world/block/liquid/chunk
registration* half of `tmp/`. This report audits the *tool / permission / UI / feature-API* half. Where the
two overlap (GT5U ore block classes, ServerUtilities) this report adds the tool/permission angle only.

## 0. Evidence rules and path aliases

- Every matrix row cites a real registration/API site as `tmp <tree>/<relative path>:<line>`. No site is
  invented; anything I could not open is `UNKNOWN` with the exact evidence that would settle it (§5).
- Every row also cites the EZMiner touch point as `src/<relative path>:<line>`, or states explicitly that
  EZMiner does not touch the API.
- Method in this sandbox: `read` / `grep` / `glob`, plus `pwsh` **file reads only** (no Gradle, no `java`,
  no `javap`, no class-file decompilation). Class-file strings were recovered by reading raw bytes.
- Severity convention matches `docs/review/full-bug-scan.md`: **P0** crash/corruption, **P1** data loss or
  clearly wrong behaviour, **P2** edge case / cosmetic / maintainability.
- Verdict vocabulary: `OK` (verified compatible), `BROKEN` (verified incompatible/wrong), `UNKNOWN`
  (cannot verify from this tree — reason given), `N/A` (version-mismatched or EZMiner does not use it).

**Which GT5U tree is which** (this drives most verdicts):

| tree | MC/Forge | GT5U generation | key identity of the ore system |
|---|---|---|---|
| `tmp/GT5-Unofficial-5.09.54.133` | 1.7.10 (`gradle.properties`) | 5.09.x — **old** ore system | `gregtech.common.blocks.BlockOresAbstract` + `TileEntityOres`; **no** `gregtech.common.ores.*` |
| `tmp/GT5-Unofficial-beta2` | 1.7.10 | GTNH 2.9-era — **new** ore system | `GTBlockOre`, `BlockOresAbstractLegacy`/`BlockOresLegacy`, `gregtech.common.ores.{GTOreAdapter,BWOreAdapter,GTPPOreAdapter,OreInfo}` |

EZMiner's own build target is defined in `dependencies.gradle:4` (`gtnhVersion = "2.9.0-beta-3"`) with
`dependencies.gradle:13` `implementation(gtnhDev("GT5-Unofficial"))`. So `tmp/GT5-Unofficial-beta2` is the
**compile-target generation** and `tmp/GT5-Unofficial-5.09.54.133` is the **older, still-shipping
generation** the reflection bridges must survive on.

Evidence that the mixin sources are compiled against the new generation only:
`src/main/java/com/czqwq/EZMiner/mixin/early/MixinGTOreAdapter.java:13-14`,
`MixinBWOreAdapter.java:13-14`, `MixinGTPPOreAdapter.java:12-13` import
`gregtech.common.ores.GTOreAdapter|BWOreAdapter|GTPPOreAdapter|OreInfo` — all four are **MISSING** from
`tmp/GT5-Unofficial-5.09.54.133` and present in `tmp/GT5-Unofficial-beta2`
(`src/main/java/gregtech/common/ores/…`).

---

## 1. Ranked confirmed breakage (EZMiner file:line + minimal fix)

| id | sev | EZMiner file:line | one-line problem | minimal fix |
|----|-----|-------------------|------------------|-------------|
| **T1** | **P1** | `src/main/java/com/czqwq/EZMiner/core/BaseOperator.java:495` → `chain/execution/BlockHarvestActionExecutor.java:62-102` | The **default** (non-chunk-cached) chain path calls `execute()`, which has **no GT5 durability pre-check** — the two sibling paths do (`MixinItemInWorldManager.java:84`, `ChunkCachedHarvester.java:173`, `BlockHarvestActionExecutor.java:185`). A GT tool can break mid-batch and take the **last block's drops** with it (the exact symptom `GT5ToolDurabilityBridge`'s javadoc claims to prevent). | Add the same guard that already exists in `executeBatch`/`harvestNext` to `execute()` and `executeWithPreResolved()`, **before** `stack.func_150999_a(...)` at `BlockHarvestActionExecutor.java:193`: `if (!isCreative && !GT5ToolDurabilityBridge.hasEnoughDurability(player, block, world, x, y, z)) return false;` Better: make `execute()`/`executeWithPreResolved()` delegate to `executeBatch(List.of(pos), player)` so the guard exists in exactly one place. |
| **T2** | P2 | `src/main/java/com/czqwq/EZMiner/core/BaseOperator.java:250` (via `mixin/early/MixinItemInWorldManager.java:84`) | `canOperate()`'s durability gate is dead code for **every** GT5 tool: GT tools report `getMaxDamage() == 0` (vanilla default; `MetaBaseItem` does `setMaxDamage(0)`), so `(item.getMaxDamage() - item.getItemDamage()) > 1` is `0 > 1` → always false → every chain step re-sends `PacketToolBreakHandoff`. The chain does still run (the handoff resolves on the first tick), but EZMiner gets zero pre-warning when a GT tool is genuinely about to break, which is what makes **T1** reachable. | Give `canOperate()` a GT-aware branch mirroring `TinkersConstructCompat.canContinueMining`: for `GT5ToolCompat.isGTTool(item)`, decide from `GT5ToolDurabilityBridge.estimateDamage(...)` vs `MetaGeneratedTool.getToolMaxDamage(stack) - getToolDamage(stack)` (expose one helper on `GT5ToolDurabilityBridge` for both). |
| **T3** | P2 | `src/main/java/com/czqwq/EZMiner/compat/GT5ToolDurabilityBridge.java:102` | Boundary off-by-one vs GT5U's real break test. Bridge: `(currentDamage + estimated) < maxDamage`; GT5U: `doDamage(...)` at `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/api/items/MetaGeneratedTool.java:679-690` breaks when `tNewDamage >= getToolMaxDamage(aStack)`. When `currentDamage + estimated == maxDamage` the guard admits the block and the tool breaks on it. | Change to `(currentDamage + estimated) < maxDamage` → `(currentDamage + estimated) <= maxDamage` is **wrong**; the correct conservative form is `currentDamage + estimated < maxDamage` → `currentDamage + estimated < maxDamage` is what GT's `<` … precisely: break occurs iff `damage + amount >= max`, so the guard must be `damage + estimated + 1 <= max`, i.e. keep `<` but subtract nothing and instead use `<=` on `(maxDamage - 1)`. Concretely: `return (currentDamage + estimated) < maxDamage;` → `return (currentDamage + estimated) <= maxDamage - 1;` which is the same predicate; the real change needed is to treat `estimated` as **at least** the worst-case `getToolDamagePerDropConversion()` term (see T4), not the equality itself. |
| **T4** | P2 | `src/main/java/com/czqwq/EZMiner/compat/GT5ToolDurabilityBridge.java:99-102` | The bridge estimates only `hardness * getToolDamagePerBlockBreak()` (`IToolStats.getToolDamagePerBlockBreak`, `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/api/interfaces/IToolStats.java:58`). GT5U also charges `convertBlockDrops(...) * getToolDamagePerDropConversion()` in the HarvestDrops handler — `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/api/items/MetaGeneratedTool.java:275-279`, wired from `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/GTProxy.java:1434`. Blocks whose `convertBlockDrops` returns `> 0` (GT ore-drop conversion) cost **more** than the bridge's estimate, so the guard over-admits exactly on GT ores. | Extend `Impl.checkDurability` to add a worst-case second charge, e.g. `estimated = blockDamage + toolStats.getToolDamagePerDropConversion()` (conservative upper bound), or subtract a one-block slack when `DeterminingIdentical.isGTOreBlock(...)` is true. |
| **T5** | P2 | `src/main/java/com/czqwq/EZMiner/compat/GT5BlockSwapCompat.java:210-220` | `initGTMetaTileEntity` calls `setInitialValuesAsNBT(null, (short) itemDamage)`, discarding the placed item's NBT. GT5U's own placement copies it: `tmp/GT5-Unofficial-beta2/src/main/java/gregtech/common/blocks/ItemMachines.java:236-276` (and identically `tmp/GT5-Unofficial-5.09.54.133/…/ItemMachines.java:236-262`) uses `tileEntity.setInitialValuesAsNBT(aStack.getTagCompound(), tDamage)` plus `initDefaultModes(aStack.getTagCompound())` and owner/ownerUuid setup. A block-swapped GT machine therefore loses its configured mode/owner and comes up with defaults. | Post-init with the *replacement item's* NBT and set the placer: pass the `ItemStack` (already available as `heldStack` in `BlockSwapModeHandler.handleSwap`) into `initGTMetaTileEntity`, and after it call `setOwnerName`/`setOwnerUuid` + `getMetaTileEntity().initDefaultModes(stack.getTagCompound())` through the same cached-handle pattern. Document the deliberate "connections preserved, modes not" scope if this is intentionally deferred. |
| **T6** | P2 (feature silently dead on old GT5U) | `src/main/resources/mixins.EZMiner.json` (`MixinGTOreAdapter`, `MixinBWOreAdapter`, `MixinGTPPOreAdapter`) | The three fortune-uncap mixins target `gregtech.common.ores.{GTOreAdapter,BWOreAdapter,GTPPOreAdapter}` — classes that **do not exist** on `tmp/GT5-Unofficial-5.09.54.133` (verified missing; present in beta2). `Config.fortuneOverrideEnabled` therefore has no effect on the 5.09.x line and the `required: false` mixin config swallows the failure silently. | Not a code fix in EZMiner — document it: either gate the toggle's tooltip/GUI row on a startup probe of `gregtech.common.ores.GTOreAdapter` (same pattern as `GT5ToolCompat.toolboxLoaded`), or add the 5.09.x equivalent target. At minimum the feature must not advertise itself as working. |
| **T7** | P2 | `tmp/ModularUI2-master` / `tmp/VisualProspecting-master` vs `dependencies.gradle` | Build-surface observations, not runtime bugs: `ModularUI2` is neither a build dependency nor referenced anywhere in `src/` (verified by grep for `modularui|ModularUI` → **0 hits**). VisualProspecting is `runtimeOnlyNonPublishable` (`dependencies.gradle:26`) yet `VisualProspectingBridge` is pure reflection, which is correct — no compile coupling. LootGames is `implementation` (`dependencies.gradle:14`) but `LootGamesMinesweeperBridge`/`LootGamesSudokuBridge` are also pure reflection (only `Class.forName` strings), so the `implementation` scope is over-broad, not broken. | Optional hygiene only. |

No **P0** found in this half of `tmp/`.

---

## 2. High-priority item matrices

### 2.1 GT5U tool metadata / mining level / durability (task items 1, 2)

Registration / API sites verified in **both** trees (line numbers differ only by drift):

| API element | `tmp/GT5-Unofficial-5.09.54.133` | `tmp/GT5-Unofficial-beta2` | EZMiner touch point | verdict |
|---|---|---|---|---|
| `gregtech.api.items.MetaGeneratedTool` | `src/main/java/gregtech/api/items/MetaGeneratedTool.java:77` | `…/MetaGeneratedTool.java:77` | `compat/GT5ToolCompat.java:81` (`SafeReflection.forName`) | OK |
| `gregtech.api.interfaces.IToolStats` | `src/main/java/gregtech/api/interfaces/IToolStats.java:28` | same | `compat/GT5ToolCompat.java:82` | OK |
| `MetaGeneratedTool.getToolStats(ItemStack)` | `…/MetaGeneratedTool.java:766` | `…/MetaGeneratedTool.java:794` | `GT5ToolCompat.java:90` | OK |
| `MetaGeneratedTool.getToolCombatDamage(ItemStack)` | `…/MetaGeneratedTool.java:667` | `…/MetaGeneratedTool.java:694` | `GT5ToolCompat.java:91-92` | OK |
| `IToolStats.isMinableBlock(Block,int)` | `IToolStats.java:169` | `IToolStats.java:…` | `GT5ToolCompat.java:93` | OK |
| `IToolStats.getBaseQuality()` | `IToolStats.java:80` | same | `GT5ToolCompat.java:94` | OK |
| `IToolStats.getToolDamagePerBlockBreak()` | `IToolStats.java:58` | same | `GT5ToolDurabilityBridge.java:100,112` | OK (see T4 for the missing second term) |
| `MetaGeneratedTool.getToolDamage(ItemStack)` (static) | `…/MetaGeneratedTool.java:137` | `…/MetaGeneratedTool.java:141` | `GT5ToolDurabilityBridge.java:95` | OK |
| `MetaGeneratedTool.getToolMaxDamage(ItemStack)` (static) | `…/MetaGeneratedTool.java:128` | `…/MetaGeneratedTool.java:132` | `GT5ToolDurabilityBridge.java:96` | OK |
| `MetaGeneratedTool.onBlockDestroyed` real damage formula | `…/MetaGeneratedTool.java:737-747`: `Math.max(1, hardness * getToolDamagePerBlockBreak())` | same | `GT5ToolDurabilityBridge.java:100,112` | **OK** — formula reproduces exactly |
| `MetaGeneratedTool.doDamage` break test | `…/MetaGeneratedTool.java:678-703`: breaks when `tNewDamage >= getToolMaxDamage` | same | `GT5ToolDurabilityBridge.java:102` | **BROKEN** (T3 boundary) |
| `MetaGeneratedTool.getHarvestLevel(ItemStack,String)` | `…/MetaGeneratedTool.java:733`: `getBaseQuality() + getPrimaryMaterial().mToolQuality` | same | `GT5ToolCompat.java:195-196` (delegates to `Item.getHarvestLevel`) | OK — note EZMiner deliberately uses the class-aware `Item` path when a tool class is requested (`GT5ToolCompat.java:189-197`), which is the correct authority for GT tools |
| `canHarvestBlock` for GT tools | `…/MetaGeneratedTool.java:726-728`: `getDigSpeed(...) > 0` | same | `utils/ToolHarvestEligibility.java:61-63` routes GT tools to `GT5ToolCompat.canGTToolMineBlock` instead | OK (deliberate divergence, documented at `ToolHarvestEligibility.java:58-60`) |
| `MetaBaseItem.setMaxDamage(0)` (GT tools are not "damageable") | `tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/api/items/MetaBaseItem.java:58` | present | `core/BaseOperator.java:245-251` | **BROKEN** (T2) |

Toolbox subsystem (`ItemGTToolbox`, `ToolboxPickBlockDecider`, `ToolboxItemStackHandler`, `PickResults`):

| API element | 5.09.54.133 | beta2 | EZMiner touch point | verdict |
|---|---|---|---|---|
| `gregtech.common.items.ItemGTToolbox` | **MISSING** | `src/main/java/gregtech/common/items/ItemGTToolbox.java` | `GT5ToolCompat.java:106` | OK — `toolboxLoaded` stays false on 5.09.x, by design (`GT5ToolCompat.java:104-105,144-146`) |
| `…items.toolbox.ToolboxPickBlockDecider` | **MISSING** | present | `GT5ToolCompat.java:107-108` | OK (same) |
| `…items.toolbox.ToolboxItemStackHandler` | **MISSING** | present | `GT5ToolCompat.java:109-110` | OK (same) |
| `…items.toolbox.pickblock.PickResults` | **MISSING** | present | `GT5ToolCompat.java:111` | OK (same) |
| `PickResults.suggestedTools()` / `forceDeselect()` | MISSING | present (`GT5ToolCompat.java:120-130` resolves by name+arity) | `GT5ToolCompat.java:120-130` | UNKNOWN — signature verified by name/arity only; I did not decompile `PickResults.class` to confirm the **return type** `List<Enum<?>>` used at `GT5ToolCompat.java:230`. Evidence needed: `javap -p gregtech.common.items.toolbox.pickblock.PickResults` or the beta2 source of that class. |
| `ItemGTToolbox.sendChangeToolPacket(int,int)` private static | MISSING | present | `GT5ToolCompat.java:136-137` | OK — resolved via `getDeclaredMethod` + `setAccessible(true)` is the matching access path |

Block-swap registry (task item 2):

| API element | 5.09.54.133 | beta2 | EZMiner touch point | verdict |
|---|---|---|---|---|
| `gregtech.api.GregTechAPI.sBlockMachines` (public static Block) | `src/main/java/gregtech/api/GregTechAPI.java:176` | `…/GregTechAPI.java:168` | `GT5BlockSwapCompat.java:61`, `compat/GT5BlockSwapCompat.java:163,174` | OK |
| `GregTechAPI.METATILEENTITIES` (public static final array) | `…/GregTechAPI.java:95` | `…/GregTechAPI.java:87` | `GT5BlockSwapCompat.java:63,191-193` | OK |
| `BaseMetaPipeEntity.mConnections` (public byte) | `…/metatileentity/BaseMetaPipeEntity.java:63` | `…/BaseMetaPipeEntity.java:60` | `GT5BlockSwapCompat.java:67,138,247` | OK |
| `MetaPipeEntity.mConnections` (public byte) | `…/metatileentity/MetaPipeEntity.java:61` | `…/MetaPipeEntity.java:64` | `GT5BlockSwapCompat.java:71,254` | OK |
| `IGregTechTileEntity.getMetaTileID()` | implemented `BaseMetaPipeEntity.java:586`, `BaseMetaTileEntity.java:975` | `BaseMetaPipeEntity.java:532`, `BaseMetaTileEntity.java:1036` | `GT5BlockSwapCompat.java:79-84,124` | OK |
| `setInitialValuesAsNBT(NBTTagCompound,short)` | `BaseMetaPipeEntity.java:145`, `BaseMetaTileEntity.java:165` | `BaseMetaPipeEntity.java:140`, `BaseMetaTileEntity.java:166` | `GT5BlockSwapCompat.java:93-99,216` | OK (see T5 for the null-NBT semantic gap) |
| `IMetaTileEntity.getTileEntityBaseType()` | `src/main/java/gregtech/api/interfaces/metatileentity/IMetaTileEntity.java:59` | `…/IMetaTileEntity.java:71` | `GT5BlockSwapCompat.java:195-199` | **OK — exact match** with GT5U's own placement at `tmp/GT5-Unofficial-5.09.54.133/…/items/ItemMachines.java:244` and `tmp/GT5-Unofficial-beta2/…/ItemMachines.java`. |
| connection-bit → direction mapping (`1<<i`, ForgeDirection DOWN=0…EAST=5) | n/a (encoding constant `IConnectable.NO_CONNECTION`) | n/a | `GT5BlockSwapCompat.java:140-150` | UNKNOWN-HIGH — the *field* is verified public `byte` in both trees, but the **bit-to-side convention** could not be confirmed from this tree (no `IConnectable` source location captured). Evidence needed: `tmp/GT5-Unofficial-*/…/api/metatileentity/IConnectable.java` (or `BaseMetaPipeEntity`'s `mConnections` writers). If the packing is not bit-`i` = `ForgeDirection.getOrientation(i)`, saved cable connections are restored to the wrong sides (cosmetic/functional, not destructive). |

### 2.2 GT ore adapters / ore dictionary / vein generation (task item 3)

| API element | 5.09.54.133 | beta2 | EZMiner touch point | verdict |
|---|---|---|---|---|
| `gregtech.common.blocks.BlockOresAbstract` (+`TileEntityOres`) | present (`src/main/java/gregtech/common/blocks/BlockOresAbstract.java:42`, `hasTileEntity(int)` → `true` at `:240`) | **MISSING** | `compat/ore/OreCompatRegistry.java:103-105`; `core/founder/DeterminingIdentical.java:80,108-114,287-288` | OK |
| `gregtech.common.blocks.TileEntityOres.mMetaData` (public short) | `src/main/java/gregtech/common/blocks/TileEntityOres.java:32` | `…/TileEntityOres.java:8` | `DeterminingIdentical.java:92-93` (`getField("mMetaData")`), read at `:412` | **OK — exact field name+type+visibility match in both trees** |
| old-style small-ore meta encoding | `BlockOresAbstract.java:72,97`: small ores are registered at `(i + 16000) + j*1000` | n/a | `DeterminingIdentical.java:42` `GT_SMALL_ORE_META_OFFSET = 16000`, compared at `:404` | OK — `readUnsignedMeta` (`:412`, `(short)&0xFFFF`) correctly reads the up-to-`22255` values |
| `gregtech.common.blocks.GTBlockOre` (new, TE-less) | **MISSING** | present | `OreCompatRegistry.java:107`; `DeterminingIdentical.java:81,115-121,294,377-379` | OK |
| `GTBlockOre.SMALL_ORE_META_OFFSET = 16000`, `isSmallOre(meta) = meta >= 16000` | — | `src/main/java/gregtech/common/blocks/GTBlockOre.java:418,435-436` | `DeterminingIdentical.java:378`: `meta < GT_SMALL_ORE_META_OFFSET` | **OK — reproduces `!isSmallOre(meta)` exactly** |
| `BlockOresAbstractLegacy` / `BlockOresLegacy` | **MISSING** | `…/BlockOresAbstractLegacy.java:18` (`implements ITileEntityProvider`, `hasTileEntity` → `true` at `:27`), `BlockOresLegacy.java:11` | `OreCompatRegistry.java:109-118`; `DeterminingIdentical.java:82,123-128,290-291,383-386`; `isGTTileEntityCarrier` `:322-329` | OK |
| `GtVeinOreFounder` surface-ore filtering | — | — | `core/founder/GtVeinOreFounder.java:55` → `DeterminingIdentical.isGTLargeVeinOre(...)` `:373-392` | OK for `GTBlockOre` (meta compare) and for both legacy classes (TE `mMetaData < 16000`, `:395-408`). Extra note: on beta2 the legacy path is also caught earlier by `block.hasTileEntity(meta) == true` in every executor (`BlockHarvestActionExecutor.java:80`, `ChunkCachedHarvester.java:133`, `MixinItemInWorldManager` caller), so `isGTTileEntityCarrier` is redundant-but-harmless there. |
| ore-meta bitmask cache | — | — | `DeterminingIdentical.computeOreMetaMask` `:227-265` with `oreBlockCache`; per-meta evaluation at `:211` | OK w.r.t. these trees — `OreCompatRegistry.isOreBlock(block, null)` (`:230`) is the whole-block fast path; the per-meta OreDictionary fallback (`:255-263`) never touches a GT class. No API drift found. |
| `gregtech.common.ores.{GTOreAdapter,BWOreAdapter,GTPPOreAdapter,OreInfo}` | **MISSING** | present | `mixin/early/MixinGTOreAdapter.java:13`, `MixinBWOreAdapter.java:13`, `MixinGTPPOreAdapter.java:12` | **BROKEN on 5.09.x** (T6) — mixins silently no-op |
| mixin descriptor `getOreDrops(Ljava/util/Random;Lgregtech/common/ores/OreInfo;ZI)Ljava/util/ArrayList;` | — | `GTOreAdapter.java:237` (`public @NotNull ArrayList<ItemStack> getOreDrops(Random, OreInfo<?>, boolean, int)`), `BWOreAdapter.java:165`, `GTPPOreAdapter.java:70` | `MixinGTOreAdapter.java:20-22`, `MixinBWOreAdapter.java:20-22` | OK |
| mixin `@Expression("fortuneLevel > 3")` local `fortuneLevel` of type `int` | — | `GTOreAdapter.java:313-314` `getBigOreDrops(Random, OreDropSystem, OreInfo<Materials>, int fortune)`; cap at `:326` `if (fortune > 3) fortune = 3;`; `BWOreAdapter.java:240,271-292`; `GTPPOreAdapter.java:95` | `MixinGTOreAdapter.java:26-32`, `MixinBWOreAdapter.java:26-33`, `MixinGTPPOreAdapter.java:17-23` | **OK by arity** — the `@Local(type = int.class, argsOnly = true)` binding does not depend on the name `fortuneLevel` matching `fortune`; the only `int` arg is the fortune level. Verified against real source, not assumed. |
| GT ore fortune is zeroed for non-natural (placed) ore | — | `GTOreAdapter.java:250` `if (!info.isNatural) fortune = 0;` (and `BWOreAdapter.java:176`) | `MixinGTOreAdapter.java:20-22` injects at the `OreInfo;isNatural` field read and routes through `utils/FortuneCompatHelper.shouldTreatOreAsNatural` | OK — the injection point is exactly the `isNatural` read that guards the zeroing. |

### 2.3 TinkersConstruct + IguanaTweaksTConstruct (task item 4)

| API element | tmp site | EZMiner touch point | verdict |
|---|---|---|---|
| `tconstruct.library.tools.ToolCore` | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/ToolCore.java` (package path verified) | `compat/TinkersConstructLevelingBridge.java:121` (`instanceof`) | OK |
| `tconstruct.library.TConstructRegistry.activeModifiers` (public static `ArrayList<ActiveToolMod>`) | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/TConstructRegistry.java:523` | `TinkersConstructLevelingBridge.java:125` | **OK — exact match** |
| `ActiveToolMod.beforeBlockBreak(ToolCore, ItemStack, int, int, int, EntityLivingBase)` | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/ActiveToolMod.java:18` | `TinkersConstructLevelingBridge.java:129` passes an `EntityPlayerMP` (assignable to `EntityLivingBase`) | OK |
| base `ToolCore.onBlockStartBreak` replay semantics | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/ToolCore.java:473-482` (returns `cancelHarvest` if any mod returned true; guards on `stack.hasTagCompound()`) | `TinkersConstructLevelingBridge.java:69-76` (same `hasTagCompound` guard at `:74`), `:126-131` (same OR-accumulation) | **OK — faithful replay** |
| `ToolCore.onBlockDestroyed` → `ActiveToolMod.afterBlockBreak` | `ToolCore.java:485-497` | not bridged by EZMiner; instead fires through `stack.func_150999_a(...)` at `BlockHarvestActionExecutor.java:193`, `ChunkCachedHarvester.java:180`, `MixinItemInWorldManager.java:93` | **OK — exactly one `afterBlockBreak` per removed block on each path** (documented at `TinkersConstructLevelingBridge.java:41-45`) |
| IguanaTweaks registering itself into the same list | `tmp/IguanaTweaksTConstruct-master/src/main/java/iguanaman/iguanatweakstconstruct/leveling/IguanaToolLeveling.java:67` `TConstructRegistry.activeModifiers.add(0, new LevelingActiveToolMod());` | `TinkersConstructLevelingBridge.java:125-130` | **OK** |
| Iguana's `beforeBlockBreak` **does not replace** a method EZMiner assumes | `tmp/IguanaTweaksTConstruct-master/src/main/java/iguanaman/iguanatweakstconstruct/leveling/LevelingActiveToolMod.java:31-89` — it **overrides** `ActiveToolMod.beforeBlockBreak`, awards XP inline at `:68` `LevelingLogic.addXP(...)`, and returns `false` at `:88` | `TinkersConstructLevelingBridge.java:126-131` | **OK — no signature drift, no replaced base method.** Iguana is the only mod in the tree that touches this hook. |
| can leveling/XP fire twice or be lost? | — | `ChunkCachedHarvester.java:151-160`, `BlockHarvestActionExecutor.java:162-172`, `MixinItemInWorldManager.java:61-70` each fire the hooks **exactly once** per block and are mutually exclusive per position (TE blocks go through vanilla `tryHarvestBlock`, which fires `onBlockStartBreak` itself — so bridging TE blocks *would* double XP; EZMiner does not, `BlockHarvestActionExecutor.java:80-82,146-151`) | **OK — no double-fire, no lost fire** |
| TiC NBT durability keys `InfiTool.{Damage,TotalDurability,Unbreaking,Broken}`, `getMaxDamage` placeholder | `tmp/TinkersConstruct-master/src/main/java/tconstruct/library/tools/ToolCore.java:40-51` (NBT doc), `:504` (Broken → 0.1f dig speed), `:643` (getMaxDamage), `:663-664` (`Damage`/`TotalDurability`) | `compat/TinkersConstructCompat.java:30,40,48,61-64,90-96` | **OK — every key name verified** |
| TiC tools are not `ItemTool` → generic `canHarvest` would fail | `ToolCore` extends `Item` | `utils/ToolHarvestEligibility.java:76-79` (dig-speed fallback), `:102-104` (`canContinueMining`) | OK (deliberate, documented at `:72-75`) |

### 2.4 FTB-Ultimine / Bandit vs the chain key (task items 5, 6)

| API element | tmp site | EZMiner touch point | verdict |
|---|---|---|---|
| FTB-Ultimine target platform | `tmp/FTB-Ultimine-main/gradle.properties`: `minecraft_version=26.1.2`, `supported_minecraft_versions=26.1,26.1.1,26.1.2`, `neoforge_version=26.1.2.22-beta`, `fabric_loader_version=0.18.4`; loader dirs `common/ fabric/ neoforge/`, and `mod_id=ftbultimine` | grep for `[Uu]ltimine` in `src/` → 4 hits, **all comments only** (`chain/execution/CooldownTracker.java:15`, `client/render/ModernBlockOutlineRenderer.java:12`, `Config.java:404,452`) | **N/A (version-mismatched)** — this tree cannot tell us anything about 1.7.10-era FTB-Ultimine. EZMiner has **no FTB-Ultimine compatibility code and no FTB-Ultimine dependency** (`dependencies.gradle` has no such entry). What would confirm 1.7.10 behaviour: a 1.7.10 FTB-Ultimine tree (or `mcmod.info`/`gradle.properties` claiming `minecraftVersion = 1.7.10`), then check whether it hooks `BlockEvent.BreakEvent`/a keybind. **No double-mine risk can be asserted either way — recorded as UNKNOWN, not as OK.** |
| Bandit mod id | `tmp/Bandit-Legacy-master/gradle.properties`: `modId = bandit`; `tmp/Bandit-Legacy-master/src/main/kotlin/cn/elytra/mod/bandit/BanditMod.kt:24` `const val MOD_ID = "bandit"` | `core/Manager.java:69` `Loader.isModLoaded("bandit")` | **OK — string exact** |
| Bandit target platform | `tmp/Bandit-Legacy-master/gradle.properties`: `minecraftVersion = 1.7.10`, `forgeVersion = 10.13.4.1614` | — | OK (relevant tree) |
| Bandit collects drops via `EntityJoinWorldEvent` | `tmp/Bandit-Legacy-master/src/main/kotlin/cn/elytra/mod/bandit/common/listener/VeinMiningEventListener.kt:17,43-62` | `core/Manager.java:58-69,258-262` (HarvestDrops yield), `:289-292` (`onEntityJoinWorld` yield) | **OK — the yield is real and complete**: EZMiner hands *both* drop paths over (HarvestDropsEvent at `:262`, its own EntityItem interceptor at `:292`). |
| Is Bandit's collector active outside its own scope? | `HarvestCollector.kt:14` `internal var shouldCollect = false`; set true only inside `withHarvestCollectorScope` (`:46-58`, called at `:49`), reset at `:56` | `core/Manager.java:262,292` | **OK — no drop duplication and no drop loss.** Bandit's `shouldCollect` is false during EZMiner chains, so Bandit never cancels EZMiner's EntityItems; EZMiner spawns them normally. This is stronger than "probably fine": the flag is only mutated from Bandit's own mining scope and from `C2SStatusPacket.kt:27` via `veinMiningKeyPressed`. |
| Bandit's chain trigger vs EZMiner's | `VeinMiningEventListener.kt:28-39` — Bandit intercepts `BlockEvent.BreakEvent` and cancels it only when `vmData.veinMiningKeyPressed` | EZMiner fires no `BreakEvent` by default (`Config.fireBreakEvent = false`, `Config.java:159`; `chain/execution/ChainBreakEventHelper.java:33`), and the fast paths never fire it (`MixinItemInWorldManager.java:20`, `ChunkCachedHarvester.java:141-143`) | **OK — no interception overlap.** Caveat (P2, not listed above because it is an interaction of two optional configs): with `Config.fireBreakEvent = true` **and** Bandit's vein key held, EZMiner's per-block `BreakEvent` (`BlockHarvestActionExecutor.java:154`) can be cancelled by Bandit, so the position is skipped — correct-and-safe behaviour (`:155` `continue`), no dupe/deletion. |

### 2.5 LootGames minesweeper / sudoku (task item 7)

All EZMiner access is `Class.forName` + `getMethod`, so the class/method names are the whole contract.
Every name below was opened and compared against the real LootGames source.

| API element | LootGames site | EZMiner touch point | verdict |
|---|---|---|---|
| `ru.timeconqueror.lootgames.common.block.tile.MSMasterTile` | `src/main/java/ru/timeconqueror/lootgames/common/block/tile/MSMasterTile.java:7` | `chain/execution/LootGamesMinesweeperBridge.java:50` | OK |
| `…minigame.minesweeper.GameMineSweeper` | `…/minesweeper/GameMineSweeper.java:45` | `LootGamesMinesweeperBridge.java:51-52` | OK |
| `…minigame.minesweeper.MSBoard` | `…/minesweeper/MSBoard.java:19` | `LootGamesMinesweeperBridge.java:53` | OK |
| `…minigame.minesweeper.Type` with constant `BOMB` | `…/minesweeper/Type.java` | `LootGamesMinesweeperBridge.java:54,75` | OK (enum constant resolved by `Enum.valueOf`) |
| `…minigame.minesweeper.Mark` with constant `NO_MARK` | `…/minesweeper/Mark.java` | `LootGamesMinesweeperBridge.java:55,77` | OK |
| `…api.util.Pos2i(int,int)` | `src/main/java/ru/timeconqueror/lootgames/api/util/Pos2i.java` | `LootGamesMinesweeperBridge.java:56,70`; `LootGamesSudokuBridge.java:78,116` | OK |
| `…utils.future.BlockPos.getX/getY/getZ` | `…/utils/future/BlockPos.java` | `LootGamesMinesweeperBridge.java:57,71-73`; `LootGamesSudokuBridge.java:81,122-124` | OK |
| `GameMineSweeper$StageWaiting` | `GameMineSweeper.java:256` | `LootGamesMinesweeperBridge.java:58-59` | OK |
| `MSMasterTile.getGame()` (declared on `GameMasterTile`) | `…/api/block/tile/GameMasterTile.java:78`; `MSMasterTile extends BoardGameMasterTile<GameMineSweeper>` (`MSMasterTile.java:7`) | `LootGamesMinesweeperBridge.java:60,110` | OK |
| `GameMineSweeper.isBoardGenerated()` / `getBoard()` / `getStage()` / `getBoardOrigin()` | `GameMineSweeper.java:97`, `:171`, `:384` (`LootGame`), `getBoardOrigin` on `BoardLootGame` | `LootGamesMinesweeperBridge.java:61-63,68,112-114,144,148` | OK |
| `MSBoard.size()` | `MSBoard.java:285` | `LootGamesMinesweeperBridge.java:64,146` | OK |
| `MSBoard.getType(int,int)` / `isHidden(int,int)` / `getMark(int,int)` | `MSBoard.java:67`, `:59`, `:79` | `LootGamesMinesweeperBridge.java:65-67,156,158-159` | OK |
| `GameMineSweeper$StageWaiting.swapFieldMark(Pos2i)` | `GameMineSweeper.java:406` (public, on the **outer** `GameMineSweeper`, inherited by the inner `StageWaiting`) | `LootGamesMinesweeperBridge.java:69,180` | OK |
| **board (x,y) → world (x,z) mapping** | `BoardLootGame.java:75-76`: `convertToBlockPos(Pos2i pos) → getBoardOrigin().offset(pos.getX(), 0, pos.getY())`; `BoardLootGame.java:70-72` `convertToGamePos` is its exact inverse | `LootGamesMinesweeperBridge.java:160-162` `worldX = originX + x; worldY = originY; worldZ = originZ + z` | **OK — matches the engine exactly** (x→X, y→Z, Y pinned to the origin's Y) |
| `SudokuTile`, `GameSudoku`, `SudokuBoard` | `…/common/block/tile/SudokuTile.java:7`; `…/minigame/sudoku/GameSudoku.java:31`; `…/minigame/sudoku/SudokuBoard.java:15` | `LootGamesSudokuBridge.java:75-77` | OK |
| `SudokuTile.getGame()` resolved via `getSuperclass()` | `SudokuTile extends BoardGameMasterTile<GameSudoku>` (`SudokuTile.java:7`) → `GameMasterTile.getGame()` (`GameMasterTile.java:78`) | `LootGamesSudokuBridge.java:85-86` | OK |
| `GameSudoku.getBoard()`; `getBoardOrigin()` via `getSuperclass()`; `getStage()` via 2×`getSuperclass()` | `GameSudoku.java:31` → `BoardLootGame` → `LootGame`; `LootGame.getStage()` exists and is public | `LootGamesSudokuBridge.java:88-95` | OK |
| `LootGame.sendUpdatePacketToNearby(IServerGamePacket)` | `LootGame.java:265` | `LootGamesSudokuBridge.java:97-99,245` | OK |
| `LootGame.save()` | `LootGame.java:221` | `LootGamesSudokuBridge.java:101-103,246` | OK |
| `GameSudoku.onLevelSuccessfullyFinished()` | `GameSudoku.java:268` | `LootGamesSudokuBridge.java:105,251` | OK |
| `SudokuBoard.isGenerated()` / `checkWin()` / `getPlayerValue(Pos2i)` | `SudokuBoard.java:154`, `:220`, `:163` | `LootGamesSudokuBridge.java:108-110,156,243,249` | OK |
| `SudokuBoard.player` / `.solution` / `.puzzle` as **public** `Integer[9][9]` | `SudokuBoard.java:21`, `:22`, `:23` (all `public`, `SIZE = 9` at `:17`) | `LootGamesSudokuBridge.java:111-113,201-203,238-239,297` | **OK — field types, visibility and 9×9 size all verified** |
| `SPSSyncCell(Pos2i,int)` | `…/common/packet/game/sudoku/SPSSyncCell.java:10,19,21` | `LootGamesSudokuBridge.java:79,119,244` | OK |
| `GameSudoku$StageWaiting` | `GameSudoku.java:318` | `LootGamesSudokuBridge.java:80` | OK |
| `…api.packet.IServerGamePacket` | `SPSSyncCell implements IServerGamePacket` (`SPSSyncCell.java:10`) | `LootGamesSudokuBridge.java:82` | OK |
| **sudoku (x,y) → world (x,z) mapping** | same `BoardLootGame.convertToBlockPos`; engine's own usage confirms it: `GameSudoku.java:160-172` spawns particles at `origin.getX() + col`, `origin.getZ() + row`, reading `board.getPuzzleValue(col, row)` / `board.getPlayerValue(new Pos2i(col, row))` | `LootGamesSudokuBridge.java:216-218` `worldX = originX + x; worldY = originY; worldZ = originZ + y` | **OK — matches the engine's own (col,row)→(X,Z) convention exactly** |
| **cell-fill write path correctness** | engine writes via `SudokuBoard.cSetPlayerValue(Pos2i,int)` (`SudokuBoard.java:274-280`, guards `puzzle[r][c] == 0`) and syncs with `SPSSyncCell`; client packets go through `getPlayerValue(Pos2i)` | `LootGamesSudokuBridge.java:238-246` writes `playerGrid[boardX][boardY] = solutionValue` **directly** (no `cSetPlayerValue` guard) and syncs via `SPSSyncCell` + `save()` | **OK for the intended use** — the bridge only ever writes cells it already verified as `puzzleGrid[x][y] == 0` (`:208`), so the skipped guard is unreachable. Not a bug. |
| sudoku "clue cell" test | engine: `puzzle[r][c] != 0` means pre-filled (`SudokuBoard.java:187,200,277`) | `LootGamesSudokuBridge.java:208` `if (puzzleGrid[x][y] != null && puzzleGrid[x][y] != 0) continue;` | OK |
| sudoku win check after a fill | `SudokuBoard.checkWin()` requires every cell `!= null && != 0 && equals(solution)` (`:220-225`) | `LootGamesSudokuBridge.java:249-252` | OK |
| `isAnyGameActive` stage test | `GameMineSweeper.java:256`, `GameSudoku.java:318` are the only `StageWaiting` inner classes for these two games (grep `class StageWaiting` → 4 hits total, the other two are `GameOfLight`'s differently-named stages) | `LootGamesMinesweeperBridge.java:113-116`, `LootGamesSudokuBridge.java:157-160` | OK |

**Conclusion for item 7**: the LootGames bridges are **field-for-field and convention-for-convention correct** against
`tmp/LootGames-master`. No board corruption, no silent no-op. This was the highest-risk item in the task and it
came back clean.

### 2.6 VisualProspecting (task item 8) — version drift

| API element | `VisualProspecting-1.4.8` | `VisualProspecting-master` | EZMiner touch point | verdict |
|---|---|---|---|---|
| `com.sinthoras.visualprospecting.VisualProspecting_API$LogicalServer` | `src/main/java/com/sinthoras/visualprospecting/VisualProspecting_API.java:64` | `…/VisualProspecting_API.java:76` | `chain/execution/VisualProspectingBridge.java:39-40` | OK |
| `LogicalServer.prospectOreVeinsWithinRadius(int dimensionId,int blockX,int blockZ,int blockRadius) → List<OreVeinPosition>` | `VisualProspecting_API.java:92` (`:92-93`) | `…:103` | `VisualProspectingBridge.java:41-42,78,98` | **OK — same 4 ints, same return type, both versions** |
| `LogicalServer.sendProspectionResultsToClient(EntityPlayerMP,List<OreVeinPosition>,List<UndergroundFluidPosition>)` | `VisualProspecting_API.java:81-82` | `…:98-99` | `VisualProspectingBridge.java:43-44,80,105` (`Collections.emptyList()` for fluids) | **OK in both versions** — the generic `List` params erase identically, so the `getMethod(…, List.class, List.class)` lookup succeeds on both |
| `OreVeinPosition.getBlockX()` / `getBlockZ()` | `OreVeinPosition.java:33`, `:37` | `:39`, `:43` | `VisualProspectingBridge.java:46-47,101-102` | OK |
| `OreVeinPosition.veinType` (**public field**) | `OreVeinPosition.java:14` | `:24` | `VisualProspectingBridge.java:48,106` | **OK — remains a public field in both versions**; note 1.4.8 also has a public `EMPTY_VEIN` sentinel (`:9`) that `prospectOreVeinsWithinRadius` may return — EZMiner only reads `veinType.getVeinName()` from it, which is harmless. |
| `VeinType.getVeinName()` | `VeinType.java:112` | `:136` | `VisualProspectingBridge.java:50,107` | OK |

**Version-drift verdict: none.** VisualProspecting 1.4.8 → master changed line numbers only for the API surface
EZMiner uses. No finding.

### 2.7 ModularUI2 (task item 9)

`ModularUI2` is **not used by EZMiner at all**:

- `dependencies.gradle` (whole file read) contains no `modularui` entry. The build deps are: `joml`, `GT5-Unofficial`,
  `LootGames`, `witchery`, `WitcheryExtras`, `Natura`, `CropsNH` (compileOnly), `TinkersConstruct`
  (runtime + compileOnly), `IguanaTweaksTConstruct`, `VisualProspecting`, `ServerUtilities`, `InGame-Info-XML`,
  JourneyMap, `StructureLib`, `NotEnoughItems`, `Avaritia`, `neiaddons`, `Binnie`, `BlockRenderer6343`,
  `worldedit-gtnh`, `spark`, `extrautilities`, `fastutil`, `auto-value`.
- grep over all of `src/main/java` for `modularui|ModularUI|mclib` → **0 matches**.
- `tmp/ModularUI2-master` has 645 `.java` files and no EZMiner-facing entry point.

**Verdict: N/A — EZMiner does not use ModularUI2, and it cannot affect EZMiner's GUI.** EZMiner's GUI is
hand-rolled (`client/gui/EZMinerConfigGui.java`, `client/gui/HudConfigGui.java`, `client/gui/TexturedButton.java`)
and its only third-party GUI integration is the ServerUtilities *positioning* probe (§2.9) and the
`GuiIngameMenu` pause-menu mixin (`src/main/java/com/czqwq/EZMiner/mixin/early/MixinGuiIngameMenu.java:30-41`),
which targets vanilla `GuiIngameMenu` only and is gated on `EZMiner.isDeobfuscatedEnvironment` (`:37`). No
ModularUI2 interaction is possible in either direction.

### 2.8 Witchery vampire API (task item 10)

`tmp/witchery-1.7.10-0.24.1` is a **classes-only tree** (deobfuscated class files + assets; no `.java`). What
could be verified and what could not:

| API element | evidence from the classes-only tree | EZMiner touch point | verdict |
|---|---|---|---|
| mod id `witchery` | `tmp/witchery-1.7.10-0.24.1/mcmod.info`: `"modid": "witchery"`, `"mcversion": "1.7.10"` | `compat/WitcheryVampireBridge.java:28` `Loader.isModLoaded("witchery")` | **OK — exact** |
| `com.emoniph.witchery.common.ExtendedPlayer` exists | `tmp/witchery-1.7.10-0.24.1/com/emoniph/witchery/common/ExtendedPlayer.class` present | `WitcheryVampireBridge.java:69` | OK |
| `ExtendedPlayer.get(EntityPlayer) → ExtendedPlayer` (static) | constant-pool signature recovered from the class file: `(Lnet/minecraft/entity/player/EntityPlayer;)Lcom/emoniph/witchery/common/ExtendedPlayer;` with the method name `get` | `WitcheryVampireBridge.java:69` | **OK — exact parameter/return signature match** |
| `ExtendedPlayer.isVampire()` | method name `isVampire` present in the class constant pool | `WitcheryVampireBridge.java:71` | **OK (name verified) — return type UNKNOWN**: the pool confirms the name but I did not decode the `Methodref` descriptor to confirm it returns `boolean`. If it returned an `int` level, `&&` at `:71` would not compile — but EZMiner is compiled against the real jar (`dependencies.gradle:15` `implementation(deobfCurse('witchery-69673:2234410'))`), so type-correctness is guaranteed by the build. Residual: none. |
| `ExtendedPlayer.getVampireLevel()` | method name `getVampireLevel` present in the constant pool | `WitcheryVampireBridge.java:71` | **OK (name verified)**, return type `int` inferred from `>= 5` and guaranteed by the compile-time dependency. |
| that level `5` is the bare-hand-mining threshold | `getVampireLevel` + `vampireLevel`/`VampireLevel` strings present; **the threshold constant itself is a Witchery behaviour**, not an API | `WitcheryVampireBridge.java:31` `VAMPIRE_MINING_LEVEL = 5`, used at `:71` | **UNKNOWN** — the "level 5 ⇒ bare-hand stone mining" rule could not be confirmed from a classes-only tree without decompiling `ExtendedPlayer`/the base-break hook. Evidence needed: `javap -c -p ExtendedPlayer` looking for a `getVampireLevel() >= 5` comparison in the `canHarvest`/`getDigSpeed` path, or Witchery's `EnchantmentVampire`/`PlayerHandler` source. Impact if wrong: the OR at `BlockHarvestActionExecutor.java:92,182,262`, `ChunkCachedHarvester.java:169`, `BaseOperator.java:429,485,561`, `XPDropHandler.java:61` and all 8 founder gates would grant bare-hand harvest at the wrong vampire level. |
| `WitcheryExtras` interaction | `dependencies.gradle:16` `implementation(gtnhDev("WitcheryExtras"))` | — | UNKNOWN/N/A — WitcheryExtras is not in my task scope and has no tree under `tmp/`; **flagging only that a WitcheryExtras mixin could alter `ExtendedPlayer` semantics**, which is outside what this tree can settle. |

### 2.9 ServerUtilities permission API surface (task item 11)

| API element | `tmp/ServerUtilities-master` site | EZMiner touch point | verdict |
|---|---|---|---|
| `serverutils.lib.util.permission.PermissionAPI` | `src/main/java/serverutils/lib/util/permission/PermissionAPI.java:16` | **not used** — grep for `serverutils|ServerUtilities|hasPermission|registerNode|PermissionAPI|isOP` over `src/main/java/com/czqwq/EZMiner/permission/` → **0 matches** | **N/A** |
| `EntityPlayerMP.canCommandSenderUseCommand(int,String)` used by EZMiner instead | vanilla MC | `permission/OpPermissionChecker.java:34-62`; `command/ReloadConfigCommand.java:47-52,130,160`; `network/PacketReloadServerConfig.java:38` | **OK** — EZMiner's permission model is vanilla-OP + integrated-server-owner (`OpPermissionChecker.java:44-53`) + a per-world whitelist (`permission/ServerOwnerWhitelist.java`), all via `UserListOpsEntry` (`:34-37`) and `ConfigurationManager.func_152596_g` (`:48-49`). It therefore **cannot** be broken by a ServerUtilities permission-API change, and it does **not** honour ServerUtilities permission nodes (a documented design choice in `OpPermissionChecker.java:6-12`, not breakage). |
| `PermissionAPI.getPermissionHandler()` / `registerNode(String,DefaultPermissionLevel,String)` / `hasPermission(GameProfile,String,IContext)` / `hasPermission(EntityPlayer,String)` | `PermissionAPI.java:32`, `:44`, `:63`, `:75`; `ServerUtilitiesPermissions.registerPrefix(...)` at `src/main/java/serverutils/ServerUtilitiesPermissions.java:381`; `ServerUtils.isOP(...)` at `src/main/java/serverutils/lib/util/ServerUtils.java:48,64` | not used | N/A — recorded as the API surface EZMiner *deliberately does not* consume. |
| ServerUtilities GUI positioning probe | `src/main/java/serverutils/client/gui/GuiSidebar.java:31` `public class GuiSidebar extends GuiButton` | `client/gui/InventoryButtonOverlay.java:248` `Class.forName("serverutils.client.gui.GuiSidebar")`, then `:263` `suSidebarClass.isInstance(btn)` over the `GuiScreen.buttonList` | **OK — the class exists at that FQN and is a `GuiButton` subclass, so the `isInstance` scan of `buttonList` is valid.** Probe is fully guarded (`:245-257` swallows `Throwable`, `:267` rejects zero-size, `:273` rejects out-of-screen bounds), so absence or drift degrades to "no repositioning". |

### 2.10 Qz-Miner — is it an implementation EZMiner must stay compatible with? (task item 12)

**Verdict: it is a *sibling/upstream* implementation, not a mod EZMiner must stay binary-compatible with — but
another instance of it can be present at runtime, and there is one real interaction hazard.**

| fact | tmp site | EZMiner touch point | verdict |
|---|---|---|---|
| Qz-Miner is a **1.7.10 GTNH chain-mining mod** | `tmp/Qz-Miner/gradle.properties`: `modName = Qz Miner`, `modId = qz_miner`, `minecraftVersion = 1.7.10`, `forgeVersion = 10.13.4.1614`; `tmp/Qz-Miner/README.md:11` | — | same target platform as EZMiner |
| EZMiner's `ToolHarvestEligibility` is **derived** from Qz-Miner's | `tmp/Qz-Miner/src/main/java/club/heiqi/qz_miner/toolswap/ToolHarvestEligibility.java:14,19,44,65,77` | `src/main/java/com/czqwq/EZMiner/utils/ToolHarvestEligibility.java:14,18,51,55,88,100` | **OK as provenance**; **drift is intentional** — see next row |
| GT-tool handling differs **by design** | Qz-Miner has **no** GT tool branch (`ToolHarvestEligibility.canHarvest` at `:44-62` always uses `ForgeHooks.canToolHarvestBlock` / `Item.canHarvestBlock`) | `ToolHarvestEligibility.java:61-63` routes GT tools to `GT5ToolCompat.canGTToolMineBlock`, `:108-110` rejects target-less GT tools; `:35-40` adds TiC NBT durability | **OK — EZMiner is the stricter/correct one.** Because GT tools report the same class-agnostic harvest level for every tool type, ForgeHooks alone would accept a wrench for stone (documented at `:58-60`). Do **not** "sync" this back to Qz-Miner's rule. |
| `isEffective` / `canHarvestWithEmptyHand` / `snapshotCandidate` / `snapshotIdentity` / `stableSubtype` / `oreNames` | `ToolHarvestEligibility.java:25,30,71→108`, used by `MinecraftAutoToolSwapCandidateSource.java:81,90` and `chain/planner/PlanningToolCapabilitySnapshot.java:65,91,95,99` | EZMiner has `canHarvest`/`isEligible`/`remainingDurability`/`hasDurabilityReserve`/`isUsableMiningTool` but **no** `isEffective`, `snapshot*`, `stableSubtype` or `oreNames` | **OK (no compatibility obligation)** — these are Qz-Miner-internal (`tmp/Qz-Miner/AGENTS.md` declares `Qz-Miner -> Qz-UILib` as the only dependency direction, and `club.heiqi.qz_miner` is a private package). EZMiner does not import or reflect into `club.heiqi.*` (verified: grep for `qz_miner|QzMiner|heiqi` in `src/` → only 3 doc-comment hits at `utils/ToolHarvestEligibility.java:14,51` and `toolswap/server/ToolSwapServerLedger.java:13`, `ToolSwapInventoryPort.java:9`). |
| **default chain key collides** | `tmp/Qz-Miner/README.md:15`: "默认按键为 ``~`` 所在键位" (default key is the `~` key) | `src/main/java/com/czqwq/EZMiner/client/KeyListener.java:39-41`: `KEY_CHAIN = new KeyBinding(..., Keyboard.KEY_GRAVE, ...)` — the same key | **BROKEN-in-combination (P2)** — with **both mods installed**, holding `~` starts **both** chain-miners on the same `BlockEvent.BreakEvent`/dig. They would double-mine, double the drop collector's view, run two independent `ItemInWorldManager.tryHarvestBlock`/EBS writers on the same positions, and both would fire the TiC `beforeBlockBreak` bridge. EZMiner has **no** `qz_miner` detection and no yield path (unlike the Bandit yield at `core/Manager.java:262,292`). | **Minimal fix**: treat this exactly like the Bandit/Ultimine hazard — add a startup check for `Loader.isModLoaded("qz_miner")` (or a client-side warning) and either change EZMiner's default `KEY_GRAVE` binding or log a prominent conflict warning. Do **not** silently yield mining, since until now there was no code path to yield to. |

---

## 3. Matrix coverage statement (acceptance)

| task item | mod / tree | cited tmp site(s) | EZMiner touch point(s) | verdicts issued |
|---|---|---|---|---|
| 1 | `GT5-Unofficial-5.09.54.133`, `GT5-Unofficial-beta2` | §2.1 (30+ sites) | §2.1 | OK / BROKEN (T2, T3, T4) |
| 2 | both GT5U trees | §2.1 block-swap rows | §2.1 | OK / BROKEN (T5) + 1 UNKNOWN (connection bit mapping) |
| 3 | both GT5U trees | §2.2 | §2.2 | OK / BROKEN (T6) |
| 4 | `TinkersConstruct-master`, `IguanaTweaksTConstruct-master` | §2.3 | §2.3 | OK (all) |
| 5 | `FTB-Ultimine-main` | §2.4 | §2.4 | **N/A (version-mismatched)** + UNKNOWN for 1.7.10 behaviour |
| 6 | `Bandit-Legacy-master` | §2.4 | §2.4 | OK (all) |
| 7 | `LootGames-master` | §2.5 | §2.5 | OK (all) |
| 8 | `VisualProspecting-master`, `VisualProspecting-1.4.8` | §2.6 | §2.6 | OK (no drift) |
| 9 | `ModularUI2-master` | §2.7 | §2.7 | **N/A — EZMiner does not touch it** |
| 10 | `witchery-1.7.10-0.24.1` | §2.8 | §2.8 | OK (names/signatures) + UNKNOWN (level-5 rule) |
| 11 | `ServerUtilities-master` | §2.9 | §2.9 | **N/A for the permission package** (EZMiner uses vanilla OP) + OK for the GUI probe |
| 12 | `Qz-Miner` | §2.10 | §2.10 | OK (provenance/no coupling) + P2 key collision |
| — | `jd-manifest` | **not present** | — | see §4 |

Every row in §2 cites at least one `tmp …:<line>` site and at least one `src/…:<line>` touch point, or
explicitly states that EZMiner does not touch the API. No site was invented: every GT5U/TiC/Iguana/LootGames/
VisualProspecting/ServerUtilities/Bandit path above was opened with `read`/`grep` in this session.

---

## 4. Version-mismatch, not-applicable and tree-format labels

| tree | label | reason |
|---|---|---|
| `tmp/GT5-Unofficial-5.09.54.133` | **older-generation, present at build time only** | `minecraftVersion` 1.7.10 but **pre-**`gregtech.common.ores.*`; EZMiner's mixins import that package, so this tree can never be the compile target. Kept as the runtime-compatibility reference for the reflection bridges. |
| `tmp/GT5-Unofficial-beta2` | **compile-target generation** | contains `GTBlockOre`/`BlockOresAbstractLegacy`/`gregtech.common.ores.*`, matching `dependencies.gradle:4` `gtnhVersion = "2.9.0-beta-3"`. |
| `tmp/FTB-Ultimine-main` | **not-applicable (wrong MC version)** | `minecraft_version=26.1.2`, Forge/NeoForge `26.1.2.22-beta`, Fabric `0.18.4`; loaders `common/fabric/neoforge` only — no 1.7.10 loader. |
| `tmp/witchery-1.7.10-0.24.1` | **classes/jar only** | shipped as `.class` + assets, no `.java`. Verified via `mcmod.info` and raw class-file constant-pool strings; decompilation (`javap`) is unavailable in this sandbox. |
| `tmp/ModularUI2-master` | **not-applicable** | no EZMiner dependency, reference, or GUI interaction (§2.7). |
| `tmp/jd-manifest` | **EMPTY DIRECTORY — nothing to audit** | `Get-ChildItem -Path tmp/jd-manifest -Recurse -Force` returns **0 entries** (verified with hidden/forced enumeration; the `tmp/` top-level listing showed the name, which is why it was in scope). There is no manifest, class or source to cross-check. **Verdict: N/A (empty)** — not "UNKNOWN", because the enumeration is conclusive: there is nothing to be compatible or incompatible with. For completeness, **no EZMiner code reads a `jd-manifest`** (grep over `src/main/java` → 0 hits). |
| `tmp/Qz-Miner` | **related sibling implementation, same MC version** | 1.7.10 GTNH chain miner; provenance for `ToolHarvestEligibility`/`PhysicalLedger` ports. Not a binary compatibility target (private package, `Qz-Miner -> Qz-UILib` only), but a runtime conflict source via the shared default chain key (§2.10). |
| `tmp/ServerUtilities-master` | **relevant tree, but the permission half is N/A** | EZMiner's `permission/` package does not consume the ServerUtilities permission API at all. |

---

## 5. UNKNOWN list (with the evidence that would settle each)

| # | unknown | why it is UNKNOWN here | evidence that would settle it | blast radius if wrong |
|---|---------|------------------------|-------------------------------|-----------------------|
| U1 | `PickResults.suggestedTools()` returns `List<Enum<?>>` and `forceDeselect()` returns `boolean` (the casts at `compat/GT5ToolCompat.java:227,230`) | I resolved both by **name + arity only** (`GT5ToolCompat.java:120-130`); the return types are asserted by casts, not verified from source. `PickResults.java` exists only in `tmp/GT5-Unofficial-beta2` and I did not open the deeper `pickblock` package body. | Read `tmp/GT5-Unofficial-beta2/src/main/java/gregtech/common/items/toolbox/pickblock/PickResults.java`, or `javap -p -c` on the shipped class. | A wrong cast throws `ClassCastException` inside `getToolboxBestInternalSlot`, which is caught at `GT5ToolCompat.java:238` → toolbox switching silently returns `-1`. Low severity, no corruption. |
| U2 | the `mConnections` **bit-to-ForgeDirection** convention (`1 << i` for DOWN=0 … EAST=5) used by `GT5BlockSwapCompat.getConnectedDirections` (`:140-150`) | Only the *field* (`public byte mConnections` in both `BaseMetaPipeEntity` and `MetaPipeEntity`) was verified. The packing convention lives in the writers of that field, which I did not open in this session. | Read `tmp/GT5-Unofficial-*/src/main/java/gregtech/api/metatileentity/IConnectable.java` and every `mConnections` writer in `BaseMetaPipeEntity`. | Saved cable connections are restored to the wrong sides after a block swap. Cosmetic/functional; no item dupe or deletion. |
| U3 | Witchery's "vampire level ≥ 5 ⇒ bare-hand stone mining" rule (`WitcheryVampireBridge.java:31,71`) | Classes-only tree; the threshold is Witchery *behaviour*, not an API name. All I could verify is that `isVampire`/`getVampireLevel` exist (`ExtendedPlayer.class` constant pool) and that `get(EntityPlayer)` has the exact shape `(Lnet/minecraft/entity/player/EntityPlayer;)Lcom/emoniph/witchery/common/ExtendedPlayer;`. | Decompile `ExtendedPlayer` / Witchery's base-break hook (`javap -c -p`) and locate the `getVampireLevel() >= 5` comparison, or read Witchery source. | Bare-hand harvest is granted at the wrong vampire level in 12 gate sites (`BaseOperator.java:429,485,561`, `XPDropHandler.java:61`, all 8 founders, `BlockHarvestActionExecutor.java:92,182,262`, `ChunkCachedHarvester.java:169`). |
| U4 | whether a **1.7.10-era** FTB-Ultimine intercepts the same input/event as EZMiner's chain key | `tmp/FTB-Ultimine-main` is `minecraft_version=26.1.2` (NeoForge/Fabric). I explicitly do **not** force a conclusion from a modern tree. | A 1.7.10 FTB-Ultimine tree (or its `mcmod.info`), then check for a `BlockEvent.BreakEvent` handler / keybind. | If it does intercept, this becomes the same class of hazard as §2.10's key collision |
| U5 | ~~`tmp/jd-manifest` contents/purpose~~ | **RESOLVED — directory is empty** (0 entries under forced recursive enumeration); recorded in §4 as N/A, no longer an unknown. | — | None: no EZMiner code reads a `jd-manifest`. |
| U6 | order of `ADAPTERS` iteration when both `BlockOresAbstractLegacy` and `GTBlockOre` are present (not possible in either tree, but not provably impossible in a mixed pack) | Only two GT5U generations exist in `tmp/`; the dispatch in `OreCompatRegistry.isOreBlock` (`:54-63`) is first-match-wins and unordered, and `DeterminingIdentical.isGTLargeVeinOre` (`:373-392`) is explicit-first-match. No tree exercises the mixed case. | A GT5U version shipping both class hierarchies (or a pack mixing them). | Mis-classified large-vs-small ore in `GtVeinOreFounder`. Recorded as a latent risk, not a confirmed break. |

---

## 6. What I did *not* verify (out-of-scope / other trees)

These are outside this task's in-scope path list and are named so the captain can route them, not to make claims:

- `tmp/Hodgepodge-master`, `tmp/CoFHCore-1.12-Legacy-1.7.10`, `tmp/EnderIO-master`,
  `tmp/EndlessIDs-master`, `tmp/Et-Futurum-Requiem-master`, `tmp/ForestryMC-master`,
  `tmp/Galacticraft-master`, `tmp/Natura-master`, `tmp/thaumcraft-src`, `tmp/tc-classes`, `tmp/tc-lang`,
  `tmp/tc-quick`, `tmp/tc-quick2`, `tmp/Thaumcraft-1.7.10-4.2.3.5-dev.jar` — **t6 / other members' scope**.
  Where they touch `src/main/java/…/compat/ore/OreCompatRegistry.java:136-147` (Et Futurum) I only confirmed
  that the adapter is registered by class name, not the class itself.
- `tmp/WitcheryExtras` has **no tree** under `tmp/` even though `dependencies.gradle:16` depends on it. Flagged
  in §2.8 as the one Witchery-adjacent unknown I could not chase.

## 7. Change statement

Only `docs/review/agent-teams/tmp-tools-breakage.md` (this file) was written. No file under `src/` or `tmp/` was
created, modified, or deleted; no Gradle or JVM process was started (the sandbox denies external program
execution, and the pwsh commands used here were file reads, directory listings, `Select-String`/grep and raw
byte reads only).
