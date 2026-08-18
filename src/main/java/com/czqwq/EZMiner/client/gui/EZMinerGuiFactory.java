package com.czqwq.EZMiner.client.gui;

import java.util.Collections;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * {@link IModGuiFactory} implementation that lets Forge's built-in mod list open
 * EZMiner's config GUI from its "Config" button (main menu → Mods → EZMiner).
 *
 * <p>
 * This is the <em>officially supported</em> 1.7.10 FML mechanism: {@link
 * cpw.mods.fml.client.GuiModList} instantiates {@link #mainConfigGuiClass()}
 * reflectively with a single {@code (GuiScreen)} constructor and shows it as the
 * screen. The returned screen ({@link EZMinerModListEntryGui}) intentionally
 * guards against being opened on the main menu — see its Javadoc.
 */
@SideOnly(Side.CLIENT)
public class EZMinerGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
        // Nothing to do — the config GUI reads Config directly.
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return EZMinerModListEntryGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
