package com.czqwq.EZMiner.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkMain {

    public static final String CHANNEL = "EZMiner";
    public final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private int packetId = 0;

    public void registry() {
        network.registerMessage(PacketChainSwitcher.Handler.class, PacketChainSwitcher.class, packetId++, Side.SERVER);
        network.registerMessage(PacketMinerConfig.Handler.class, PacketMinerConfig.class, packetId++, Side.SERVER);
        network.registerMessage(PacketMinerConfig.Handler.class, PacketMinerConfig.class, packetId++, Side.CLIENT);
        network
            .registerMessage(PacketMinerModeState.Handler.class, PacketMinerModeState.class, packetId++, Side.SERVER);
        network.registerMessage(PacketChainCount.Handler.class, PacketChainCount.class, packetId++, Side.CLIENT);
    }
}
