package ru.violence.worldcleaner.regen;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.violence.worldcleaner.RegenChunk;
import ru.violence.worldcleaner.TempWorld;
import ru.violence.worldcleaner.TempWorldNotLoadedException;
import ru.violence.worldcleaner.WorldCleanerPlugin;
import ru.violence.worldcleaner.util.IntPair;
import ru.violence.worldcleaner.util.Utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RegenRunnable extends BukkitRunnable {
    private static final Map<UUID, RegenRunnable> RUNNING_TASKS = new HashMap<>();
    private final UUID playerUniqueId;
    private final @Getter World realWorld;
    private final @Getter World tempWorld;
    private final @Getter TempWorld tempWorldObj;
    private final @Getter int middleX;
    private final @Getter int middleZ;
    private final @Getter int radius;
    private final @Getter List<RegenChunk> regenChunks;
    private final @Getter List<IntPair> chunkToGenerate;
    private final @NotNull BossBar bossBar;

    private final DecimalFormat speedFormat = new DecimalFormat("#0.0");
    private final List<RegenTask> tasks = new ArrayList<>();
    private int step = 0;

    private RegenRunnable(Player player, TempWorld tempWorldObj, int middleX, int middleZ, int radius, int maxMspt, int genPad) throws TempWorldNotLoadedException {
        this.playerUniqueId = player.getUniqueId();
        this.realWorld = tempWorldObj.getRealWorld();
        this.tempWorldObj = tempWorldObj;
        this.tempWorld = tempWorldObj.getTempWorld();
        this.middleX = middleX;
        this.middleZ = middleZ;
        this.radius = radius;
        this.regenChunks = Utils.radiusChunksReversed(middleX, middleZ, radius, tempWorld, realWorld);
        this.chunkToGenerate = new ArrayList<>();
        long maxNspt = maxMspt * 1_000_000L;
        this.tasks.add(new ExcludeUngeneratedChunks(this, maxNspt));
        this.tasks.add(new PrepareChunkToGenerateList(this, maxNspt, genPad));
        this.tasks.add(new TempChunksGenerate(this, maxNspt));
        this.tasks.add(new ChunksCleaning(this, maxNspt));
        this.tasks.add(new ScheduleLightClean(this));
        this.bossBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        this.bossBar.addPlayer(player);
    }

    public static @NotNull RegenRunnable start(Player player, TempWorld tempWorld, int middleX, int middleZ, int radius, int maxMspt, int genPad) throws TempWorldNotLoadedException {
        RegenRunnable runnable = new RegenRunnable(player, tempWorld, middleX, middleZ, radius, maxMspt, genPad);
        RUNNING_TASKS.put(player.getUniqueId(), runnable);
        runnable.runTaskTimer(WorldCleanerPlugin.getInstance(), 0, 0);
        return runnable;
    }

    public static @Nullable RegenRunnable get(@NotNull Player player) {
        return RUNNING_TASKS.get(player.getUniqueId());
    }

    public static @Nullable RegenRunnable get(@NotNull World realWorld) {
        Collection<RegenRunnable> values = RUNNING_TASKS.values();
        for (RegenRunnable runnable : values) {
            if (runnable.getRealWorld().equals(realWorld)) {
                return runnable;
            }
        }
        return null;
    }

    public static @NotNull List<RegenRunnable> getRunningTasks() {
        return new ArrayList<>(RUNNING_TASKS.values());
    }

    @Override
    public void run() {
        if (this.step >= this.tasks.size()) {
            cancelRegen();
            return;
        }

        try {
            RegenTask task = this.tasks.get(this.step);

            boolean isTaskDone = task.run();

            int done = task.getDone();
            int total = task.getTotal();
            updateBossBar("§e" + task.getTaskName() + " [" + done + "/" + total + "] [" + this.speedFormat.format(calcSpeed()) + " per sec]", (double) done / total);

            if (isTaskDone) {
                ++this.step;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            sendInfo("§cERROR! SEE THE CONSOLE");
            cancel();
            this.tempWorldObj.updateLastUsage();
            this.bossBar.removeAll();
            RUNNING_TASKS.remove(this.playerUniqueId);
        }
    }

    private double calcSpeed() {
        RegenTask task = this.tasks.get(this.step);
        if (task instanceof TickTimeLimitedIndexIteratorTask) {
            return ((TickTimeLimitedIndexIteratorTask) task).getSpeed();
        }
        return 0;
    }

    private void updateBossBar(String title, double progress) {
        Player player = Bukkit.getPlayer(playerUniqueId);
        if (player == null) return;

        if (!bossBar.getPlayers().contains(player)) {
            bossBar.removeAll();
            bossBar.addPlayer(player);
        }

        if (!bossBar.getTitle().equals(title)) bossBar.setTitle(title);
        bossBar.setProgress(progress);
    }

    private void sendInfo(String message) {
        Player player = Bukkit.getPlayer(this.playerUniqueId);
        if (player != null) {
            player.sendActionBar(message);
        }
    }

    public void cancelRegen() {
        super.cancel();
        sendInfo("Done");
        this.bossBar.removeAll();
        this.tempWorldObj.updateLastUsage();
        RUNNING_TASKS.remove(this.playerUniqueId);
    }
}
