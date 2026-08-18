package com.czqwq.EZMiner.client.gui;

import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;

import cpw.mods.fml.client.GuiScrollingList;
import cpw.mods.fml.common.ModContainer;

/**
 * Scrollable mod list backing {@link EZMinerModOptionsScreen}. Each row shows the
 * mod name, version and an "open config" hint; clicking forwards to
 * {@link EZMinerModOptionsScreen#onModEntryClicked(int)}.
 */
public class EZMinerModOptionList extends GuiScrollingList {

    private final EZMinerModOptionsScreen parent;
    private final List<ModContainer> mods;

    public EZMinerModOptionList(EZMinerModOptionsScreen parent, List<ModContainer> mods) {
        super(parent.getMinecraftInstance(), 150, parent.height, 32, parent.height - 65 + 4, 10, 35);
        this.parent = parent;
        this.mods = mods;
    }

    @Override
    protected int getSize() {
        return mods.size();
    }

    @Override
    protected void elementClicked(int index, boolean doubleClick) {
        parent.onModEntryClicked(index);
    }

    @Override
    protected boolean isSelected(int index) {
        return false;
    }

    @Override
    protected void drawBackground() {}

    @Override
    protected void drawSlot(int listIndex, int var2, int var3, int var4, Tessellator var5) {
        ModContainer mod = mods.get(listIndex);
        FontRenderer font = parent.getFontRenderer();
        String name = (mod.getMetadata() != null && mod.getMetadata().name != null) ? mod.getMetadata().name
            : mod.getName();
        String version = mod.getDisplayVersion();
        font.drawString(font.trimStringToWidth(name, listWidth - 10), this.left + 3, var3 + 2, 0xFFFFFF);
        font.drawString(font.trimStringToWidth(version, listWidth - 10), this.left + 3, var3 + 12, 0xCCCCCC);
        font.drawString(
            font.trimStringToWidth(I18n.format("ezminer.gui.modoptions.openConfig"), listWidth - 10),
            this.left + 3,
            var3 + 22,
            0x77BBFF);
    }
}
