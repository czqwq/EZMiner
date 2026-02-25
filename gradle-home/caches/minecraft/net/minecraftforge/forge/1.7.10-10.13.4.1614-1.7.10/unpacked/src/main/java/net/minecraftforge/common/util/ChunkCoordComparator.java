package net.minecraftforge.common.util;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkCoordIntPair;

// Sorter to load nearby chunks first
public class ChunkCoordComparator implements java.util.Comparator<ChunkCoordIntPair>
{
    private int x;
    private int z;

    public ChunkCoordComparator(EntityPlayerMP entityplayer)
    {
        x = (int) entityplayer.field_70165_t >> 4;
        z = (int) entityplayer.field_70161_v >> 4;
    }

    public int compare(ChunkCoordIntPair a, ChunkCoordIntPair b)
    {
        if (a.equals(b))
        {
            return 0;
        }

        // Subtract current position to set center point
        int ax = a.field_77276_a - this.x;
        int az = a.field_77275_b - this.z;
        int bx = b.field_77276_a - this.x;
        int bz = b.field_77275_b - this.z;
        int result = ((ax - bx) * (ax + bx)) + ((az - bz) * (az + bz));

        if (result != 0)
        {
            return result;
        }

        if (ax < 0)
        {
            if (bx < 0)
            {
                return bz - az;
            }
            else
            {
                return -1;
            }
        }
        else
        {
            if (bx < 0)
            {
                return 1;
            }
            else
            {
                return az - bz;
            }
        }
    }
}