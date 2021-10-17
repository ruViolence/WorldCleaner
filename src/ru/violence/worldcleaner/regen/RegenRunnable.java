package ru.violence.worldcleaner.regen;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import ru.violence.worldcleaner.RegenChunk;
import ru.violence.worldcleaner.TempWorld;
import ru.violence.worldcleaner.TempWorldNotLoadedException;
import ru.violence.worldcleaner.WorldCleanerPlugin;
import ru.violence.worldcleaner.util.IntPair;
import ru.violence.worldcleaner.util.Utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegenRunnable extends BukkitRunnable {
    private final UUID playerUniqueId;
    private final @Getter World realWorld;
    private final @Getter World tempWorld;
    private final @Getter TempWorld tempWorldObj;
    private final @Getter int middleX;
    private final @Getter int middleZ;
    private final @Getter int radius;
    private final @Getter List<RegenChunk> regenChunks;
    private final @Getter List<IntPair> chunkToGenerate;

    private final DecimalFormat percentageFormat = new DecimalFormat("#0.00");
    private final DecimalFormat speedFormat = new DecimalFormat("#0.0");
    private final List<RegenTask> tasks = new ArrayList<>();
    private int step = 0;
    private boolean cancelled = false;

    public RegenRunnable(Player player, World realWorld, TempWorld tempWorldObj, int middleX, int middleZ, int radius, int maxMspt, int genPad) throws TempWorldNotLoadedException {
        this.playerUniqueId = player.getUniqueId();
        this.realWorld = realWorld;
        this.tempWorldObj = tempWorldObj;
        this.tempWorld = tempWorldObj.getWorld();
        this.middleX = middleX;
        this.middleZ = middleZ;
        this.radius = radius;
        this.regenChunks = Utils.radiusChunksReversed(middleX, middleZ, radius, tempWorldObj.getWorld(), realWorld);
        this.chunkToGenerate = new ArrayList<>();
        long maxNspt = maxMspt * 1_000_000L;
        this.tasks.add(new ExcludeUngeneratedChunks(this, maxNspt));
        this.tasks.add(new PrepareChunkToGenerateList(this, maxNspt, genPad));
        this.tasks.add(new TempChunksGenerate(this, maxNspt));
        this.tasks.add(new ChunksCleaning(this, maxNspt));
        this.tasks.add(new ScheduleLightClean(this));
    }

    @Override
    public void run() {
        if (this.cancelled || this.step >= this.tasks.size()) {
            cancel();
            sendInfo("Done");
            this.tempWorldObj.deleteWorld();
            WorldCleanerPlugin.getInstance().getWorkingPlayers().remove(this.playerUniqueId);
            return;
        }

        try {
            RegenTask task = this.tasks.get(this.step);

            boolean isTaskDone = task.run();

            int done = task.getDone();
            int total = task.getTotal();
            sendInfo("§e" + task.getTaskName() + " [" + this.percentageFormat.format(done * 100.0f / total) + "%] [" + done + "/" + total + "] [" + this.speedFormat.format(calcSpeed()) + " per sec]");

            if (isTaskDone) {
                ++this.step;
            }
        } catch (Throwable e) {
            e.printStackTrace();
            sendInfo("§cERROR! SEE THE CONSOLE");
            cancel();
            this.tempWorldObj.deleteWorld();
            WorldCleanerPlugin.getInstance().getWorkingPlayers().remove(this.playerUniqueId);
        }
    }

    private double calcSpeed() {
        RegenTask task = this.tasks.get(this.step);
        if (task instanceof TickTimeLimitedIndexIteratorTask) {
            return ((TickTimeLimitedIndexIteratorTask) task).getSpeed();
        }
        return 0;
    }

    private void sendInfo(String message) {
        Player player = Bukkit.getPlayer(this.playerUniqueId);
        if (player != null) {
            player.sendActionBar(message);
        }
    }

    public void cancelRegen() {
        this.cancelled = true;
    }
}
