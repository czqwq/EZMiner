# Review: Tree-felling leaves remain (砍树树叶依旧存在)

Scope: how tree-felling ("Log" blast sub-mode) matches blocks, why leaves stay
floating after felling, and a perf-friendly fix. All file/line references are to
the current working tree. Root package `com.czqwq.EZMiner`.

---

## A) How tree-felling matches blocks

Tree-felling is **blast sub-mode 4 ("Log")**, wired through the planning layer to
`LogFounder`:

- `chain/planning/LegacyFounderPlanningFactory.java:57-72` — blast sub-modes are
  switched on `modeState.blastMode`; case 4 (`:64-65`) returns `new LogFounder(...)`.
  So tree-felling runs the legacy `BasePositionFounder` search + the shared harvest
  path in `core/BaseOperator`. It is not a chain-mode feature.
- `chain/planning/ChainPlanner.java:22-24`, `ChainCandidateFilter.java`, `ChainTraverser.java`,
  `ChainBlockMatcher.java`, `ChainCommitteeFilter` — these are **interfaces** with no
  concrete tree-felling impl; the tree-felling match logic lives entirely in
  `LogFounder.checkCanAdd`.

`LogFounder.checkCanAdd` (`core/founder/LogFounder.java:67-91`) has two branches:

1. **`logFuzzyEnabled == true` (the default, `Config.java:87`, `:618-623`)** —
   `checkCanAdd` uses class equality, `LogFounder.java:78-83`:
   `sampleBlock.getClass().equals(block.getClass())`.
   - `sampleBlock` is the block the player first broke
     (`BasePositionFounder.java:116-118`), i.e. a log (`BlockLog`).
   - Leaves are a different class (`BlockLeaves`), so **leaves are NEVER matched in
     fuzzy mode** — the plan simply never contains them.
   - It also only matches one log *class* (oak `BlockOldLog` vs birch `BlockNewLog`
     differ), so even wood variant classes are excluded.

2. **`logFuzzyEnabled == false`** — OreDict wood/leaf lookup,
   `LogFounder.java:84-87` → `isWoodOrLeaf` (`:94-101`), which returns true for both
   `"logWood"` AND `"treeLeaves"` oredict names (`:98`). So in non-fuzzy mode leaves
   ARE included in the plan and harvested directly (broken, not decayed).

**Answer A:** In the default config, tree-felling matches **only the one log class**
the player broke; leaves are never in the plan. In OreDict mode, leaves *are*
matched and harvested directly. The chain subsystem (`ChainPositionFounder` /
`FuzzyChainPositionFounder` / `ChainPreCalcEngine`) is unrelated to tree-felling —
both `DeterminingIdentical.identical(...)` (`ChainPositionFounder.java:301-302`) and
the cached-engine match (`ChainPreCalcEngine.java:246-258`) require the same block
class as the sample, so leaves are excluded there too.

---

## B) Why leaves remain floating / uneaten

Two independent causes both present in the default configuration:

**Cause 1 — fuzzy mode simply never targets leaves (dominant, default on).**
With `logFuzzyEnabled=true`, `LogFounder.java:78-83` requires the exact sample
block class, so a leaf canopy around a felled trunk is never added to
`canBreakPositions` at all. The mod removes only the logs and stops.

**Cause 2 — the fast harvest path never flags leaves for decay.**
Leaf decay in 1.7.10 is **not** "remove log → schedule leaf" and is **not** purely
random-tick decay either. It is a tagged-flag cascade:

- `build/rfg/minecraft-src/java/net/minecraft/block/BlockLeaves.java:81-103` —
  `breakBlock` scans the adjacent `2×2×2` region and calls `beginLeavesDecay(...)`
  on neighboring leaves.
- `BlockLeaves.java:341-351` — `beginLeavesDecay` sets the **bit-8 decay-check
  flag** on the leaf (via `setBlockMetadataWithNotify`).
- `BlockLeaves.java:108-219` — only leaves with bit-8 set (and bit-4 clear) run the
  log-distance scan on their random tick and, finding no log within range
  (`canSustainLeaves`, `:142`), call `removeLeaves` → `setBlockToAir`
  (`:236-240`). Fresh worldgen leaves have bit-8 = 0 and **never decay on their
  own**; the flag is only ever set by an adjacent block's `breakBlock`.

The trigger for that flag is `Block.breakBlock`. EZMiner's fast path removes blocks
via `ExtendedBlockStorage.func_150818_a` — `chain/execution/ChunkBlockWriteHelper.java:150-165`
(`writeAirToEbs`, line 163) — and then calls only `block.onBlockDestroyedByPlayer`
and `world.markBlockForUpdate(...)`:

- `chain/execution/ChunkCachedHarvester.java:169-177` (chunk-cached path, used when
  `Config.useChunkCachedHarvest=true`, `Config.java:291`)
- `chain/execution/BlockHarvestActionExecutor.java:179-184` (batch path)
- `chain/execution/BlockHarvestActionExecutor.java:73-76` / the fast-harvest mixin
  `mixin/early/MixinItemInWorldManager.java:108` (`setBlock(x,y,z,air,0,2)` with
  flag=2 trips on `World.setBlock`; `World.setBlockToAir` is not what this calls)
  for the `useChunkCachedHarvest=false` fallback.

None of these invoke `Block.breakBlock` on the removed log, so no neighboring leaf
ever gets bit-8 set, and therefore no floating leaf ever runs the decay scan. **The
leaves float indefinitely** — this is the mechanism the task's premise (B)
describes, with the precise root cause being the skipped `breakBlock`/flag step
(not just missing neighbor notifications).

Net effect: with defaults, leaves are (1) not matched at all and (2) even if they
were adjacent, the fast path never marks them to decay — so the canopy persists.

---

## C) Proposed fix (recommend one)

**Recommendation — Option (i): flag adjacent leaves for decay after felling
(`beginLeavesDecay`), mirroring vanilla's `BlockLeaves.breakBlock` trigger.**

This is the correct and cheapest fix because it *reuses the engine's existing vanana
decay path* instead of re-implementing log-distance checks, and it is O(1) per
harvested block with a small constant neighborhood (1 block radius), no re-planning,
no per-tick scan, and no extra config plumbing.

Concretely, add a small helper that, after the fast-path EBS write for a **log**,
walks the 6 (or 26) neighbors and calls `beginLeavesDecay` on any leaf, then touches
the exact places the fast path traditionally skips:

1. **New helper** in `chain/execution/ChunkBlockWriteHelper.java` (next to
   `writeAirToEbs`, `:150`), e.g.:
   ```java
   public static void flagNeighbouringLeavesForDecay(World world, int x, int y, int z, Block removed) {
       if (removed == null || !removed.getMaterial().isWood()) return; // logs only
       final int r = 1;
       for (int dx = -r; dx <= r; dx++)
       for (int dy = -r; dy <= r; dy++)
       for (int dz = -r; dz <= r; dz++) {
           if (dx == 0 && dy == 0 && dz == 0) continue;
           int nx = x + dx, ny = y + dy, nz = z + dz;
           if (!world.blockExists(nx, ny, nz)) continue;
           Block nb = world.getBlock(nx, ny, nz);
           if (nb.isLeaves(world, nx, ny, nz)) {
               nb.beginLeavesDecay(world, nx, ny, nz);   // sets bit-8 (vanilla)
               world.markBlockForUpdate(nx, ny, nz);      // cheap client sync
           }
       }
   }
   ```
   `Block.isLeaves` and `Block.beginLeavesDecay` exist in 1.7.10 (see
   `BlockLeavesBase`/`BlockLeaves.java:341-357`). Calling `beginLeavesDecay` marks
   the leaf bit-8 so its own random tick later runs `updateTick` and removes it when
   it is out of range — this is exactly what vanilla does when a log is broken
   (`BlockLeaves.breakBlock`, `:81-103`), so decay timing/meshes match vanilla.

2. **Call it from the fast-path harvest sites** right after the air write for a
   wood block:
   - `chain/execution/ChunkCachedHarvester.java` after `:169` (`writeAirToEbs`),
     guarded by `block.getMaterial().isWood()`
   - `chain/execution/BlockHarvestActionExecutor.java` after `:179` in both the
     per-block and batch loops.
   This mirrors the existing `NaturaSaguaroCompat.cascadeUnsupportedNeighbors(...)`
   post-removal pattern (`compat/NaturaSaguaroCompat.java:69-82`), which is the
   established precedent in this codebase for "after removing a block, fix up
   dependent neighbours and feed them into the pipeline".

   Optional: for the `useChunkCachedHarvest=false` path, do the same after
   `MixinItemInWorldManager.java:108`.

3. **Optionally (recommend for the default fuzzy config):** also let non-fuzzy
   OreDict matching be the tree-felling mode when leaves should be broken directly
   (sapling drops). If breaking leaves *directly* (rather than by decay) is desired,
   that already works with `logFuzzyEnabled=false` and requires no code change —
   but direct leaf breaking bypasses the natural-mesh look, and shears/fortune
   drops are already handled (`ShearsHarvestBridge`, `core/founder/LogFounder.java:94-101`).
   Because short-distance decay is the user-facing expectation for a tree-feller,
   the flag-based decay fix above is preferable and safe in both modes.

**Why not the other options:**
- *(ii) random-tick scheduling* (`world.scheduleBlockUpdate`) is heavier and does
  not turn a non-decaying leaf into a decaying one — the bit-8 flag still must be
  set first (see `updateTick :108-114`), so (i) already subsumes this.
- *(iii) a full cleanup pass / re-run founder* (remove any leaf with no log within
  4-6 blocks using the match pipeline) re-plans from scratch, adds a second
  background search and a second harvest pass, and risks large fan-out on big trees
  for marginal benefit — not worth it when the vanilla flag mechanism exists.

No changes to `Config.java`, the GUI, or the network layer are required; the fix
lives entirely in the harvest executor + one helper, and stays within the existing
per-tick batch (the neighbor walk is bounded and on the server thread).

---

### Summary of files to touch for the recommended fix
- `chain/execution/ChunkBlockWriteHelper.java` — add `flagNeighbouringLeavesForDecay(...)`.
- `chain/execution/ChunkCachedHarvester.java:169` — call it after `writeAirToEbs`
  for wood blocks.
- `chain/execution/BlockHarvestActionExecutor.java:179` — call it after the air
  write in both the per-block and batch paths.
- (fallback) `mixin/early/MixinItemInWorldManager.java:108` — call it after the
  flag=2 setBlock when `useChunkCachedHarvest=false`.
