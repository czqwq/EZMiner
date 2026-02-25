package net.minecraftforge.client;

import static net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiPlayerInfo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.Direction;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.FoodStats;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.FMLCommonHandler;

public class GuiIngameForge extends GuiIngame
{
    //private static final ResourceLocation VIGNETTE     = new ResourceLocation("textures/misc/vignette.png");
    private static final ResourceLocation WIDGITS      = new ResourceLocation("textures/gui/widgets.png");
    //private static final ResourceLocation PUMPKIN_BLUR = new ResourceLocation("textures/misc/pumpkinblur.png");

    private static final int WHITE = 0xFFFFFF;

    //Flags to toggle the rendering of certain aspects of the HUD, valid conditions
    //must be met for them to render normally. If those conditions are met, but this flag
    //is false, they will not be rendered.
    public static boolean renderHelmet = true;
    public static boolean renderPortal = true;
    public static boolean renderHotbar = true;
    public static boolean renderCrosshairs = true;
    public static boolean renderBossHealth = true;
    public static boolean renderHealth = true;
    public static boolean renderArmor = true;
    public static boolean renderFood = true;
    public static boolean renderHealthMount = true;
    public static boolean renderAir = true;
    public static boolean renderExperiance = true;
    public static boolean renderJumpBar = true;
    public static boolean renderObjective = true;

    public static int left_height = 39;
    public static int right_height = 39;

    private ScaledResolution res = null;
    private FontRenderer fontrenderer = null;
    private RenderGameOverlayEvent eventParent;
    private static final String MC_VERSION = MinecraftForge.MC_VERSION;

    public GuiIngameForge(Minecraft mc)
    {
        super(mc);
    }

    @Override
    public void func_73830_a(float partialTicks, boolean hasScreen, int mouseX, int mouseY)
    {
        res = new ScaledResolution(field_73839_d, field_73839_d.field_71443_c, field_73839_d.field_71440_d);
        eventParent = new RenderGameOverlayEvent(partialTicks, res, mouseX, mouseY);
        int width = res.func_78326_a();
        int height = res.func_78328_b();
        renderHealthMount = field_73839_d.field_71439_g.field_70154_o instanceof EntityLivingBase;
        renderFood = field_73839_d.field_71439_g.field_70154_o == null;
        renderJumpBar = field_73839_d.field_71439_g.func_110317_t();

        right_height = 39;
        left_height = 39;

        if (pre(ALL)) return;

        fontrenderer = field_73839_d.field_71466_p;
        field_73839_d.field_71460_t.func_78478_c();
        GL11.glEnable(GL11.GL_BLEND);

        if (Minecraft.func_71375_t())
        {
            func_73829_a(field_73839_d.field_71439_g.func_70013_c(partialTicks), width, height);
        }
        else
        {
            OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        }

        if (renderHelmet) renderHelmet(res, partialTicks, hasScreen, mouseX, mouseY);

        if (renderPortal && !field_73839_d.field_71439_g.func_70644_a(Potion.field_76431_k))
        {
            renderPortal(width, height, partialTicks);
        }

        if (!field_73839_d.field_71442_b.func_78747_a())
        {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            field_73735_i = -90.0F;
            field_73842_c.setSeed((long)(field_73837_f * 312871));

            if (renderCrosshairs) renderCrosshairs(width, height);
            if (renderBossHealth) func_73828_d();

            if (this.field_73839_d.field_71442_b.func_78755_b())
            {
                if (renderHealth) renderHealth(width, height);
                if (renderArmor)  renderArmor(width, height);
                if (renderFood)   renderFood(width, height);
                if (renderHealthMount) renderHealthMount(width, height);
                if (renderAir)    renderAir(width, height);
            }
            if (renderHotbar) renderHotbar(width, height, partialTicks);
        }

        if (renderJumpBar)
        {
            renderJumpBar(width, height);
        }
        else if (renderExperiance)
        {
            renderExperience(width, height);
        }

        renderSleepFade(width, height);
        renderToolHightlight(width, height);
        renderHUDText(width, height);
        renderRecordOverlay(width, height, partialTicks);

        ScoreObjective objective = field_73839_d.field_71441_e.func_96441_U().func_96539_a(1);
        if (renderObjective && objective != null)
        {
            this.func_96136_a(objective, height, width, fontrenderer);
        }

        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_ALPHA_TEST);

        renderChat(width, height);

        renderPlayerList(width, height);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);

        post(ALL);
    }

    public ScaledResolution getResolution()
    {
        return res;
    }

    protected void renderHotbar(int width, int height, float partialTicks)
    {
        if (pre(HOTBAR)) return;
        field_73839_d.field_71424_I.func_76320_a("actionBar");

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        field_73839_d.field_71446_o.func_110577_a(WIDGITS);

        InventoryPlayer inv = field_73839_d.field_71439_g.field_71071_by;
        func_73729_b(width / 2 - 91, height - 22, 0, 0, 182, 22);
        func_73729_b(width / 2 - 91 - 1 + inv.field_70461_c * 20, height - 22 - 1, 0, 22, 24, 22);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.func_74520_c();

        for (int i = 0; i < 9; ++i)
        {
            int x = width / 2 - 90 + i * 20 + 2;
            int z = height - 16 - 3;
            func_73832_a(i, x, z, partialTicks);
        }

        RenderHelper.func_74518_a();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        field_73839_d.field_71424_I.func_76319_b();
        post(HOTBAR);
    }

    protected void renderCrosshairs(int width, int height)
    {
        if (pre(CROSSHAIRS)) return;
        bind(Gui.field_110324_m);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.func_148821_a(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, 1, 0);
        func_73729_b(width / 2 - 7, height / 2 - 7, 0, 0, 16, 16);
        OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glDisable(GL11.GL_BLEND);
        post(CROSSHAIRS);
    }

    @Override
    protected void func_73828_d()
    {
        if (pre(BOSSHEALTH)) return;
        field_73839_d.field_71424_I.func_76320_a("bossHealth");
        GL11.glEnable(GL11.GL_BLEND);
        super.func_73828_d();
        GL11.glDisable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        post(BOSSHEALTH);
    }

    private void renderHelmet(ScaledResolution res, float partialTicks, boolean hasScreen, int mouseX, int mouseY)
    {
        if (pre(HELMET)) return;

        ItemStack itemstack = this.field_73839_d.field_71439_g.field_71071_by.func_70440_f(3);

        if (this.field_73839_d.field_71474_y.field_74320_O == 0 && itemstack != null && itemstack.func_77973_b() != null)
        {
            if (itemstack.func_77973_b() == Item.func_150898_a(Blocks.field_150423_aK))
            {
                func_73836_a(res.func_78326_a(), res.func_78328_b());
            }
            else
            {
                itemstack.func_77973_b().renderHelmetOverlay(itemstack, field_73839_d.field_71439_g, res, partialTicks, hasScreen, mouseX, mouseY);
            }
        }

        post(HELMET);
    }

    protected void renderArmor(int width, int height)
    {
        if (pre(ARMOR)) return;
        field_73839_d.field_71424_I.func_76320_a("armor");

        GL11.glEnable(GL11.GL_BLEND);
        int left = width / 2 - 91;
        int top = height - left_height;

        int level = ForgeHooks.getTotalArmorValue(field_73839_d.field_71439_g);
        for (int i = 1; level > 0 && i < 20; i += 2)
        {
            if (i < level)
            {
                func_73729_b(left, top, 34, 9, 9, 9);
            }
            else if (i == level)
            {
                func_73729_b(left, top, 25, 9, 9, 9);
            }
            else if (i > level)
            {
                func_73729_b(left, top, 16, 9, 9, 9);
            }
            left += 8;
        }
        left_height += 10;

        GL11.glDisable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        post(ARMOR);
    }

    protected void renderPortal(int width, int height, float partialTicks)
    {
        if (pre(PORTAL)) return;

        float f1 = field_73839_d.field_71439_g.field_71080_cy + (field_73839_d.field_71439_g.field_71086_bY - field_73839_d.field_71439_g.field_71080_cy) * partialTicks;

        if (f1 > 0.0F)
        {
            func_130015_b(f1, width, height);
        }

        post(PORTAL);
    }

    protected void renderAir(int width, int height)
    {
        if (pre(AIR)) return;
        field_73839_d.field_71424_I.func_76320_a("air");
        GL11.glEnable(GL11.GL_BLEND);
        int left = width / 2 + 91;
        int top = height - right_height;

        if (field_73839_d.field_71439_g.func_70055_a(Material.field_151586_h))
        {
            int air = field_73839_d.field_71439_g.func_70086_ai();
            int full = MathHelper.func_76143_f((double)(air - 2) * 10.0D / 300.0D);
            int partial = MathHelper.func_76143_f((double)air * 10.0D / 300.0D) - full;

            for (int i = 0; i < full + partial; ++i)
            {
                func_73729_b(left - i * 8 - 9, top, (i < full ? 16 : 25), 18, 9, 9);
            }
            right_height += 10;
        }

        GL11.glDisable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        post(AIR);
    }

    public void renderHealth(int width, int height)
    {
        bind(field_110324_m);
        if (pre(HEALTH)) return;
        field_73839_d.field_71424_I.func_76320_a("health");
        GL11.glEnable(GL11.GL_BLEND);

        boolean highlight = field_73839_d.field_71439_g.field_70172_ad / 3 % 2 == 1;

        if (field_73839_d.field_71439_g.field_70172_ad < 10)
        {
            highlight = false;
        }

        IAttributeInstance attrMaxHealth = this.field_73839_d.field_71439_g.func_110148_a(SharedMonsterAttributes.field_111267_a);
        int health = MathHelper.func_76123_f(field_73839_d.field_71439_g.func_110143_aJ());
        int healthLast = MathHelper.func_76123_f(field_73839_d.field_71439_g.field_70735_aL);
        float healthMax = (float)attrMaxHealth.func_111126_e();
        float absorb = this.field_73839_d.field_71439_g.func_110139_bj();

        int healthRows = MathHelper.func_76123_f((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        this.field_73842_c.setSeed((long)(field_73837_f * 312871));

        int left = width / 2 - 91;
        int top = height - left_height;
        left_height += (healthRows * rowHeight);
        if (rowHeight != 10) left_height += 10 - rowHeight;

        int regen = -1;
        if (field_73839_d.field_71439_g.func_70644_a(Potion.field_76428_l))
        {
            regen = field_73837_f % 25;
        }

        final int TOP =  9 * (field_73839_d.field_71441_e.func_72912_H().func_76093_s() ? 5 : 0);
        final int BACKGROUND = (highlight ? 25 : 16);
        int MARGIN = 16;
        if (field_73839_d.field_71439_g.func_70644_a(Potion.field_76436_u))      MARGIN += 36;
        else if (field_73839_d.field_71439_g.func_70644_a(Potion.field_82731_v)) MARGIN += 72;
        float absorbRemaining = absorb;

        for (int i = MathHelper.func_76123_f((healthMax + absorb) / 2.0F) - 1; i >= 0; --i)
        {
            //int b0 = (highlight ? 1 : 0);
            int row = MathHelper.func_76123_f((float)(i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            if (health <= 4) y += field_73842_c.nextInt(2);
            if (i == regen) y -= 2;

            func_73729_b(x, y, BACKGROUND, TOP, 9, 9);

            if (highlight)
            {
                if (i * 2 + 1 < healthLast)
                    func_73729_b(x, y, MARGIN + 54, TOP, 9, 9); //6
                else if (i * 2 + 1 == healthLast)
                    func_73729_b(x, y, MARGIN + 63, TOP, 9, 9); //7
            }

            if (absorbRemaining > 0.0F)
            {
                if (absorbRemaining == absorb && absorb % 2.0F == 1.0F)
                    func_73729_b(x, y, MARGIN + 153, TOP, 9, 9); //17
                else
                    func_73729_b(x, y, MARGIN + 144, TOP, 9, 9); //16
                absorbRemaining -= 2.0F;
            }
            else
            {
                if (i * 2 + 1 < health)
                    func_73729_b(x, y, MARGIN + 36, TOP, 9, 9); //4
                else if (i * 2 + 1 == health)
                    func_73729_b(x, y, MARGIN + 45, TOP, 9, 9); //5
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        post(HEALTH);
    }

    public void renderFood(int width, int height)
    {
        if (pre(FOOD)) return;
        field_73839_d.field_71424_I.func_76320_a("food");

        GL11.glEnable(GL11.GL_BLEND);
        int left = width / 2 + 91;
        int top = height - right_height;
        right_height += 10;
        boolean unused = false;// Unused flag in vanilla, seems to be part of a 'fade out' mechanic

        FoodStats stats = field_73839_d.field_71439_g.func_71024_bL();
        int level = stats.func_75116_a();
        int levelLast = stats.func_75120_b();

        for (int i = 0; i < 10; ++i)
        {
            int idx = i * 2 + 1;
            int x = left - i * 8 - 9;
            int y = top;
            int icon = 16;
            byte backgound = 0;

            if (field_73839_d.field_71439_g.func_70644_a(Potion.field_76438_s))
            {
                icon += 36;
                backgound = 13;
            }
            if (unused) backgound = 1; //Probably should be a += 1 but vanilla never uses this

            if (field_73839_d.field_71439_g.func_71024_bL().func_75115_e() <= 0.0F && field_73837_f % (level * 3 + 1) == 0)
            {
                y = top + (field_73842_c.nextInt(3) - 1);
            }

            func_73729_b(x, y, 16 + backgound * 9, 27, 9, 9);

            if (unused)
            {
                if (idx < levelLast)
                    func_73729_b(x, y, icon + 54, 27, 9, 9);
                else if (idx == levelLast)
                    func_73729_b(x, y, icon + 63, 27, 9, 9);
            }

            if (idx < level)
                func_73729_b(x, y, icon + 36, 27, 9, 9);
            else if (idx == level)
                func_73729_b(x, y, icon + 45, 27, 9, 9);
        }
        GL11.glDisable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        post(FOOD);
    }

    protected void renderSleepFade(int width, int height)
    {
        if (field_73839_d.field_71439_g.func_71060_bI() > 0)
        {
            field_73839_d.field_71424_I.func_76320_a("sleep");
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            int sleepTime = field_73839_d.field_71439_g.func_71060_bI();
            float opacity = (float)sleepTime / 100.0F;

            if (opacity > 1.0F)
            {
                opacity = 1.0F - (float)(sleepTime - 100) / 10.0F;
            }

            int color = (int)(220.0F * opacity) << 24 | 1052704;
            func_73734_a(0, 0, width, height, color);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            field_73839_d.field_71424_I.func_76319_b();
        }
    }

    protected void renderExperience(int width, int height)
    {
        bind(field_110324_m);
        if (pre(EXPERIENCE)) return;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);

        if (field_73839_d.field_71442_b.func_78763_f())
        {
            field_73839_d.field_71424_I.func_76320_a("expBar");
            int cap = this.field_73839_d.field_71439_g.func_71050_bK();
            int left = width / 2 - 91;

            if (cap > 0)
            {
                short barWidth = 182;
                int filled = (int)(field_73839_d.field_71439_g.field_71106_cc * (float)(barWidth + 1));
                int top = height - 32 + 3;
                func_73729_b(left, top, 0, 64, barWidth, 5);

                if (filled > 0)
                {
                    func_73729_b(left, top, 0, 69, filled, 5);
                }
            }

            this.field_73839_d.field_71424_I.func_76319_b();


            if (field_73839_d.field_71442_b.func_78763_f() && field_73839_d.field_71439_g.field_71068_ca > 0)
            {
                field_73839_d.field_71424_I.func_76320_a("expLevel");
                boolean flag1 = false;
                int color = flag1 ? 16777215 : 8453920;
                String text = "" + field_73839_d.field_71439_g.field_71068_ca;
                int x = (width - fontrenderer.func_78256_a(text)) / 2;
                int y = height - 31 - 4;
                fontrenderer.func_78276_b(text, x + 1, y, 0);
                fontrenderer.func_78276_b(text, x - 1, y, 0);
                fontrenderer.func_78276_b(text, x, y + 1, 0);
                fontrenderer.func_78276_b(text, x, y - 1, 0);
                fontrenderer.func_78276_b(text, x, y, color);
                field_73839_d.field_71424_I.func_76319_b();
            }
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        post(EXPERIENCE);
    }

    protected void renderJumpBar(int width, int height)
    {
        bind(field_110324_m);
        if (pre(JUMPBAR)) return;
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_BLEND);

        field_73839_d.field_71424_I.func_76320_a("jumpBar");
        float charge = field_73839_d.field_71439_g.func_110319_bJ();
        final int barWidth = 182;
        int x = (width / 2) - (barWidth / 2);
        int filled = (int)(charge * (float)(barWidth + 1));
        int top = height - 32 + 3;

        func_73729_b(x, top, 0, 84, barWidth, 5);

        if (filled > 0)
        {
            this.func_73729_b(x, top, 0, 89, filled, 5);
        }

        GL11.glEnable(GL11.GL_BLEND);
        field_73839_d.field_71424_I.func_76319_b();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        post(JUMPBAR);
    }

    protected void renderToolHightlight(int width, int height)
    {
        if (this.field_73839_d.field_71474_y.field_92117_D)
        {
            field_73839_d.field_71424_I.func_76320_a("toolHighlight");

            if (this.field_92017_k > 0 && this.field_92016_l != null)
            {
                String name = this.field_92016_l.func_82833_r();

                int opacity = (int)((float)this.field_92017_k * 256.0F / 10.0F);
                if (opacity > 255) opacity = 255;

                if (opacity > 0)
                {
                    int y = height - 59;
                    if (!field_73839_d.field_71442_b.func_78755_b()) y += 14;

                    GL11.glPushMatrix();
                    GL11.glEnable(GL11.GL_BLEND);
                    OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                    FontRenderer font = field_92016_l.func_77973_b().getFontRenderer(field_92016_l);
                    if (font != null)
                    {
                        int x = (width - font.func_78256_a(name)) / 2;
                        font.func_78261_a(name, x, y, WHITE | (opacity << 24));
                    }
                    else
                    {
                        int x = (width - fontrenderer.func_78256_a(name)) / 2;
                        fontrenderer.func_78261_a(name, x, y, WHITE | (opacity << 24));
                    }
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glPopMatrix();
                }
            }

            field_73839_d.field_71424_I.func_76319_b();
        }
    }

    protected void renderHUDText(int width, int height)
    {
        field_73839_d.field_71424_I.func_76320_a("forgeHudText");
        OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        ArrayList<String> left = new ArrayList<String>();
        ArrayList<String> right = new ArrayList<String>();

        if (field_73839_d.func_71355_q())
        {
            long time = field_73839_d.field_71441_e.func_82737_E();
            if (time >= 120500L)
            {
                right.add(I18n.func_135052_a("demo.demoExpired"));
            }
            else
            {
                right.add(I18n.func_135052_a("demo.remainingTime", StringUtils.func_76337_a((int)(120500L - time))));
            }
        }


        if (this.field_73839_d.field_71474_y.field_74330_P && !pre(DEBUG))
        {
            field_73839_d.field_71424_I.func_76320_a("debug");
            GL11.glPushMatrix();
            left.add("Minecraft " + MC_VERSION + " (" + this.field_73839_d.field_71426_K + ")");
            left.add(field_73839_d.func_71393_m());
            left.add(field_73839_d.func_71408_n());
            left.add(field_73839_d.func_71374_p());
            left.add(field_73839_d.func_71388_o());
            left.add(null); //Spacer

            long max = Runtime.getRuntime().maxMemory();
            long total = Runtime.getRuntime().totalMemory();
            long free = Runtime.getRuntime().freeMemory();
            long used = total - free;

            right.add("Used memory: " + used * 100L / max + "% (" + used / 1024L / 1024L + "MB) of " + max / 1024L / 1024L + "MB");
            right.add("Allocated memory: " + total * 100L / max + "% (" + total / 1024L / 1024L + "MB)");

            int x = MathHelper.func_76128_c(field_73839_d.field_71439_g.field_70165_t);
            int y = MathHelper.func_76128_c(field_73839_d.field_71439_g.field_70163_u);
            int z = MathHelper.func_76128_c(field_73839_d.field_71439_g.field_70161_v);
            float yaw = field_73839_d.field_71439_g.field_70177_z;
            int heading = MathHelper.func_76128_c((double)(field_73839_d.field_71439_g.field_70177_z * 4.0F / 360.0F) + 0.5D) & 3;

            left.add(String.format("x: %.5f (%d) // c: %d (%d)", field_73839_d.field_71439_g.field_70165_t, x, x >> 4, x & 15));
            left.add(String.format("y: %.3f (feet pos, %.3f eyes pos)", field_73839_d.field_71439_g.field_70121_D.field_72338_b, field_73839_d.field_71439_g.field_70163_u));
            left.add(String.format("z: %.5f (%d) // c: %d (%d)", field_73839_d.field_71439_g.field_70161_v, z, z >> 4, z & 15));
            left.add(String.format("f: %d (%s) / %f", heading, Direction.field_82373_c[heading], MathHelper.func_76142_g(yaw)));

            if (field_73839_d.field_71441_e != null && field_73839_d.field_71441_e.func_72899_e(x, y, z))
            {
                Chunk chunk = this.field_73839_d.field_71441_e.func_72938_d(x, z);
                left.add(String.format("lc: %d b: %s bl: %d sl: %d rl: %d",
                  chunk.func_76625_h() + 15,
                  chunk.func_76591_a(x & 15, z & 15, field_73839_d.field_71441_e.func_72959_q()).field_76791_y,
                  chunk.func_76614_a(EnumSkyBlock.Block, x & 15, y, z & 15),
                  chunk.func_76614_a(EnumSkyBlock.Sky, x & 15, y, z & 15),
                  chunk.func_76629_c(x & 15, y, z & 15, 0)));
            }
            else
            {
                left.add(null);
            }

            left.add(String.format("ws: %.3f, fs: %.3f, g: %b, fl: %d", field_73839_d.field_71439_g.field_71075_bZ.func_75094_b(), field_73839_d.field_71439_g.field_71075_bZ.func_75093_a(), field_73839_d.field_71439_g.field_70122_E, field_73839_d.field_71441_e.func_72976_f(x, z)));
            if (field_73839_d.field_71460_t != null && field_73839_d.field_71460_t.func_147702_a())
            {
                left.add(String.format("shader: %s", field_73839_d.field_71460_t.func_147706_e().func_148022_b()));
            }

            right.add(null);
            for (String brand : FMLCommonHandler.instance().getBrandings(false))
            {
                right.add(brand);
            }
            GL11.glPopMatrix();
            field_73839_d.field_71424_I.func_76319_b();
            post(DEBUG);
        }

        RenderGameOverlayEvent.Text event = new RenderGameOverlayEvent.Text(eventParent, left, right);
        if (!MinecraftForge.EVENT_BUS.post(event))
        {
            for (int x = 0; x < left.size(); x++)
            {
                String msg = left.get(x);
                if (msg == null) continue;
                fontrenderer.func_78261_a(msg, 2, 2 + x * 10, WHITE);
            }

            for (int x = 0; x < right.size(); x++)
            {
                String msg = right.get(x);
                if (msg == null) continue;
                int w = fontrenderer.func_78256_a(msg);
                fontrenderer.func_78261_a(msg, width - w - 10, 2 + x * 10, WHITE);
            }
        }

        field_73839_d.field_71424_I.func_76319_b();
        post(TEXT);
    }

    protected void renderRecordOverlay(int width, int height, float partialTicks)
    {
        if (field_73845_h > 0)
        {
            field_73839_d.field_71424_I.func_76320_a("overlayMessage");
            float hue = (float)field_73845_h - partialTicks;
            int opacity = (int)(hue * 256.0F / 20.0F);
            if (opacity > 255) opacity = 255;

            if (opacity > 0)
            {
                GL11.glPushMatrix();
                GL11.glTranslatef((float)(width / 2), (float)(height - 48), 0.0F);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.func_148821_a(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
                int color = (field_73844_j ? Color.HSBtoRGB(hue / 50.0F, 0.7F, 0.6F) & WHITE : WHITE);
                fontrenderer.func_78276_b(field_73838_g, -fontrenderer.func_78256_a(field_73838_g) / 2, -4, color | (opacity << 24));
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glPopMatrix();
            }

            field_73839_d.field_71424_I.func_76319_b();
        }
    }

    protected void renderChat(int width, int height)
    {
        field_73839_d.field_71424_I.func_76320_a("chat");

        RenderGameOverlayEvent.Chat event = new RenderGameOverlayEvent.Chat(eventParent, 0, height - 48);
        if (MinecraftForge.EVENT_BUS.post(event)) return;

        GL11.glPushMatrix();
        GL11.glTranslatef((float)event.posX, (float)event.posY, 0.0F);
        field_73840_e.func_146230_a(field_73837_f);
        GL11.glPopMatrix();

        post(CHAT);

        field_73839_d.field_71424_I.func_76319_b();
    }

    @SuppressWarnings("unchecked")
    protected void renderPlayerList(int width, int height)
    {
        ScoreObjective scoreobjective = this.field_73839_d.field_71441_e.func_96441_U().func_96539_a(0);
        NetHandlerPlayClient handler = field_73839_d.field_71439_g.field_71174_a;

        if (field_73839_d.field_71474_y.field_74321_H.func_151470_d() && (!field_73839_d.func_71387_A() || handler.field_147303_b.size() > 1 || scoreobjective != null))
        {
            if (pre(PLAYER_LIST)) return;
            this.field_73839_d.field_71424_I.func_76320_a("playerList");
            List<GuiPlayerInfo> players = (List<GuiPlayerInfo>)handler.field_147303_b;
            int maxPlayers = handler.field_147304_c;
            int rows = maxPlayers;
            int columns = 1;

            for (columns = 1; rows > 20; rows = (maxPlayers + columns - 1) / columns)
            {
                columns++;
            }

            int columnWidth = 300 / columns;

            if (columnWidth > 150)
            {
                columnWidth = 150;
            }

            int left = (width - columns * columnWidth) / 2;
            byte border = 10;
            func_73734_a(left - 1, border - 1, left + columnWidth * columns, border + 9 * rows, Integer.MIN_VALUE);

            for (int i = 0; i < maxPlayers; i++)
            {
                int xPos = left + i % columns * columnWidth;
                int yPos = border + i / columns * 9;
                func_73734_a(xPos, yPos, xPos + columnWidth - 1, yPos + 8, 553648127);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glEnable(GL11.GL_ALPHA_TEST);

                if (i < players.size())
                {
                    GuiPlayerInfo player = (GuiPlayerInfo)players.get(i);
                    ScorePlayerTeam team = field_73839_d.field_71441_e.func_96441_U().func_96509_i(player.field_78831_a);
                    String displayName = ScorePlayerTeam.func_96667_a(team, player.field_78831_a);
                    fontrenderer.func_78261_a(displayName, xPos, yPos, 16777215);

                    if (scoreobjective != null)
                    {
                        int endX = xPos + fontrenderer.func_78256_a(displayName) + 5;
                        int maxX = xPos + columnWidth - 12 - 5;

                        if (maxX - endX > 5)
                        {
                            Score score = scoreobjective.func_96682_a().func_96529_a(player.field_78831_a, scoreobjective);
                            String scoreDisplay = EnumChatFormatting.YELLOW + "" + score.func_96652_c();
                            fontrenderer.func_78261_a(scoreDisplay, maxX - fontrenderer.func_78256_a(scoreDisplay), yPos, 16777215);
                        }
                    }

                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                    field_73839_d.func_110434_K().func_110577_a(Gui.field_110324_m);
                    int pingIndex = 4;
                    int ping = player.field_78829_b;
                    if (ping < 0) pingIndex = 5;
                    else if (ping < 150) pingIndex = 0;
                    else if (ping < 300) pingIndex = 1;
                    else if (ping < 600) pingIndex = 2;
                    else if (ping < 1000) pingIndex = 3;

                    field_73735_i += 100.0F;
                    func_73729_b(xPos + columnWidth - 12, yPos, 0, 176 + pingIndex * 8, 10, 8);
                    field_73735_i -= 100.0F;
                }
            }
            post(PLAYER_LIST);
        }
    }

    protected void renderHealthMount(int width, int height)
    {
        Entity tmp = field_73839_d.field_71439_g.field_70154_o;
        if (!(tmp instanceof EntityLivingBase)) return;

        bind(field_110324_m);

        if (pre(HEALTHMOUNT)) return;

        boolean unused = false;
        int left_align = width / 2 + 91;

        field_73839_d.field_71424_I.func_76318_c("mountHealth");
        GL11.glEnable(GL11.GL_BLEND);
        EntityLivingBase mount = (EntityLivingBase)tmp;
        int health = (int)Math.ceil((double)mount.func_110143_aJ());
        float healthMax = mount.func_110138_aP();
        int hearts = (int)(healthMax + 0.5F) / 2;

        if (hearts > 30) hearts = 30;

        final int MARGIN = 52;
        final int BACKGROUND = MARGIN + (unused ? 1 : 0);
        final int HALF = MARGIN + 45;
        final int FULL = MARGIN + 36;

        for (int heart = 0; hearts > 0; heart += 20)
        {
            int top = height - right_height;

            int rowCount = Math.min(hearts, 10);
            hearts -= rowCount;

            for (int i = 0; i < rowCount; ++i)
            {
                int x = left_align - i * 8 - 9;
                func_73729_b(x, top, BACKGROUND, 9, 9, 9);

                if (i * 2 + 1 + heart < health)
                    func_73729_b(x, top, FULL, 9, 9, 9);
                else if (i * 2 + 1 + heart == health)
                    func_73729_b(x, top, HALF, 9, 9, 9);
            }

            right_height += 10;
        }
        GL11.glDisable(GL11.GL_BLEND);
        post(HEALTHMOUNT);
    }

    //Helper macros
    private boolean pre(ElementType type)
    {
        return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Pre(eventParent, type));
    }
    private void post(ElementType type)
    {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(eventParent, type));
    }
    private void bind(ResourceLocation res)
    {
        field_73839_d.func_110434_K().func_110577_a(res);
    }
}
