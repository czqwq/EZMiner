package cpw.mods.fml.client.config;

import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.IChatComponent;

public class GuiMessageDialog extends GuiDisconnected
{
    private String buttonText;

    public GuiMessageDialog(GuiScreen nextScreen, String title, IChatComponent message, String buttonText)
    {
        super(nextScreen, title, message);
        this.buttonText = buttonText;
    }

    @Override
    public void func_73866_w_()
    {
        super.func_73866_w_();
        ((GuiButton) field_146292_n.get(0)).field_146126_j = I18n.func_135052_a(buttonText);
    }
}
