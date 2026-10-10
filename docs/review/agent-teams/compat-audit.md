# Compat-bridge & Mixin Audit — `audit-infra-ds` (task t5, attempt 5)

**Scope**: `src/main/java/com/czqwq/EZMiner/compat/**` (16 files incl. `compat/ore/*`),
`src/main/java/com/czqwq/EZMiner/mixin/**` (10 files incl. `early/*`, `interfaces/*`),
`src/main/resources/mixins.EZMiner.json`, `src/main/resources/mixins.EZMiner.late.json`.

**Method**: read-only `read`/`grep`/`glob` over the current tree plus every third-party source
named in the task under `tmp/`, and — because they exist in this checkout — the 1.7.10
decompiled sources (`build/rfg/minecraft-src/java/**`), the generated refmap
(`build/tmp/mixins/mixins.EZMiner.refmap.json`), the built manifest
(`build/tmp/jar/MANIFEST.MF`) and the released mod jar (`build/libs/EZMiner-6.1.jar`,
read in-memory via `System.IO.Compression`, no extraction). No file outside this report was
created or modified; no Gradle/Java process was executed.

Severity legend (same intent as `docs/review/full-bug-scan.md`):
**P1** = feature silently dead / wrong behaviour / item or XP loss; **P2** = edge case or
config-dependent divergence; **P3** = dead code, doc drift, robustness.

**Headline**: the *class/method/field names* of every reflective hook are correct for the
GT5U generation this project actually resolves (**GT5-Unofficial-beta2**), and van-Target
mixins resolve correctly in the shipped jar — but (a) **three of the five mixins are
version-locked to the new ore API and MISSING on `GT5-Unofficial-5.09.54.133`**, with no
legacy-generation fortune coverage at all (**[C1](#c1)**, **[C2](#c2)**), (b) the fast-harvest
mixin diverges from vanilla `tryHarvestBlock` in four concrete ways
(**[C4](#c4)/[C5](#c5)/[C6](#c6)**), and (c) **`GT5ToolCompat.init()` is only called from the
client proxy**, so every GT-tool branch is dead on a dedicated server (**[C3](#c3)**).

---

## 1. Findings table

| id | sev | file:line | one-line problem |
|----|-----|-----------|------------------|
| [C1](#c1) | **P1** | `mixin/early/MixinGTOreAdapter.java:16`, `MixinBWOreAdapter.java:16`, `MixinGTPPOreAdapter.java:14` | All three fortune-uncap mixins target `gregtech.common.ores.*`, which **does not exist in `tmp/GT5-Unofficial-5.09.54.133`** (no `gregtech/common/ores` package) — on that generation the feature is a complete no-op and the sources cannot even compile against it |
| [C2](#c2) | **P1** | `src/main/resources/mixins.EZMiner.json:11-19` | No `plugin`, no `@Pseudo`, no `require = 1` on any injection — a GT5U shape change becomes a **silent no-op** instead of a loud failure; upstream `tmp/Qz-Miner` gates the same three mixins through an `IMixinConfigPlugin` + `require = 1` |
| [C3](#c3) | **P1** | `client/ClientProxy.java:29` (only caller of `GT5ToolCompat.init()`) | On a dedicated server `GT5ToolCompat.gtLoaded` stays `false` forever → `ToolHarvestEligibility.canHarvest` falls through to `ForgeHooks.canToolHarvestBlock`, which the class's own comment (`utils/ToolHarvestEligibility.java:58-60`) says **wrongly accepts a GT wrench for stone** |
| [C4](#c4) | **P2** | `mixin/early/MixinItemInWorldManager.java:108` vs `build/rfg/.../ItemInWorldManager.java:270` | The fast path never calls `Block.removedByPlayer(...)`; GT's `BlockReinforced.removedByPlayer` (powder barrel, `tmp/GT5-Unofficial-beta2/.../BlockReinforced.java:304-319`) and any other non-TE override are silently ignored |
| [C5](#c5) | **P2** | `mixin/early/MixinItemInWorldManager.java:122-130` vs `build/rfg/.../ItemInWorldManager.java:330` | XP is dropped for **creative** players when `Config.fireBreakEvent=true` (vanilla guards with `!isCreative()`); `handlePreComputedXP` has no creative check |
| [C6](#c6) | **P2** | `mixin/early/MixinItemInWorldManager.java:61-81` | Only shears + TiC `ActiveToolMod` are replayed from `Item.onBlockStartBreak`; GT `MetaGeneratedTool.onBlockStartBreak` (`beta2 MetaGeneratedTool.java:307-347`) is lost — GT-chainsaw leaves and the `isSaw()` ice/packed-ice drop-and-consume branch no longer work on chained blocks |
| [C7](#c7) | **P2** | `compat/EtFuturumCropCompat.java:94` | `harvest()` calls `Block.onBlockActivated` directly → no `ForgeEventFactory.onPlayerInteract` / `PlayerInteractEvent`, so protection mods and ServerUtilities' vanish gate (`tmp/ServerUtilities-master/.../vanish/MixinItemInWorldManager.java:24-46`) cannot veto chained crop harvesting |
| [C8](#c8) | **P2** | `compat/EtFuturumOreCompat.java:26-27`, `EtFuturumCropCompat.java:40-41` | `initialized = true` is set **before** the class lookups; a second racing caller observes `isLoaded() == false` permanently (the registry path happens to call them in order, `CropAdapterRegistry.java:46-47`) |
| [C9](#c9) | **P3** | `compat/BushSupportBridge.java:45` | Not an exact `BlockBush.checkAndDropBlock` mirror: vanilla uses `setBlock(...,air,0,2)` (`BlockBush.java:75`), the bridge uses `setBlockToAir` (flag **3** → extra neighbour notifications, `World.java:649-652`) plus a redundant `markBlockForUpdate` |
| [C10](#c10) | **P3** | `mixin/early/MixinGuiIngameMenu.java:37` | The client "Mod Options" replacement is a **production no-op** (`EZMiner.isDeobfuscatedEnvironment`, `EZMiner.java:40`) yet the mixin sits in the config's **common** `mixins` array while `"client": []` is empty — the registration risk (dedicated server) is bought for zero production benefit (cross-ref `infra-audit.md` F8) |
| [C11](#c11) | **P3** | `compat/TinkersConstructCompat.java:96` | `canContinueMining` refuses at `remaining == 1` while TiC itself only breaks at `damageTrue > maxDamage` (`AbilityHelper.java:384`) — a conservative one-block-off difference |
| [C12](#c12) | **P3** | `compat/NaturaSaguaroCompat.java:26-32` | The class javadoc's mechanism claim is wrong (Natura's `SaguaroBlock.onNeighborBlockChange(World,int,int,int,int)`, `SaguaroBlock.java:213`, does **not** override `Block.onNeighborBlockChange(…,Block)` on 1.7.10), which makes `cascadeUnsupportedNeighbors` load-bearing rather than a robustness net |
| [C13](#c13) | **P3** | `CLAUDE.md` §"Mixin changes require a game restart"; `docs/review/perf-mixin-review.md:163` | Docs name a non-existent field `Config.fortuneOverrideEnabled` and claim restart-required semantics; the handlers read `Config` per call (hot), and the three fortune fields are **not synced** (cross-ref `infra-audit.md` §2 rows 53-55) → client/server divergence in ore-drop display |
| [C14](#c14) | **P3** | `mixin/Mixins.java:13`, `mixin/TargetMod.java:8`, `mixins.EZMiner.late.json:4` | Late-mixin infrastructure is dead (empty enum → `getLateMixins` always `[]`), the late config's `package` (`…mixin.late`) does not exist, and `MixinCapabilityPlugin` is unreachable (re-verified from `infra-audit.md` F7) |

---

## 2. Findings — detail

### C1
**The fortune-uncap mixins only exist for the new ore API; the whole feature is a no-op on GT5U 5.09.54.133.**

EZMiner's three mixins hard-reference the new-generation ore adapters:
```java
// mixin/early/MixinGTOreAdapter.java:13-16
import gregtech.common.ores.GTOreAdapter;
import gregtech.common.ores.OreInfo;
@Mixin(value = GTOreAdapter.class, remap = false)
```
Verified target existence:

| class | `GT5-Unofficial-beta2` | `GT5-Unofficial-5.09.54.133` |
|---|---|---|
| `gregtech.common.ores.GTOreAdapter` | present (`.../gregtech/common/ores/GTOreAdapter.java`, `class` at `:38`) | **absent — no `gregtech/common/ores` package exists at all** |
| `gregtech.common.ores.BWOreAdapter` | present (`:1`) | **absent** |
| `gregtech.common.ores.GTPPOreAdapter` | present (`:22`) | **absent** |
| `gregtech.common.ores.OreInfo` (`public boolean isNatural`, `OreInfo.java:50`) | present | **absent** |

The 5.09.54.133 generation clamps fortune in the *legacy* paths instead —
`tmp/GT5-Unofficial-5.09.54.133/src/main/java/gregtech/common/blocks/TileEntityOres.java:339`
(`if (aFortune > 3) aFortune = 3;`, inside `getDrops(Block,int)` at `:299`, guarded by
`shouldFortune && this.mNatural` at `:337`, field `mNatural` at `:35`) and
`.../gtPlusPlus/core/block/base/BlockBaseOre.java:163` (`if (fortune > 3) fortune = 3;` inside
`getDrops(World,int,int,int,int,int)` at `:146`).

**Failure scenario.** On a pack whose GT5U is 5.09.54.133 (or any generation without
`gregtech.common.ores`): (1) the mod cannot be compiled against that GT5U because the mixin
classes import the missing types; (2) if a jar built against beta2 is dropped into such a pack,
all three mixins fail their target lookup while `mixins.EZMiner.json` lists them
unconditionally → `enableUnlimitedOreFortune` / `enableFortuneForPlacedOre` are **silent
no-ops** (no clamp is bypassed anywhere), and `Config` still advertises them.

**Evidence that this is a real coverage gap and not an oversight in this report**: the upstream
project EZMiner's fortune compat was copied from (`utils/FortuneCompatHelper.java:5`
"Compat code was from the Qzminer") keeps **three additional mixins for exactly that
generation**:
`tmp/Qz-Miner/src/main/java/club/heiqi/qz_miner/mixins/MixinTileEntityOresLegacy.java:14`
(`targets = "gregtech.common.blocks.TileEntityOres"`, `@Expression("fortuneLevel > 3")` on
`getDrops(Lnet/minecraft/block/Block;I)` — matches the real clamp at `TileEntityOres.java:339`),
`.../MixinBlockBaseOreLegacy.java:14` (`targets = "gtPlusPlus.core.block.base.BlockBaseOre"`,
fortune local `ordinal = 4` — matches the real signature at `BlockBaseOre.java:146`), and
`.../MixinBWTileEntityMetaGeneratedOreLegacy.java:14` (BW legacy TE, `getDrops(int)` at
`BWTileEntityMetaGeneratedOre.java:68`).
EZMiner has none of them.

**Minimal fix.** Either declare 09.54.133 unsupported and *gate* the three mixins (C2) so they
degrade to "feature off + one startup log line", or port the three legacy mixins from
`tmp/Qz-Miner` (they are 20-30 lines each) into a **late** config so they are only applied when
the legacy classes exist.

**Confidence**: high for the code facts (package/class/file existence checked in both trees;
clamp lines read). Medium for "which generation the dev was targeting": `dependencies.gradle:5`
pins `gtnhVersion = "2.9.0-beta-3"` and the sources compile only against a GT5U that already has
`gregtech.common.ores`, i.e. beta2 is the intended target; 5.09.54.133 is still shipped in `tmp/`
and explicitly named by the task as a first-class drift axis.

### C2
**Nothing gates the mixins: no config plugin, no `@Pseudo`, no `require`.**

* `src/main/resources/mixins.EZMiner.json:1-20` — the file has `"required": false`,
  `"mixinextras"`, `"package"`, `"refmap"` and the five class names; there is **no `"plugin"`
  key** (grep for `"plugin"` over `src/main/resources` finds nothing) and `gradle.properties:131`
  leaves `mixinPlugin =` empty.
* `mixin/Mixins.java:13` is `public enum Mixins { ; }` — **zero constants**, so
  `getLateMixins()` (`:27-47`) always returns an empty list and
  `MixinClass.addBytecodeCondition()` (`:123-127`) is never called; `MixinCapabilityPlugin`
  (`MixinCapabilityPlugin.java:48-49`) is only reachable from there. (Independently reported as
  `infra-audit.md` F7.)
* The three ore mixins use `@Mixin(value = X.class)` — a hard class reference — **without
  `@Pseudo`**, and none of the three injections carries `require = 1`
  (`MixinGTOreAdapter.java:19-33`, `MixinBWOreAdapter.java:19-33`, `MixinGTPPOreAdapter.java:17-24`).

Compare the upstream (`tmp/Qz-Miner/src/main/java/club/heiqi/qz_miner/mixins/MixinGTOreAdapter.java`):
`@Pseudo` (`:15`), `@Mixin(targets = "gregtech.common.ores.GTOreAdapter", remap = false)` (`:16`),
`require = 1` on both injections (`:28`, `:44`), and
`mixins.qz_miner.json` line `"plugin": "club.heiqi.qz_miner.mixins.QzMinerMixinPlugin"` whose
`shouldApplyMixin` (`QzMinerMixinPlugin.java:44-52`) inspects the target **bytecode** (method +
descriptor + `isNatural` GETFIELD + the exact `ILOAD/ICONST_3/IF_ICMPLE/ICONST_3/ISTORE` clamp
shape, `:94-156`) before applying.

**Failure scenario.** If a future GT5U renames `getBigOreDrops`, changes the clamp shape, or adds
a second `isNatural` read, EZMiner's injections silently do nothing (`require` default is 0) and
the user sees "the config knob does nothing" with no log line; with the `@Redirect` on
`isNatural` it can instead be a hard "multiple redirect candidates" apply error. Confusingly,
`Config.enableMixinCapabilityGates` (default `true`, `Config.java:220`) *documents* this
protection.

**Minimal fix.** Add `require = 1` to all five injections (cheap, no new class), and port
Qz-Miner's `QzMinerMixinPlugin` (already written for this exact target set) as the JSON `plugin`,
or delete the knob + `Mixins`/`MixinCapabilityPlugin`/`ILateMixinPlugin`/`TargetMod`/late JSON
(option (b) of `infra-audit.md` F7).

**Confidence**: high (all files read; the upstream contrast is a direct quote).

### C3
**`GT5ToolCompat` is never initialised on a dedicated server.**

`init()` (`compat/GT5ToolCompat.java:74-155`) is the only writer of the `gtLoaded` flag
(`:148`), and grep over `src/main/java` finds exactly **one** call site:
`client/ClientProxy.java:29`. `CommonProxy` (`CommonProxy.java:58`) only calls
`CropAdapterRegistry.init()`. `ClientProxy` is the `@SidedProxy` client side and is never loaded
on a dedicated server.

Consumers that run **server-side**:
`utils/ToolHarvestEligibility.java:61-63` (used by `core/BaseOperator.java:313`,
`network/PacketToolBreakHandoff.java:154`, `network/PacketToolSwapRequest.java:85`) and
`:108-110`.

**Failure scenario.** On a dedicated server, mining with a GT wrench: `isGTTool(stack)` →
`false` (`:164-168` early-returns on `!gtLoaded`), so `canHarvest` skips the GT branch and
reaches `ForgeHooks.canToolHarvestBlock(target, metadata, stack)` (`ToolHarvestEligibility.java:66`).
The class's own comment (`:58-60`) states GT tools report a *class-agnostic* harvest level, so a
wrench is accepted for stone → the auto tool-switch can hand the player a wrench for a stone/ore
chain, and `isUsableMiningTool` (`:100-115`) also stops rejecting GT tools (they are damageable
`Item`s). On the integrated server, where `ClientProxy.preInit` did run, none of this happens —
so the bug is dedicated-server-only.

**Minimal fix.** Call `GT5ToolCompat.init()` from `CommonProxy` (it is side-neutral: no client
classes, only reflection + `Loader`), keeping the `ClientProxy` call idempotent.

**Confidence**: high (grep is exhaustive; behaviour is a direct consequence of the `!gtLoaded`
guards).

### C4
**The fast-harvest path bypasses `Block.removedByPlayer`.**

Vanilla `ItemInWorldManager.removeBlock` (`build/rfg/minecraft-src/java/net/minecraft/server/management/ItemInWorldManager.java:265-278`):
```java
block.onBlockHarvested(this.theWorld, x, y, z, l, this.thisPlayerMP);
boolean flag = block.removedByPlayer(theWorld, thisPlayerMP, x, y, z, canHarvest);   // :270
if (flag) block.onBlockDestroyedByPlayer(theWorld, x, y, z, l);                      // :274
```
`Block.removedByPlayer` (`build/rfg/.../block/Block.java:1659-1668`) is the mod-facing removal
hook (`world.setBlockToAir`) and is overridden by many mods. The mixin replaces it with a raw
`theWorld.setBlock(x, y, z, Blocks.air, 0, 2)` (`mixin/early/MixinItemInWorldManager.java:108`);
the same substitution happens in `BlockHarvestActionExecutor.executeBatch`
(`chain/execution/BlockHarvestActionExecutor.java:206`).

Overrides that survive only because EZMiner routes TE blocks to vanilla (verified):
vanilla `BlockFlowerPot.removedByPlayer` (`BlockFlowerPot.java:278`, TE), GT
`BlockMachines.removedByPlayer` (`beta2 …/BlockMachines.java:520`, implements
`ITileEntityProvider`, `:67`), Forestry `BlockFruitPod` (TE `TileFruitPod`,
`tmp/ForestryMC-master/.../BlockFruitPod.java:66-77`).

**Failure scenario (non-TE, reproduced from source).** GT `BlockReinforced` is not a TE carrier
(`class BlockReinforced extends GTGenericBlock`, `beta2 …/BlockReinforced.java:45`; no
`hasTileEntity`), and its `removedByPlayer` (`:304-319`) turns meta 5 into a primed powder barrel:
```java
if (!world.isRemote && world.getBlockMetadata(x, y, z) == 5) {
    world.spawnEntityInWorld(new EntityPowderBarrelPrimed(...));
```
Chain-mining a powder barrel therefore **deletes it silently instead of priming/exploding it** —
observably different from a single vanilla break. Any other non-TE `removedByPlayer` override
(e.g. `tmp/TinkersConstruct-master/.../LavaTankBlock.java:255`, Galacticraft
`BlockCavernousVine.java:53`) is likewise skipped.

**Minimal fix.** In the fast path, replace `setBlock(...,air,0,2)` with
`block.removedByPlayer(theWorld, thisPlayerMP, x, y, z, canHarvest)` and mark the chunk for
update (mirrors vanilla; the caller already knows `canHarvest`), or exempt blocks whose class
declares `removedByPlayer` (cheap `getMethod` probe once per Block instance).

**Confidence**: high (both sides read; the only vanilla override is TE-gated, so this is a
*mod*-block issue, and the GT case is a direct quote).

### C5
**Creative XP leak in the fast path when `fireBreakEvent=true`.**

Vanilla guards the XP drop with `if (!this.isCreative() && flag && event != null)`
(`ItemInWorldManager.java:330`), and `BlockEvent.BreakEvent.getExpToDrop()` has **no** creative
check (it computes `block.getExpDrop(...)` whenever the block is harvestable and not silk-touched,
`build/rfg/.../forge/event/world/BlockEvent.java:83-93,106-109`).

The mixin does:
```java
// MixinItemInWorldManager.java:122-130
if (removed) {
    int exp = preFiredEvent != null ? preFiredEvent.getExpToDrop()
                                    : XPDropHandler.computeBlockXP(block, theWorld, meta, thisPlayerMP);
    XPDropHandler.handlePreComputedXP(theWorld, block, x, y, z, exp, thisPlayerMP);
}
```
and `handlePreComputedXP` (`chain/execution/XPDropHandler.java:106-114`) has no creative guard
(only `computeBlockXP` does, `:54`). The same shape exists in the batch path
(`BlockHarvestActionExecutor.java:225-226`, with `isCreative` available at `:121` but unused
there).

**Failure scenario.** `Config.fireBreakEvent = true` (server config, default `false`,
`Config.java:159`) + a creative player chain-mining coal/diamond ore → XP orbs spawn for blocks
that vanilla would break for free. Cosmetic-to-exploitable on a creative server.

**Minimal fix.** `if (removed && !isCreative())` around the XP block (both call sites).

**Confidence**: high.

### C6
**`Item.onBlockStartBreak` is only partially replayed.**

Vanilla calls `stack.getItem().onBlockStartBreak(...)` and cancels the harvest if it returns
`true` (`ItemInWorldManager.java:292-296`). The fast path replays only TiC
(`TinkersConstructLevelingBridge.fireBeforeBlockBreak`, mixin `:61-70` **and** `:81`
`ShearsHarvestBridge.fireIfShears` for vanilla shears). Grep confirms no other
`onBlockStartBreak` replay.

Concrete loss — GT5U's own tool hook (`tmp/GT5-Unofficial-beta2/src/main/java/gregtech/api/items/MetaGeneratedTool.java:307-347`):
* `:313-334` chainsaw on `IShearable` → sheared drops + tool damage; EZMiner's
  `ShearsHarvestBridge` only fires for `stack.getItem() instanceof ItemShears`
  (`compat/ShearsHarvestBridge.java:86`), so a GT chainsaw (or GT axe) on leaves now drops
  saplings instead of leaves;
* `:335-345` `isSaw()` on ice/packed ice → drops the block item, damages the tool and returns
  `true` (block consumed by the hook). On the fast path the hook is never called, so a
  chain-mined ice block with a GT saw yields **nothing** (vanilla `BlockIce` only drops with
  silk touch) — item loss for the player.

**Minimal fix.** Call `stack.getItem().onBlockStartBreak(...)` first and honour a `true` return
(exactly like vanilla), then keep the shears/TiC bridges only for the TiC case where the AOE
overrides must be suppressed (`TinkersConstruct-master/.../AOEHarvestTool.java:28`,
`LumberAxe.java:96`, `Scythe.java:138` — the reason given in `TinkersConstructLevelingBridge.java:24-28`).

**Confidence**: high for the code path; medium for the GT-saw ice case being reachable in a
GTNH chain (the tool must be a GT saw and the target ice; both are common enough).

### C7
**`EtFuturumCropCompat.harvest` bypasses the right-click event chain.**

```java
// compat/EtFuturumCropCompat.java:94
return block.onBlockActivated(world, x, y, z, player, 0, 0.5F, 0.5F, 0.5F);
```
Vanilla right-click reaches a block through
`ItemInWorldManager.activateBlockOrUseItem` → `PlayerInteractEvent` (`PlayerInteractEvent` is
fired at `ItemInWorldManager.java:386`). Calling the block method directly skips
`ForgeEventFactory.onPlayerInteract`, so a protection/claim mod or ServerUtilities'
vanish `WrapOperation` on exactly that call (`tmp/ServerUtilities-master/src/mixins/java/serverutils/mixins/early/minecraft/vanish/MixinItemInWorldManager.java:24-46`)
cannot veto the chained crops. The verification of the *crop semantics* themselves is fine:
EFR berry bush meta 2-3 = has berries (`tmp/Et-Futurum-Requiem-master/.../BlockBerryBush.java:107-125`)
matches `meta >= 2` (`EtFuturumCropCompat.java:73-75`); cave vines meta 1 = glow berries
(`BaseCaveVines.java:96,136,154,181`) matches `meta == 1` (`:77-79`), and both real classes
(`BlockCaveVines.java:19`, `BlockCaveVinesPlant.java:6`) extend the resolved `BaseCaveVines`.

**Failure scenario.** On a protected claim, a player in crop mode right-clicks a mature berry
bush and the whole chain of bushes is harvested — the per-block protection the trigger block was
subject to is not applied to the rest.

**Minimal fix.** Wrap the call in `PlayerInteractEvent` (or have `Manager` run the chain's crop
harvest through `ForgeHooks.onRightClickBlock`) — the infrastructure for firing the event already
exists in the mod's own `BreakEvent` helper (`chain/execution/ChainBreakEventHelper.java:32-35`).

**Confidence**: high for the mechanism; medium that any given pack has a protection mod that
cares about right-clicks.

### C8
**`initialized` published before the lookup result.**

```java
// EtFuturumOreCompat.java:25-38 (same shape at EtFuturumCropCompat.java:39-47)
if (initialized) return;
initialized = true;             // :27
netherGoldOreType = ClassNameCompatSupport.resolveClass(...);   // :29-33
efLoaded = ... ;                // :35
```
`initialized` is `volatile` (`:13`), so a thread that loses the race sees `true` and returns
immediately while `efLoaded` is still `false`; `init()` never runs again → the EFR adapters are
permanently absent for that JVM. `OreCompatRegistry`'s static block calls
`EtFuturumOreCompat.init()` (`compat/ore/OreCompatRegistry.java:36`) and can be triggered from a
founder/search thread (`core/founder/DeterminingIdentical.java:230`), while
`CropAdapterRegistry.init()` calls the crop twin on the server thread at postInit
(`core/crop/CropAdapterRegistry.java:46-47`) — so the two are not as serialised as they look.
**Minimal fix**: resolve first, assign `initialized = true` last (or use a
`static final` holder / `synchronized`).
**Confidence**: medium (a genuine ordering defect; the reachable race window is narrow).

### C9
**`BushSupportBridge` is not an exact mirror.**

Vanilla (`build/rfg/.../block/BlockBush.java:70-77`):
```java
if (!this.canBlockStay(worldIn, x, y, z)) {
    this.dropBlockAsItem(worldIn, x, y, z, worldIn.getBlockMetadata(x, y, z), 0);
    worldIn.setBlock(x, y, z, getBlockById(0), 0, 2);      // flag 2
}
```
Bridge (`compat/BushSupportBridge.java:43-46`) uses `world.setBlockToAir(x, y, z)` which is
`setBlock(..., Blocks.air, 0, 3)` (`build/rfg/.../world/World.java:649-652`) — flag 3 adds the
neighbour notification vanilla deliberately omits here — plus `markBlockForUpdate` (already
implied by flag 2/3). Guards and the drop call match, and `canBlockStay` is public
(`BlockBush.java:82`) so the cast is valid.
**Minimal fix**: `world.setBlock(x, y, z, Blocks.air, 0, 2)` and drop the extra
`markBlockForUpdate` if exact parity is wanted.
**Confidence**: high (trivial code comparison); impact is limited to extra neighbour updates.

### C10
**The client mixin is a production no-op in the common list.**

`MixinGuiIngameMenu.java:37` only cancels the id-12 branch when
`EZMiner.isDeobfuscatedEnvironment` is true (`EZMiner.java:40`), so in a real modpack the
injection runs and does nothing. Meanwhile `mixins.EZMiner.json:11-19` lists it in the **common**
`mixins` array while `"client": []` is empty → on a dedicated server the mixin class references
`net.minecraft.client.gui.GuiIngameMenu`/`GuiButton`. Cross-ref `infra-audit.md` F8 for the
registration analysis (verified independently here: the JSON has no `client` entry and the
javadoc's premise is otherwise correct — button id 12 *is* "Mod Options..." and *does* call
`FMLClientHandler.showInGameModOptions` at `build/rfg/.../client/gui/GuiIngameMenu.java:36,75-77`,
`FMLClientHandler.java:705-707`).
Hodgepodge also patches the same class, but at a different point
(`@ModifyConstant(method = "initGui", stringValue = "Mod Options...")`,
`tmp/Hodgepodge-master/.../MixinGuiIngameMenu_LocalizeModOptionsButton.java:10-16`) → **no
injection conflict**.
**Minimal fix**: move it to `"client": [...]` (and treat it as a dev-only convenience) or delete it.
**Confidence**: high on the facts; the blast radius of the dedicated-server error is the open
question already recorded in `infra-audit.md` F8/inconclusive #2.

### C11
**TiC durability gate is one block more conservative than TiC.**

`compat/TinkersConstructCompat.java:96` returns `(maxDurability - damage) > 1`, i.e. refuses when
one point remains; TiC breaks the tool only when `damageTrue > maxDamage`
(`AbilityHelper.java:378-390`). Verified keys/thresholds are otherwise exact: `InfiTool`
(`:27-30`), `Unbreaking >= 10` = all damage negated (`AbilityHelper.java:363` per-point
`random.nextInt(10) < reinforced`, `Hammer.java:144` sets 10), `Broken` → 0.1× speed
(`HarvestTool.java:60`), and `getMaxDamage()` really is the hardcoded 100 (`ToolCore.java:643-645`)
so the NBT read is required. **Minimal fix**: `>= 1` (or `damage < maxDurability`).
**Confidence**: high; impact = a tool with exactly 1 durability left is not used for a chain.

### C12
**Natura javadoc claims a mechanism that does not exist on 1.7.10.**

`compat/NaturaSaguaroCompat.java:22-32` states SaguaroBlock's integrity "relies entirely on
`onNeighborBlockChange → canBlockStay` self-destruction". The real Natura source declares
`public void onNeighborBlockChange(World world, int x, int y, int z, int l)`
(`tmp/Natura-master/.../blocks/trees/SaguaroBlock.java:213`) — a **5th `int` parameter**, no
`@Override` — while 1.7.10's hook is `onNeighborBlockChange(World,int,int,int,Block)`
(`BlockBush.java:53`). That method therefore never runs, and `updateTick` (`SaguaroBlock.java:38-65`)
never calls `canBlockStay` either → the bridge's cascade (`:69-76`, `canBlockStay` check at `:79`
against the real predicate `below == this || Blocks.sand || null`, `SaguaroBlock.java:221-224`)
is the *only* thing that makes a floating saguaro disappear. The drop substitution is also
correct: `NContent.seedFood` exists and is only registered at damage 0
(`tmp/Natura-master/.../NContent.java:746-747`, OreDict `cropCactusfruit` `:1942`) while Natura's
`getItemDropped` returns it for every nonzero meta with `damageDropped(meta) = meta`
(`SaguaroBlock.java:148-154`).
**Minimal fix**: correct the javadoc ("Natura's neighbour hook is a legacy 5-arg stub and never
fires on 1.7.10 — this cascade is the primary mechanism"), and consider mirroring the same drop
fix for the `cascadeUnsupportedNeighbors` positions, which currently rely on the executor's
`maybeHarvest` call (`MixinItemInWorldManager.java:117`, `BlockHarvestActionExecutor.java:219`).
**Confidence**: high (both signatures read).

### C13
**Doc drift + unsynced fortune config.**

* `CLAUDE.md` ("Controlled by `Config.fortuneOverrideEnabled`") and
  `docs/review/perf-mixin-review.md:163` ("restart-only semantics, same as `fortuneOverrideEnabled`")
  name a field that does not exist: the real fields are `Config.enableUnlimitedOreFortune`
  (`Config.java:310`) and `Config.enableFortuneForPlacedOre` (`:327`).
* `utils/FortuneCompatHelper.java:10-16` reads `Config` **on every ore-drop call**, so the
  redirect takes effect on the next server-config reload — no restart is required (the
  "restart" claim is stale).
* Those two fields are loaded but never synced / never GUI-written (`infra-audit.md` §2 rows
  53-55). `GTOreAdapter.getOreDrops` etc. also run *client-side* for ore-drop previews (NEI/JEI
  reads the adapters), so client and server can disagree about the uncap.

**Failure scenario**: an admin sets `enableUnlimitedOreFortune=true` on a dedicated server, the
server uncaps, the client's `EZMiner.cfg` keeps `false` → the client-side drop preview still
shows 3-capped output (cosmetic), and a client-side read of `Config` cannot see the server value.
**Minimal fix**: fix `CLAUDE.md`/the review doc, and either sync the two fields or mark them
server-only in their javadoc.
**Confidence**: high.

### C14
**Dead late-mixin infrastructure (re-verified).**

`mixin/Mixins.java:13` (`public enum Mixins { ; }`), `mixin/ILateMixinPlugin.java:16-23`
(returns `Mixins.getLateMixins(...)` = always empty), `mixin/TargetMod.java:8` (single constant,
only consumer is the dead `Mixins` code), `mixin/MixinCapabilityPlugin.java:48-70` (only called
from the dead `addBytecodeCondition`), `mixins.EZMiner.late.json:1-8` (declares
`"package": "com.czqwq.EZMiner.mixin.late"`, a package that does not exist under
`src/main/java/com/czqwq/EZMiner/mixin/`, and lists no mixins while `"required": true`).
Same conclusion as `infra-audit.md` F7; recorded here because it is the mechanism that C2 says
is missing.
**Minimal fix**: delete, or implement (see C2).
**Confidence**: high.

---

## 3. Hook → `tmp/` target verified? matrix

Legend: **MATCH** = target exists with the expected name/signature; **MISMATCH** = exists but
differs; **MISSING** = not present in that tree; **n/v** = not verifiable from the available
material. `b2` = `tmp/GT5-Unofficial-beta2`, `5.09` = `tmp/GT5-Unofficial-5.09.54.133`.

### 3.1 Ore compat (reflective / string based)

| EZMiner hook (file:line) | target | tmp source (file:line) | verdict |
|---|---|---|---|
| `compat/ClassNameCompatSupport.java:40` | `Class.forName(name,false,loader)` + catches `ClassNotFoundException\|LinkageError\|SecurityException` | — (no target) | **MATCH** (fail-safe probe; `n/a` for third-party) |
| `ore/OreCompatRegistry.java:104-105` | `gregtech.common.blocks.BlockOresAbstract`, `…TileEntityOres` | 5.09 `…/common/blocks/BlockOresAbstract.java`, `TileEntityOres.java` (both present); **absent in b2** | **MATCH on 5.09 / MISSING on b2** (adapter simply not added there) |
| `ore/OreCompatRegistry.java:107` | `gregtech.common.blocks.GTBlockOre` | b2 `…/common/blocks/GTBlockOre.java`; **absent in 5.09** | **MATCH on b2 / MISSING on 5.09** |
| `ore/OreCompatRegistry.java:112-113, 116-118` | `BlockOresAbstractLegacy`, `BlockOresLegacy` | b2 both present (`…/blocks/BlockOresAbstractLegacy.java`, `BlockOresLegacy.java`); absent in 5.09 | **MATCH on b2 / MISSING on 5.09** |
| `ore/OreCompatRegistry.java:122-123` | `bartworks.system.material.BWMetaGeneratedSmallOres`, `BWMetaGeneratedOres` | 5.09 `…/bartworks/system/material/BWMetaGeneratedSmallOres.java`, `BWMetaGeneratedOres.java`; b2 only `BWMetaGeneratedOres.java` | **MATCH / partial** (small-ore adapter is 5.09-only; harmless) |
| `ore/OreCompatRegistry.java:126, 129` | `bartworks.system.material.BWTileEntityMetaGeneratedOre`, `…SmallOre` | 5.09 `…/BWTileEntityMetaGeneratedOre.java`, `BWTileEntityMetaGeneratedSmallOre.java`; **absent in b2** | **MATCH on 5.09 / MISSING on b2** |
| `ore/OreCompatRegistry.java:131` | `gtPlusPlus.core.block.base.BlockBaseOre` | 5.09 `…/gtPlusPlus/core/block/base/BlockBaseOre.java:146`; b2 same path (classic block, `getDrops` at `:117`) | **MATCH (both)** |
| `ore/OreCompatRegistry.java:133-134` | `appeng.block.solids.OreQuartz`, `OreQuartzCharged` | AE2 not present in `tmp/` | **n/v** |
| `ore/OreCompatRegistry.java:136-147` → `EtFuturumOreCompat.isLoaded()` | see EFR rows below | | **MATCH** |
| `ore/NamedClassOreCompatAdapter.java:22-23,28,33,38-39` | `isInstance` block/TE; `isTileEntityOnly` = blockType==null && te!=null | self-consistent; `OreCompatRegistry.isOreBlockByTileEntity` (`:83-96`) calls it with `null` block (`:88`) | **MATCH** (no reflection traps) |
| `core/founder/DeterminingIdentical.java:204-206,230` | `hasTileEntityOnlyDetectors()` / `isOreBlock(block,null)` | as above | **MATCH** |

### 3.2 Et Futurum Requiem

| EZMiner hook (file:line) | target | tmp source (`tmp/Et-Futurum-Requiem-master/src/main/java/…`) | verdict |
|---|---|---|---|
| `EtFuturumOreCompat.java:29` | `ganymedes01.etfuturum.blocks.ores.BlockOreNetherGold` | `ganymedes01/etfuturum/blocks/ores/BlockOreNetherGold.java` | **MATCH** |
| `:30` | `ganymedes01.etfuturum.blocks.BlockAncientDebris` | `…/blocks/BlockAncientDebris.java` | **MATCH** |
| `:31` | `ganymedes01.etfuturum.blocks.ores.BaseDeepslateOre` | `…/ores/BaseDeepslateOre.java` (superclass of `BlockDeepslateOre.java`) | **MATCH** |
| `:32-33` | `…blocks.ores.modded.BlockGeneralModdedDeepslateOre` | `…/ores/modded/BlockGeneralModdedDeepslateOre.java` | **MATCH** |
| `:59-68` registry-name fallback (`etfuturum:*_ore`, `ancient_debris`) | EFR modid `"etfuturum"`; ore names e.g. `NETHER_GOLD_ORE`, `DEEPSLATE_*_ORE` | `…/lib/Reference.java:10`; `…/ModBlocks.java:234,272-273,331-338,880-886` | **MATCH** (heuristic; all sampled ore registry names end in `_ore`) |
| `EtFuturumCropCompat.java:43` | `ganymedes01.etfuturum.blocks.BlockBerryBush` | `…/blocks/BlockBerryBush.java:29` | **MATCH** |
| `:44` | `ganymedes01.etfuturum.blocks.BaseCaveVines` | `…/blocks/BaseCaveVines.java:27` (+ `BlockCaveVines.java:19`, `BlockCaveVinesPlant.java:6` both extend it) | **MATCH** |
| `EtFuturumCropCompat.java:73-75` | berry-bush berries at meta ≥ 2 | `BlockBerryBush.java:107-125` (`i > 1`) | **MATCH** |
| `EtFuturumCropCompat.java:77-79` | cave-vine berries at meta 1 | `BaseCaveVines.java:96,136,154,181` | **MATCH** |
| `EtFuturumCropCompat.java:94` | `Block.onBlockActivated` for harvest | `BlockBerryBush.java:107`, `BaseCaveVines.java:95-107` | **MATCH** (but bypasses `PlayerInteractEvent` → C7) |

### 3.3 GT5U tool / toolbox / block-swap (reflection)

| EZMiner hook (file:line) | target | b2 source (file:line) | 5.09 | verdict |
|---|---|---|---|---|
| `GT5ToolCompat.java:81` | `gregtech.api.items.MetaGeneratedTool` | `…/gregtech/api/items/MetaGeneratedTool.java` | present | **MATCH** |
| `:82` | `gregtech.api.interfaces.IToolStats` | `…/interfaces/IToolStats.java` | present | **MATCH** |
| `:90` | `MetaGeneratedTool.getToolStats(ItemStack)` | `MetaGeneratedTool.java:794` (public) | present | **MATCH** |
| `:91-92` | `MetaGeneratedTool.getToolCombatDamage(ItemStack)` | `:694` | present | **MATCH** |
| `:93` | `IToolStats.isMinableBlock(Block,int)` | `IToolStats.java:171` | present | **MATCH** |
| `:94` | `IToolStats.getBaseQuality()` | `IToolStats.java:80` | present | **MATCH** |
| `:106` | `gregtech.common.items.ItemGTToolbox` | `…/common/items/ItemGTToolbox.java:84` | **MISSING (no toolbox classes on 5.09)** | **MATCH on b2 / MISSING on 5.09** (documented graceful degradation, `:104-105`) |
| `:107-108` | `…items.toolbox.ToolboxPickBlockDecider` | `…/toolbox/ToolboxPickBlockDecider.java` | missing | **MATCH on b2 / MISSING on 5.09** |
| `:109-110` | `…toolbox.ToolboxItemStackHandler` | `…/toolbox/ToolboxItemStackHandler.java:19`, public ctor `(ItemStack)` `:24` | missing | **MATCH on b2 / MISSING on 5.09** |
| `:111` | `…toolbox.pickblock.PickResults` | `…/toolbox/pickblock/PickResults.java:18` (`record(boolean forceDeselect, List<ToolboxSlot> suggestedTools)`) | missing | **MATCH on b2 / MISSING on 5.09** |
| `:117` | `ToolboxPickBlockDecider.getSuggestedTool(EntityPlayer)` (static) | `:158` | missing | **MATCH on b2** |
| `:120-130` | `PickResults.suggestedTools()`, `forceDeselect()` (0-arg record accessors) | `PickResults.java:18` record components | missing | **MATCH on b2** |
| `:132-133` | `ToolboxItemStackHandler.getStackInSlot(int)` | `ModularUI2-master/.../utils/item/ItemStackHandler.java:57` (inherited, public) | missing | **MATCH on b2** |
| `:134` | `ToolboxItemStackHandler.getSlots()` | `ItemStackHandler.java:52` | missing | **MATCH on b2** |
| `:136-137` | `ItemGTToolbox.sendChangeToolPacket(int,int)` *private static* | `ItemGTToolbox.java:514` | missing | **MATCH on b2** (`SafeReflection.getDeclaredMethod` calls `setAccessible(true)`, `utils/SafeReflection.java:88-97` → invocation works) |
| `:233, 236` | `ToolboxSlot.ordinal()` used as the internal slot id | `…/api/enums/ToolboxSlot.java:39-64` (slot ids 0…13 in declaration order) | n/a | **MATCH** (fragile if GT reorders the enum) |
| `:230` | `List<Enum<?>> slots = (List<Enum<?>>) suggestedTools()` | `ToolboxSlot` is an `enum` (`ToolboxSlot.java:39`) | n/a | **MATCH** (no `ClassCastException` on `slots.get(0)`) |
| `GT5ToolDurabilityBridge.java:90` | `instanceof gregtech.api.items.MetaGeneratedTool` | present | present | **MATCH** |
| `:92` | `tool.getToolStats(ItemStack)` | `MetaGeneratedTool.java:794` | present | **MATCH** |
| `:95-96` | `MetaGeneratedTool.getToolDamage(ItemStack)`, `getToolMaxDamage(ItemStack)` (static) | `:141`, `:132` | present | **MATCH** |
| `:100,112` | damage formula `Math.max(1, hardness × getToolDamagePerBlockBreak())` | `MetaGeneratedTool.java:771-773` (identical expression) | present | **MATCH** |
| `:102` | `(currentDamage + estimated) < maxDamage` = "won't break" | `MetaGeneratedTool.java:712,726` (`tNewDamage >= getToolMaxDamage` → break) | present | **MATCH** |
| `:58` | `IToolStats.getToolDamagePerBlockBreak()` | `IToolStats.java:58` | present | **MATCH** |
| `GT5BlockSwapCompat.java:57` | `gregtech.api.GregTechAPI` | `…/api/GregTechAPI.java` | present | **MATCH** |
| `:58` | `gregtech.api.metatileentity.CommonBaseMetaTileEntity` | `…/api/metatileentity/CommonBaseMetaTileEntity.java:45` (`abstract … extends CoverableTileEntity`) | present | **MATCH** |
| `:61` | `GregTechAPI.sBlockMachines` (public static Block) | b2 `GregTechAPI.java:168` | 5.09 `:176` | **MATCH (both)** — 5.09 line from `chain-audit.md` §5 |
| `:63` | `GregTechAPI.METATILEENTITIES` (public static `IMetaTileEntity[]`) | b2 `:87` | 5.09 `:95` | **MATCH (both)** |
| `:66-67` | `BaseMetaPipeEntity.mConnections` (public byte) | b2 `…/metatileentity/BaseMetaPipeEntity.java:60` | 5.09 `:63` | **MATCH (both)** |
| `:70-71` | `MetaPipeEntity.mConnections` (public byte) | b2 `MetaPipeEntity.java:64` | 5.09 `:61` | **MATCH (both)** |
| `:79-84` | `getMetaTileID()` on `CommonBaseMetaTileEntity` else on `IGregTechTileEntity` | b2 `IGregTechTileEntity.java:39` (`int getMetaTileID()`); 5.09 `:39` | present | **MATCH** |
| `:93-99` | `setInitialValuesAsNBT(NBTTagCompound, short)` | b2 `base: IGregTechTileEntity.java:127`, impl `BaseMetaTileEntity.java:166`, `BaseMetaPipeEntity.java:140` | 5.09 `IGregTechTileEntity.java:131`, `BaseMetaTileEntity.java:165`, `BaseMetaPipeEntity.java:145` | **MATCH (both)** |
| `:196-198` | `IMetaTileEntity.getTileEntityBaseType()` (byte) used as the placement base meta | b2 `IMetaTileEntity.java:71`; GT itself does the same in `MapGenRuins.java:121` | 5.09 `:59` | **MATCH (both)** |
| `:249-251` | `BaseMetaPipeEntity.getMetaTileEntity()` | b2 (used at `BaseMetaPipeEntity.java:106,238`) | present | **MATCH** |

### 3.4 Other bridges

| EZMiner hook (file:line) | target | tmp source (file:line) | verdict |
|---|---|---|---|
| `CoFHWaterBridge.java:45,57` | `Material.water`; `instanceof BlockDynamicLiquid` catches CoFH water | `tmp/CoFHCore-1.12-Legacy-1.7.10/src/main/java/cofh/asmhooks/block/BlockTickingWater.java:9` (`extends BlockDynamicLiquid`, no `onNeighborBlockChange` override) | **MATCH** |
| `CoFHWaterBridge.java:16-19` doc: "`BlockLiquid.onNeighborBlockChange` only handles lava" | `BlockLiquid.func_149805_n` is lava-only | `build/rfg/minecraft-src/java/net/minecraft/block/BlockLiquid.java:538-547` | **MATCH** |
| `CoFHWaterBridge.java:49-52` "vanilla `func_149813_h` downward flow, level 8, flag 3" | `BlockDynamicLiquid.func_149813_h` → `setBlock(…, this, level, 3)` | `BlockDynamicLiquid.java:172-189` (falling water = level + 8, called at `:128-132`) | **MATCH** |
| `BushSupportBridge.java:42,44-46` | `BlockBush.canBlockStay` / `dropBlockAsItem` / removal | `BlockBush.java:82-85`, `70-77` | **MATCH** (flag differs → C9) |
| `NaturaSaguaroCompat.java:129` | `mods.natura.blocks.trees.SaguaroBlock` | `tmp/Natura-master/…/blocks/trees/SaguaroBlock.java:27`; modid `Natura` (`…/Natura.java` `@Mod(modid = "Natura")`) | **MATCH** |
| `NaturaSaguaroCompat.java:133` | `SaguaroBlock.canBlockStay(World,int,int,int)` | `SaguaroBlock.java:221-224` | **MATCH** |
| `NaturaSaguaroCompat.java:138` | `mods.natura.common.NContent.seedFood` | `NContent.java:746,2205` (registered `saguaro.fruit`, damage 0 only, OreDict `:1942`) | **MATCH** |
| `NaturaSaguaroCompat.java:100,104-105` | `StatList.mineBlockStatArray[Block.getIdFromBlock(block)]`, `BlockEvent.HarvestDropsEvent` | extended-array patch in `tmp/EndlessIDs-master/.../StatListMixin.java:33-47` (`ModifyConstant` 4096 → `ExtendedConstants.blockIDCount`); event ctor `build/rfg/.../forge/event/world/BlockEvent.java:59-70` | **MATCH** (safe *because* EndlessIDs resizes the array; without it, an extended block id would AIOOBE) |
| `ShearsHarvestBridge.java:82,86,91,95-96,122-123` | `IShearable.isShearable/onSheared`, `ItemShears`, fortune, `damageItem(1)`, stat | `build/rfg/.../item/ItemShears.java:86-118`; `ItemShears.onBlockDestroyed` returns without damage for `IShearable` (`:30-40`) → exactly 1 durability total | **MATCH** |
| `TinkersConstructCompat.java:30,40,48,61-62,90-91` | `InfiTool` / `Unbreaking` / `Broken` / `Damage` / `TotalDurability` | `tmp/TinkersConstruct-master/…/AbilityHelper.java:357-390`, `ToolCore.java:643-645,663-664`, `HarvestTool.java:59-60`, `Hammer.java:141-144`, `ModReinforced.java:27-29` | **MATCH** |
| `TinkersConstructLevelingBridge.java:121` | `tconstruct.library.tools.ToolCore` | `ToolCore.java:70,473-482` | **MATCH** |
| `:125` | `TConstructRegistry.activeModifiers` is `ArrayList<ActiveToolMod>` | `TConstructRegistry.java:523` (`public static ArrayList<ActiveToolMod>`) | **MATCH** |
| `:129` | `ActiveToolMod.beforeBlockBreak(ToolCore,ItemStack,int,int,int,EntityLivingBase)` | `ActiveToolMod.java:18`; base impl in `ToolCore.java:473-482` (`hasTagCompound` guard mirrored at `TinkersConstructLevelingBridge.java:74`) | **MATCH** |
| `TinkersConstructLevelingBridge.java:24-28` AOE-override rationale | `AOEHarvestTool`, `LumberAxe`, `Scythe` override `onBlockStartBreak` | `AOEHarvestTool.java:28`, `LumberAxe.java:96`, `Scythe.java:138` | **MATCH** |
| IguanaTweaks leveling is an `ActiveToolMod` | `TConstructRegistry.activeModifiers.add(0, new LevelingActiveToolMod())` | `tmp/IguanaTweaksTConstruct-master/…/IguanaToolLeveling.java:67` | **MATCH** |
| `WitcheryVampireBridge.java:69` | `com.emoniph.witchery.common.ExtendedPlayer.get(EntityPlayer)` | classes-only tree: `tmp/witchery-1.7.10-0.24.1/com/emoniph/witchery/common/ExtendedPlayer.class` — constant pool contains `get` + descriptor `(Lnet/minecraft/entity/player/EntityPlayer;)Lcom/emoniph/witchery/common/ExtendedPlayer;` | **MATCH** (verified from the class file) |
| `WitcheryVampireBridge.java:71` | `isVampire()`, `getVampireLevel()` | same class file: both names present | **MATCH** |
| `WitcheryVampireBridge.java:31` (`VAMPIRE_MINING_LEVEL = 5`) | "vampire at level 5+ can mine stone bare-handed" | not derivable from the class constant pool | **n/v** (see inconclusive #3) |
| `OreCompatRegistry.java:36` + `EtFuturumOreCompat.init()` init order | — | `core/crop/CropAdapterRegistry.java:46-47` calls init before `isLoaded()` (`:47`) | **MATCH** for the registry path (race caveat → C8) |

### 3.5 Mixins

| EZMiner mixin | target class | tmp / vanilla source | verdict |
|---|---|---|---|
| `MixinGTOreAdapter` (`:16`) | `gregtech.common.ores.GTOreAdapter` | b2 `GTOreAdapter.java:38`; **no such package in 5.09** | **MATCH on b2 / MISSING on 5.09** |
| ↳ `@Redirect` FIELD `OreInfo.isNatural:Z` in `getOreDrops(Random,OreInfo,boolean,int)` (`:19-24`) | single GETFIELD | b2 `GTOreAdapter.java:250` (`if (!info.isNatural) fortune = 0;`) — exactly one match | **MATCH** |
| ↳ `@Expression("fortuneLevel > 3")` on `getBigOreDrops(Random,GTProxy$OreDropSystem,OreInfo,int)` (`:26-33`) | `if (fortune > 3) fortune = 3;`; only `int` arg is `fortune`; `GTProxy$OreDropSystem` exists | b2 `GTOreAdapter.java:326`, `:313`; `GTProxy.java:617` | **MATCH** |
| `MixinBWOreAdapter` (`:16`) | `gregtech.common.ores.BWOreAdapter` | b2 `BWOreAdapter.java`; absent in 5.09 | **MATCH on b2 / MISSING on 5.09** |
| ↳ `@Redirect` `isNatural` in `getOreDrops` (`:19-24`) | one GETFIELD | b2 `BWOreAdapter.java:176` | **MATCH** |
| ↳ `@Expression` on `getBigOreDrops` (`:26-33`) | clamp | b2 `BWOreAdapter.java:253` (`if (fortune > 3) fortune = 3;`), method at `:240` | **MATCH** |
| `MixinGTPPOreAdapter` (`:14`) | `gregtech.common.ores.GTPPOreAdapter` | b2 `GTPPOreAdapter.java:22`; absent in 5.09 | **MATCH on b2 / MISSING on 5.09** |
| ↳ `@Expression` on `getBigOreDrops` (`:17-24`) | clamp | b2 `GTPPOreAdapter.java:109` (`if (fortune > 3) fortune = 3;`), method at `:95` | **MATCH** |
| `MixinItemInWorldManager` (`:33`) | `net.minecraft.server.management.ItemInWorldManager` | `build/rfg/.../ItemInWorldManager.java:26` (class) | **MATCH** |
| ↳ `@Shadow public World theWorld` (`:36-37`) | `public World theWorld` | `ItemInWorldManager.java:28` | **MATCH** (and reobfuscated to `field_73092_a` in the shipped jar — see §5.1) |
| ↳ `@Shadow public EntityPlayerMP thisPlayerMP` (`:39-40`) | `public EntityPlayerMP thisPlayerMP` | `:30` | **MATCH** (shipped jar: `field_73090_b`) |
| ↳ `@Shadow public abstract boolean isCreative()` (`:42-43`) | `public boolean isCreative()` | `:73-76` | **MATCH** |
| ↳ interface `IEZMinerItemInWorldManager.ezminer$tryHarvestBlockFast` (`:34, 51-53`) | callers cast `player.theItemInWorldManager` | `chain/execution/BlockHarvestActionExecutor.java:90,260` | **MATCH** (only safe because the mixin always applies — see C2) |
| `MixinGuiIngameMenu` (`:30`) | `net.minecraft.client.gui.GuiIngameMenu` | `build/rfg/.../client/gui/GuiIngameMenu.java:10` | **MATCH** (client-only class → §C10) |
| ↳ `@Inject(method="actionPerformed", HEAD, cancellable)` (`:33-41`) | `protected void actionPerformed(GuiButton)`, id 12 branch | `GuiIngameMenu.java:44-79`; refmap maps it to `func_146284_a` (`build/tmp/mixins/mixins.EZMiner.refmap.json:4`) | **MATCH** |
| ↳ id 12 == FML's Mod Options | `FMLClientHandler.instance().showInGameModOptions(this)` | `GuiIngameMenu.java:75-77`, `FMLClientHandler.java:705-707` | **MATCH** |
| other mixins patching `ItemInWorldManager` | EndlessIDs `@ModifyConstant(tryHarvestBlock)`; ServerUtilities `@WrapOperation(activateBlockOrUseItem)` | `tmp/EndlessIDs-master/.../ItemInWorldManagerMixin.java:33-41`; `tmp/ServerUtilities-master/.../vanish/MixinItemInWorldManager.java:21-46` | **no conflict** (different members; EZMiner adds only a `@Unique` method) |
| config registration | `Mixins.EZMiner.json` in the jar manifest | `build/tmp/jar/MANIFEST.MF:4` (`MixinConfigs: mixins.EZMiner.json`) | **MATCH** |
| late config registration | `mixins.EZMiner.late.json` + `@LateMixin ILateMixinLoader` | `ILateMixinPlugin.java:13-23` returns an always-empty list (C14) | **MISSING** (no mixins, dead path) |

---

## 4. Re-verified known items

| item from the review corpus | current status (this audit) |
|---|---|
| `infra-audit.md` **F6** — `maxFortuneLevel` is a dead field, Fortune cap unenforced | **still present, and now confirmed to matter for these mixins**: `utils/FortuneCompatHelper.java:10-16` reads only `enableFortuneForPlacedOre`/`enableUnlimitedOreFortune`, never `maxFortuneLevel` (grep over `src/main/java`: `Config.java:313`, `ConfigValidator.java:79-83`, javadoc only). Consequence on the real code path: with `enableUnlimitedOreFortune=true` and a modded Fortune-N tool, `GTOreAdapter.getBigOreDrops` (`beta2 :328`) computes `random.nextInt(fortune + 2) - 1` with the raw N → up to N+1 raw-ore items **per block**, unbounded (BWOreAdapter `:255`, GTPPOreAdapter `:110` likewise). No cross-branch corruption: the clamp sites are the only places that use `fortune`, and `getPotentialDrops` calls `getBigOreDrops(..., 0)` (`GTOreAdapter.java:287`, `BWOreAdapter.java:213`, `GTPPOreAdapter.java:92`) so previews are unaffected. |
| `infra-audit.md` **F7** — the "mixin capability gate" does not exist | **confirmed** (C2/C14): `Mixins.java:13` empty enum, no `"plugin"` key in the JSON, `MixinCapabilityPlugin` reachable only from `Mixins.java:123-127`. |
| `infra-audit.md` **F8** — `MixinGuiIngameMenu` in the common list, `"client": []` empty, late config empty | **confirmed** (C10/C14) and **extended**: the injection is additionally dead in production because of `EZMiner.isDeobfuscatedEnvironment` (`EZMiner.java:40`), so the registration risk buys nothing. |
| `infra-audit.md` §2 rows 53-55 — the three fortune fields are load-only / unsynced | **confirmed relevant to the mixins** (C13): `MixinGTOreAdapter.java:23,32` reads `Config` per call, and the adapters also run client-side → no restart needed, but client/server divergence possible. |
| `perf-mixin-review.md:160-163` — "mixin JSON is `required: false`, and the existing capability gating via `Mixins.java` + `MixinCapabilityPlugin.targetHasMethod`" | **partially stale**: `required: false` is correct (`mixins.EZMiner.json:2`); the capability gating does not exist (F7/C2); the field the doc names (`Config.fortuneOverrideEnabled`) does not exist (C13). |
| `perf-mixin-review.md:87-93` (B5) — `MixinItemInWorldManager` is the TE/fallback path, `setBlock flag=2` at line 108 | **accurate**: `MixinItemInWorldManager.java:108` is `setBlock(x,y,z,air,0,2)`; `BlockHarvestActionExecutor.java:80-82,146-151` routes `hasTileEntity(meta)` / `isGTTileEntityCarrier` to vanilla `tryHarvestBlock`. |
| `chain-audit.md` §5 "GT5U block swap — accessors verified for 5.09.54.133" | **re-verified and extended to beta2**: every accessor in `GT5BlockSwapCompat` exists in **both** trees (matrix §3.3); beta2 line numbers: `GregTechAPI.java:168,87`, `BaseMetaPipeEntity.java:60,140`, `MetaPipeEntity.java:64`, `IGregTechTileEntity.java:39,127`, `IMetaTileEntity.java:71`. |
| `chain-audit.md` §5 note — `world.setBlock(...)+setInitialValuesAsNBT(null, itemDamage)` TE rebuild can silently fail (logged at debug, `GT5BlockSwapCompat.java:213-219`) | **unchanged**; the reflection surface it depends on is verified above, so the residual risk stays exactly where `chain-audit.md` left it (runtime `METATILEENTITIES` population). |
| `tree-leaves-review.md:156-157` — "shears/fortune drops are already handled (`ShearsHarvestBridge`)" | **accurate for vanilla shears** (matrix §3.4: `ItemShears.java:86-118` semantics reproduced, including the single-durability argument via `ItemShears.onBlockDestroyed` `:30-40`), **incomplete for GT chainsaws/TiC tools** (C6). |
| `chain-audit.md` F10 — unguarded `world.getBlock` in the batch neighbour notify | **confirmed at the bridge call sites**: `chain/execution/ChunkBlockWriteHelper.java:240-263` calls `CoFHWaterBridge.ensureWaterFlowUpdate` and `BushSupportBridge.popIfUnsupported` on every notified neighbour, and `:269-271` calls `CoFHWaterBridge.sweepFloatingWaterAbove` for every removed block (up to 6 upward reads each) — the bridges themselves add no new unguarded reads beyond the ones F10 already recorded. |

---

## 5. Verified OK

### 5.1 Refmap / MCP-name strategy (production remap) — verified from the shipped artifact
* `build/tmp/jar/MANIFEST.MF:4` registers the early config (`MixinConfigs: mixins.EZMiner.json`).
* `build/tmp/mixins/mixins.EZMiner.refmap.json:3-5` maps exactly one reference —
  `MixinGuiIngameMenu.actionPerformed` → `Lnet/minecraft/client/gui/GuiIngameMenu;func_146284_a(Lnet/minecraft/client/gui/GuiButton;)V`
  — which is **correct and sufficient**: annotation strings cannot be reobfuscated, so they need
  the refmap, while real member references are reobfuscated by `reobfJar`. Verified in
  `build/libs/EZMiner-6.1.jar` (read in memory): `mixin/early/MixinItemInWorldManager.class`
  contains `field_73092_a` and `field_73090_b` (the SRG names of `theWorld`/`thisPlayerMP`) and
  **not** the MCP names → the `@Shadow` fields resolve in production without refmap entries.
* The three GT mixins use `remap = false` (`MixinGTOreAdapter.java:16`, `MixinBWOreAdapter.java:16`,
  `MixinGTPPOreAdapter.java:14`) — correct for non-remapped mod classes, and their string
  descriptors (`…/OreInfo;isNatural:Z`, `GTProxy$OreDropSystem`, `…/OreInfo;I`) match the real
  beta2 bytecode shapes.
* `@Unique @Override` for the added `ItemInWorldManager` method (`MixinItemInWorldManager.java:51-53`)
  plus the caller-side interface cast (`IEZMinerItemInWorldManager.java:12,46`;
  `BlockHarvestActionExecutor.java:90`) is the correct pattern; no `@Shadow` method is missing
  (`isCreative()` really is public, `ItemInWorldManager.java:73`).

### 5.2 Fortune-uncap semantics on the real adapter code
* **It does take effect** on the generation the project resolves: `GTOreAdapter.getBigOreDrops`
  (`beta2 :323-341`) is reached from `getOreDrops` (`:252-260`) for non-small ores with
  `oreDropSystem = GTMod.proxy.oreDropSystem` (default `OreDropSystem.FortuneItem`,
  `GTProxy.java:630`), and the mixin can force `if (fortune > 3)` to `false` → `fortune` keeps the
  real enchantment level. Same for `BWOreAdapter:253` and `GTPPOreAdapter:109`.
* **No other branch is corrupted**: `isNatural` is read exactly once per `getOreDrops`
  (`GTOreAdapter:250`, `BWOreAdapter:176`) and is only used to zero `fortune`; the redirect is a
  pass-through when `enableFortuneForPlacedOre=false` (`FortuneCompatHelper.java:10-12`). The
  `UnifiedBlock/PerDimBlock/Block` branches do not use `fortune` at all
  (`GTOreAdapter.java:342-369`), and the small-ore path (`:291-311`) is already uncapped by design
  and untouched.
* GT++ drops reach the mixin on beta2 because the block-level path delegates to the adapter:
  `BlockBaseOre.getDrops` → `GTPPOreAdapter.INSTANCE.getOreDrops(...)` (`beta2 BlockBaseOre.java:117-131`).
* Mixed-in class is the same instance the drop path uses — no shadow/copy mismatch (the adapters
  are singleton `INSTANCE` fields, `GTOreAdapter.java:40`, `GTPPOreAdapter.java:24`), and the drops
  are computed by the *block* being mined, not by a per-player copy.

### 5.3 `MixinItemInWorldManager` vs vanilla `tryHarvestBlock` — points that do match
* Order of operations matches vanilla: tool damage before removal
  (`mixin :89-98` vs `ItemInWorldManager.java:312-320`), `onBlockHarvested` before removal
  (`:101` vs `:269`), `onBlockDestroyedByPlayer` after a successful removal (`:111-112` vs `:274`),
  drops only when `canHarvest` (`:117-119` vs `:323-326`), `destroyCurrentEquippedItem()` when
  the stack empties (`:94-96` vs `:316-319`).
* `canHarvest` is computed by the callers with the same predicate vanilla uses
  (`block.canHarvestBlock(player, meta)`, `BlockHarvestActionExecutor.java:91-92,181-182`) plus
  the Witchery OR (`XPDropHandler.java:61`), and TE blocks are excluded
  (`:80-82,146-151`) so vanilla semantics (incl. `BreakEvent`, TE cleanup) are preserved for them.
* `setBlock(..., air, 0, 2)` still reaches clients: flag 2 triggers `markBlockForUpdate`/
  chunk send-queue in `World.setBlock` (`World.java:490+`), so the interface javadoc's "caller must
  send a chunk resync instead of relying on per-block S23" is over-cautious, not a defect.
* XP source equivalence when `fireBreakEvent=false`: `XPDropHandler.computeBlockXP`
  (`:53-65`) reproduces `BreakEvent`'s rules (creative → 0, silk touch → 0, unharvestable → 0,
  `block.getExpDrop(world, meta, fortune)`) against `BlockEvent.java:83-93`.
* `NaturaSaguaroCompat.maybeHarvest` is invoked only on `removed && canHarvest` (`:117`), returns
  before `harvestBlock` exactly as documented, and posts `HarvestDropsEvent` with the real player
  (`NaturaSaguaroCompat.java:104-105`) so `Manager`'s collector sees the drops.

### 5.4 Bridge → core coupling
* `OreCompatRegistry` evaluates vanilla ores first (`:51-53`), then every adapter inside
  `try { … } catch (RuntimeException | LinkageError)` (`:54-62`, `:85-94`) — a broken optional
  adapter cannot fail the whole detection; adapters with no resolvable class are never added
  (`:151-155`), so `isAvailable()` is always true inside the list (harmless redundancy).
* Duplicate registration is possible in principle (a GT5U legacy block matching several
  adapters) but harmless: first match returns (`:56-58`) and the list is deterministic
  (`createAdapters` order, `:98-148`), wrapped `unmodifiableList` (`:148`).
* `CoFHWaterBridge`'s core assumption verified: CoFH water **is** a `BlockDynamicLiquid` whose
  inherited `onNeighborBlockChange` is a water no-op, so the deterministic fill is needed and its
  flag/level match vanilla `func_149813_h` (matrix §3.4).
* `ChunkBlockWriteHelper.notifyBatchNeighborChange` dedupes neighbours before the bridge calls
  (`:231-239`) and passes the *removed* block as the neighbour argument (`:248`), matching
  `World.notifyBlockOfNeighborChange`'s contract; bridge calls sit inside the same try/catch
  (`:247-263`).
* `GT5ToolDurabilityBridge` is only consulted in survival
  (`MixinItemInWorldManager.java:84`, `BlockHarvestActionExecutor.java:185`,
  `ChunkCachedHarvester.java:173`) and returns `true` when GT is absent or the item is not a GT
  tool — no new failure mode for other tools; the "skip the block instead of breaking the tool"
  contract is respected (the block is not removed before the check).
* `TinkersConstructLevelingBridge`'s smelt-ownership map is server-thread-only, keyed by
  `BasePositionFounder.encodePos` and cleared in a `finally`
  (`MixinItemInWorldManager.java:61-67`, `BlockHarvestActionExecutor.java:162-168`) — no leak;
  `Manager` consults it through `isSmeltCandidate` (`core/Manager.java:304`).
* `WitcheryVampireBridge.canHarvestWithBareHands` requires an empty hand (`:48`), so it cannot
  mask a bad tool choice; it is OR-ed into `canHarvest` at both executors (`:92`, `:182`) and into
  `computeBlockXP` (`XPDropHandler.java:61`).
* `NaturaSaguaroCompat.cascadeUnsupportedNeighbors` positions are admitted by the caller's drain
  loop (already verified by `chain-audit.md` §5 lines 719-723) and re-checked by the executors'
  `isUnbreakable` gate (`BlockHarvestActionExecutor.java:69,140,254`) — no unsupported block is
  forced through.

### 5.5 Dedicated-server / absent-mod safety
* Every optional-mod class reference is either behind a nested `Impl` holder guarded by a
  constant `Loader.isModLoaded(...)` (`GT5ToolDurabilityBridge.java:41,85-114`,
  `NaturaSaguaroCompat.java:44,124-140`, `TinkersConstructLevelingBridge.java:49,116-133`,
  `WitcheryVampireBridge.java:28,64-73`) or resolved with `Class.forName(name, false, loader)`
  (`ClassNameCompatSupport.java:40`) / `SafeReflection.forName` (`SafeReflection.java:42-55`) —
  no static initialiser of an absent mod can run.
* `NaturaSaguaroCompat`'s and `TinkersConstructLevelingBridge`'s `Loader.isModLoaded` ids match
  the real mod ids (`Natura` — `tmp/Natura-master/.../Natura.java` `@Mod(modid = "Natura")`;
  `TConstruct` — the TiC tree's package/`@Mod`; `witchery`, `gregtech` as used in
  `GT5ToolDurabilityBridge.java:41`).
* `SafeReflection.getMethod`/`getDeclaredMethod`/`getField` all call `setAccessible(true)`
  (`SafeReflection.java:68-97,109-118`) → the private static `sendChangeToolPacket`
  (`GT5ToolCompat.java:136-137`) is actually invokable; `Class.getMethod` finds the
  interface-declared `getMetaTileID`/`setInitialValuesAsNBT`
  (`GT5BlockSwapCompat.java:81-99`) with the interface fallback as a safety net.
* `ToolboxSlot` is an enum (`ToolboxSlot.java:39`), so the `(List<Enum<?>>)` cast at
  `GT5ToolCompat.java:230` cannot throw; `ToolboxItemStackHandler`'s public `(ItemStack)`
  constructor exists (`:24`) and `getSlots`/`getStackInSlot` come from ModularUI2's
  `ItemStackHandler` (`:52,57`).

---

## 6. Inconclusive

1. **Mixin failure mode when the target class is absent (C1/C2).** Whether Mixin 0.8.5-GTNH with
   `"required": false` drops only the single failing mixin (leaving `MixinItemInWorldManager`
   active) or marks the whole `mixins.EZMiner.json` config as errored (taking the fast-harvest path
   down too) cannot be determined without executing the loader — same open item as
   `infra-audit.md` inconclusive #2 and `client-audit.md` §5. The blast radius of C10/C1 depends on
   it; the registration inconsistency itself is certain.
2. **Whether `GT5-Unofficial-5.09.54.133` is a supported target at all.** `dependencies.gradle:5`
   pins GTNH `2.9.0-beta-3`, and the sources compile only against a GT5U that has
   `gregtech.common.ores` (beta2); but `tmp/` ships the 5.09.54.133 tree and the task names it as a
   drift axis. If 5.09.54.133 is a target, C1 is a P1 gap; if it is only historical reference
   material, C1 degrades to "the mod is not buildable/functional on that generation, and nothing
   says so" (P3).
3. **Witchery vampire threshold (level 5).** `isVampire()`/`getVampireLevel()` exist and
   `ExtendedPlayer.get(EntityPlayer)` exists (verified from the class file constant pool:
   the descriptor `(Lnet/minecraft/entity/player/EntityPlayer;)Lcom/emoniph/witchery/common/ExtendedPlayer;`
   and the UTF-8 name `get` are both present), but whether bare-hand stone mining is granted at
   level 5, by another predicate (e.g. a specific vampire power), or only against particular
   blocks/harvest levels cannot be derived from class files alone. If the real gate is narrower,
   EZMiner over-grants; if broader, it under-grants. No defect claimed.
4. **`require` default and the `@Redirect`-field fragility (C2).** MixinExtras' default `require`
   for `@ModifyExpressionValue`/`@Redirect` is asserted to be 0 (silent skip) from documentation,
   not re-read from the bundled MixinExtras sources (not present in the workspace); the
   `@Redirect` on a field additionally fails hard if the instruction count ever exceeds one.
   The suggested `require = 1` fix is safe under either reading.
5. **GT tool remaining durability in `ToolHarvestEligibility`.** `remainingDurability`
   (`ToolHarvestEligibility.java:38-39`) uses `stack.getMaxDamage() - stack.getItemDamage()`;
   `MetaGeneratedTool` does **not** declare `getMaxDamage(ItemStack)`/`getDamage(ItemStack)`
   overrides (grep over `tmp/GT5-Unofficial-beta2/.../gregtech/**` finds none), yet GT's own tooltip
   uses the same `aStack.getMaxDamage() - getDamage(aStack)` pattern
   (`GTGenericItem.java:98-99`) → either the mapping is done in a superclass not read here or both
   are equally rough. Not a defect claim, but the GT durability reserve for auto tool-switching is
   only as good as that mapping. (Note this only becomes live on a dedicated server once C3 is
   fixed.)
6. **Qz-Miner's own legacy BW mixin appears mis-targeted** —
   `tmp/Qz-Miner/.../MixinBWTileEntityMetaGeneratedOreLegacy.java:17` binds a field named
   `natural` while the real field on `bartworks.system.material.BWTileEntityMetaGeneratedOre` is
   `mNatural` (`tmp/GT5-Unofficial-5.09.54.133/.../BWTileEntityMetaGeneratedOre.java:36`), and its
   `require = 1` would make that a loud failure. Recorded as a cross-check caveat against C1's
   "upstream covers the legacy generation" argument: the `TileEntityOres` and `BlockBaseOre`
   legacy mixins do match the real 5.09.54.133 bytecode; this one may not.
7. **Real impact of C4/C6 for a GTNH pack.** Both require a specific non-TE block/tool pair
   (GT powder barrel for C4, GT chainsaw/saw for C6). The vanilla divergence is proven from source;
   how often a player chain-mines exactly those is not measurable here.

---

## 7. Scope / process notes

* Files read for this audit: all 16 files under `compat/**` (incl. `compat/ore/**`), all 10 files
  under `mixin/**`, both mixin JSONs, `utils/FortuneCompatHelper.java`,
  `utils/ToolHarvestEligibility.java`, `utils/SafeReflection.java`,
  `chain/execution/{BlockHarvestActionExecutor,ChainBreakEventHelper,XPDropHandler,ChunkCachedHarvester,ChunkBlockWriteHelper,BlockSwapModeHandler}.java`,
  `core/BaseOperator.java`, `core/Manager.java`, `core/founder/DeterminingIdentical.java`,
  `core/crop/CropAdapterRegistry.java`, `network/PacketToolBreakHandoff.java`,
  `network/PacketToolSwapRequest.java`, `client/ClientProxy.java`, `CommonProxy.java`,
  `EZMiner.java`, `Config.java` (fortune/mixin sections), `dependencies.gradle`,
  `gradle.properties`, plus the third-party trees named in the task
  (`GT5-Unofficial-beta2`, `GT5-Unofficial-5.09.54.133`, `Natura-master`,
  `Et-Futurum-Requiem-master`, `CoFHCore-1.12-Legacy-1.7.10`, `TinkersConstruct-master`,
  `IguanaTweaksTConstruct-master`, `witchery-1.7.10-0.24.1` (classes only, read as raw class-file
  constant pools), `Galacticraft-master`, `EndlessIDs-master`, `Hodgepodge-master`,
  `ModularUI2-master`, `ServerUtilities-master`, `ForestryMC-master`, `Qz-Miner`) and the 1.7.10
  decompiled sources under `build/rfg/minecraft-src/java/**`.
* `Galacticraft-master` was checked for compat hooks: **no EZMiner compat/mixin code references
  Galacticraft** (grep over `compat/**` + `mixin/**` finds no `galacticraft`/`Galacticraft`
  reference; the AGENTS.md Galacticraft note concerns `DeterminingIdentical`'s ore classification,
  which is `core/founder` scope). Recorded as "no hook to verify", not as a finding.
* No file under `src/` or `tmp/` was created or modified; the only write is this report. No Gradle
  or Java process was executed (the build is therefore not part of the evidence, except where a
  pre-existing build artifact was read: the refmap, the jar manifest and the released jar — all
  read-only, the jar in memory without extraction).
