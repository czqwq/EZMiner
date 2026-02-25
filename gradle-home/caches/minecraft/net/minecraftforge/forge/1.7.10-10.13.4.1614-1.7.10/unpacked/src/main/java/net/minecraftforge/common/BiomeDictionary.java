package net.minecraftforge.common;

import java.util.*;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.*;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.terraingen.DeferredBiomeDecorator;
import static net.minecraft.world.biome.BiomeGenBase.*;
import static net.minecraftforge.common.BiomeDictionary.Type.*;

public class BiomeDictionary
{
    public enum Type
    {
        /*Temperature-based tags. Specifying neither implies a biome is temperate*/
        HOT,
        COLD,
        /*Tags specifying the amount of vegetation a biome has. Specifying neither implies a biome to have moderate amounts*/
        SPARSE,
        DENSE,
        /*Tags specifying how moist a biome is. Specifying neither implies the biome as having moderate humidity*/
        WET,
        DRY,
        /*Tree-based tags, SAVANNA refers to dry, desert-like trees (Such as Acacia), CONIFEROUS refers to snowy trees (Such as Spruce) and JUNGLE refers to jungle trees.
         * Specifying no tag implies a biome has temperate trees (Such as Oak)*/
        SAVANNA,
        CONIFEROUS,
        JUNGLE,

        /*Tags specifying the nature of a biome*/
        SPOOKY,
        DEAD,
        LUSH,
        NETHER,
        END,
        MUSHROOM,
        MAGICAL,

        OCEAN,
        RIVER,
        /**A general tag for all water-based biomes. Shown as present if OCEAN or RIVER are.**/
        WATER(OCEAN, RIVER),

        /*Generic types which a biome can be*/
        MESA,
        FOREST,
        PLAINS,
        MOUNTAIN,
        HILLS,
        SWAMP,
        SANDY,
        SNOWY,
        WASTELAND,
        BEACH,

        /*Deprecated tags, kept for compatibility*/
        @Deprecated
        /**Replaced by SANDY**/
        DESERT(SANDY),
        @Deprecated
        /**Replaced by SNOWY**/
        FROZEN(SNOWY);

        private List<Type> subTags;

        private Type(Type... subTags)
        {
            this.subTags = Arrays.asList(subTags);
        }

        private boolean hasSubTags()
        {
            return subTags != null && !subTags.isEmpty();
        }

        /**
         * Retrieves a Type value by name,
         * if one does not exist already it creates one.
         * This can be used as interm measure for modders to
         * add there own category of Biome.
         *
         * There are NO naming conventions besides:
         *   MUST be all upper case (enforced by name.toUpper())
         *   NO Special characters. {Unenforced, just don't be a pain, if it becomes a issue I WILL
         *                             make this RTE with no worry about backwards compatibility}
         *
         * Note: For performance sake, the return value of this function SHOULD be cached.
         * Two calls with the same name SHOULD return the same value.
         *
         *
         * @param name The name of this Type
         * @return An instance of Type for this name.
         */
        public static Type getType(String name, Type... subTypes)
        {
            name = name.toUpperCase();
            for (Type t : values())
            {
                if (t.name().equals(name))
                    return t;
            }
            Type ret = EnumHelper.addEnum(Type.class, name, new Class[]{Type[].class}, new Object[]{subTypes});
            if (ret.ordinal() >= typeInfoList.length)
            {
                typeInfoList = Arrays.copyOf(typeInfoList, ret.ordinal());
            }
            return ret;
        }
    }

    private static final int BIOME_LIST_SIZE = BiomeGenBase.func_150565_n().length;
    private static BiomeInfo[] biomeList = new BiomeInfo[BIOME_LIST_SIZE];
    @SuppressWarnings("unchecked")
    private static ArrayList<BiomeGenBase>[] typeInfoList = new ArrayList[Type.values().length];

    private static class BiomeInfo
    {
        public EnumSet<Type> typeList;

        public BiomeInfo(Type[] types)
        {
            typeList = EnumSet.noneOf(Type.class);
            for(Type t : types)
            {
                typeList.add(t);
            }
        }
    }

    static
    {
        registerVanillaBiomes();
    }

    /**
     * Registers a biome with a specific biome type
     *
     * @param biome the biome to be registered
     * @param type the type to register the biome as
     * @return returns true if the biome was registered successfully
     */
    public static boolean registerBiomeType(BiomeGenBase biome, Type ... types)
    {
        types = listSubTags(types);

        if(BiomeGenBase.func_150565_n()[biome.field_76756_M] != null)
        {
            for(Type type : types)
            {
                if(typeInfoList[type.ordinal()] == null)
                {
                    typeInfoList[type.ordinal()] = new ArrayList<BiomeGenBase>();
                }

                typeInfoList[type.ordinal()].add(biome);
            }

            if(biomeList[biome.field_76756_M] == null)
            {
                biomeList[biome.field_76756_M] = new BiomeInfo(types);
            }
            else
            {
                for(Type type : types)
                {
                    biomeList[biome.field_76756_M].typeList.add(type);
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Returns a list of biomes registered with a specific type
     *
     * @param type the Type to look for
     * @return a list of biomes of the specified type, null if there are none
     */
    public static BiomeGenBase[] getBiomesForType(Type type)
    {
        if(typeInfoList[type.ordinal()] != null)
        {
            return (BiomeGenBase[])typeInfoList[type.ordinal()].toArray(new BiomeGenBase[0]);
        }

        return new BiomeGenBase[0];
    }

    /**
     * Gets a list of Types that a specific biome is registered with
     *
     * @param biome the biome to check
     * @return the list of types, null if there are none
     */
    public static Type[] getTypesForBiome(BiomeGenBase biome)
    {
        checkRegistration(biome);

        if(biomeList[biome.field_76756_M] != null)
        {
            return (Type[])biomeList[biome.field_76756_M].typeList.toArray(new Type[0]);
        }

        return new Type[0];
    }

    /**
     * Checks to see if two biomes are registered as having the same type
     *
     * @param biomeA
     * @param biomeB
     * @return returns true if a common type is found, false otherwise
     */
    public static boolean areBiomesEquivalent(BiomeGenBase biomeA, BiomeGenBase biomeB)
    {
        int a = biomeA.field_76756_M;
        int b = biomeB.field_76756_M;

        checkRegistration(biomeA);
        checkRegistration(biomeB);

        if(biomeList[a] != null && biomeList[b] != null)
        {
            for(Type type : biomeList[a].typeList)
            {
                if(containsType(biomeList[b], type))
                {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks to see if the given biome is registered as being a specific type
     *
     * @param biome the biome to be considered
     * @param type the type to check for
     * @return returns true if the biome is registered as being of type type, false otherwise
     */
    public static boolean isBiomeOfType(BiomeGenBase biome, Type type)
    {
        checkRegistration(biome);

        if(biomeList[biome.field_76756_M] != null)
        {
            return containsType(biomeList[biome.field_76756_M], type);
        }

        return false;
    }

    /**
     * Checks to see if the given biome has been registered as being of any type
     * @param biome the biome to consider
     * @return returns true if the biome has been registered, false otherwise
     */
    public static boolean isBiomeRegistered(BiomeGenBase biome)
    {
        return biomeList[biome.field_76756_M] != null;
    }

    public static boolean isBiomeRegistered(int biomeID)
    {
        return biomeList[biomeID] != null;
    }

    public static void registerAllBiomes()
    {
        FMLLog.warning("Redundant call to BiomeDictionary.registerAllBiomes ignored");
    }
    /**
     * Loops through the biome list and automatically adds tags to any biome that does not have any
     * This is called by Forge at postinit time. It will additionally dispatch any deferred decorator
     * creation events.
     *
     * DO NOT call this during world generation
     */
    public static void registerAllBiomesAndGenerateEvents()
    {
        for(int i = 0; i < BiomeGenBase.func_150565_n().length; i++)
        {
            BiomeGenBase biome = BiomeGenBase.func_150565_n()[i];

            if(biome == null)
            {
                continue;
            }

            if (biome.field_76760_I instanceof DeferredBiomeDecorator)
            {
                DeferredBiomeDecorator decorator = (DeferredBiomeDecorator) biome.field_76760_I;
                decorator.fireCreateEventAndReplace(biome);
            }

            checkRegistration(biome);
        }
    }

    /**
     * Automatically looks for and registers a given biome with appropriate tags
     * This method is called automatically if a biome has not been registered with any tags,
     * And another method requests information about it
     *
     * @param biome the biome to be considered
     */
    public static void makeBestGuess(BiomeGenBase biome)
    {
        if (biome.field_76760_I.field_76832_z >= 3)
        {
            if (biome.func_76736_e() && biome.field_76750_F >= 0.9F)
            {
                BiomeDictionary.registerBiomeType(biome, JUNGLE);
            }
            else if (!biome.func_76736_e())
            {
                BiomeDictionary.registerBiomeType(biome, FOREST);

                if (biome.field_76750_F <= 0.2f)
                {
                    BiomeDictionary.registerBiomeType(biome, CONIFEROUS);
                }
            }
        }
        else if(biome.field_76749_E <= 0.3F && biome.field_76749_E >= 0.0F)
        {
            if(!biome.func_76736_e() || biome.field_76748_D >= 0.0F)
            {
                BiomeDictionary.registerBiomeType(biome, PLAINS);
            }
        }

        if (biome.field_76751_G > 0.85f)
        {
            BiomeDictionary.registerBiomeType(biome, WET);
        }

        if (biome.field_76751_G < 0.15f)
        {
            BiomeDictionary.registerBiomeType(biome, DRY);
        }

        if (biome.field_76750_F > 0.85f)
        {
            BiomeDictionary.registerBiomeType(biome, HOT);
        }

        if (biome.field_76750_F < 0.15f)
        {
            BiomeDictionary.registerBiomeType(biome, COLD);
        }

        if (biome.field_76760_I.field_76832_z > 0 && biome.field_76760_I.field_76832_z < 3)
        {
            BiomeDictionary.registerBiomeType(biome, SPARSE);
        }
        else if (biome.field_76760_I.field_76832_z >= 10)
        {
            BiomeDictionary.registerBiomeType(biome, DENSE);
        }

        if (biome.func_76736_e() && biome.field_76748_D < 0.0F && (biome.field_76749_E <= 0.3F && biome.field_76749_E >= 0.0F))
        {
            BiomeDictionary.registerBiomeType(biome, SWAMP);
        }

        if (biome.field_76748_D <= -0.5F)
        {
            if (biome.field_76749_E == 0.0F)
            {
                BiomeDictionary.registerBiomeType(biome, RIVER);
            }
            else
            {
                BiomeDictionary.registerBiomeType(biome, OCEAN);
            }
        }

        if (biome.field_76749_E >= 0.4F && biome.field_76749_E < 1.5F)
        {
            BiomeDictionary.registerBiomeType(biome, HILLS);
        }

        if (biome.field_76749_E >= 1.5F)
        {
            BiomeDictionary.registerBiomeType(biome, MOUNTAIN);
        }

        if (biome.func_76746_c())
        {
            BiomeDictionary.registerBiomeType(biome, SNOWY);
        }

        if (biome.field_76752_A != Blocks.field_150354_m && biome.field_76750_F >= 1.0f && biome.field_76751_G < 0.2f)
        {
            BiomeDictionary.registerBiomeType(biome, SAVANNA);
        }

        if (biome.field_76752_A == Blocks.field_150354_m )
        {
            BiomeDictionary.registerBiomeType(biome, SANDY);
        }
        else if (biome.field_76752_A == Blocks.field_150405_ch)
        {
            BiomeDictionary.registerBiomeType(biome, MESA);
        }
        else if (biome.field_76752_A == Blocks.field_150391_bh)
        {
            BiomeDictionary.registerBiomeType(biome, MUSHROOM);
        }
    }

    //Internal implementation
    private static void checkRegistration(BiomeGenBase biome)
    {
        if(!isBiomeRegistered(biome))
        {
            makeBestGuess(biome);
        }
    }

    private static boolean containsType(BiomeInfo info, Type type)
    {
        if (type.hasSubTags())
        {
            for (Type remappedType : listSubTags(type))
            {
                if (info.typeList.contains(remappedType)) return true;
            }

            return false;
        }

        return info.typeList.contains(type);
    }

    private static Type[] listSubTags(Type... types)
    {
        List<Type> subTags = new ArrayList<Type>();

        for (Type type : types)
        {
            if (type.hasSubTags()) subTags.addAll(type.subTags);
            else subTags.add(type);
        }

        return subTags.toArray(new Type[subTags.size()]);
    }

    private static void registerVanillaBiomes()
    {
        registerBiomeType(field_76771_b,               OCEAN                                        );
        registerBiomeType(field_76772_c,              PLAINS                                       );
        registerBiomeType(field_76769_d,              HOT,      DRY,        SANDY                  );
        registerBiomeType(field_76770_e,        MOUNTAIN, HILLS                              );
        registerBiomeType(field_76767_f,              FOREST                                       );
        registerBiomeType(field_76768_g,               COLD,     CONIFEROUS, FOREST                 );
        registerBiomeType(field_76784_u,          COLD,     CONIFEROUS, FOREST,   HILLS        );
        registerBiomeType(field_76780_h,           WET,      SWAMP                              );
        registerBiomeType(field_76781_i,               RIVER                                        );
        registerBiomeType(field_76776_l,         COLD,     OCEAN,      SNOWY                  );
        registerBiomeType(field_76777_m,         COLD,     RIVER,      SNOWY                  );
        registerBiomeType(field_76774_n,           COLD,     SNOWY,      WASTELAND              );
        registerBiomeType(field_76775_o,        COLD,     SNOWY,      MOUNTAIN               );
        registerBiomeType(field_76787_r,               BEACH                                        );
        registerBiomeType(field_76786_s,         HOT,      DRY,        SANDY,    HILLS        );
        registerBiomeType(field_76782_w,              HOT,      WET,        DENSE,    JUNGLE       );
        registerBiomeType(field_76792_x,         HOT,      WET,        DENSE,    JUNGLE, HILLS);
        registerBiomeType(field_76785_t,         FOREST,   HILLS                              );
        registerBiomeType(field_76779_k,                 COLD,     DRY,        END                    );
        registerBiomeType(field_76778_j,                HOT,      DRY,        NETHER                 );
        registerBiomeType(field_76789_p,      MUSHROOM                                     );
        registerBiomeType(field_76783_v,    MOUNTAIN                                     );
        registerBiomeType(field_76788_q, MUSHROOM, BEACH                              );
        registerBiomeType(field_150574_L,          HOT,      WET,        JUNGLE,   FOREST       );
        registerBiomeType(field_150575_M,           OCEAN                                        );
        registerBiomeType(field_150576_N,          BEACH                                        );
        registerBiomeType(field_150577_O,           COLD,     BEACH,      SNOWY                  );
        registerBiomeType(field_150583_P,         FOREST                                       );
        registerBiomeType(field_150582_Q,    FOREST,   HILLS                              );
        registerBiomeType(field_150585_R,        SPOOKY,   DENSE,      FOREST                 );
        registerBiomeType(field_150584_S,           COLD,     CONIFEROUS, FOREST,   SNOWY        );
        registerBiomeType(field_150579_T,      COLD,     CONIFEROUS, FOREST,   SNOWY,  HILLS);
        registerBiomeType(field_150578_U,           COLD,     CONIFEROUS, FOREST                 );
        registerBiomeType(field_150581_V,      COLD,     CONIFEROUS, FOREST,   HILLS        );
        registerBiomeType(field_150580_W,    MOUNTAIN, FOREST,     SPARSE                 );
        registerBiomeType(field_150588_X,             HOT,      SAVANNA,    PLAINS,   SPARSE       );
        registerBiomeType(field_150587_Y,      HOT,      SAVANNA,    PLAINS,   SPARSE       );
        registerBiomeType(field_150589_Z,                MESA,     SANDY                              );
        registerBiomeType(field_150607_aa,       MESA,     SPARSE,     SANDY                  );
        registerBiomeType(field_150608_ab,         MESA,     SANDY                              );
    }
}
