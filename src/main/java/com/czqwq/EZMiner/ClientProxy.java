package com.czqwq.EZMiner;

import com.czqwq.EZMiner.client.ClientStateContainer;
import com.czqwq.EZMiner.client.KeyListener;
import com.czqwq.EZMiner.client.render.MinerRenderer;

import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    public final ClientStateContainer clientState = new ClientStateContainer();
    public final KeyListener keyListener = new KeyListener();
    public final MinerRenderer minerRenderer = new MinerRenderer(clientState);

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        keyListener.registry();
        minerRenderer.registry();
    }
}
