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

import org.lwjgl.opengl.GL11;

/**
 * This class provides a button that shows a string glyph at the beginning. The glyph can be scaled using the glyphScale parameter.
 * 
 * @author bspkrs
 */
public class GuiUnicodeGlyphButton extends GuiButtonExt
{
    public String glyph;
    public float  glyphScale;
    
    public GuiUnicodeGlyphButton(int id, int xPos, int yPos, int width, int height, String displayString, String glyph, float glyphScale)
    {
        super(id, xPos, yPos, width, height, displayString);
        this.glyph = glyph;
        this.glyphScale = glyphScale;
    }
    
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
            int glyphWidth = (int) (mc.field_71466_p.func_78256_a(glyph) * glyphScale);
            int strWidth = mc.field_71466_p.func_78256_a(buttonText);
            int elipsisWidth = mc.field_71466_p.func_78256_a("...");
            int totalWidth = strWidth + glyphWidth;
            
            if (totalWidth > field_146120_f - 6 && totalWidth > elipsisWidth)
                buttonText = mc.field_71466_p.func_78269_a(buttonText, field_146120_f - 6 - elipsisWidth).trim() + "...";
            
            strWidth = mc.field_71466_p.func_78256_a(buttonText);
            totalWidth = glyphWidth + strWidth;
            
            GL11.glPushMatrix();
            GL11.glScalef(glyphScale, glyphScale, 1.0F);
            this.func_73732_a(mc.field_71466_p, glyph,
                    (int) (((this.field_146128_h + (this.field_146120_f / 2) - (strWidth / 2)) / glyphScale) - (glyphWidth / (2 * glyphScale)) + 2),
                    (int) (((this.field_146129_i + ((this.field_146121_g - 8) / glyphScale) / 2) - 1) / glyphScale), color);
            GL11.glPopMatrix();
            
            this.func_73732_a(mc.field_71466_p, buttonText, (int) (this.field_146128_h + (this.field_146120_f / 2) + (glyphWidth / glyphScale)),
                    this.field_146129_i + (this.field_146121_g - 8) / 2, color);
        }
    }
}