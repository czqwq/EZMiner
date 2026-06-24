package com.czqwq.EZMiner;

import com.czqwq.EZMiner.client.ClientStateContainer;
import com.czqwq.EZMiner.client.HudRenderer;
import com.czqwq.EZMiner.client.KeyListener;
import com.czqwq.EZMiner.client.SmartToolSwitchHandler;
import com.czqwq.EZMiner.client.gui.InventoryButtonOverlay;
import com.czqwq.EZMiner.client.render.MinerRenderer;
import com.czqwq.EZMiner.compat.GT5ToolCompat;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;

public class ClientProxy extends CommonProxy {

    public final ClientStateContainer clientState = new ClientStateContainer();
    public final HudRenderer hudRenderer = new HudRenderer();
    public final KeyListener keyListener = new KeyListener();
    public final MinerRenderer minerRenderer = new MinerRenderer(clientState);
    public final InventoryButtonOverlay inventoryButtonOverlay = new InventoryButtonOverlay();
    public final SmartToolSwitchHandler smartToolSwitchHandler = new SmartToolSwitchHandler();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // Initialise optional-mod compat bridges before any features that may use them
        GT5ToolCompat.init();
        hudRenderer.registry();
        keyListener.registry();
        minerRenderer.registry();
        inventoryButtonOverlay.registry();
        smartToolSwitchHandler.registry();
    }

    @Override
    public void serverStarted(FMLServerStartedEvent event) {}
}
