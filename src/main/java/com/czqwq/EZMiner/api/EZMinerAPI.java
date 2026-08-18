package com.czqwq.EZMiner.api;

import java.util.UUID;

import net.minecraft.util.MathHelper;

import org.joml.Vector3i;

import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.network.PacketChainModeSwitch;
import com.czqwq.EZMiner.chain.network.PacketKeyState;
import com.czqwq.EZMiner.chain.state.ChainPlayerState;
import com.czqwq.EZMiner.core.Manager;
import com.czqwq.EZMiner.core.PlayerManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Public programming interface for other mods to drive EZMiner's chain mining.
 *
 * <p>
 * <b>Server-thread rule.</b> All methods in this class that mutate server state
 * ({@link #setChainKeyHeld}, {@link #setMainMode}, {@link #setMode},
 * {@link #startChain}) must be called on the server's main thread.
 * {@link #isActive} and {@link #isKeyHeld} are read-only and may be called from
 * any thread. Wiring mirrors what the trusted {@link PacketKeyState} /
 * {@link PacketChainModeSwitch} handlers would do, so no new permissions are
 * granted — the caller of a server-side method is trusted by construction
 * (it is server-side code).
 *
 * <p>
 * <b>Client entry.</b> {@link #pressChainKeyClient()} / {@link #releaseChainKeyClient()}
 * are the only client-side entry points: they send the same C→S packets the
 * chain key fires, leaving server authority intact.
 */
public final class EZMinerAPI {

    private EZMinerAPI() {}

    // ── Modes (array lengths, keep in sync with MinerModeState) ──
    private static final int MAX_MAIN_MODE = Math.max(0, 2);
    private static final int MAX_BLAST_MODE = Math.max(0, 6);
    private static final int MAX_CHAIN_MODE = Math.max(0, 3);
    private static final int MAX_SPECIAL_MODE = Math.max(0, 5);

    private static Manager managerOf(UUID player) {
        if (player == null || PlayerManager.instance == null) return null;
        return PlayerManager.instance.managers.get(player);
    }

    private static ChainPlayerState stateOf(UUID player) {
        if (player == null) return null;
        return EZMiner.chainStateService.getOrCreate(player);
    }

    // =====================================================================
    // Server-authoritative chain toggle
    // =====================================================================

    /**
     * Sets whether the chain key is held for {@code player} (server-side,
     * equivalent to receiving a trusted {@link PacketKeyState}). {@code true}
     * arms the chain: the next {@code BreakEvent} from a matching block starts
     * a chain operation. {@code false} stops the current operation.
     */
    public static boolean setChainKeyHeld(UUID player, boolean held) {
        Manager mgr = managerOf(player);
        if (mgr == null) return false;
        ChainPlayerState state = stateOf(player);
        if (state == null) return false;
        state.keyPressed = held;
        // Mirror PacketKeyState.Handler side effects (block-swap clear / re-send marks).
        if (!held && mgr.isBlockSwapMode() && mgr.player != null) {
            EZMiner.network.network.sendTo(new com.czqwq.EZMiner.chain.network.PacketBlockSwapClear(), mgr.player);
        }
        if (held && mgr.player != null) {
            if (mgr.isSpecialMinesweeperMode()) {
                mgr.resendMinesweeperMarks(mgr.player);
            } else if (mgr.isSpecialSudokuMode()) {
                mgr.resendSudokuFills(mgr.player);
            }
        }
        return true;
    }

    /**
     * Programmatically starts a chain operation rooted at {@code pos} for
     * {@code player}, regardless of key state (except the player must be online
     * and the position valid). Server thread only.
     *
     * @return {@code true} if a chain was started
     */
    public static boolean startChain(UUID player, Vector3i pos) {
        Manager mgr = managerOf(player);
        if (mgr == null || pos == null || mgr.player == null) return false;
        if (mgr.player.worldObj == null || !mgr.player.worldObj.blockExists(pos.x, pos.y, pos.z)) return false;
        mgr.startChain(pos, mgr.player);
        return true;
    }

    // =====================================================================
    // Server-authoritative mode control
    // =====================================================================

    /** Sets the main mode (0 Blast, 1 Chain, 2 Special). Other sub-modes untouched. */
    public static boolean setMainMode(UUID player, int mainMode) {
        return setMode(player, mainMode, -1, -1, -1);
    }

    /** Sets the blast sub-mode index (0-6). */
    public static boolean setBlastSubMode(UUID player, int index) {
        return setMode(player, -1, index, -1, -1);
    }

    /** Sets the chain sub-mode index (0-3). */
    public static boolean setChainSubMode(UUID player, int index) {
        return setMode(player, -1, -1, index, -1);
    }

    /** Sets the special sub-mode index (0-5). */
    public static boolean setSpecialSubMode(UUID player, int index) {
        return setMode(player, -1, -1, -1, index);
    }

    /**
     * Sets any combination of the four mode selectors. A value of {@code -1}
     * leaves that selector untouched; all others are clamped to valid ranges.
     */
    public static boolean setMode(UUID player, int mainMode, int blastMode, int chainMode, int specialMode) {
        ChainPlayerState state = stateOf(player);
        if (state == null) return false;
        if (mainMode >= 0) state.minerModeState.mainMode = MathHelper.clamp_int(mainMode, 0, MAX_MAIN_MODE);
        if (blastMode >= 0) state.minerModeState.blastMode = MathHelper.clamp_int(blastMode, 0, MAX_BLAST_MODE);
        if (chainMode >= 0) state.minerModeState.chainMode = MathHelper.clamp_int(chainMode, 0, MAX_CHAIN_MODE);
        if (specialMode >= 0) state.minerModeState.specialMode = MathHelper.clamp_int(specialMode, 0, MAX_SPECIAL_MODE);
        return true;
    }

    // =====================================================================
    // Read-only queries
    // =====================================================================

    /** Whether {@code player}'s chain key is currently held (server-side). */
    public static boolean isKeyHeld(UUID player) {
        ChainPlayerState state = stateOf(player);
        return state != null && state.keyPressed;
    }

    /** Whether {@code player} has an active chain operation running. */
    public static boolean isOperateActive(UUID player) {
        ChainPlayerState state = stateOf(player);
        return state != null && state.runtimeState.inOperate;
    }

    /** Convenience: key held <em>or</em> operation running. */
    public static boolean isActive(UUID player) {
        ChainPlayerState state = stateOf(player);
        return state != null && (state.keyPressed || state.runtimeState.inOperate);
    }

    // =====================================================================
    // Chain activation behaviour (hold-to-activate vs click-to-toggle)
    // =====================================================================

    /**
     * Sets the chain activation mode (client config, persisted): {@code 0} =
     * hold the chain key to mine, {@code 1} = press once to toggle.
     */
    public static void setChainActivationMode(int mode) {
        Config.saveChainActivationMode(mode == 0 ? 0 : 1);
    }

    /** Current chain activation mode ({@code 0} hold, {@code 1} toggle). */
    public static int getChainActivationMode() {
        return Config.chainActivationMode;
    }

    // =====================================================================
    // Client-side entry (sends C→S packets; server stays authoritative)
    // =====================================================================

    /** Client: press the chain key (sends {@link PacketKeyState} to the server). */
    @SideOnly(Side.CLIENT)
    public static void pressChainKeyClient() {
        EZMiner.network.network.sendToServer(new PacketKeyState(true));
    }

    /** Client: release the chain key (sends {@link PacketKeyState} to the server). */
    @SideOnly(Side.CLIENT)
    public static void releaseChainKeyClient() {
        EZMiner.network.network.sendToServer(new PacketKeyState(false));
    }

    /**
     * Client: apply a mode selection (sends {@link PacketChainModeSwitch}).
     * The server clamps and stores it authoritatively.
     */
    @SideOnly(Side.CLIENT)
    public static void applyModeClient(int mainMode, int blastMode, int chainMode, int specialMode) {
        EZMiner.network.network.sendToServer(new PacketChainModeSwitch(mainMode, blastMode, chainMode, specialMode));
    }
}
