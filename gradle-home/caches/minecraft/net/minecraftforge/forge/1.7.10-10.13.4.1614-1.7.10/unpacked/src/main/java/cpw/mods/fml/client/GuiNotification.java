package cpw.mods.fml.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import cpw.mods.fml.common.StartupQuery;

public class GuiNotification extends GuiScreen
{
    public GuiNotification(StartupQuery query)
    {
        this.query = query;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void func_73866_w_()
    {
        this.field_146292_n.add(new GuiButton(0, this.field_146294_l / 2 - 100, this.field_146295_m - 38, I18n.func_135052_a("gui.done")));
    }

    @Override
    protected void func_146284_a(GuiButton button)
    {
        if (button.field_146124_l && button.field_146127_k == 0)
        {
            FMLClientHandler.instance().showGuiScreen(null);
            query.finish();
        }
    }

    @Override
    public void func_73863_a(int p_73863_1_, int p_73863_2_, float p_73863_3_)
    {
        this.func_146276_q_();

        String[] lines = query.getText().split("\n");

        int spaceAvailable = this.field_146295_m - 38 - 20;
        int spaceRequired = Math.min(spaceAvailable, 10 + 10 * lines.length);

        int offset = 10 + (spaceAvailable - spaceRequired) / 2; // vertically centered

        for (String line : lines)
        {
            if (offset >= spaceAvailable)
            {
                this.func_73732_a(this.field_146289_q, "...", this.field_146294_l / 2, offset, 0xFFFFFF);
                break;
            }
            else
            {
                if (!line.isEmpty()) this.func_73732_a(this.field_146289_q, line, this.field_146294_l / 2, offset, 0xFFFFFF);
                offset += 10;
            }
        }

        super.func_73863_a(p_73863_1_, p_73863_2_, p_73863_3_);
    }

    protected final StartupQuery query;
}
