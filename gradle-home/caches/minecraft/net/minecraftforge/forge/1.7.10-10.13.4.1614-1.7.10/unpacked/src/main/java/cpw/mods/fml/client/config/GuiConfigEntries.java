/*
 * Forge Mod Loader
 * Copyright (c) 2012-2014 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors (this class):
 *     bspkrs - implementation
 */
package cpw.mods.fml.client.config;

import static cpw.mods.fml.client.config.GuiUtils.RESET_CHAR;
import static cpw.mods.fml.client.config.GuiUtils.UNDO_CHAR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * This class implements the scrolling list functionality of the config GUI screens. It also provides all the default control handlers
 * for the various property types.
 *
 * @author bspkrs
 */
public class GuiConfigEntries extends GuiListExtended
{
    public final GuiConfig owningScreen;
    public final Minecraft mc;
    @SuppressWarnings("rawtypes")
    public List<IConfigEntry> listEntries;
    /**
     * The max width of the label of all IConfigEntry objects.
     */
    public int maxLabelTextWidth  = 0;
    /**
     * The max x boundary of all IConfigEntry objects.
     */
    public int maxEntryRightBound = 0;
    /**
     * The x position where the label should be drawn.
     */
    public int labelX;
    /**
     * The x position where the control should be drawn.
     */
    public int controlX;
    /**
     * The width of the control.
     */
    public int controlWidth;
    /**
     * The minimum x position where the Undo/Default buttons will start
     */
    public int resetX;
    /**
     * The x position of the scroll bar.
     */
    public int scrollBarX;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public GuiConfigEntries(GuiConfig parent, Minecraft mc)
    {
        super(mc, parent.field_146294_l, parent.field_146295_m, parent.titleLine2 != null ? 33 : 23, parent.field_146295_m - 32, 20);
        this.owningScreen = parent;
        this.func_148130_a(false);
        this.mc = mc;
        this.listEntries = new ArrayList<IConfigEntry>();
//        int i = 0;
//        String s = null;

        for (IConfigElement configElement : parent.configElements)
        {
            if (configElement != null)
            {
                if (configElement.isProperty() && configElement.showInGui()) // as opposed to being a child category entry
                {
                    int length;

                    // protects against language keys that are not defined in the .lang file
                    if (!I18n.func_135052_a(configElement.getLanguageKey()).equals(configElement.getLanguageKey()))
                        length = mc.field_71466_p.func_78256_a(I18n.func_135052_a(configElement.getLanguageKey()));
                    else
                        length = mc.field_71466_p.func_78256_a(configElement.getName());

                    if (length > this.maxLabelTextWidth)
                        this.maxLabelTextWidth = length;
                }
            }
        }

        int viewWidth = this.maxLabelTextWidth + 8 + (field_148155_a / 2);
        labelX = (this.field_148155_a / 2) - (viewWidth / 2);
        controlX = labelX + maxLabelTextWidth + 8;
        resetX = (this.field_148155_a / 2) + (viewWidth / 2) - 45;
        controlWidth = resetX - controlX - 5;
        scrollBarX = this.field_148155_a;

        for (IConfigElement configElement : parent.configElements)
        {
            if (configElement != null && configElement.showInGui())
            {
                if (configElement.getConfigEntryClass() != null)
                    try
                    {
                        this.listEntries.add((IConfigEntry) configElement.getConfigEntryClass()
                                .getConstructor(GuiConfig.class, GuiConfigEntries.class, IConfigElement.class)
                                .newInstance(this.owningScreen, this, configElement));
                    }
                    catch (Throwable e)
                    {
                        FMLLog.severe("There was a critical error instantiating the custom IConfigEntry for config element %s.", configElement.getName());
                        e.printStackTrace();
                    }
                else if (configElement.isProperty())
                {
                    if (configElement.isList())
                        this.listEntries.add(new GuiConfigEntries.ArrayEntry(this.owningScreen, this, configElement));
                    else if (configElement.getType() == ConfigGuiType.BOOLEAN)
                        this.listEntries.add(new GuiConfigEntries.BooleanEntry(this.owningScreen, this, (IConfigElement<Boolean>) configElement));
                    else if (configElement.getType() == ConfigGuiType.INTEGER)
                        this.listEntries.add(new GuiConfigEntries.IntegerEntry(this.owningScreen, this, (IConfigElement<Integer>) configElement));
                    else if (configElement.getType() == ConfigGuiType.DOUBLE)
                        this.listEntries.add(new GuiConfigEntries.DoubleEntry(this.owningScreen, this, (IConfigElement<Double>) configElement));
                    else if (configElement.getType() == ConfigGuiType.COLOR)
                    {
                        if (configElement.getValidValues() != null && configElement.getValidValues().length > 0)
                            this.listEntries.add(new GuiConfigEntries.ChatColorEntry(this.owningScreen, this, (IConfigElement<String>) configElement));
                        else
                            this.listEntries.add(new GuiConfigEntries.StringEntry(this.owningScreen, this, (IConfigElement<String>) configElement));
                    }
                    else if (configElement.getType() == ConfigGuiType.MOD_ID)
                    {
                        Map<Object, String> values = new TreeMap<Object, String>();
                        for (ModContainer mod : Loader.instance().getActiveModList())
                            values.put(mod.getModId(), mod.getName());
                        values.put("minecraft", "Minecraft");
                        this.listEntries.add(new SelectValueEntry(this.owningScreen, this, (IConfigElement<String>) configElement, values));
                    }
                    else if (configElement.getType() == ConfigGuiType.STRING)
                    {
                        if (configElement.getValidValues() != null && configElement.getValidValues().length > 0)
                            this.listEntries.add(new GuiConfigEntries.CycleValueEntry(this.owningScreen, this, (IConfigElement<String>) configElement));
                        else
                            this.listEntries.add(new GuiConfigEntries.StringEntry(this.owningScreen, this, (IConfigElement<String>) configElement));
                    }
                }
                else if (configElement.getType() == ConfigGuiType.CONFIG_CATEGORY)
                    this.listEntries.add(new CategoryEntry(this.owningScreen, this, configElement));
            }
        }
    }

    @SuppressWarnings("rawtypes")
    protected void initGui()
    {
        this.field_148155_a = owningScreen.field_146294_l;
        this.field_148158_l = owningScreen.field_146295_m;

        this.maxLabelTextWidth = 0;
        for (IConfigEntry entry : this.listEntries)
            if (entry.getLabelWidth() > this.maxLabelTextWidth)
                this.maxLabelTextWidth = entry.getLabelWidth();

        this.field_148153_b = owningScreen.titleLine2 != null ? 33 : 23;
        this.field_148154_c = owningScreen.field_146295_m - 32;
        this.field_148152_e = 0;
        this.field_148151_d = field_148155_a;
        int viewWidth = this.maxLabelTextWidth + 8 + (field_148155_a / 2);
        labelX = (this.field_148155_a / 2) - (viewWidth / 2);
        controlX = labelX + maxLabelTextWidth + 8;
        resetX = (this.field_148155_a / 2) + (viewWidth / 2) - 45;

        this.maxEntryRightBound = 0;
        for (IConfigEntry entry : this.listEntries)
            if (entry.getEntryRightBound() > this.maxEntryRightBound)
                this.maxEntryRightBound = entry.getEntryRightBound();

        scrollBarX = this.maxEntryRightBound + 5;
        controlWidth = maxEntryRightBound - controlX - 45;
    }

    @Override
    public int func_148127_b()
    {
        return this.listEntries.size();
    }

    /**
     * Gets the IGuiListEntry object for the given index
     */
    @SuppressWarnings("rawtypes")
    @Override
    public IConfigEntry func_148180_b(int index)
    {
        return this.listEntries.get(index);
    }

    @Override
    public int func_148137_d()
    {
        return scrollBarX;
    }

    /**
     * Gets the width of the list
     */
    @Override
    public int func_148139_c()
    {
        return owningScreen.field_146294_l;
    }

    /**
     * This method is a pass-through for IConfigEntry objects that require keystrokes. Called from the parent GuiConfig screen.
     */
    @SuppressWarnings("rawtypes")
    public void keyTyped(char eventChar, int eventKey)
    {
        for (IConfigEntry entry : this.listEntries)
            entry.keyTyped(eventChar, eventKey);
    }

    /**
     * This method is a pass-through for IConfigEntry objects that contain GuiTextField elements. Called from the parent GuiConfig
     * screen.
     */
    @SuppressWarnings("rawtypes")
    public void updateScreen()
    {
        for (IConfigEntry entry : this.listEntries)
            entry.updateCursorCounter();
    }

    /**
     * This method is a pass-through for IConfigEntry objects that contain GuiTextField elements. Called from the parent GuiConfig
     * screen.
     */
    @SuppressWarnings("rawtypes")
    public void mouseClicked(int mouseX, int mouseY, int mouseEvent)
    {
        for (IConfigEntry entry : this.listEntries)
            entry.mouseClicked(mouseX, mouseY, mouseEvent);
    }

    /**
     * This method is a pass-through for IConfigListEntry objects that need to perform actions when the containing GUI is closed.
     */
    @SuppressWarnings("rawtypes")
    public void onGuiClosed()
    {
        for (IConfigEntry entry : this.listEntries)
            entry.onGuiClosed();
    }

    /**
     * Saves all properties on this screen / child screens. This method returns true if any elements were changed that require
     * a restart for proper handling.
     */
    @SuppressWarnings("rawtypes")
    public boolean saveConfigElements()
    {
        boolean requiresRestart = false;
        for (IConfigEntry entry : this.listEntries)
            if (entry.saveConfigElement())
                requiresRestart = true;

        return requiresRestart;
    }

    /**
     * Returns true if all IConfigEntry objects on this screen are set to default. If includeChildren is true sub-category
     * objects are checked as well.
     */
    @SuppressWarnings("rawtypes")
    public boolean areAllEntriesDefault(boolean includeChildren)
    {
        for (IConfigEntry entry : this.listEntries)
            if ((includeChildren || !(entry instanceof CategoryEntry)) && !entry.isDefault())
                return false;

        return true;
    }

    /**
     * Sets all IConfigEntry objects on this screen to default. If includeChildren is true sub-category objects are set as
     * well.
     */
    @SuppressWarnings("rawtypes")
    public void setAllToDefault(boolean includeChildren)
    {
        for (IConfigEntry entry : this.listEntries)
            if ((includeChildren || !(entry instanceof CategoryEntry)))
                entry.setToDefault();
    }

    /**
     * Returns true if any IConfigEntry objects on this screen are changed. If includeChildren is true sub-category objects
     * are checked as well.
     */
    @SuppressWarnings("rawtypes")
    public boolean hasChangedEntry(boolean includeChildren)
    {
        for (IConfigEntry entry : this.listEntries)
            if ((includeChildren || !(entry instanceof CategoryEntry)) && entry.isChanged())
                return true;

        return false;
    }

    /**
     * Returns true if any IConfigEntry objects on this screen are enabled. If includeChildren is true sub-category objects
     * are checked as well.
     */
    @SuppressWarnings("rawtypes")
    public boolean areAnyEntriesEnabled(boolean includeChildren)
    {
        for (IConfigEntry entry : this.listEntries)
            if ((includeChildren || !(entry instanceof CategoryEntry)) && entry.enabled())
                return true;

        return false;
    }

    /**
     * Reverts changes to all IConfigEntry objects on this screen. If includeChildren is true sub-category objects are
     * reverted as well.
     */
    @SuppressWarnings("rawtypes")
    public void undoAllChanges(boolean includeChildren)
    {
        for (IConfigEntry entry : this.listEntries)
            if ((includeChildren || !(entry instanceof CategoryEntry)))
                entry.undoChanges();
    }

    /**
     * Calls the drawToolTip() method for all IConfigEntry objects on this screen. This is called from the parent GuiConfig screen
     * after drawing all other elements.
     */
    @SuppressWarnings("rawtypes")
    public void drawScreenPost(int mouseX, int mouseY, float partialTicks)
    {
        for (IConfigEntry entry : this.listEntries)
            entry.drawToolTip(mouseX, mouseY);
    }

    /**
     * BooleanPropEntry
     *
     * Provides a GuiButton that toggles between true and false.
     */
    public static class BooleanEntry extends ButtonEntry
    {
        protected final boolean beforeValue;
        protected boolean       currentValue;

        private BooleanEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<Boolean> configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            this.beforeValue = Boolean.valueOf(configElement.get().toString());
            this.currentValue = beforeValue;
            this.btnValue.field_146124_l = enabled();
            updateValueButtonText();
        }

        @Override
        public void updateValueButtonText()
        {
            this.btnValue.field_146126_j = I18n.func_135052_a(String.valueOf(currentValue));
            btnValue.packedFGColour = currentValue ? GuiUtils.getColorCode('2', true) : GuiUtils.getColorCode('4', true);
        }

        @Override
        public void valueButtonPressed(int slotIndex)
        {
            if (enabled())
                currentValue = !currentValue;
        }

        @Override
        public boolean isDefault()
        {
            return currentValue == Boolean.valueOf(configElement.getDefault().toString());
        }

        @Override
        public void setToDefault()
        {
            if (enabled())
            {
                currentValue = Boolean.valueOf(configElement.getDefault().toString());
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged()
        {
            return currentValue != beforeValue;
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
            {
                currentValue = beforeValue;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled() && isChanged())
            {
                configElement.set(currentValue);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public Boolean getCurrentValue()
        {
            return currentValue;
        }

        @Override
        public Boolean[] getCurrentValues()
        {
            return new Boolean[] { getCurrentValue() };
        }
    }

    /**
     * CycleValueEntry
     *
     * Provides a GuiButton that cycles through the prop's validValues array. If the current prop value is not a valid value, the first
     * entry replaces the current value.
     */
    public static class CycleValueEntry extends ButtonEntry
    {
        protected final int beforeIndex;
        protected final int defaultIndex;
        protected int       currentIndex;

        private CycleValueEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<String> configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            beforeIndex = getIndex(configElement.get().toString());
            defaultIndex = getIndex(configElement.getDefault().toString());
            currentIndex = beforeIndex;
            this.btnValue.field_146124_l = enabled();
            updateValueButtonText();
        }

        private int getIndex(String s)
        {
            for (int i = 0; i < configElement.getValidValues().length; i++)
                if (configElement.getValidValues()[i].equalsIgnoreCase(s))
                {
                    return i;
                }

            return 0;
        }

        @Override
        public void updateValueButtonText()
        {
            this.btnValue.field_146126_j = I18n.func_135052_a(configElement.getValidValues()[currentIndex]);
        }

        @Override
        public void valueButtonPressed(int slotIndex)
        {
            if (enabled())
            {
                if (++this.currentIndex >= configElement.getValidValues().length)
                    this.currentIndex = 0;

                updateValueButtonText();
            }
        }

        @Override
        public boolean isDefault()
        {
            return currentIndex == defaultIndex;
        }

        @Override
        public void setToDefault()
        {
            if (enabled())
            {
                currentIndex = defaultIndex;
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged()
        {
            return currentIndex != beforeIndex;
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
            {
                currentIndex = beforeIndex;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled() && isChanged())
            {
                configElement.set(configElement.getValidValues()[currentIndex]);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public String getCurrentValue()
        {
            return configElement.getValidValues()[currentIndex];
        }

        @Override
        public String[] getCurrentValues()
        {
            return new String[] { getCurrentValue() };
        }
    }

    /**
     * ChatColorEntry
     *
     * Provides a GuiButton that cycles through the list of chat color codes.
     */
    public static class ChatColorEntry extends CycleValueEntry
    {
        ChatColorEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<String> configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            this.btnValue.field_146124_l = enabled();
            updateValueButtonText();
        }

        @Override
        public void func_148279_a(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
        {
            this.btnValue.packedFGColour = GuiUtils.getColorCode(this.configElement.getValidValues()[currentIndex].charAt(0), true);
            super.func_148279_a(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);
        }

        @Override
        public void updateValueButtonText()
        {
            this.btnValue.field_146126_j = I18n.func_135052_a(configElement.getValidValues()[currentIndex]) + " - " + I18n.func_135052_a("fml.configgui.sampletext");
        }
    }

    /**
     * SelectValueEntry
     *
     * Provides a GuiButton with the current value as the displayString. Accepts a Map of selectable values with the signature <Object,
     * String> where the key is the Object to be selected and the value is the String that will show on the selection list. EG: a map of Mod
     * ID values where the key is the Mod ID and the value is the Mod Name.
     */
    public static class SelectValueEntry extends ButtonEntry
    {
        protected final String        beforeValue;
        protected Object              currentValue;
        protected Map<Object, String> selectableValues;

        public SelectValueEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<String> configElement, Map<Object, String> selectableValues)
        {
            super(owningScreen, owningEntryList, configElement);
            beforeValue = configElement.get().toString();
            currentValue = configElement.get().toString();
            this.selectableValues = selectableValues;
            updateValueButtonText();
        }

        @Override
        public void updateValueButtonText()
        {
            this.btnValue.field_146126_j = currentValue.toString();
        }

        @Override
        public void valueButtonPressed(int slotIndex)
        {
            mc.func_147108_a(new GuiSelectString(this.owningScreen, configElement, slotIndex, selectableValues, currentValue, enabled()));
        }

        public void setValueFromChildScreen(Object newValue)
        {
            if (enabled() && currentValue != null ? !currentValue.equals(newValue) : newValue != null)
            {
                currentValue = newValue;
                updateValueButtonText();
            }
        }

        @Override
        public boolean isDefault()
        {
            if (configElement.getDefault() != null)
                return configElement.getDefault().equals(currentValue);
            else
                return currentValue == null;
        }

        @Override
        public void setToDefault()
        {
            if (enabled())
            {
                this.currentValue = configElement.getDefault().toString();
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged()
        {
            if (beforeValue != null)
                return !beforeValue.equals(currentValue);
            else
                return currentValue == null;
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
            {
                currentValue = beforeValue;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled() && isChanged())
            {
                this.configElement.set(currentValue);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public String getCurrentValue()
        {
            return this.currentValue.toString();
        }

        @Override
        public String[] getCurrentValues()
        {
            return new String[] { getCurrentValue() };
        }
    }

    /**
     * ArrayEntry
     *
     * Provides a GuiButton with the list contents as the displayString. Clicking the button navigates to a screen where the list can be
     * edited.
     */
    public static class ArrayEntry extends ButtonEntry
    {
        protected final Object[] beforeValues;
        protected Object[]       currentValues;

        @SuppressWarnings("rawtypes")
        public ArrayEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            beforeValues = configElement.getList();
            currentValues = configElement.getList();
            updateValueButtonText();
        }

        @Override
        public void updateValueButtonText()
        {
            this.btnValue.field_146126_j = "";
            for (Object o : currentValues)
                this.btnValue.field_146126_j += ", [" + o + "]";

            this.btnValue.field_146126_j = this.btnValue.field_146126_j.replaceFirst(", ", "");
        }

        @Override
        public void valueButtonPressed(int slotIndex)
        {
            mc.func_147108_a(new GuiEditArray(this.owningScreen, configElement, slotIndex, currentValues, enabled()));
        }

        public void setListFromChildScreen(Object[] newList)
        {
            if (enabled() && !Arrays.deepEquals(currentValues, newList))
            {
                currentValues = newList;
                updateValueButtonText();
            }
        }

        @Override
        public boolean isDefault()
        {
            return Arrays.deepEquals(configElement.getDefaults(), currentValues);
        }

        @Override
        public void setToDefault()
        {
            if (enabled())
            {
                this.currentValues = configElement.getDefaults();
                updateValueButtonText();
            }
        }

        @Override
        public boolean isChanged()
        {
            return !Arrays.deepEquals(beforeValues, currentValues);
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
            {
                currentValues = beforeValues;
                updateValueButtonText();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled() && isChanged())
            {
                this.configElement.set(currentValues);
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public Object getCurrentValue()
        {
            return this.btnValue.field_146126_j;
        }

        @Override
        public Object[] getCurrentValues()
        {
            return this.currentValues;
        }
    }

    /**
     * NumberSliderEntry
     *
     * Provides a slider for numeric properties.
     */
    public static class NumberSliderEntry extends ButtonEntry
    {
        protected final double beforeValue;

        public NumberSliderEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<?> configElement)
        {
            super(owningScreen, owningEntryList, configElement, new GuiSlider(0, owningEntryList.controlX, 0, owningEntryList.controlWidth, 18,
                    "", "", Double.valueOf(configElement.getMinValue().toString()), Double.valueOf(configElement.getMaxValue().toString()),
                    Double.valueOf(configElement.get().toString()), configElement.getType() == ConfigGuiType.DOUBLE, true));

            if (configElement.getType() == ConfigGuiType.INTEGER)
                this.beforeValue = Integer.valueOf(configElement.get().toString());
            else
                this.beforeValue = Double.valueOf(configElement.get().toString());
        }

        @Override
        public void updateValueButtonText()
        {
            ((GuiSlider) this.btnValue).updateSlider();
        }

        @Override
        public void valueButtonPressed(int slotIndex) {}

        @Override
        public boolean isDefault()
        {
            if (configElement.getType() == ConfigGuiType.INTEGER)
                return ((GuiSlider) this.btnValue).getValueInt() == Integer.valueOf(configElement.getDefault().toString());
            else
                return ((GuiSlider) this.btnValue).getValue() == Double.valueOf(configElement.getDefault().toString());
        }

        @Override
        public void setToDefault()
        {
            if (this.enabled())
            {
                ((GuiSlider) this.btnValue).setValue(Double.valueOf(configElement.getDefault().toString()));
                ((GuiSlider) this.btnValue).updateSlider();
            }
        }

        @Override
        public boolean isChanged()
        {
            if (configElement.getType() == ConfigGuiType.INTEGER)
                return ((GuiSlider) this.btnValue).getValueInt() != (int) Math.round(beforeValue);
            else
                return ((GuiSlider) this.btnValue).getValue() != beforeValue;
        }

        @Override
        public void undoChanges()
        {
            if (this.enabled())
            {
                ((GuiSlider) this.btnValue).setValue(beforeValue);
                ((GuiSlider) this.btnValue).updateSlider();
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (this.enabled() && this.isChanged())
            {
                if (configElement.getType() == ConfigGuiType.INTEGER)
                    configElement.set(((GuiSlider) this.btnValue).getValueInt());
                else
                    configElement.set(((GuiSlider) this.btnValue).getValue());
                return configElement.requiresMcRestart();
            }
            return false;
        }

        @Override
        public Object getCurrentValue()
        {
            if (configElement.getType() == ConfigGuiType.INTEGER)
                return ((GuiSlider) this.btnValue).getValueInt();
            else
                return ((GuiSlider) this.btnValue).getValue();
        }

        @Override
        public Object[] getCurrentValues()
        {
            return new Object[] { getCurrentValue() };
        }
    }

    /**
     * ButtonEntry
     *
     * Provides a basic GuiButton entry to be used as a base for other entries that require a button for the value.
     */
    public static abstract class ButtonEntry extends ListEntryBase
    {
        protected final GuiButtonExt btnValue;

        public ButtonEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<?> configElement)
        {
            this(owningScreen, owningEntryList, configElement, new GuiButtonExt(0, owningEntryList.controlX, 0, owningEntryList.controlWidth, 18,
                    configElement.get() != null ? I18n.func_135052_a(String.valueOf(configElement.get())) : ""));
        }

        public ButtonEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<?> configElement, GuiButtonExt button)
        {
            super(owningScreen, owningEntryList, configElement);
            this.btnValue = button;
        }

        /**
         * Updates the displayString of the value button.
         */
        public abstract void updateValueButtonText();

        /**
         * Called when the value button has been clicked.
         */
        public abstract void valueButtonPressed(int slotIndex);

        @Override
        public void func_148279_a(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
        {
            super.func_148279_a(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);
            this.btnValue.field_146120_f = this.owningEntryList.controlWidth;
            this.btnValue.field_146128_h = this.owningScreen.entryList.controlX;
            this.btnValue.field_146129_i = y;
            this.btnValue.field_146124_l = enabled();
            this.btnValue.func_146112_a(this.mc, mouseX, mouseY);
        }

        /**
         * Returns true if the mouse has been pressed on this control.
         */
        @Override
        public boolean func_148278_a(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            if (this.btnValue.func_146116_c(this.mc, x, y))
            {
                btnValue.func_146113_a(mc.func_147118_V());
                valueButtonPressed(index);
                updateValueButtonText();
                return true;
            }
            else
                return super.func_148278_a(index, x, y, mouseEvent, relativeX, relativeY);
        }

        /**
         * Fired when the mouse button is released. Arguments: index, x, y, mouseEvent, relativeX, relativeY
         */
        @Override
        public void func_148277_b(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            super.func_148277_b(index, x, y, mouseEvent, relativeX, relativeY);
            this.btnValue.func_146118_a(x, y);
        }

        @Override
        public void keyTyped(char eventChar, int eventKey)
        {}

        @Override
        public void updateCursorCounter()
        {}

        @Override
        public void mouseClicked(int x, int y, int mouseEvent)
        {}
    }

    /**
     * IntegerEntry
     *
     * Provides a GuiTextField for user input. Input is restricted to ensure the value can be parsed using Integer.parseInteger().
     */
    public static class IntegerEntry extends StringEntry
    {
        protected final int beforeValue;

        @SuppressWarnings("rawtypes")
        public IntegerEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            this.beforeValue = Integer.valueOf(configElement.get().toString());
        }

        @Override
        public void keyTyped(char eventChar, int eventKey)
        {
            if (enabled() || eventKey == Keyboard.KEY_LEFT || eventKey == Keyboard.KEY_RIGHT || eventKey == Keyboard.KEY_HOME || eventKey == Keyboard.KEY_END)
            {
                String validChars = "0123456789";
                String before = this.textFieldValue.func_146179_b();
                if (validChars.contains(String.valueOf(eventChar))
                        || (!before.startsWith("-") && this.textFieldValue.func_146198_h() == 0 && eventChar == '-')
                        || eventKey == Keyboard.KEY_BACK || eventKey == Keyboard.KEY_DELETE
                        || eventKey == Keyboard.KEY_LEFT || eventKey == Keyboard.KEY_RIGHT || eventKey == Keyboard.KEY_HOME || eventKey == Keyboard.KEY_END)
                    this.textFieldValue.func_146201_a((enabled() ? eventChar : Keyboard.CHAR_NONE), eventKey);

                if (!textFieldValue.func_146179_b().trim().isEmpty() && !textFieldValue.func_146179_b().trim().equals("-"))
                {
                    try
                    {
                        long value = Long.parseLong(textFieldValue.func_146179_b().trim());
                        if (value < Integer.valueOf(configElement.getMinValue().toString()) || value > Integer.valueOf(configElement.getMaxValue().toString()))
                            this.isValidValue = false;
                        else
                            this.isValidValue = true;
                    }
                    catch (Throwable e)
                    {
                        this.isValidValue = false;
                    }
                }
                else
                    this.isValidValue = false;
            }
        }

        @Override
        public boolean isChanged()
        {
            try
            {
                return this.beforeValue != Integer.parseInt(textFieldValue.func_146179_b().trim());
            }
            catch (Throwable e)
            {
                return true;
            }
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
                this.textFieldValue.func_146180_a(String.valueOf(beforeValue));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled())
            {
                if (isChanged() && this.isValidValue)
                    try
                    {
                        int value = Integer.parseInt(textFieldValue.func_146179_b().trim());
                        this.configElement.set(value);
                        return configElement.requiresMcRestart();
                    }
                    catch (Throwable e)
                    {
                        this.configElement.setToDefault();
                    }
                else if (isChanged() && !this.isValidValue)
                    try
                    {
                        int value = Integer.parseInt(textFieldValue.func_146179_b().trim());
                        if (value < Integer.valueOf(configElement.getMinValue().toString()))
                            this.configElement.set(configElement.getMinValue());
                        else
                            this.configElement.set(configElement.getMaxValue());

                    }
                    catch (Throwable e)
                    {
                        this.configElement.setToDefault();
                    }

                return configElement.requiresMcRestart() && beforeValue != Integer.parseInt(configElement.get().toString());
            }
            return false;
        }
    }

    /**
     * DoubleEntry
     *
     * Provides a GuiTextField for user input. Input is restricted to ensure the value can be parsed using Double.parseDouble().
     */
    public static class DoubleEntry extends StringEntry
    {
        protected final double beforeValue;

        @SuppressWarnings("rawtypes")
        public DoubleEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            this.beforeValue = Double.valueOf(configElement.get().toString());
        }

        @Override
        public void keyTyped(char eventChar, int eventKey)
        {
            if (enabled() || eventKey == Keyboard.KEY_LEFT || eventKey == Keyboard.KEY_RIGHT || eventKey == Keyboard.KEY_HOME || eventKey == Keyboard.KEY_END)
            {
                String validChars = "0123456789";
                String before = this.textFieldValue.func_146179_b();
                if (validChars.contains(String.valueOf(eventChar)) ||
                        (!before.startsWith("-") && this.textFieldValue.func_146198_h() == 0 && eventChar == '-')
                        || (!before.contains(".") && eventChar == '.')
                        || eventKey == Keyboard.KEY_BACK || eventKey == Keyboard.KEY_DELETE || eventKey == Keyboard.KEY_LEFT || eventKey == Keyboard.KEY_RIGHT
                        || eventKey == Keyboard.KEY_HOME || eventKey == Keyboard.KEY_END)
                    this.textFieldValue.func_146201_a((enabled() ? eventChar : Keyboard.CHAR_NONE), eventKey);

                if (!textFieldValue.func_146179_b().trim().isEmpty() && !textFieldValue.func_146179_b().trim().equals("-"))
                {
                    try
                    {
                        double value = Double.parseDouble(textFieldValue.func_146179_b().trim());
                        if (value < Double.valueOf(configElement.getMinValue().toString()) || value > Double.valueOf(configElement.getMaxValue().toString()))
                            this.isValidValue = false;
                        else
                            this.isValidValue = true;
                    }
                    catch (Throwable e)
                    {
                        this.isValidValue = false;
                    }
                }
                else
                    this.isValidValue = false;
            }
        }

        @Override
        public boolean isChanged()
        {
            try
            {
                return this.beforeValue != Double.parseDouble(textFieldValue.func_146179_b().trim());
            }
            catch (Throwable e)
            {
                return true;
            }
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
                this.textFieldValue.func_146180_a(String.valueOf(beforeValue));
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled())
            {
                if (isChanged() && this.isValidValue)
                    try
                    {
                        double value = Double.parseDouble(textFieldValue.func_146179_b().trim());
                        this.configElement.set(value);
                        return configElement.requiresMcRestart();
                    }
                    catch (Throwable e)
                    {
                        this.configElement.setToDefault();
                    }
                else if (isChanged() && !this.isValidValue)
                    try
                    {
                        double value = Double.parseDouble(textFieldValue.func_146179_b().trim());
                        if (value < Double.valueOf(configElement.getMinValue().toString()))
                            this.configElement.set(configElement.getMinValue());
                        else
                            this.configElement.set(configElement.getMaxValue());
                    }
                    catch (Throwable e)
                    {
                        this.configElement.setToDefault();
                    }

                return configElement.requiresMcRestart() && beforeValue != Double.parseDouble(configElement.get().toString());
            }
            return false;
        }
    }

    /**
     * StringEntry
     *
     * Provides a GuiTextField for user input.
     */
    public static class StringEntry extends ListEntryBase
    {
        protected final GuiTextField textFieldValue;
        protected final String       beforeValue;

        public StringEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement<?> configElement)
        {
            super(owningScreen, owningEntryList, configElement);
            beforeValue = configElement.get().toString();
            this.textFieldValue = new GuiTextField(this.mc.field_71466_p, this.owningEntryList.controlX + 1, 0, this.owningEntryList.controlWidth - 3, 16);
            this.textFieldValue.func_146203_f(10000);
            this.textFieldValue.func_146180_a(configElement.get().toString());
        }

        @Override
        public void func_148279_a(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
        {
            super.func_148279_a(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);
            this.textFieldValue.field_146209_f = this.owningEntryList.controlX + 2;
            this.textFieldValue.field_146210_g = y + 1;
            this.textFieldValue.field_146218_h = this.owningEntryList.controlWidth - 4;
            this.textFieldValue.func_146184_c(enabled());
            this.textFieldValue.func_146194_f();
        }

        @Override
        public void keyTyped(char eventChar, int eventKey)
        {
            if (enabled() || eventKey == Keyboard.KEY_LEFT || eventKey == Keyboard.KEY_RIGHT || eventKey == Keyboard.KEY_HOME || eventKey == Keyboard.KEY_END)
            {
                this.textFieldValue.func_146201_a((enabled() ? eventChar : Keyboard.CHAR_NONE), eventKey);

                if (configElement.getValidationPattern() != null)
                {
                    if (configElement.getValidationPattern().matcher(this.textFieldValue.func_146179_b().trim()).matches())
                        isValidValue = true;
                    else
                        isValidValue = false;
                }
            }
        }

        @Override
        public void updateCursorCounter()
        {
            this.textFieldValue.func_146178_a();
        }

        @Override
        public void mouseClicked(int x, int y, int mouseEvent)
        {
            this.textFieldValue.func_146192_a(x, y, mouseEvent);
        }

        @Override
        public boolean isDefault()
        {
            return configElement.getDefault() != null ? configElement.getDefault().toString().equals(this.textFieldValue.func_146179_b()) :
                this.textFieldValue.func_146179_b().trim().isEmpty();
        }

        @Override
        public void setToDefault()
        {
            if (enabled())
            {
                this.textFieldValue.func_146180_a(this.configElement.getDefault().toString());
                keyTyped((char) Keyboard.CHAR_NONE, Keyboard.KEY_HOME);
            }
        }

        @Override
        public boolean isChanged()
        {
            return beforeValue != null ? !this.beforeValue.equals(textFieldValue.func_146179_b()) : this.textFieldValue.func_146179_b().trim().isEmpty();
        }

        @Override
        public void undoChanges()
        {
            if (enabled())
                this.textFieldValue.func_146180_a(beforeValue);
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean saveConfigElement()
        {
            if (enabled())
            {
                if (isChanged() && this.isValidValue)
                {
                    this.configElement.set(this.textFieldValue.func_146179_b());
                    return configElement.requiresMcRestart();
                }
                else if (isChanged() && !this.isValidValue)
                {
                    this.configElement.setToDefault();
                    return configElement.requiresMcRestart()
                            && beforeValue != null ? beforeValue.equals(configElement.getDefault()) : configElement.getDefault() == null;
                }
            }
            return false;
        }

        @Override
        public Object getCurrentValue()
        {
            return this.textFieldValue.func_146179_b();
        }

        @Override
        public Object[] getCurrentValues()
        {
            return new Object[] { getCurrentValue() };
        }
    }

    /**
     * CategoryEntry
     *
     * Provides an entry that consists of a GuiButton for navigating to the child category GuiConfig screen.
     */
    public static class CategoryEntry extends ListEntryBase
    {
        protected GuiScreen childScreen;
        protected final GuiButtonExt btnSelectCategory;

        @SuppressWarnings("rawtypes")
        public CategoryEntry(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement)
        {
            super(owningScreen, owningEntryList, configElement);

            this.childScreen = this.buildChildScreen();

            this.btnSelectCategory = new GuiButtonExt(0, 0, 0, 300, 18, I18n.func_135052_a(name));
            this.tooltipHoverChecker = new HoverChecker(this.btnSelectCategory, 800);

            this.drawLabel = false;
        }

        /**
         * This method is called in the constructor and is used to set the childScreen field.
         */
        @SuppressWarnings("unchecked")
        protected GuiScreen buildChildScreen()
        {
            return new GuiConfig(this.owningScreen, this.configElement.getChildElements(), this.owningScreen.modID,
                    owningScreen.allRequireWorldRestart || this.configElement.requiresWorldRestart(),
                    owningScreen.allRequireMcRestart || this.configElement.requiresMcRestart(), this.owningScreen.title,
                    ((this.owningScreen.titleLine2 == null ? "" : this.owningScreen.titleLine2) + " > " + this.name));
        }

        @Override
        public void func_148279_a(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
        {
            this.btnSelectCategory.field_146128_h = listWidth / 2 - 150;
            this.btnSelectCategory.field_146129_i = y;
            this.btnSelectCategory.field_146124_l = enabled();
            this.btnSelectCategory.func_146112_a(this.mc, mouseX, mouseY);

            super.func_148279_a(slotIndex, x, y, listWidth, slotHeight, tessellator, mouseX, mouseY, isSelected);
        }

        @Override
        public void drawToolTip(int mouseX, int mouseY)
        {
            boolean canHover = mouseY < this.owningScreen.entryList.field_148154_c && mouseY > this.owningScreen.entryList.field_148153_b;

            if (this.tooltipHoverChecker.checkHover(mouseX, mouseY, canHover))
                this.owningScreen.drawToolTip(toolTip, mouseX, mouseY);

            super.drawToolTip(mouseX, mouseY);
        }

        @Override
        public boolean func_148278_a(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            if (this.btnSelectCategory.func_146116_c(this.mc, x, y))
            {
                btnSelectCategory.func_146113_a(mc.func_147118_V());
                Minecraft.func_71410_x().func_147108_a(childScreen);
                return true;
            }
            else
                return super.func_148278_a(index, x, y, mouseEvent, relativeX, relativeY);
        }

        @Override
        public void func_148277_b(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            this.btnSelectCategory.func_146118_a(x, y);
        }

        @Override
        public boolean isDefault()
        {
            if (childScreen instanceof GuiConfig && ((GuiConfig) childScreen).entryList != null)
                return ((GuiConfig) childScreen).entryList.areAllEntriesDefault(true);

            return true;
        }

        @Override
        public void setToDefault()
        {
            if (childScreen instanceof GuiConfig && ((GuiConfig) childScreen).entryList != null)
                ((GuiConfig) childScreen).entryList.setAllToDefault(true);
        }

        @Override
        public void keyTyped(char eventChar, int eventKey)
        {}

        @Override
        public void updateCursorCounter()
        {}

        @Override
        public void mouseClicked(int x, int y, int mouseEvent)
        {}

        @Override
        public boolean saveConfigElement()
        {
            boolean requiresRestart = false;

            if (childScreen instanceof GuiConfig && ((GuiConfig) childScreen).entryList != null)
            {
                requiresRestart = configElement.requiresMcRestart() && ((GuiConfig) childScreen).entryList.hasChangedEntry(true);

                if (((GuiConfig) childScreen).entryList.saveConfigElements())
                    requiresRestart = true;
            }

            return requiresRestart;
        }

        @Override
        public boolean isChanged()
        {
            if (childScreen instanceof GuiConfig && ((GuiConfig) childScreen).entryList != null)
                return ((GuiConfig) childScreen).entryList.hasChangedEntry(true);
            else
                return false;
        }

        @Override
        public void undoChanges()
        {
            if (childScreen instanceof GuiConfig && ((GuiConfig) childScreen).entryList != null)
                ((GuiConfig) childScreen).entryList.undoAllChanges(true);
        }

        @Override
        public boolean enabled()
        {
            return true;
        }

        @Override
        public int getLabelWidth()
        {
            return 0;
        }

        @Override
        public int getEntryRightBound()
        {
            return this.owningEntryList.field_148155_a / 2 + 155 + 22 + 18;
        }

        @Override
        public String getCurrentValue()
        {
            return "";
        }

        @Override
        public String[] getCurrentValues()
        {
            return new String[] { getCurrentValue() };
        }
    }

    /**
     * ListEntryBase
     *
     * Provides a base entry for others to extend. Handles drawing the prop label (if drawLabel == true) and the Undo/Default buttons.
     */
    @SuppressWarnings("rawtypes")
    public static abstract class ListEntryBase implements IConfigEntry
    {
        protected final GuiConfig owningScreen;
        protected final GuiConfigEntries owningEntryList;
        protected final IConfigElement configElement;
        protected final Minecraft mc;
        protected final String name;
        protected final GuiButtonExt btnUndoChanges;
        protected final GuiButtonExt btnDefault;
        protected List toolTip;
        protected List undoToolTip;
        protected List defaultToolTip;
        protected boolean isValidValue = true;
        protected HoverChecker tooltipHoverChecker;
        protected HoverChecker undoHoverChecker;
        protected HoverChecker defaultHoverChecker;
        protected boolean drawLabel;

        @SuppressWarnings({ "unchecked" })
        public ListEntryBase(GuiConfig owningScreen, GuiConfigEntries owningEntryList, IConfigElement configElement)
        {
            this.owningScreen = owningScreen;
            this.owningEntryList = owningEntryList;
            this.configElement = configElement;
            this.mc = Minecraft.func_71410_x();
            String trans = I18n.func_135052_a(configElement.getLanguageKey());
            if (!trans.equals(configElement.getLanguageKey()))
                this.name = trans;
            else
                this.name = configElement.getName();
            this.btnUndoChanges = new GuiButtonExt(0, 0, 0, 18, 18, UNDO_CHAR);
            this.btnDefault = new GuiButtonExt(0, 0, 0, 18, 18, RESET_CHAR);

            this.undoHoverChecker = new HoverChecker(this.btnUndoChanges, 800);
            this.defaultHoverChecker = new HoverChecker(this.btnDefault, 800);
            this.undoToolTip = Arrays.asList(new String[] { I18n.func_135052_a("fml.configgui.tooltip.undoChanges") });
            this.defaultToolTip = Arrays.asList(new String[] { I18n.func_135052_a("fml.configgui.tooltip.resetToDefault") });

            this.drawLabel = true;

            String comment;

            comment = I18n.func_135052_a(configElement.getLanguageKey() + ".tooltip").replace("\\n", "\n");

            if (!comment.equals(configElement.getLanguageKey() + ".tooltip"))
                toolTip = new ArrayList<String>(this.mc.field_71466_p.func_78271_c(
                        EnumChatFormatting.GREEN + name + "\n" + EnumChatFormatting.YELLOW + comment, 300));
            else if (configElement.getComment() != null && !configElement.getComment().trim().isEmpty())
                toolTip = new ArrayList<String>(this.mc.field_71466_p.func_78271_c(
                        EnumChatFormatting.GREEN + name + "\n" + EnumChatFormatting.YELLOW + configElement.getComment(), 300));
            else
                toolTip = new ArrayList<String>(this.mc.field_71466_p.func_78271_c(
                        EnumChatFormatting.GREEN + name + "\n" + EnumChatFormatting.RED + "No tooltip defined.", 300));

            if ((configElement.getType() == ConfigGuiType.INTEGER
                    && (Integer.valueOf(configElement.getMinValue().toString()) != Integer.MIN_VALUE || Integer.valueOf(configElement.getMaxValue().toString()) != Integer.MAX_VALUE))
                    || (configElement.getType() == ConfigGuiType.DOUBLE
                    && (Double.valueOf(configElement.getMinValue().toString()) != -Double.MAX_VALUE || Double.valueOf(configElement.getMaxValue().toString()) != Double.MAX_VALUE)))
                toolTip.addAll(this.mc.field_71466_p.func_78271_c(
                        EnumChatFormatting.AQUA + I18n.func_135052_a("fml.configgui.tooltip.defaultNumeric", configElement.getMinValue(), configElement.getMaxValue(), configElement.getDefault()), 300));
            else if (configElement.getType() != ConfigGuiType.CONFIG_CATEGORY)
                toolTip.addAll(this.mc.field_71466_p.func_78271_c(EnumChatFormatting.AQUA + I18n.func_135052_a("fml.configgui.tooltip.default", configElement.getDefault()),300));

            if (configElement.requiresMcRestart() || owningScreen.allRequireMcRestart)
                toolTip.add(EnumChatFormatting.RED + "[" + I18n.func_135052_a("fml.configgui.gameRestartTitle") + "]");
        }

        @Override
        public void func_148279_a(int slotIndex, int x, int y, int listWidth, int slotHeight, Tessellator tessellator, int mouseX, int mouseY, boolean isSelected)
        {
            boolean isChanged = isChanged();

            if (drawLabel)
            {
                String label = (!isValidValue ? EnumChatFormatting.RED.toString() :
                        (isChanged ? EnumChatFormatting.WHITE.toString() : EnumChatFormatting.GRAY.toString()))
                        + (isChanged ? EnumChatFormatting.ITALIC.toString() : "") + this.name;
                this.mc.field_71466_p.func_78276_b(
                        label,
                        this.owningScreen.entryList.labelX,
                        y + slotHeight / 2 - this.mc.field_71466_p.field_78288_b / 2,
                        16777215);
            }

            this.btnUndoChanges.field_146128_h = this.owningEntryList.scrollBarX - 44;
            this.btnUndoChanges.field_146129_i = y;
            this.btnUndoChanges.field_146124_l = enabled() && isChanged;
            this.btnUndoChanges.func_146112_a(this.mc, mouseX, mouseY);

            this.btnDefault.field_146128_h = this.owningEntryList.scrollBarX - 22;
            this.btnDefault.field_146129_i = y;
            this.btnDefault.field_146124_l = enabled() && !isDefault();
            this.btnDefault.func_146112_a(this.mc, mouseX, mouseY);

            if (this.tooltipHoverChecker == null)
                this.tooltipHoverChecker = new HoverChecker(y, y + slotHeight, x, this.owningScreen.entryList.controlX - 8, 800);
            else
                this.tooltipHoverChecker.updateBounds(y, y + slotHeight, x, this.owningScreen.entryList.controlX - 8);
        }

        @Override
        public void drawToolTip(int mouseX, int mouseY)
        {
            boolean canHover = mouseY < this.owningScreen.entryList.field_148154_c && mouseY > this.owningScreen.entryList.field_148153_b;
            if (toolTip != null && this.tooltipHoverChecker != null)
            {
                if (this.tooltipHoverChecker.checkHover(mouseX, mouseY, canHover))
                    this.owningScreen.drawToolTip(toolTip, mouseX, mouseY);
            }

            if (this.undoHoverChecker.checkHover(mouseX, mouseY, canHover))
                this.owningScreen.drawToolTip(undoToolTip, mouseX, mouseY);

            if (this.defaultHoverChecker.checkHover(mouseX, mouseY, canHover))
                this.owningScreen.drawToolTip(defaultToolTip, mouseX, mouseY);
        }

        @Override
        public boolean func_148278_a(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            if (this.btnDefault.func_146116_c(this.mc, x, y))
            {
                btnDefault.func_146113_a(mc.func_147118_V());
                setToDefault();
                return true;
            }
            else if (this.btnUndoChanges.func_146116_c(this.mc, x, y))
            {
                btnUndoChanges.func_146113_a(mc.func_147118_V());
                undoChanges();
                return true;
            }
            return false;
        }

        @Override
        public void func_148277_b(int index, int x, int y, int mouseEvent, int relativeX, int relativeY)
        {
            this.btnDefault.func_146118_a(x, y);
        }

        @Override
        public abstract boolean isDefault();

        @Override
        public abstract void setToDefault();

        @Override
        public abstract void keyTyped(char eventChar, int eventKey);

        @Override
        public abstract void updateCursorCounter();

        @Override
        public abstract void mouseClicked(int x, int y, int mouseEvent);

        @Override
        public abstract boolean isChanged();

        @Override
        public abstract void undoChanges();

        @Override
        public abstract boolean saveConfigElement();

        @Override
        public boolean enabled()
        {
            return owningScreen.isWorldRunning ? !owningScreen.allRequireWorldRestart && !configElement.requiresWorldRestart() : true;
        }

        @Override
        public int getLabelWidth()
        {
            return this.mc.field_71466_p.func_78256_a(this.name);
        }

        @Override
        public int getEntryRightBound()
        {
            return this.owningEntryList.resetX + 40;
        }

        @Override
        public IConfigElement getConfigElement()
        {
            return configElement;
        }

        @Override
        public String getName()
        {
            return configElement.getName();
        }

        @Override
        public abstract Object getCurrentValue();

        @Override
        public abstract Object[] getCurrentValues();

        @Override
        public void onGuiClosed()
        {}
    }

    /**
     * Provides an interface for defining GuiPropertyList.listEntry objects.
     */
    public static interface IConfigEntry<T> extends GuiListExtended.IGuiListEntry
    {
        /**
         * Gets the IConfigElement object owned by this entry.
         * @return
         */
        @SuppressWarnings("rawtypes")
        public IConfigElement getConfigElement();

        /**
         * Gets the name of the ConfigElement owned by this entry.
         */
        public String getName();

        /**
         * Gets the current value of this entry as a String.
         */
        public T getCurrentValue();

        /**
         * Gets the current values of this list entry as a String[].
         */
        public T[] getCurrentValues();

        /**
         * Is this list entry enabled?
         *
         * @return true if this entry's controls should be enabled, false otherwise.
         */
        public boolean enabled();

        /**
         * Handles user keystrokes for any GuiTextField objects in this entry. Call {@code GuiTextField.keyTyped()} for any GuiTextField
         * objects that should receive the input provided.
         */
        public void keyTyped(char eventChar, int eventKey);

        /**
         * Call {@code GuiTextField.updateCursorCounter()} for any GuiTextField objects in this entry.
         */
        public void updateCursorCounter();

        /**
         * Call {@code GuiTextField.mouseClicked()} for and GuiTextField objects in this entry.
         */
        public void mouseClicked(int x, int y, int mouseEvent);

        /**
         * Is this entry's value equal to the default value? Generally true should be returned if this entry is not a property or category
         * entry.
         *
         * @return true if this entry's value is equal to this entry's default value.
         */
        public boolean isDefault();

        /**
         * Sets this entry's value to the default value.
         */
        public void setToDefault();

        /**
         * Handles reverting any changes that have occurred to this entry.
         */
        public void undoChanges();

        /**
         * Has the value of this entry changed?
         *
         * @return true if changes have been made to this entry's value, false otherwise.
         */
        public boolean isChanged();

        /**
         * Handles saving any changes that have been made to this entry back to the underlying object. It is a good practice to check
         * isChanged() before performing the save action. This method should return true if the element has changed AND REQUIRES A RESTART.
         */
        public boolean saveConfigElement();

        /**
         * Handles drawing any tooltips that apply to this entry. This method is called after all other GUI elements have been drawn to the
         * screen, so it could also be used to draw any GUI element that needs to be drawn after all entries have had drawEntry() called.
         */
        public void drawToolTip(int mouseX, int mouseY);

        /**
         * Gets this entry's label width.
         */
        public int getLabelWidth();

        /**
         * Gets this entry's right-hand x boundary. This value is used to control where the scroll bar is placed.
         */
        public int getEntryRightBound();

        /**
         * This method is called when the parent GUI is closed. Most handlers won't need this; it is provided for special cases.
         */
        public void onGuiClosed();
    }
}