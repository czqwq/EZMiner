package net.minecraftforge.fluids;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Reference implementation of {@link IFluidContainerItem}. Use/extend this or implement your own.
 * 
 * @author King Lemming
 * 
 */
public class ItemFluidContainer extends Item implements IFluidContainerItem
{
    protected int capacity;

    public ItemFluidContainer(int itemID)
    {
        super();
    }

    public ItemFluidContainer(int itemID, int capacity)
    {
        super();
        this.capacity = capacity;
    }

    public ItemFluidContainer setCapacity(int capacity)
    {
        this.capacity = capacity;
        return this;
    }

    /* IFluidContainerItem */
    @Override
    public FluidStack getFluid(ItemStack container)
    {
        if (container.field_77990_d == null || !container.field_77990_d.func_74764_b("Fluid"))
        {
            return null;
        }
        return FluidStack.loadFluidStackFromNBT(container.field_77990_d.func_74775_l("Fluid"));
    }

    @Override
    public int getCapacity(ItemStack container)
    {
        return capacity;
    }

    @Override
    public int fill(ItemStack container, FluidStack resource, boolean doFill)
    {
        if (resource == null)
        {
            return 0;
        }

        if (!doFill)
        {
            if (container.field_77990_d == null || !container.field_77990_d.func_74764_b("Fluid"))
            {
                return Math.min(capacity, resource.amount);
            }

            FluidStack stack = FluidStack.loadFluidStackFromNBT(container.field_77990_d.func_74775_l("Fluid"));

            if (stack == null)
            {
                return Math.min(capacity, resource.amount);
            }

            if (!stack.isFluidEqual(resource))
            {
                return 0;
            }

            return Math.min(capacity - stack.amount, resource.amount);
        }

        if (container.field_77990_d == null)
        {
            container.field_77990_d = new NBTTagCompound();
        }

        if (!container.field_77990_d.func_74764_b("Fluid"))
        {
            NBTTagCompound fluidTag = resource.writeToNBT(new NBTTagCompound());

            if (capacity < resource.amount)
            {
                fluidTag.func_74768_a("Amount", capacity);
                container.field_77990_d.func_74782_a("Fluid", fluidTag);
                return capacity;
            }

            container.field_77990_d.func_74782_a("Fluid", fluidTag);
            return resource.amount;
        }

        NBTTagCompound fluidTag = container.field_77990_d.func_74775_l("Fluid");
        FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidTag);

        if (!stack.isFluidEqual(resource))
        {
            return 0;
        }

        int filled = capacity - stack.amount;
        if (resource.amount < filled)
        {
            stack.amount += resource.amount;
            filled = resource.amount;
        }
        else
        {
            stack.amount = capacity;
        }

        container.field_77990_d.func_74782_a("Fluid", stack.writeToNBT(fluidTag));
        return filled;
    }

    @Override
    public FluidStack drain(ItemStack container, int maxDrain, boolean doDrain)
    {
        if (container.field_77990_d == null || !container.field_77990_d.func_74764_b("Fluid"))
        {
            return null;
        }

        FluidStack stack = FluidStack.loadFluidStackFromNBT(container.field_77990_d.func_74775_l("Fluid"));
        if (stack == null)
        {
            return null;
        }

        int currentAmount = stack.amount;
        stack.amount = Math.min(stack.amount, maxDrain);
        if (doDrain)
        {
            if (currentAmount == stack.amount)
            {
                container.field_77990_d.func_82580_o("Fluid");

                if (container.field_77990_d.func_82582_d())
                {
                    container.field_77990_d = null;
                }
                return stack;
            }

            NBTTagCompound fluidTag = container.field_77990_d.func_74775_l("Fluid");
            fluidTag.func_74768_a("Amount", currentAmount - stack.amount);
            container.field_77990_d.func_74782_a("Fluid", fluidTag);
        }
        return stack;
    }
}
