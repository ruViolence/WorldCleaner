package ru.violence.worldcleaner;

import com.google.common.base.Preconditions;
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
import org.jetbrains.annotations.Nullable;
import ru.violence.worldcleaner.regen.RegenRunnable;
import ru.violence.worldcleaner.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TempWorld {
    public final static String TEMP_WORLD_SUFFIX = "_WorldCleaner_Temp";
    private static final File CACHED_TEMP_WORLDS = new File(WorldCleanerPlugin.getInstance().getDataFolder(), "worlds");
    private static final long WORLD_LIFETIME = 5 * 60 * 1000; // 5 Minutes 
    private static final Map<UUID, TempWorld> realToTempWorldMap = new HashMap<>(1);

    private final File realWorldFolder;
    private final File tempWorldFolder;
    private final World realWorld;
    private final World tempWorld;
    private final WorldListener listener = new WorldListener();

    private long lastUsage;
    private boolean isWorldUnloaded = false;

    private TempWorld(@NotNull File realWorldFolder, @NotNull File tempWorldFolder, @NotNull World realWorld, @NotNull World tempWorld) {
        this.realWorldFolder = Preconditions.checkNotNull(realWorldFolder);
        this.tempWorldFolder = Preconditions.checkNotNull(tempWorldFolder);
        this.realWorld = Preconditions.checkNotNull(realWorld);
        this.tempWorld = Preconditions.checkNotNull(tempWorld);
        Bukkit.getPluginManager().registerEvents(this.listener, WorldCleanerPlugin.getInstance());
        updateLastUsage();
    }

    public static void terminate(WorldCleanerPlugin plugin) {
        for (TempWorld tempWorld : realToTempWorldMap.values().toArray(new TempWorld[0])) {
            tempWorld.unloadWorld();
        }
        realToTempWorldMap.clear();
    }

    public static TempWorld get(World realWorld) {
        return realToTempWorldMap.get(realWorld.getUID());
    }

    public static @NotNull TempWorld @NotNull [] getAll() {
        return realToTempWorldMap.values().toArray(new TempWorld[0]);
    }

    public static TempWorld getOrCreate(World realWorld) throws TempWorldCreateException {
        if (!Utils.isLoaded(realWorld)) {
            throw new TempWorldCreateException(TempWorldCreateException.Reason.REAL_WORLD_NOT_LOADED);
        }

        TempWorld existed = realToTempWorldMap.get(realWorld.getUID());
        if (existed != null) {
            existed.updateLastUsage();
            return existed;
        }

        String tempWorldName = realWorld.getName() + TempWorld.TEMP_WORLD_SUFFIX;
        File realWorldFolder = new File(Bukkit.getWorldContainer(), realWorld.getName());
        File tempWorldFolder = new File(Bukkit.getWorldContainer(), tempWorldName);

        World world = Bukkit.getWorld(tempWorldName);
        tempWorldCreation:
        {
            if (world == null) {
                File cachedTempWorldFolder = getCachedWorldFolder(tempWorldName);
                if (cachedTempWorldFolder != null) {
                    try {
                        Files.move(cachedTempWorldFolder.toPath(), Bukkit.getWorldContainer().toPath().resolve(cachedTempWorldFolder.getName()));
                        world = Bukkit.createWorld(WorldCreator.name(tempWorldName).environment(realWorld.getEnvironment()));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break tempWorldCreation;
                }
            }
            world = createNewTempWorld(realWorld, realWorldFolder, tempWorldFolder);
        }

        TempWorld tempWorld = new TempWorld(realWorldFolder, tempWorldFolder, realWorld, world);
        realToTempWorldMap.put(realWorld.getUID(), tempWorld);

        return tempWorld;
    }

    public static @Nullable File getCachedWorldFolder(@NotNull String worldName) {
        CACHED_TEMP_WORLDS.mkdirs();
        for (File file : CACHED_TEMP_WORLDS.listFiles()) {
            if (file.getName().equals(worldName)) {
                return file;
            }
        }
        return null;
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
        copy.environment(realWorld.getEnvironment());

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

    public @NotNull World getRealWorld() {
        return this.realWorld;
    }

    public @NotNull World getTempWorld() throws TempWorldNotLoadedException {
        if (this.isWorldUnloaded) throw TempWorldNotLoadedException.INSTANCE;
        return this.tempWorld;
    }

    public void unloadWorld() {
        unregisterListener();
        Bukkit.unloadWorld(this.tempWorld, true);
        try {
            Files.move(tempWorldFolder.toPath(), CACHED_TEMP_WORLDS.toPath().resolve(tempWorldFolder.getName()));
        } catch (IOException e) {
            e.printStackTrace();
            Utils.deleteWorldFolder(this.tempWorld.getName());
        }
        realToTempWorldMap.remove(this.realWorld.getUID());

        Utils.fixMC128547(tempWorld);
    }

    public boolean isCanBeUnloaded() {
        return RegenRunnable.get(realWorld) == null && lastUsage + WORLD_LIFETIME < System.currentTimeMillis();
    }

    public long getLastUsage() {
        return this.lastUsage;
    }

    public void updateLastUsage() {
        this.lastUsage = System.currentTimeMillis();
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
                if (getTempWorld().equals(event.getWorld())) {
                    TempWorld.this.isWorldUnloaded = true;
                    unregisterListener();
                    unloadWorld();
                    return;
                }
            } catch (TempWorldNotLoadedException ignored) {
                return;
            }

            // If the real world is unloading
            if (event.getWorld().equals(TempWorld.this.realWorld)) {
                WorldCleanerPlugin.getInstance().getLogger().warning("World " + TempWorld.this.realWorld.getName() + " was unloaded while it's cleaning!");
                unloadWorld();
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
