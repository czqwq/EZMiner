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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * This class provides a button that fixes several bugs present in the vanilla GuiButton drawing code.
 * The gist of it is that it allows buttons of any size without gaps in the graphics and with the 
 * borders drawn properly. It also prevents button text from extending out of the sides of the button by
 * trimming the end of the string and adding an ellipsis.<br/><br/>
 * 
 * The code that handles drawing the button is in GuiUtils.
 * 
 * @author bspkrs
 */
public class GuiButtonExt extends GuiButton
{
    public GuiButtonExt(int id, int xPos, int yPos, String displayString)
    {
        super(id, xPos, yPos, displayString);
    }
    
    public GuiButtonExt(int id, int xPos, int yPos, int width, int height, String displayString)
    {
        super(id, xPos, yPos, width, height, displayString);
    }
    
    /**
     * Draws this button to the screen.
     */
    @Override
    public void func_146112_a(Minecraft mc, int mouseX, int mouseY)
    {
        if (this.field_146125_m)
        {
            this.field_146123_n = mouseX >= this.field_146128_h && mouseY >= this.field_146129_i && mouseX < this.field_146128_h + this.field_146120_f && mouseY < this.field_146129_i + this.field_146121_g;
            int k = this.func_146114_a(this.field_146123_n);
            GuiUtils.drawContinuousTexturedBox(field_146122_a, this.field_146128_h, this.field_146129_i, 0, 46 + k * 20, this.field_146120_f, this.field_146121_g, 200, 20, 2, 3, 2, 2, this.field_73735_i);
            this.func_146119_b(mc, mouseX, mouseY);
            int color = 14737632;
            
            if (packedFGColour != 0)
            {
                color = packedFGColour;
            }
            else if (!this.field_146124_l)
            {
                color = 10526880;
            }
            else if (this.field_146123_n)
            {
                color = 16777120;
            }
            
            String buttonText = this.field_146126_j;
            int strWidth = mc.field_71466_p.func_78256_a(buttonText);
            int ellipsisWidth = mc.field_71466_p.func_78256_a("...");
            
            if (strWidth > field_146120_f - 6 && strWidth > ellipsisWidth)
                buttonText = mc.field_71466_p.func_78269_a(buttonText, field_146120_f - 6 - ellipsisWidth).trim() + "...";
            
            this.func_73732_a(mc.field_71466_p, buttonText, this.field_146128_h + this.field_146120_f / 2, this.field_146129_i + (this.field_146121_g - 8) / 2, color);
        }
    }
}