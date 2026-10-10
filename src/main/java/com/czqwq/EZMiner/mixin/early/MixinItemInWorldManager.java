package com.czqwq.EZMiner.mixin.early;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.ItemInWorldManager;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.czqwq.EZMiner.chain.execution.ChainBreakEventHelper;
import com.czqwq.EZMiner.chain.execution.XPDropHandler;
import com.czqwq.EZMiner.compat.GT5ToolDurabilityBridge;
import com.czqwq.EZMiner.compat.NaturaSaguaroCompat;
import com.czqwq.EZMiner.compat.RemovedByPlayerBridge;
import com.czqwq.EZMiner.compat.ShearsHarvestBridge;
import com.czqwq.EZMiner.compat.TinkersConstructCompat;
import com.czqwq.EZMiner.compat.TinkersConstructLevelingBridge;
import com.czqwq.EZMiner.mixin.interfaces.IEZMinerItemInWorldManager;

/**
 * Adds a fast-harvest path to {@code ItemInWorldManager} that skips per-block
 * {@code BreakEvent}, sound/particle packets, excess world queries, and neighbor
 * notifications during chain mining.
 * <p>
 * Hodgepodge compatibility: this mixin targets {@code ItemInWorldManager} which
 * has no Hodgepodge mixins. The skipped {@code ForgeHooks.onBlockBreakEvent}
 * path means Hodgepodge's TE-description batcher (which hooks that method) is
 * also skipped — this is intentional because non-TE blocks do not need
 * description packets.
 */
@Mixin(ItemInWorldManager.class)
public abstract class MixinItemInWorldManager implements IEZMinerItemInWorldManager {

    @Shadow
    public World theWorld;

    @Shadow
    public EntityPlayerMP thisPlayerMP;

    @Shadow
    public abstract boolean isCreative();

    /**
     * Fast block harvest without per-block event firing, sound effects, or neighbor
     * notifications.
     *
     * @see IEZMinerItemInWorldManager#ezminer$tryHarvestBlockFast(int, int, int, boolean, BlockEvent.BreakEvent)
     */
    @Unique
    @Override
    public boolean ezminer$tryHarvestBlockFast(int x, int y, int z, boolean canHarvest,
        BlockEvent.BreakEvent preFiredEvent) {
        // ── Resolve block info early (needed by the protection gate and the durability check) ──
        Block block = theWorld.getBlock(x, y, z);
        int meta = theWorld.getBlockMetadata(x, y, z);

        // ── Protection gate: the single source of truth for all three harvest paths ──
        // The fast/EBS paths never call World.setBlock, so World.canMineBlock and any claim mod
        // that cancels through BlockEvent.BreakEvent (e.g. ServerUtilities' claimed-chunk
        // handler) were both skipped, letting a chain grief protected areas. canBreakAt always
        // consults canMineBlock and fires the break event when the config or a protection mod
        // requires it. Runs BEFORE any mutation (ToolCore hook included).
        if (!ChainBreakEventHelper.canBreakAt(theWorld, thisPlayerMP, x, y, z)) {
            return false;
        }

        // ── Vanilla Item.onBlockStartBreak replay ──
        // Vanilla calls this and cancels the harvest on a true return. The fast path used to
        // replay only TiC + vanilla shears, which lost GT's own hooks: a GT chainsaw dropped
        // saplings instead of leaves, and a GT saw on ice produced nothing (vanilla BlockIce
        // only drops with silk touch) while vanilla's hook would have dropped the block and
        // damaged the tool. Honour the hook exactly like vanilla, then keep the bridges below
        // for the cases they exist for.
        //
        // TiC tools are EXCLUDED. ToolCore.onBlockStartBreak (reached through HarvestTool) itself
        // loops TConstructRegistry.activeModifiers and fires beforeBlockBreak — the very hook
        // TinkersConstructLevelingBridge.fireBeforeBlockBreak replays a few lines below — so
        // calling it here would double-fire IguanaTweaks' XP/autosmelt per chained block. It
        // would also re-enable the AOE recursion the bridge exists to avoid: LumberAxe/Scythe/
        // AOEHarvestTool spawn a TreeChopTask per invocation and return true, which at chain
        // scale is one chop task per mined log. For TiC the bridge stays the single hook path.
        ItemStack startBreakStack = thisPlayerMP.getCurrentEquippedItem();
        if (startBreakStack != null && startBreakStack.getItem() != null
            && !TinkersConstructCompat.isTiCTool(startBreakStack)
            && startBreakStack.getItem()
                .onBlockStartBreak(startBreakStack, x, y, z, thisPlayerMP)) {
            return true;
        }

        // ── TiC compat: fire ActiveToolMod.beforeBlockBreak (IguanaTweaks tool XP,
        // autosmelt, …) like vanilla tryHarvestBlock does via onBlockStartBreak.
        // true = a hook consumed the block itself — skip our own harvest steps. ──
        // The position is pre-registered so Manager's EntityJoinWorldEvent
        // interceptor can attribute the synchronous smelt-drop spawn to this
        // player, and cleared afterwards (the hook may or may not consume).
        TinkersConstructLevelingBridge.markBeforeBlockBreak(thisPlayerMP, x, y, z);
        boolean consumed;
        try {
            consumed = TinkersConstructLevelingBridge.fireBeforeBlockBreak(thisPlayerMP, x, y, z);
        } finally {
            TinkersConstructLevelingBridge.clearBlockBreak(x, y, z);
        }
        if (consumed) {
            return true;
        }

        // ── Vanilla shear hook: shears on IShearable blocks (leaves/grass/vines)
        // drop the block item itself, injected into the drop collector via
        // HarvestDropsEvent. Cheap: a single IShearable instanceof short-circuits
        // for non-shearable blocks. The block is not consumed — we continue with
        // the normal removal below (getDrops may still add saplings etc.). ──
        ShearsHarvestBridge.fireIfShears(thisPlayerMP, x, y, z, block);

        // ── GT5 tool durability pre-check: skip blocks that would break the tool ──
        if (!isCreative() && !GT5ToolDurabilityBridge.hasEnoughDurability(thisPlayerMP, block, theWorld, x, y, z)) {
            return false;
        }

        // ── Tool damage (only in survival) ──
        if (!isCreative()) {
            ItemStack stack = thisPlayerMP.getCurrentEquippedItem();
            if (stack != null) {
                // notify the item that it was used to break a block
                stack.func_150999_a(theWorld, block, x, y, z, thisPlayerMP);
                if (stack.stackSize == 0) {
                    thisPlayerMP.destroyCurrentEquippedItem();
                }
            }
        }

        // ── Harvest callbacks ──
        block.onBlockHarvested(theWorld, x, y, z, meta, thisPlayerMP);

        // ── Remove the block ──
        // Default path: flag=2 sends the client update (flag & 2) but skips the neighbour
        // notification (flag & 1). That avoids 6 onNeighborBlockChange calls per block, which is
        // deliberate — the chain paths batch and de-duplicate neighbour notifications afterwards
        // via ChunkBlockWriteHelper.notifyBatchNeighborChange.
        //
        // Blocks that declare their own Block.removedByPlayer get the vanilla hook instead, because
        // the raw write skips mod side effects entirely: GT's BlockReinforced.removedByPlayer turns
        // meta 5 into a primed powder barrel, so a chain-mined barrel was silently deleted. The raw
        // write is kept for everyone else because vanilla's default removedByPlayer is
        // setBlockToAir (flag 3), which would reintroduce the per-block notifications above.
        final boolean removed;
        if (RemovedByPlayerBridge.overridesRemovedByPlayer(block)) {
            removed = block.removedByPlayer(theWorld, thisPlayerMP, x, y, z, canHarvest);
            if (removed) {
                theWorld.markBlockForUpdate(x, y, z);
            }
        } else {
            removed = theWorld.setBlock(x, y, z, Blocks.air, 0, 2);
        }

        if (removed) {
            block.onBlockDestroyedByPlayer(theWorld, x, y, z, meta);
        }

        // ── Drops ──
        // Saguaro non-zero metas drop an unregistered ItemStack; the compat
        // intercepts and replaces the drops (returns true = handled).
        if (removed && canHarvest && !NaturaSaguaroCompat.maybeHarvest(theWorld, thisPlayerMP, x, y, z, block, meta)) {
            block.harvestBlock(theWorld, thisPlayerMP, x, y, z, meta);
        }

        // ── XP ──
        // Vanilla guards the XP block with !isCreative(); handlePreComputedXP has no guard of
        // its own, so without this a creative player chain-mining coal/redstone ore spawned
        // orbs whenever the BreakEvent path supplied a pre-computed amount.
        if (removed && !isCreative()) {
            int exp;
            if (preFiredEvent != null) {
                exp = preFiredEvent.getExpToDrop();
            } else {
                exp = XPDropHandler.computeBlockXP(block, theWorld, meta, thisPlayerMP);
            }
            XPDropHandler.handlePreComputedXP(theWorld, block, x, y, z, exp, thisPlayerMP);
        }

        return removed;
    }
}
