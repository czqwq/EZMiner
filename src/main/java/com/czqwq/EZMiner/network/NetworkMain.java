package com.czqwq.EZMiner.network;

import com.czqwq.EZMiner.chain.network.PacketChainModeSwitch;
import com.czqwq.EZMiner.chain.network.PacketChainStateSync;
import com.czqwq.EZMiner.chain.network.PacketKeyState;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkMain {

    public static final String CHANNEL = "EZMiner";
    public final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
    private int packetId = 0;

    public void registry() {
        network.registerMessage(PacketMinerConfig.Handler.class, PacketMinerConfig.class, packetId++, Side.SERVER);
        network.registerMessage(PacketMinerConfig.Handler.class, PacketMinerConfig.class, packetId++, Side.CLIENT);
        network.registerMessage(PacketHudPos.Handler.class, PacketHudPos.class, packetId++, Side.CLIENT);
        network.registerMessage(PacketServerConfig.Handler.class, PacketServerConfig.class, packetId++, Side.CLIENT);
        network.registerMessage(
            PacketReloadClientConfig.Handler.class,
            PacketReloadClientConfig.class,
            packetId++,
            Side.CLIENT);
        network.registerMessage(PacketKeyState.Handler.class, PacketKeyState.class, packetId++, Side.SERVER);
        network
            .registerMessage(PacketChainModeSwitch.Handler.class, PacketChainModeSwitch.class, packetId++, Side.SERVER);
        network
            .registerMessage(PacketChainStateSync.Handler.class, PacketChainStateSync.class, packetId++, Side.CLIENT);
    }
}
