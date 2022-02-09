package ru.violence.worldcleaner;

import net.minecraft.server.v1_12_R1.BiomeBase;
import net.minecraft.server.v1_12_R1.WorldGenBigTree;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import ru.violence.worldcleaner.util.Utils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class TempWorld {
    public final static String TEMP_WORLD_SUFFIX = "_WorldCleaner_Temp";
    private static final Map<World, TempWorld> realToTempWorldMap = new HashMap<>(1);

    private final World realWorld;
    private final World tempWorld;
    private final WorldListener listener = new WorldListener();

    private boolean isWorldUnloaded = false;

    private TempWorld(World realWorld, World tempWorld) {
        this.realWorld = realWorld;
        this.tempWorld = tempWorld;
        Bukkit.getPluginManager().registerEvents(this.listener, WorldCleanerPlugin.getInstance());
    }

    public static void terminate(WorldCleanerPlugin plugin) {
        for (TempWorld tempWorld : realToTempWorldMap.values()) {
            tempWorld.deleteWorld();
        }
        realToTempWorldMap.clear();
    }

    public static TempWorld get(World realWorld) {
        return realToTempWorldMap.get(realWorld);
    }

    public static TempWorld create(World realWorld) throws TempWorldCreateException {
        if (!Utils.isLoaded(realWorld)) {
            throw new TempWorldCreateException(TempWorldCreateException.Reason.REAL_WORLD_NOT_LOADED);
        }
        if (realToTempWorldMap.get(realWorld) != null) {
            throw new TempWorldCreateException(TempWorldCreateException.Reason.ALREADY_EXISTS);
        }

        World world = Bukkit.getWorld(realWorld + TempWorld.TEMP_WORLD_SUFFIX);
        if (world == null) {
            WorldCreator copy = new WorldCreator(realWorld.getName() + TempWorld.TEMP_WORLD_SUFFIX).copy(realWorld);
            copy.type(realWorld.getWorldType());
            copy.generateStructures(realWorld.canGenerateStructures());
            world = Bukkit.createWorld(copy);
            world.setKeepSpawnInMemory(false);
            world.setDifficulty(realWorld.getDifficulty());
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doEntityDrops", "false");
            world.setGameRuleValue("doFireTick", "false");
            world.setGameRuleValue("doMobLoot", "false");
            world.setGameRuleValue("maxEntityCramming", "0");
            world.setGameRuleValue("mobGriefing", "false");
            world.setGameRuleValue("doWeatherCycle", "false");
            world.setGameRuleValue("randomTickSpeed", "0");
            world.setGameRuleValue("spawnRadius", "0");
        }

        TempWorld tempWorld = new TempWorld(realWorld, world);
        realToTempWorldMap.put(realWorld, tempWorld);

        return tempWorld;
    }

    public World getWorld() throws TempWorldNotLoadedException {
        if (this.isWorldUnloaded) throw TempWorldNotLoadedException.INSTANCE;
        return this.tempWorld;
    }

    public void deleteWorld() {
        unregisterListener();
        Bukkit.unloadWorld(this.tempWorld, false);
        Utils.deleteWorldFolder(this.tempWorld.getName());
        realToTempWorldMap.remove(this.realWorld);

        // Let the GC delete the world from memory (MC-128547)
        try {
            Field f1 = BiomeBase.class.getDeclaredField("n");
            f1.setAccessible(true);
            WorldGenBigTree genBigTree = (WorldGenBigTree) f1.get(BiomeBase.class);
            Field f2 = WorldGenBigTree.class.getDeclaredField("l");
            f2.setAccessible(true);
            if (f2.get(genBigTree) == ((CraftWorld) this.tempWorld).getHandle()) {
                f2.set(genBigTree, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unregisterListener() {
        HandlerList.unregisterAll(this.listener);
    }

    public static boolean isTempWorld(World world) {
        return world != null && world.getName().endsWith(TEMP_WORLD_SUFFIX);
    }

    public class WorldListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onWorldUnload(WorldUnloadEvent event) {
            // If the temp world is unloading
            try {
                if (getWorld().equals(event.getWorld())) {
                    TempWorld.this.isWorldUnloaded = true;
                    unregisterListener();
                    deleteWorld();
                    return;
                }
            } catch (TempWorldNotLoadedException ignored) {
                return;
            }

            // If the real world is unloading
            if (event.getWorld().equals(TempWorld.this.realWorld)) {
                WorldCleanerPlugin.getInstance().getLogger().warning("World " + TempWorld.this.realWorld.getName() + " was unloaded while it's cleaning!");
                deleteWorld();
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onEntityTeleport(EntityTeleportEvent event) {
            if (TempWorld.this.tempWorld == event.getTo().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onEntityTeleport(PlayerTeleportEvent event) {
            if (TempWorld.this.tempWorld == event.getTo().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockFadeInTemp(BlockFadeEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockFormInTemp(BlockFormEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockGrowInTemp(BlockGrowEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockIgniteInTemp(BlockIgniteEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockBurnInTemp(BlockBurnEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onBlockSpreadInTemp(BlockSpreadEvent event) {
            if (TempWorld.this.tempWorld == event.getBlock().getWorld()) {
                event.setCancelled(true);
            }
        }
    }
}
