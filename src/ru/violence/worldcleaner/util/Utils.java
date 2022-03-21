package ru.violence.worldcleaner.util;

import com.bergerkiller.bukkit.common.wrappers.LongHashSet;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import lombok.experimental.UtilityClass;
import net.minecraft.server.v1_12_R1.BiomeBase;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.BlockTileEntity;
import net.minecraft.server.v1_12_R1.Blocks;
import net.minecraft.server.v1_12_R1.ChunkSection;
import net.minecraft.server.v1_12_R1.IBlockData;
import net.minecraft.server.v1_12_R1.ITileEntity;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import net.minecraft.server.v1_12_R1.TileEntity;
import net.minecraft.server.v1_12_R1.WorldGenBigTree;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import ru.violence.worldcleaner.RegenChunk;
import ru.violence.worldcleaner.WorldCleanerPlugin;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@UtilityClass
public class Utils {
    private final BlockPosition.MutableBlockPosition mutableBlockPos = new BlockPosition.MutableBlockPosition();

    public ProtectedCuboidRegion getChunkWGRegion(int x, int z) {
        int bx = x << 4;
        int bz = z << 4;
        BlockVector pt1 = new BlockVector(bx, 0, bz);
        BlockVector pt2 = new BlockVector(bx + 15, 255, bz + 15);

        return new ProtectedCuboidRegion("TMP", pt1, pt2);
    }

    public void copyBlockDataNMS(World fromWorld, World toWorld, int x, int y, int z) {
        BlockPosition pos = mutableBlockPos.c(x, y, z);
        TileEntity fromTileEntity = ((CraftWorld) fromWorld).getHandle().getTileEntity(pos);
        if (fromTileEntity == null) return;

        NBTTagCompound nbt = new NBTTagCompound();
        fromTileEntity.save(nbt);

        TileEntity realTileEntity = ((CraftWorld) toWorld).getHandle().getTileEntity(pos);
        if (realTileEntity != null) {
            realTileEntity.load(nbt);
        } else {
            WorldCleanerPlugin.getInstance().getLogger().warning("Tile entity does not exists in the real world on pos: " + pos + ", while it's exists in the temp world: " + fromTileEntity);
        }
    }

    public boolean isLoaded(World world) {
        return world != null && Bukkit.getWorld(world.getUID()) != null;
    }

    public List<RegenChunk> radiusChunksReversed(int middleX, int middleZ, int radius, World tempWorld, World realWorld) {
        List<RegenChunk> list = new ArrayList<>((radius + 1) * (4 * radius) + 1);
        for (int z = radius; z >= -radius; --z) {
            for (int x = radius; x >= -radius; --x) {
                list.add(new RegenChunk(tempWorld, realWorld, middleX + x, middleZ + z));
            }
        }
        return list;
    }

    public List<IntPair> radiusChunksCoordsReversed(int middleX, int middleZ, int radius) {
        List<IntPair> list = new ArrayList<>((radius + 1) * (4 * radius) + 1);
        for (int z = radius; z >= -radius; --z) {
            for (int x = radius; x >= -radius; --x) {
                list.add(new IntPair(middleX + x, middleZ + z));
            }
        }
        return list;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public boolean isInAnyRegion(int x, int z, List<ProtectedRegion> regions) {
        BlockVector2D pos = new BlockVector2D(x, z);
        for (int i = 0; i < regions.size(); i++) {
            ProtectedRegion region = regions.get(i);
            if (region.getMinimumPoint().getBlockY() == 0
                    && region.getMaximumPoint().getBlockY() == 255
                    && region.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public boolean isInAnyRegion(int x, int y, int z, List<ProtectedRegion> regions) {
        Vector pos = new Vector(x, y, z);
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public int parseInt(String str, int def) {
        try {
            def = Integer.parseInt(str);
        } catch (NumberFormatException ignored) {}
        return def;
    }

    @SuppressWarnings("ManualMinMaxCalculation")
    public int clamp(int input, int min, int max) {
        if (input < min) {
            return min;
        } else {
            return input > max ? max : input;
        }
    }

    public void pushUpToSafe(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        ChunkSnapshot chunkSnapshot = loc.getChunk().getChunkSnapshot(false, false, false);
        int xInChunk = loc.getBlockX() & 15;
        int zInChunk = loc.getBlockZ() & 15;
        for (int checkY = 255; checkY > 0; --checkY) {
            if (chunkSnapshot.getBlockType(xInChunk, checkY, zInChunk).isSolid()) {
                player.teleport(new Location(world, loc.getBlockX() + 0.5, checkY + 1, loc.getBlockZ() + 0.5, loc.getYaw(), loc.getPitch()));
                return;
            }
        }
    }

    public void deleteWorldFolder(String worldName) {
        try {
            FileUtils.deleteDirectory(new File(Bukkit.getWorldContainer(), worldName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("deprecation")
    public boolean setTypeIdAndDataSilent(CraftChunk chunk, int x, int y, int z, int type, byte data) {
        net.minecraft.server.v1_12_R1.Chunk nmsChunk = chunk.getHandle();
        IBlockData blockData = CraftMagicNumbers.getBlock(type).fromLegacyData(data);
        BlockPosition position = mutableBlockPos.c(x, y, z);

        // SPIGOT-611: need to do this to prevent glitchiness. Easier to handle this here (like /setblock) than to fix weirdness in tile entity cleanup
        if (type != 0 && blockData.getBlock() instanceof BlockTileEntity && type != CraftMagicNumbers.getId(nmsChunk.getBlockData(x, y, z).getBlock())) {
            setTypeAndDataSilent(nmsChunk.getWorld(), position, Blocks.AIR.getBlockData(), 0);
        }

        IBlockData old = nmsChunk.getBlockData(x, y, z); // NOTIFY | NO_OBSERVER
        boolean success = setTypeAndDataSilent(nmsChunk.getWorld(), position, blockData, 18);

        if (success) {
            nmsChunk.getWorld().notify(position, old, blockData, 3);
        }

        return success;
    }

    @SuppressWarnings("deprecation")
    private boolean setTypeAndDataSilent(net.minecraft.server.v1_12_R1.World nmsWorld, BlockPosition blockposition, IBlockData iblockdata, int flags) {
        int x = blockposition.getX();
        int y = blockposition.getY();
        int z = blockposition.getZ();

        List<BlockState> capturedBlockStates = nmsWorld.capturedBlockStates;

        if (nmsWorld.captureTreeGeneration) {
            BlockState blockstate = null;
            Iterator<BlockState> it = capturedBlockStates.iterator();
            while (it.hasNext()) {
                BlockState previous = it.next();
                if (previous.getX() == x && previous.getY() == y && previous.getZ() == z) {
                    blockstate = previous;
                    it.remove();
                    break;
                }
            }
            if (blockstate == null) {
                blockstate = org.bukkit.craftbukkit.v1_12_R1.block.CraftBlockState.getBlockState(nmsWorld, x, y, z, flags);
            }
            blockstate.setTypeId(CraftMagicNumbers.getId(iblockdata.getBlock()));
            blockstate.setRawData((byte) iblockdata.getBlock().toLegacyData(iblockdata));
            capturedBlockStates.add(blockstate);
            return true;
        }

        if (blockposition.isInvalidYLocation()) {
            return false;
        }

        net.minecraft.server.v1_12_R1.Chunk chunk = nmsWorld.getChunkAtWorldCoords(x, z);

        BlockState blockstate = null;
        if (nmsWorld.captureBlockStates) {
            blockstate = nmsWorld.getWorld().getBlockAt(x, y, z).getState();
            capturedBlockStates.add(blockstate);
        }

        IBlockData iblockdata1 = setBlockStateSilent(chunk, blockposition, iblockdata);

        if (iblockdata1 == null) {
            if (nmsWorld.captureBlockStates) {
                capturedBlockStates.remove(blockstate);
            }
            return false;
        }

        if (!nmsWorld.captureBlockStates) {
            nmsWorld.notifyAndUpdatePhysics(blockposition, chunk, iblockdata1, iblockdata, flags);
        }

        return true;
    }

    @Nullable
    private IBlockData setBlockStateSilent(net.minecraft.server.v1_12_R1.Chunk nmsChunk, BlockPosition blockposition, IBlockData iblockdata) {
        int i = blockposition.getX() & 15;
        int j = blockposition.getY();
        int k = blockposition.getZ() & 15;

        IBlockData iblockdata1 = nmsChunk.getBlockData(blockposition.getX(), blockposition.getY(), blockposition.getZ());

        if (iblockdata1 == iblockdata) {
            return null;
        }

        int l = k << 4 | i;
        int[] precipitationHeightMap = nmsChunk.getPrecipitationHeightMap();
        if (j >= precipitationHeightMap[l] - 1) {
            precipitationHeightMap[l] = -999;
        }

        Block block = iblockdata.getBlock();
        Block block1 = iblockdata1.getBlock();
        ChunkSection chunksection = nmsChunk.getSections()[j >> 4];

        net.minecraft.server.v1_12_R1.World world = nmsChunk.world;
        if (chunksection == null) {
            if (block == Blocks.AIR) {
                return null;
            }

            chunksection = new ChunkSection(j >> 4 << 4, world.worldProvider.m());
            nmsChunk.getSections()[j >> 4] = chunksection;
        }

        chunksection.setType(i, j & 15, k, iblockdata);

        if (block1 instanceof ITileEntity) {
            world.s(blockposition);
        }

        /* It's useless!
        if (chunksection.getType(i, j & 15, k).getBlock() != block) {
            return null;
        }
        */

        if (block1 instanceof ITileEntity) {
            TileEntity tileentity = nmsChunk.a(blockposition, net.minecraft.server.v1_12_R1.Chunk.EnumTileEntityState.CHECK);
            if (tileentity != null) {
                tileentity.invalidateBlockCache();
            }
        }

        if (block instanceof ITileEntity) {
            TileEntity tileentity = nmsChunk.a(blockposition, net.minecraft.server.v1_12_R1.Chunk.EnumTileEntityState.CHECK);
            if (tileentity == null) {
                tileentity = ((ITileEntity) block).a(world, block.toLegacyData(iblockdata));
                world.setTileEntity(blockposition, tileentity);
            }

            if (tileentity != null) {
                tileentity.invalidateBlockCache();
            }
        }

        nmsChunk.markDirty();
        return iblockdata1;
    }

    public LongHashSet intPairsToLongHashSet(Collection<IntPair> intPairs) {
        LongHashSet hashset = new LongHashSet(intPairs.size());

        for (IntPair pair : intPairs) {
            hashset.add(pair.left, pair.right);
        }

        return hashset;
    }

    public void fixMC128547(World world) {
        // Let the GC delete the world from memory (MC-128547)
        try {
            Field f1 = BiomeBase.class.getDeclaredField("n");
            f1.setAccessible(true);
            WorldGenBigTree genBigTree = (WorldGenBigTree) f1.get(BiomeBase.class);
            Field f2 = WorldGenBigTree.class.getDeclaredField("l");
            f2.setAccessible(true);
            if (f2.get(genBigTree) == ((CraftWorld) world).getHandle()) {
                f2.set(genBigTree, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isExcludedRootFile(File file) {
        String name = file.getName();
        switch (name) {
            case "session.lock":
            case "uid.dat":
            case "advancements":
            case "playerdata":
            case "stats":
            case "region":
            case "level.dat_old":
                return true;
        }
        return name.startsWith("DIM");
    }

    public boolean isExcludedDataFile(File file) {
        String name = file.getName();
        switch (name) {
            case "advancements":
            case "functions":
            case "idcounts.dat":
            case "scoreboard.dat":
                return true;
        }
        return name.startsWith("map_") && name.endsWith(".dat");
    }
}
