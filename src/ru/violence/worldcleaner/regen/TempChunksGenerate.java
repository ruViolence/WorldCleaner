package ru.violence.worldcleaner.regen;

import org.bukkit.World;
import ru.violence.worldcleaner.util.IntPair;
import ru.violence.worldcleaner.util.Utils;

public class TempChunksGenerate extends TickTimeLimitedIndexIteratorTask {
    private final RegenRunnable runnable;

    public TempChunksGenerate(RegenRunnable runnable, long maxNspt) {
        super(maxNspt);
        this.runnable = runnable;
    }

    @Override
    boolean doIter(int currentIndex) {
        IntPair pair = this.runnable.getChunkToGenerate().get(currentIndex);
        World world = this.runnable.getTempWorld();
        // Keep near chunks loaded to avoid the chunk populator bugs
        for (IntPair p : Utils.radiusChunksCoordsReversed(pair.left, pair.right, 1)) {
            world.loadChunk(p.left, p.right, false);
        }
        // Generate
        world.getChunkAt(pair.left, pair.right);
        return true;
    }

    @Override
    public int getTotal() {
        return this.runnable.getChunkToGenerate().size();
    }

    @Override
    public String getTaskName() {
        return "Generating temporary chunks";
    }
}
