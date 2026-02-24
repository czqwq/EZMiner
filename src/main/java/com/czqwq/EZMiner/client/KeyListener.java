package com.czqwq.EZMiner.client;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.czqwq.EZMiner.ClientProxy;
import com.czqwq.EZMiner.EZMiner;
import com.czqwq.EZMiner.core.MinerConfig;
import com.czqwq.EZMiner.core.MinerModeState;
import com.czqwq.EZMiner.network.PacketChainSwitcher;
import com.czqwq.EZMiner.network.PacketMinerConfig;
import com.czqwq.EZMiner.network.PacketMinerModeState;
import com.czqwq.EZMiner.utils.MessageUtils;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
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

    @SubscribeEvent
    public void onInput(InputEvent event) {
        MinerModeState state = ((ClientProxy) EZMiner.proxy).clientState.minerModeState;

        // ===== Main-mode toggle =====
        if (KEY_MODE_SWITCH.isPressed()) {
            String mode = state.nextMainMode();
            MessageUtils.printSelfMessage(I18n.format("ezminer.message.mainMode") + ": " + I18n.format(mode));
            EZMiner.network.network.sendToServer(new PacketMinerModeState(state));
        }

        boolean holding = KEY_CHAIN.getIsKeyPressed();

        // ===== Chain key pressed =====
        if (holding) {
            if (!wasHoldingChain) {
                // Rising edge: start chain
                EZMiner.network.network.sendToServer(new PacketChainSwitcher(true));
                ((ClientProxy) EZMiner.proxy).minerRenderer.inPressChainKey = true;
                // Sync config to server
                EZMiner.network.network.sendToServer(new PacketMinerConfig(new MinerConfig()));
            }
            wasHoldingChain = true;

            // Scroll wheel → switch sub-mode
            if (event instanceof InputEvent.MouseInputEvent) {
                int dWheel = Mouse.getEventDWheel();
                if (dWheel != 0) {
                    String subMode = (dWheel < 0) ? state.nextSubMode() : state.previousSubMode();
                    EZMiner.network.network.sendToServer(new PacketMinerModeState(state));
                    MessageUtils.printSelfMessage(I18n.format("ezminer.message.subMode") + ": " + I18n.format(subMode));
                }
            }
        }

        // ===== Chain key released =====
        if (!holding && wasHoldingChain) {
            EZMiner.network.network.sendToServer(new PacketChainSwitcher(false));
            ((ClientProxy) EZMiner.proxy).minerRenderer.inPressChainKey = false;
            wasHoldingChain = false;
        }
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
