package com.czqwq.EZMiner.client;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.Config;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.chain.network.PacketChainModeSwitch;
import com.czqwq.EZMiner.chain.network.PacketKeyState;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.network.PacketMinerConfig;
import com.czqwq.EZMiner.utils.MessageUtils;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client-side input handler.
 * <p>
 * Chain key (default: {@code `}) – hold to activate chain/blast, release to stop.<br>
 * Mode switch key (default: {@code V}) – tap to cycle the main mode (chain ↔ blast).<br>
 * Mouse scroll while chain key held – cycle the current sub-mode.
 */
@SideOnly(Side.CLIENT)
public class KeyListener {

    public static final KeyBinding KEY_CHAIN = new KeyBinding(
        "key.ezminer.chainKey",
        Keyboard.KEY_GRAVE,
        "key.categories.ezminer");
    public static final KeyBinding KEY_MODE_SWITCH = new KeyBinding(
        "key.ezminer.modeSwitch",
        Keyboard.KEY_V,
        "key.categories.ezminer");

    private boolean wasHoldingChain = false;
    /** Tracks whether chain is currently toggled on (only used in toggle mode). */
    private boolean chainToggled = false;

    @SubscribeEvent
    public void onInput(InputEvent event) {
        ClientProxy proxy = (ClientProxy) EZMiner.proxy;
        MinerModeState state = proxy.clientState.minerModeState;

        // ===== Main-mode toggle =====
        if (KEY_MODE_SWITCH.isPressed()) {
            String mode = state.nextMainMode();
            MessageUtils.printSelfMessage(I18n.format("ezminer.message.mainMode") + ": " + I18n.format(mode));
            syncModeToServer(state);
            proxy.clientState.chainClientState.mainMode = state.mainMode;
        }

        boolean holding = KEY_CHAIN.getIsKeyPressed();
        boolean risingEdge = holding && !wasHoldingChain;

        if (Config.chainActivationMode == 1) {
            // ===== Toggle mode: one press activates, next press deactivates =====
            if (risingEdge) {
                chainToggled = !chainToggled;
                if (chainToggled) {
                    startChain(state);
                } else {
                    stopChain();
                }
            }
            // Scroll wheel switches sub-mode while chain is toggled on
            if (chainToggled && event instanceof InputEvent.MouseInputEvent) {
                handleSubModeScroll(state);
            }
        } else {
            // ===== Hold mode (default): key held activates, release deactivates =====
            if (holding) {
                if (risingEdge) {
                    startChain(state);
                }
                // Scroll wheel switches sub-mode while chain key is held
                if (event instanceof InputEvent.MouseInputEvent) {
                    handleSubModeScroll(state);
                }
            }
            if (!holding && wasHoldingChain) {
                stopChain();
            }
        }

        wasHoldingChain = holding;
    }

    private void startChain(MinerModeState state) {
        ClientProxy proxy = (ClientProxy) EZMiner.proxy;
        // Sync mode to server BEFORE the chain-switcher packet so that when the server
        // receives the BreakEvent it already has the correct mode. Without this, the
        // server's Manager.minerModeState stays at its default (chain/basic) and ignores
        // whatever mode the client HUD is showing.
        syncModeToServer(state);
        EZMiner.network.network.sendToServer(new PacketKeyState(true));
        proxy.clientState.chainClientState.keyPressed = true;
        // NOTE: do NOT freeze here. The preview should start searching immediately so the
        // player sees the chain outline before breaking any blocks. The freeze() will be
        // triggered by PacketChainStateSync when inOperate transitions false → true (i.e.
        // when the first block is actually being mined on the server).
        // Sync config to server on activation
        EZMiner.network.network.sendToServer(new PacketMinerConfig(Config.buildClientMinerConfigForSync()));
    }

    private void stopChain() {
        ClientProxy proxy = (ClientProxy) EZMiner.proxy;
        EZMiner.network.network.sendToServer(new PacketKeyState(false));
        proxy.clientState.chainClientState.keyPressed = false;
        proxy.clientState.chainedBlockCount = 0;
        // Unfreeze preview: clear the frozen wireframe and allow the renderer to start a
        // fresh search when the player next aims at a block.
        proxy.minerRenderer.unfreeze();
        // Reset toggle so the next key press starts a new chain.
        // stopChain() is only ever called on explicit user key input, so resetting
        // chainToggled here is correct. When a chain ends naturally (ore exhausted)
        // the toggle intentionally stays on, allowing the player to immediately chain
        // another ore block without pressing the key again.
        chainToggled = false;
    }

    private void handleSubModeScroll(MinerModeState state) {
        ClientProxy proxy = (ClientProxy) EZMiner.proxy;
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            String subMode = (dWheel < 0) ? state.nextSubMode() : state.previousSubMode();
            syncModeToServer(state);
            proxy.clientState.chainClientState.mainMode = state.mainMode;
            proxy.clientState.chainClientState.subMode = state.currentSubModeIndex();
            MessageUtils.printSelfMessage(I18n.format("ezminer.message.subMode") + ": " + I18n.format(subMode));
        }
    }

    private void syncModeToServer(MinerModeState state) {
        EZMiner.network.network
            .sendToServer(new PacketChainModeSwitch(state.mainMode, state.blastMode, state.chainMode));
    }

    /**
     * Resets all chain-key state and clears any frozen preview when the client disconnects
     * from a server (or exits a single-player world).
     *
     * <p>
     * Without this, a frozen preview (set by the server {@code inOperate=true} packet) would
     * persist across sessions — the player would see a stale wireframe on the next login.
     * This is the client-side "lifecycle single-responsibility" guard required by Bug-R.
     */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientProxy proxy = (ClientProxy) EZMiner.proxy;
        proxy.clientState.chainClientState.keyPressed = false;
        proxy.clientState.chainClientState.inOperate = false;
        wasHoldingChain = false;
        chainToggled = false;
        // Always unfreeze so no stale wireframe survives across sessions.
        proxy.minerRenderer.unfreeze();
        Config.clearServerRuntimeOverridesAndReloadClient();
    }

    public void registry() {
        ClientRegistry.registerKeyBinding(KEY_CHAIN);
        ClientRegistry.registerKeyBinding(KEY_MODE_SWITCH);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
    }
}
