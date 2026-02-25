package net.minecraftforge.oredict;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.RandomAccess;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.Level;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.common.MinecraftForge;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.registry.GameData;

public class OreDictionary
{
    private static boolean hasInit = false;
    private static List<String>          idToName = new ArrayList<String>();
    private static Map<String, Integer>  nameToId = new HashMap<String, Integer>(128);
    private static List<ArrayList<ItemStack>> idToStack = Lists.newArrayList(); //ToDo: Unqualify to List when possible {1.8}
    private static List<ArrayList<ItemStack>> idToStackUn = Lists.newArrayList(); //ToDo: Unqualify to List when possible {1.8}
    private static Map<Integer, List<Integer>> stackToId = Maps.newHashMapWithExpectedSize(96); // Calculated from 128 * 0.75
    public static final ArrayList<ItemStack> EMPTY_LIST = new UnmodifiableArrayList(Lists.newArrayList()); //ToDo: Unqualify to List when possible {1.8}

    /**
     * Minecraft changed from -1 to Short.MAX_VALUE in 1.5 release for the "block wildcard". Use this in case it
     * changes again.
     */
    public static final int WILDCARD_VALUE = Short.MAX_VALUE;

    static {
        initVanillaEntries();
    }

    @SuppressWarnings("unchecked")
    public static void initVanillaEntries()
    {
        if (!hasInit)
        {
            registerOre("logWood",     new ItemStack(Blocks.field_150364_r, 1, WILDCARD_VALUE));
            registerOre("logWood",     new ItemStack(Blocks.field_150363_s, 1, WILDCARD_VALUE));
            registerOre("plankWood",   new ItemStack(Blocks.field_150344_f, 1, WILDCARD_VALUE));
            registerOre("slabWood",    new ItemStack(Blocks.field_150376_bx, 1, WILDCARD_VALUE));
            registerOre("stairWood",   Blocks.field_150476_ad);
            registerOre("stairWood",   Blocks.field_150485_bF);
            registerOre("stairWood",   Blocks.field_150487_bG);
            registerOre("stairWood",   Blocks.field_150481_bH);
            registerOre("stairWood",   Blocks.field_150400_ck);
            registerOre("stairWood",   Blocks.field_150401_cl);
            registerOre("stickWood",   Items.field_151055_y);
            registerOre("treeSapling", new ItemStack(Blocks.field_150345_g, 1, WILDCARD_VALUE));
            registerOre("treeLeaves",  new ItemStack(Blocks.field_150362_t, 1, WILDCARD_VALUE));
            registerOre("treeLeaves",  new ItemStack(Blocks.field_150361_u, 1, WILDCARD_VALUE));
            registerOre("oreGold",     Blocks.field_150352_o);
            registerOre("oreIron",     Blocks.field_150366_p);
            registerOre("oreLapis",    Blocks.field_150369_x);
            registerOre("oreDiamond",  Blocks.field_150482_ag);
            registerOre("oreRedstone", Blocks.field_150450_ax);
            registerOre("oreEmerald",  Blocks.field_150412_bA);
            registerOre("oreQuartz",   Blocks.field_150449_bY);
            registerOre("oreCoal",     Blocks.field_150365_q);
            registerOre("blockGold",     Blocks.field_150340_R);
            registerOre("blockIron",     Blocks.field_150339_S);
            registerOre("blockLapis",    Blocks.field_150368_y);
            registerOre("blockDiamond",  Blocks.field_150484_ah);
            registerOre("blockRedstone", Blocks.field_150451_bX);
            registerOre("blockEmerald",  Blocks.field_150475_bE);
            registerOre("blockQuartz",   Blocks.field_150371_ca);
            registerOre("blockCoal",     Blocks.field_150402_ci);
            registerOre("blockGlassColorless", Blocks.field_150359_w);
            registerOre("blockGlass",    Blocks.field_150359_w);
            registerOre("blockGlass",    new ItemStack(Blocks.field_150399_cn, 1, WILDCARD_VALUE));
            //blockGlass{Color} is added below with dyes
            registerOre("paneGlassColorless", Blocks.field_150410_aZ);
            registerOre("paneGlass",     Blocks.field_150410_aZ);
            registerOre("paneGlass",     new ItemStack(Blocks.field_150397_co, 1, WILDCARD_VALUE));
            //paneGlass{Color} is added below with dyes
            registerOre("ingotIron",     Items.field_151042_j);
            registerOre("ingotGold",     Items.field_151043_k);
            registerOre("ingotBrick",    Items.field_151118_aC);
            registerOre("ingotBrickNether", Items.field_151130_bT);
            registerOre("nuggetGold",  Items.field_151074_bl);
            registerOre("gemDiamond",  Items.field_151045_i);
            registerOre("gemEmerald",  Items.field_151166_bC);
            registerOre("gemQuartz",   Items.field_151128_bU);
            registerOre("dustRedstone",  Items.field_151137_ax);
            registerOre("dustGlowstone", Items.field_151114_aO);
            registerOre("gemLapis",    new ItemStack(Items.field_151100_aR, 1, 4));
            registerOre("slimeball",   Items.field_151123_aH);
            registerOre("glowstone",   Blocks.field_150426_aN);
            registerOre("cropWheat",   Items.field_151015_O);
            registerOre("cropPotato",  Items.field_151174_bG);
            registerOre("cropCarrot",  Items.field_151172_bF);
            registerOre("stone",       Blocks.field_150348_b);
            registerOre("cobblestone", Blocks.field_150347_e);
            registerOre("sandstone",   new ItemStack(Blocks.field_150322_A, 1, WILDCARD_VALUE));
            registerOre("sand",        new ItemStack(Blocks.field_150354_m, 1, WILDCARD_VALUE));
            registerOre("dye",         new ItemStack(Items.field_151100_aR, 1, WILDCARD_VALUE));
            registerOre("record",      Items.field_151096_cd);
            registerOre("record",      Items.field_151093_ce);
            registerOre("record",      Items.field_151094_cf);
            registerOre("record",      Items.field_151091_cg);
            registerOre("record",      Items.field_151092_ch);
            registerOre("record",      Items.field_151089_ci);
            registerOre("record",      Items.field_151090_cj);
            registerOre("record",      Items.field_151087_ck);
            registerOre("record",      Items.field_151088_cl);
            registerOre("record",      Items.field_151085_cm);
            registerOre("record",      Items.field_151086_cn);
            registerOre("record",      Items.field_151084_co);
        }

        // Build our list of items to replace with ore tags
        Map<ItemStack, String> replacements = new HashMap<ItemStack, String>();
        replacements.put(new ItemStack(Items.field_151055_y), "stickWood");
        replacements.put(new ItemStack(Blocks.field_150344_f), "plankWood");
        replacements.put(new ItemStack(Blocks.field_150344_f, 1, WILDCARD_VALUE), "plankWood");
        replacements.put(new ItemStack(Blocks.field_150348_b), "stone");
        replacements.put(new ItemStack(Blocks.field_150348_b, 1, WILDCARD_VALUE), "stone");
        replacements.put(new ItemStack(Blocks.field_150347_e), "cobblestone");
        replacements.put(new ItemStack(Blocks.field_150347_e, 1, WILDCARD_VALUE), "cobblestone");
        replacements.put(new ItemStack(Items.field_151043_k), "ingotGold");
        replacements.put(new ItemStack(Items.field_151042_j), "ingotIron");
        replacements.put(new ItemStack(Items.field_151045_i), "gemDiamond");
        replacements.put(new ItemStack(Items.field_151166_bC), "gemEmerald");
        replacements.put(new ItemStack(Items.field_151137_ax), "dustRedstone");
        replacements.put(new ItemStack(Items.field_151114_aO), "dustGlowstone");
        replacements.put(new ItemStack(Blocks.field_150426_aN), "glowstone");
        replacements.put(new ItemStack(Items.field_151123_aH), "slimeball");
        replacements.put(new ItemStack(Blocks.field_150359_w), "blockGlassColorless");

        // Register dyes
        String[] dyes =
        {
            "Black",
            "Red",
            "Green",
            "Brown",
            "Blue",
            "Purple",
            "Cyan",
            "LightGray",
            "Gray",
            "Pink",
            "Lime",
            "Yellow",
            "LightBlue",
            "Magenta",
            "Orange",
            "White"
        };

        for(int i = 0; i < 16; i++)
        {
            ItemStack dye = new ItemStack(Items.field_151100_aR, 1, i);
            ItemStack block = new ItemStack(Blocks.field_150399_cn, 1, 15 - i);
            ItemStack pane = new ItemStack(Blocks.field_150397_co, 1, 15 - i);
            if (!hasInit)
            {
                registerOre("dye" + dyes[i], dye);
                registerOre("blockGlass" + dyes[i], block);
                registerOre("paneGlass"  + dyes[i], pane);
            }
            replacements.put(dye,   "dye" + dyes[i]);
            replacements.put(block, "blockGlass" + dyes[i]);
            replacements.put(pane,  "paneGlass" + dyes[i]);
        }
        hasInit = true;

        ItemStack[] replaceStacks = replacements.keySet().toArray(new ItemStack[replacements.keySet().size()]);

        // Ignore recipes for the following items
        ItemStack[] exclusions = new ItemStack[]
        {
            new ItemStack(Blocks.field_150368_y),
            new ItemStack(Items.field_151106_aX),
            new ItemStack(Blocks.field_150417_aV),
            new ItemStack(Blocks.field_150333_U, 1, WILDCARD_VALUE),
            new ItemStack(Blocks.field_150446_ar),
            new ItemStack(Blocks.field_150463_bK),
            new ItemStack(Blocks.field_150476_ad),
            new ItemStack(Blocks.field_150485_bF),
            new ItemStack(Blocks.field_150487_bG),
            new ItemStack(Blocks.field_150481_bH),
            new ItemStack(Blocks.field_150400_ck),
            new ItemStack(Blocks.field_150401_cl),
            new ItemStack(Blocks.field_150410_aZ)
        };

        List<IRecipe> recipes = CraftingManager.func_77594_a().func_77592_b();
        List<IRecipe> recipesToRemove = new ArrayList<IRecipe>();
        List<IRecipe> recipesToAdd = new ArrayList<IRecipe>();

        // Search vanilla recipes for recipes to replace
        for(Object obj : recipes)
        {
            if(obj instanceof ShapedRecipes)
            {
                ShapedRecipes recipe = (ShapedRecipes)obj;
                ItemStack output = recipe.func_77571_b();
                if (output != null && containsMatch(false, exclusions, output))
                {
                    continue;
                }

                if(containsMatch(true, recipe.field_77574_d, replaceStacks))
                {
                    recipesToRemove.add(recipe);
                    recipesToAdd.add(new ShapedOreRecipe(recipe, replacements));
                }
            }
            else if(obj instanceof ShapelessRecipes)
            {
                ShapelessRecipes recipe = (ShapelessRecipes)obj;
                ItemStack output = recipe.func_77571_b();
                if (output != null && containsMatch(false, exclusions, output))
                {
                    continue;
                }

                if(containsMatch(true, (ItemStack[])recipe.field_77579_b.toArray(new ItemStack[recipe.field_77579_b.size()]), replaceStacks))
                {
                    recipesToRemove.add((IRecipe)obj);
                    IRecipe newRecipe = new ShapelessOreRecipe(recipe, replacements);
                    recipesToAdd.add(newRecipe);
                }
            }
        }

        recipes.removeAll(recipesToRemove);
        recipes.addAll(recipesToAdd);
        if (recipesToRemove.size() > 0)
        {
            FMLLog.info("Replaced %d ore recipies", recipesToRemove.size());
        }
    }

    /**
     * Gets the integer ID for the specified ore name.
     * If the name does not have a ID it assigns it a new one.
     *
     * @param name The unique name for this ore 'oreIron', 'ingotIron', etc..
     * @return A number representing the ID for this ore type
     */
    public static int getOreID(String name)
    {
        Integer val = nameToId.get(name);
        if (val == null)
        {
            idToName.add(name);
            val = idToName.size() - 1; //0 indexed
            nameToId.put(name, val);
            idToStack.add(new ArrayList<ItemStack>());
            idToStackUn.add(new UnmodifiableArrayList(idToStack.get(val)));
        }
        return val;
    }

    /**
     * Reverse of getOreID, will not create new entries.
     *
     * @param id The ID to translate to a string
     * @return The String name, or "Unknown" if not found.
     */
    public static String getOreName(int id)
    {
        return (id >= 0 && id < idToName.size()) ? idToName.get(id) : "Unknown";
    }

    /**
     * Gets the integer ID for the specified item stack.
     * If the item stack is not linked to any ore, this will return -1 and no new entry will be created.
     *
     * @param stack The item stack of the ore.
     * @return A number representing the ID for this ore type, or -1 if couldn't find it.
     */
    @Deprecated // Use getOreIds below for more accuracy
    public static int getOreID(ItemStack stack)
    {
        if (stack == null || stack.func_77973_b() == null) return -1;

        // HACK: use the registry name's ID. It is unique and it knows about substitutions. Fallback to a -1 value (what Item.getIDForItem would have returned) in the case where the registry is not aware of the item yet
        // IT should be noted that -1 will fail the gate further down, if an entry already exists with value -1 for this name. This is what is broken and being warned about.
        // APPARENTLY it's quite common to do this. OreDictionary should be considered alongside Recipes - you can't make them properly until you've registered with the game.
        String registryName = stack.func_77973_b().delegate.name();
        int id;
        if (registryName == null)
        {
            FMLLog.log(Level.DEBUG, "Attempted to find the oreIDs for an unregistered object (%s). This won't work very well.", stack);
            return -1;
        }
        else
        {
            id = GameData.getItemRegistry().getId(registryName);
        }
        List<Integer> ids = stackToId.get(id); //Try the wildcard first
        if (ids == null || ids.size() == 0)
        {
            ids = stackToId.get(id | ((stack.func_77960_j() + 1) << 16)); // Mow the Meta specific one, +1 so that meta 0 is significant
        }
        return (ids != null && ids.size() > 0) ? ids.get(0) : -1;
    }

    /**
     * Gets all the integer ID for the ores that the specified item stakc is registered to.
     * If the item stack is not linked to any ore, this will return an empty array and no new entry will be created.
     *
     * @param stack The item stack of the ore.
     * @return An array of ids that this ore is registerd as.
     */
    public static int[] getOreIDs(ItemStack stack)
    {
        if (stack == null || stack.func_77973_b() == null) return new int[0];

        Set<Integer> set = new HashSet<Integer>();

        // HACK: use the registry name's ID. It is unique and it knows about substitutions. Fallback to a -1 value (what Item.getIDForItem would have returned) in the case where the registry is not aware of the item yet
        // IT should be noted that -1 will fail the gate further down, if an entry already exists with value -1 for this name. This is what is broken and being warned about.
        // APPARENTLY it's quite common to do this. OreDictionary should be considered alongside Recipes - you can't make them properly until you've registered with the game.
        String registryName = stack.func_77973_b().delegate.name();
        int id;
        if (registryName == null)
        {
            FMLLog.log(Level.DEBUG, "Attempted to find the oreIDs for an unregistered object (%s). This won't work very well.", stack);
            return new int[0];
        }
        else
        {
            id = GameData.getItemRegistry().getId(registryName);
        }
        List<Integer> ids = stackToId.get(id);
        if (ids != null) set.addAll(ids);
        ids = stackToId.get(id | ((stack.func_77960_j() + 1) << 16));
        if (ids != null) set.addAll(ids);

        Integer[] tmp = set.toArray(new Integer[set.size()]);
        int[] ret = new int[tmp.length];
        for (int x = 0; x < tmp.length; x++)
            ret[x] = tmp[x];
        return ret;
    }

    /**
     * Retrieves the ArrayList of items that are registered to this ore type.
     * Creates the list as empty if it did not exist.
     *
     * The returned List is unmodifiable, but will be updated if a new ore
     * is registered using registerOre
     *
     * @param name The ore name, directly calls getOreID
     * @return An arrayList containing ItemStacks registered for this ore
     */
    public static ArrayList<ItemStack> getOres(String name) //TODO: 1.8 ArrayList -> List
    {
        return getOres(getOreID(name));
    }

    /**
     * Retrieves the List of items that are registered to this ore type at this instant.
     * If the flag is TRUE, then it will create the list as empty if it did not exist.
     *
     * This option should be used by modders who are doing blanket scans in postInit.
     * It greatly reduces clutter in the OreDictionary is the responsible and proper
     * way to use the dictionary in a large number of cases.
     *
     * The other function above is utilized in OreRecipe and is required for the
     * operation of that code.
     *
     * @param name The ore name, directly calls getOreID if the flag is TRUE
     * @param alwaysCreateEntry Flag - should a new entry be created if empty
     * @return An arraylist containing ItemStacks registered for this ore
     */
    public static List<ItemStack> getOres(String name, boolean alwaysCreateEntry)
    {
        if (alwaysCreateEntry) {
            return getOres(getOreID(name));
        }
        return nameToId.get(name) != null ? getOres(getOreID(name)) : EMPTY_LIST;
    }

    /**
     * Returns whether or not an oreName exists in the dictionary.
     * This function can be used to safely query the Ore Dictionary without
     * adding needless clutter to the underlying map structure.
     *
     * Please use this when possible and appropriate.
     *
     * @param name The ore name
     * @return Whether or not that name is in the Ore Dictionary.
     */
    public static boolean doesOreNameExist(String name)
    {
        return nameToId.get(name) != null;
    }

    /**
     * Retrieves a list of all unique ore names that are already registered.
     *
     * @return All unique ore names that are currently registered.
     */
    public static String[] getOreNames()
    {
        return idToName.toArray(new String[idToName.size()]);
    }

    /**
     * Retrieves the ArrayList of items that are registered to this ore type.
     * Creates the list as empty if it did not exist.
     *
     * Warning: In 1.8, the return value will become a immutible list,
     * and this function WILL NOT create the entry if the ID doesn't exist,
     * IDs are intended to be internal OreDictionary things and modders
     * should not ever code them in.
     *
     * @param id The ore ID, see getOreID
     * @return An List containing ItemStacks registered for this ore
     */
    @Deprecated // Use the named version not int
    public static ArrayList<ItemStack> getOres(Integer id) //TODO: delete in 1.8 in favor of unboxed version below
    {
       return getOres((int)id.intValue());
    }
    private static ArrayList<ItemStack> getOres(int id) //TODO: change to ImmutibleList<ItemStack> in 1.8, also make private
    {
        while (idToName.size() < id + 1) // TODO: Remove this in 1.8, this is only for backwards compatibility
        {
            String name = "Filler: " + idToName.size();
            idToName.add(name);
            nameToId.put(name, idToName.size() - 1); //0 indexed
            idToStack.add(null);
            idToStackUn.add(EMPTY_LIST);
        }
        return idToStackUn.size() > id ? idToStackUn.get(id) : EMPTY_LIST;
    }

    private static boolean containsMatch(boolean strict, ItemStack[] inputs, ItemStack... targets)
    {
        for (ItemStack input : inputs)
        {
            for (ItemStack target : targets)
            {
                if (itemMatches(target, input, strict))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsMatch(boolean strict, List<ItemStack> inputs, ItemStack... targets)
    {
        for (ItemStack input : inputs)
        {
            for (ItemStack target : targets)
            {
                if (itemMatches(target, input, strict))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean itemMatches(ItemStack target, ItemStack input, boolean strict)
    {
        if (input == null && target != null || input != null && target == null)
        {
            return false;
        }
        return (target.func_77973_b() == input.func_77973_b() && ((target.func_77960_j() == WILDCARD_VALUE && !strict) || target.func_77960_j() == input.func_77960_j()));
    }

    //Convenience functions that make for cleaner code mod side. They all drill down to registerOre(String, int, ItemStack)
    public static void registerOre(String name, Item      ore){ registerOre(name, new ItemStack(ore));  }
    public static void registerOre(String name, Block     ore){ registerOre(name, new ItemStack(ore));  }
    public static void registerOre(String name, ItemStack ore){ registerOreImpl(name, ore);             }
    @Deprecated //Use named, not ID in 1.8+
    public static void registerOre(int    id,   Item      ore){ registerOre(id,   new ItemStack(ore));  }
    @Deprecated //Use named, not ID in 1.8+
    public static void registerOre(int    id,   Block     ore){ registerOre(id,   new ItemStack(ore));  }
    @Deprecated //Use named, not ID in 1.8+
    public static void registerOre(int    id,   ItemStack ore){ registerOreImpl(getOreName(id), ore);   }

    /**
     * Registers a ore item into the dictionary.
     * Raises the registerOre function in all registered handlers.
     *
     * @param name The name of the ore
     * @param id The ID of the ore
     * @param ore The ore's ItemStack
     */
    private static void registerOreImpl(String name, ItemStack ore)
    {
        if (name == null || name.isEmpty() || "Unknown".equals(name)) return; //prevent bad IDs.
        if (ore == null || ore.func_77973_b() == null)
        {
            FMLLog.bigWarning("Invalid registration attempt for an Ore Dictionary item with name %s has occurred. The registration has been denied to prevent crashes. The mod responsible for the registration needs to correct this.", name);
            return; //prevent bad ItemStacks.
        }

        int oreID = getOreID(name);
        // HACK: use the registry name's ID. It is unique and it knows about substitutions. Fallback to a -1 value (what Item.getIDForItem would have returned) in the case where the registry is not aware of the item yet
        // IT should be noted that -1 will fail the gate further down, if an entry already exists with value -1 for this name. This is what is broken and being warned about.
        // APPARENTLY it's quite common to do this. OreDictionary should be considered alongside Recipes - you can't make them properly until you've registered with the game.
        String registryName = ore.func_77973_b().delegate.name();
        int hash;
        if (registryName == null)
        {
            FMLLog.bigWarning("A broken ore dictionary registration with name %s has occurred. It adds an item (type: %s) which is currently unknown to the game registry. This dictionary item can only support a single value when"
                    + " registered with ores like this, and NO I am not going to turn this spam off. Just register your ore dictionary entries after the GameRegistry.\n"
                    + "TO USERS: YES this is a BUG in the mod "+Loader.instance().activeModContainer().getName()+" report it to them!", name, ore.func_77973_b().getClass());
            hash = -1;
        }
        else
        {
            hash = GameData.getItemRegistry().getId(registryName);
        }
        if (ore.func_77960_j() != WILDCARD_VALUE)
        {
            hash |= ((ore.func_77960_j() + 1) << 16); // +1 so 0 is significant
        }

        //Add things to the baked version, and prevent duplicates
        List<Integer> ids = stackToId.get(hash);
        if (ids != null && ids.contains(oreID)) return;
        if (ids == null)
        {
            ids = Lists.newArrayList();
            stackToId.put(hash, ids);
        }
        ids.add(oreID);

        //Add to the unbaked version
        ore = ore.func_77946_l();
        idToStack.get(oreID).add(ore);
        MinecraftForge.EVENT_BUS.post(new OreRegisterEvent(name, ore));
    }

    public static class OreRegisterEvent extends Event
    {
        public final String Name;
        public final ItemStack Ore;

        public OreRegisterEvent(String name, ItemStack ore)
        {
            this.Name = name;
            this.Ore = ore;
        }
    }

    public static void rebakeMap()
    {
        //System.out.println("Baking OreDictionary:");
        stackToId.clear();
        for (int id = 0; id < idToStack.size(); id++)
        {
            List<ItemStack> ores = idToStack.get(id);
            if (ores == null) continue;
            for (ItemStack ore : ores)
            {
                // HACK: use the registry name's ID. It is unique and it knows about substitutions
                String name = ore.func_77973_b().delegate.name();
                int hash;
                if (name == null)
                {
                    FMLLog.log(Level.DEBUG, "Defaulting unregistered ore dictionary entry for ore dictionary %s: type %s to -1", getOreName(id), ore.func_77973_b().getClass());
                    hash = -1;
                }
                else
                {
                    hash = GameData.getItemRegistry().getId(name);
                }
                if (ore.func_77960_j() != WILDCARD_VALUE)
                {
                    hash |= ((ore.func_77960_j() + 1) << 16); // +1 so meta 0 is significant
                }
                List<Integer> ids = stackToId.get(hash);
                if (ids == null)
                {
                    ids = Lists.newArrayList();
                    stackToId.put(hash, ids);
                }
                ids.add(id);
                //System.out.println(id + " " + getOreName(id) + " " + Integer.toHexString(hash) + " " + ore);
            }
        }
    }


    //Pulled from Collections.UnmodifiableList, as we need to explicitly subclass ArrayList for backward compatibility.
    //Delete this class in 1.8 when we loose the ArrayList specific return types.
    private static class UnmodifiableArrayList<E> extends ArrayList<E>
    {
        final ArrayList<? extends E> list;

        UnmodifiableArrayList(ArrayList<? extends E> list)
        {
            super(0);
            this.list = list;
        }

        public ListIterator<E> listIterator() {return listIterator(0);      }
        public boolean  equals(Object o)      { return o == this || list.equals(o); }
        public int      hashCode()            { return list.hashCode();     }
        public E        get(int index)        { return list.get(index);     }
        public int      indexOf(Object o)     { return list.indexOf(o);     }
        public int      lastIndexOf(Object o) { return list.lastIndexOf(o); }
        public int      size()                { return list.size();         }
        public boolean  isEmpty()             { return list.isEmpty();      }
        public boolean  contains(Object o)    { return list.contains(o);    }
        public Object[] toArray()             { return list.toArray();      }
        public <T> T[]  toArray(T[] a)        { return list.toArray(a);     }
        public String   toString()            { return list.toString();     }
        public boolean containsAll(Collection<?> coll) { return list.containsAll(coll); }

        public E set(int index, E element)    { throw new UnsupportedOperationException(); }
        public void add(int index, E element) { throw new UnsupportedOperationException(); }
        public E remove(int index)            { throw new UnsupportedOperationException(); }
        public boolean add(E e)               { throw new UnsupportedOperationException(); }
        public boolean remove(Object o)       { throw new UnsupportedOperationException(); }
        public void clear()                   { throw new UnsupportedOperationException(); }
        public boolean removeAll(Collection<?> coll) { throw new UnsupportedOperationException(); }
        public boolean retainAll(Collection<?> coll) { throw new UnsupportedOperationException(); }
        public boolean addAll(Collection<? extends E> coll) { throw new UnsupportedOperationException(); }
        public boolean addAll(int index, Collection<? extends E> c) { throw new UnsupportedOperationException(); }

        public ListIterator<E> listIterator(final int index)
        {
            return new ListIterator<E>()
            {
                private final ListIterator<? extends E> i = list.listIterator(index);
                public boolean hasNext()     {return i.hasNext();}
                public E next()              {return i.next();}
                public boolean hasPrevious() {return i.hasPrevious();}
                public E previous()          {return i.previous();}
                public int nextIndex()       {return i.nextIndex();}
                public int previousIndex()   {return i.previousIndex();}

                public void remove() { throw new UnsupportedOperationException(); }
                public void set(E e) { throw new UnsupportedOperationException(); }
                public void add(E e) { throw new UnsupportedOperationException(); }
            };
        }

        public List<E> subList(int fromIndex, int toIndex)
        {
            return  Collections.unmodifiableList(list.subList(fromIndex, toIndex));
        }

        public Iterator<E> iterator()
        {
            return new Iterator<E>()
            {
                private final Iterator<? extends E> i = list.iterator();

                public boolean hasNext() { return i.hasNext(); }
                public E next()          { return i.next();    }
                public void remove()     { throw new UnsupportedOperationException(); }
            };
        }
    }
}
