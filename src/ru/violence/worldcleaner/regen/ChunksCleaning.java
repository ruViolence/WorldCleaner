package ru.violence.worldcleaner.regen;

import com.sk89q.worldguard.bukkit.WGBukkit;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.minecraft.server.v1_12_R1.IBlockData;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftChunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import ru.violence.worldcleaner.RegenChunk;
import ru.violence.worldcleaner.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChunksCleaning extends TickTimeLimitedIndexIteratorTask {
    private final RegenRunnable runnable;
    // Working variables
    private int currentSection = 15;
    private RegenChunk regenChunk;
    private boolean isBiomesRestored = false;
    private List<ProtectedRegion> regions;
    private ChunkSnapshot tempChunkSnap;
    private int minXOfChunk;
    private int minZOfChunk;

    public ChunksCleaning(RegenRunnable runnable, long maxNspt) {
        super(maxNspt);
        this.runnable = runnable;
        prepareForNextChunk(0);
    }

    private void prepareForNextChunk(int nextIndex) {
        if (nextIndex >= this.runnable.getRegenChunks().size()) return;
        this.regenChunk = this.runnable.getRegenChunks().get(nextIndex);
        this.isBiomesRestored = false;
        ApplicableRegionSet set = WGBukkit.getRegionManager(this.runnable.getRealWorld()).getApplicableRegions(Utils.getChunkWGRegion(this.regenChunk.x, this.regenChunk.z));
        this.regions = set.size() != 0 ? new ArrayList<>(set.getRegions()) : Collections.emptyList();
        this.tempChunkSnap = this.runnable.getTempWorld().getChunkAt(this.regenChunk.x, this.regenChunk.z).getChunkSnapshot(false, false, false);
        this.minXOfChunk = this.regenChunk.x << 4;
        this.minZOfChunk = this.regenChunk.z << 4;
    }

    @Override
    boolean doIter(int currentIndex) {
        List<ProtectedRegion> regions = this.regions;
        ChunkSnapshot tempChunkSnap = this.tempChunkSnap;

        Chunk tempChunk = this.runnable.getTempWorld().getChunkAt(this.regenChunk.x, this.regenChunk.z);
        Chunk realChunk = this.runnable.getRealWorld().getChunkAt(this.regenChunk.x, this.regenChunk.z);
        net.minecraft.server.v1_12_R1.Chunk nmsTempChunk = ((CraftChunk) tempChunk).getHandle();
        net.minecraft.server.v1_12_R1.Chunk nmsRealChunk = ((CraftChunk) realChunk).getHandle();

        // Biomes
        if (!this.isBiomesRestored) {
            for (int xInChunk = 0; xInChunk < 16; xInChunk++) {
                for (int zInChunk = 0; zInChunk < 16; zInChunk++) {
                    final int x = xInChunk + this.minXOfChunk;
                    final int z = zInChunk + this.minZOfChunk;
                    if (!Utils.isInAnyRegion(x, z, regions)) {
                        this.runnable.getRealWorld().setBiome(x, z, this.runnable.getTempWorld().getBiome(x, z));
                    }
                }
            }
            this.isBiomesRestored = true;
        }

        cleaning:
        {
            // Skip if real and temp chunks have no sections at current index
            if (nmsTempChunk.getSections()[this.currentSection] == null && nmsRealChunk.getSections()[this.currentSection] == null) {
                // At least entities can be inside the empty sections
                break cleaning;
            }

            int minYOfSection = this.currentSection << 4;

            // Order is important
            for (int yInSection = 15; yInSection >= 0; yInSection--) {
                for (int xInSection = 0; xInSection < 16; xInSection++) {
                    for (int zInSection = 0; zInSection < 16; zInSection++) {
                        final int x = xInSection + this.minXOfChunk;
                        final int y = yInSection + minYOfSection;
                        final int z = zInSection + this.minZOfChunk;

                        // Skip the protected coords
                        if (Utils.isInAnyRegion(x, y, z, regions)) continue;

                        restoreBlock(tempChunk.getWorld(), realChunk, xInSection, zInSection, x, y, z, tempChunkSnap);
                    }
                }
            }

            evacuatePlayers(realChunk, this.currentSection);
        }

        --this.currentSection;
        if (this.currentSection < 0) {
            restoreEntities(realChunk, tempChunk, regions);
            this.currentSection = 15;
            prepareForNextChunk(currentIndex + 1);
            tempChunk.unload(false);
            return true;
        }
        return false;
    }

    @Override
    public int getTotal() {
        return this.runnable.getRegenChunks().size();
    }

    @Override
    public String getTaskName() {
        return "Cleaning chunks";
    }

    @SuppressWarnings("deprecation")
    private void restoreBlock(World tempWorld, Chunk realChunk, int xInChunk, int zInChunk, int x, int y, int z, ChunkSnapshot tempChunkSnap) {
        int tempBlockId = tempChunkSnap.getBlockTypeId(xInChunk, y, zInChunk);
        byte tempBlockData = (byte) tempChunkSnap.getBlockData(xInChunk, y, zInChunk);

        // Remove CHECK_DECAY from the leaves
        if (tempBlockId == 18 || tempBlockId == 161) {
            if (8 <= tempBlockData && tempBlockData <= 11) {
                tempBlockData -= 8;
            }
        }

        { // Skip the same blocks that already set
            boolean isSimpleBlockWithoutData = isSimpleBlockWithoutData(tempBlockId);
            boolean isSimpleBlockWithData = isSimpleBlockWithData(tempBlockId);
            if (isSimpleBlockWithoutData || isSimpleBlockWithData) {
                IBlockData realIBlockData = ((CraftChunk) realChunk).getHandle().getBlockData(x, y, z);
                net.minecraft.server.v1_12_R1.Block realBlockNMS = realIBlockData.getBlock();
                int realBlockId = net.minecraft.server.v1_12_R1.Block.getId(realBlockNMS);

                if (tempBlockId == realBlockId) {
                    if (isSimpleBlockWithoutData) {
                        return;
                    } else if (tempBlockData == realBlockNMS.toLegacyData(realIBlockData)) {
                        return;
                    }
                }
            }
        }

        // Copy the block
        Utils.setTypeIdAndDataSilent((CraftChunk) realChunk, x, y, z, tempBlockId, tempBlockData);
        Utils.copyBlockDataNMS(tempWorld, this.runnable.getRealWorld(), x, y, z);
    }

    private static void restoreEntities(Chunk realChunk, Chunk tempChunk, List<ProtectedRegion> regions) {
        // Remove entities that are not standing in any region
        for (Entity entity : realChunk.getEntities()) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();
            if (Utils.isInAnyRegion(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), regions)) continue;

            entity.remove();
        }

        // Move entities from temp to the real world, that is not standing in any region
        for (Entity entity : tempChunk.getEntities()) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();
            if (Utils.isInAnyRegion(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), regions)) continue;

            entity.teleport(new Location(
                    realChunk.getWorld(),
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    loc.getYaw(),
                    loc.getPitch())
            );
        }
    }

    private void evacuatePlayers(Chunk realChunk, int ySection) {
        for (Entity entity : realChunk.getEntities()) {
            if (!(entity instanceof Player)) continue;
            Player player = (Player) entity;
            if (player.getGameMode() == GameMode.SPECTATOR) continue;

            Location loc = entity.getLocation();
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();

            if (ySection != y >> 4) continue;

            World world = entity.getWorld();
            if (world.getBlockAt(x, y, z).getType().isSolid() || world.getBlockAt(x, y + 1, z).getType().isSolid()) {
                Utils.pushUpToSafe(player);
            }
        }
    }

    private static boolean isSimpleBlockWithoutData(int id) {
        // @formatter:off
        return id == 0    // Air
            || id == 4    // Cobblestone
            || id == 7    // Bedrock
            || id == 12   // Sand
            || id == 13   // Gravel
            || id == 14   // Gold ore
            || id == 15   // Iron ore
            || id == 16   // Coal ore
            || id == 21   // Lapis ore
            || id == 37   // Yellow Flower
            || id == 39   // Brown Mushroom
            || id == 40   // Red Mushroom
            || id == 48   // Moss Stone
            || id == 56   // Diamond ore
            || id == 73   // Redstone Ore
            || id == 82   // Clay
            || id == 83   // Sugar Canes
            || id == 129; // Emerald Ore
        // @formatter:on
    }

    private static boolean isSimpleBlockWithData(int id) {
        // @formatter:off
        return id == 1    // Stone
            || id == 2    // Grass
            || id == 3    // Dirt
            || id == 8    // Flowing Water
            || id == 9    // Still Water
            || id == 10   // Flowing Lava
            || id == 11   // Still Lava
            || id == 17   // Wood
            || id == 18   // Leaves
            || id == 24   // Sandstone
            || id == 31   // Tallgrass
            || id == 38   // Red Flower
            || id == 97   // Monster Egg
            || id == 159  // Hardened Clay
            || id == 161  // Leaves-2
            || id == 162  // Wood-2
            || id == 175; // Double Plant
        // @formatter:on
    }
}
