package ru.violence.worldcleaner.regen;

import com.bergerkiller.bukkit.lightcleaner.lighting.LightingService;
import ru.violence.worldcleaner.util.IntPair;
import ru.violence.worldcleaner.util.Utils;

import java.util.stream.Collectors;

public class ScheduleLightClean implements RegenTask {
    private final RegenRunnable runnable;

    public ScheduleLightClean(RegenRunnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public boolean run() {
        LightingService.ScheduleArguments args = new LightingService.ScheduleArguments();
        args.setWorld(this.runnable.getRealWorld());
        args.setChunks(Utils.intPairsToLongHashSet(this.runnable.getRegenChunks()
                .stream()
                .map(rc -> new IntPair(rc.x, rc.z))
                .collect(Collectors.toList())));
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
