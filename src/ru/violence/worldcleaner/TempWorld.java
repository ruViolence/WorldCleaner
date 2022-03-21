package ru.violence.worldcleaner;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
import org.jetbrains.annotations.NotNull;
import ru.violence.worldcleaner.util.Utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TempWorld {
    public final static String TEMP_WORLD_SUFFIX = "_WorldCleaner_Temp";
    private static final Map<UUID, TempWorld> realToTempWorldMap = new HashMap<>(1);

    private final World realWorld;
    private final World tempWorld;
    private final WorldListener listener = new WorldListener();

    private boolean isWorldUnloaded = false;

    private TempWorld(@NotNull World realWorld, @NotNull World tempWorld) {
        this.realWorld = Preconditions.checkNotNull(realWorld);
        this.tempWorld = Preconditions.checkNotNull(tempWorld);
        Bukkit.getPluginManager().registerEvents(this.listener, WorldCleanerPlugin.getInstance());
    }

    public static void terminate(WorldCleanerPlugin plugin) {
        for (TempWorld tempWorld : realToTempWorldMap.values()) {
            tempWorld.deleteWorld();
        }
        realToTempWorldMap.clear();
    }

    public static TempWorld get(World realWorld) {
        return realToTempWorldMap.get(realWorld.getUID());
    }

    public static TempWorld create(World realWorld) throws TempWorldCreateException {
        if (!Utils.isLoaded(realWorld)) {
            throw new TempWorldCreateException(TempWorldCreateException.Reason.REAL_WORLD_NOT_LOADED);
        }
        if (realToTempWorldMap.get(realWorld.getUID()) != null) {
            throw new TempWorldCreateException(TempWorldCreateException.Reason.ALREADY_EXISTS);
        }

        String tempWorldName = realWorld.getName() + TempWorld.TEMP_WORLD_SUFFIX;
        File realWorldFolder = new File(Bukkit.getWorldContainer(), realWorld.getName());
        File tempWorldFolder = new File(Bukkit.getWorldContainer(), tempWorldName);

        World world = Bukkit.getWorld(tempWorldName);
        if (world == null) {
            world = createNewTempWorld(realWorld, realWorldFolder, tempWorldFolder);
        }

        TempWorld tempWorld = new TempWorld(realWorldFolder, tempWorldFolder, realWorld, world);
        realToTempWorldMap.put(realWorld.getUID(), tempWorld);

        return tempWorld;
    }

    private static @NotNull World createNewTempWorld(@NotNull World realWorld, @NotNull File realWorldFolder, @NotNull File tempWorldFolder) {
        realWorld.save(); // Save to copy up-to-date data below

        try { // Copy PersistentStructure and other data
            if (tempWorldFolder.exists()) {
                FileUtils.deleteDirectory(tempWorldFolder);
            }

            tempWorldFolder.mkdirs();

            for (File file : realWorldFolder.listFiles()) {
                if (Utils.isExcludedRootFile(file)) continue;

                if (file.isDirectory()) {
                    FileUtils.copyDirectory(file, new File(tempWorldFolder, file.getName()), pathname -> {
                        if (!file.getName().equals("data") || !pathname.getParentFile().equals(file)) return true;
                        return !Utils.isExcludedDataFile(pathname);
                    });
                } else {
                    FileUtils.copyFile(file, new File(tempWorldFolder, file.getName()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        WorldCreator copy = new WorldCreator(tempWorldFolder.getName()).copy(realWorld);
        copy.type(realWorld.getWorldType());
        copy.generateStructures(realWorld.canGenerateStructures());

        World world = Bukkit.createWorld(copy);

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

        return world;
    }

    public @NotNull World getWorld() throws TempWorldNotLoadedException {
        if (this.isWorldUnloaded) throw TempWorldNotLoadedException.INSTANCE;
        return this.tempWorld;
    }

    public void deleteWorld() {
        unregisterListener();
        Bukkit.unloadWorld(this.tempWorld, false);
        Utils.deleteWorldFolder(this.tempWorld.getName());
        realToTempWorldMap.remove(this.realWorld.getUID());

        Utils.fixMC128547(tempWorld);
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
