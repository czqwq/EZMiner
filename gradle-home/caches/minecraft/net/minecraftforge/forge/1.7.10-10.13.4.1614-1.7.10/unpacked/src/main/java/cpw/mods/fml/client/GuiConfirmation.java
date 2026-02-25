package cpw.mods.fml.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.resources.I18n;
import cpw.mods.fml.common.StartupQuery;

public class GuiConfirmation extends GuiNotification
{
    public GuiConfirmation(StartupQuery query)
    {
        super(query);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void func_73866_w_()
    {
        this.field_146292_n.add(new GuiOptionButton(0, this.field_146294_l / 2 - 155, this.field_146295_m - 38, I18n.func_135052_a("gui.yes")));
        this.field_146292_n.add(new GuiOptionButton(1, this.field_146294_l / 2 - 155 + 160, this.field_146295_m - 38, I18n.func_135052_a("gui.no")));
    }

    @Override
    protected void func_146284_a(GuiButton button)
    {
        if (button.field_146124_l && (button.field_146127_k == 0 || button.field_146127_k == 1))
        {
            FMLClientHandler.instance().showGuiScreen(null);
            query.setResult(button.field_146127_k == 0);
            query.finish();
        }
    }
}
