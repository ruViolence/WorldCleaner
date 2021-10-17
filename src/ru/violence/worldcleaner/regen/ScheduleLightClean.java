package ru.violence.worldcleaner.regen;

import com.bergerkiller.bukkit.lightcleaner.lighting.LightingService;
import ru.violence.worldcleaner.util.Utils;

public class ScheduleLightClean implements RegenTask {
    private final RegenRunnable runnable;

    public ScheduleLightClean(RegenRunnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public boolean run() {
        LightingService.ScheduleArguments args = new LightingService.ScheduleArguments();
        args.setWorld(this.runnable.getRealWorld());
        args.setChunks(Utils.intPairsToLongHashSet(this.runnable.getChunkToGenerate()));
        LightingService.schedule(args);
        return true;
    }

    @Override
    public int getDone() {
        return 0;
    }

    @Override
    public int getTotal() {
        return 1;
    }

    @Override
    public String getTaskName() {
        return "Schedule light cleaning";
    }
}
