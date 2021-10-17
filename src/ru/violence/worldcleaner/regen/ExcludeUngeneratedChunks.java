package ru.violence.worldcleaner.regen;

import ru.violence.worldcleaner.RegenChunk;

import java.util.List;

public class ExcludeUngeneratedChunks extends TickTimeLimitedIndexIteratorTask {
    private final RegenRunnable runnable;
    private final int initTotal;
    private int cursor;

    public ExcludeUngeneratedChunks(RegenRunnable runnable, long maxNspt) {
        super(maxNspt);
        this.runnable = runnable;
        this.initTotal = runnable.getRegenChunks().size();
        this.cursor = this.initTotal;
    }

    @Override
    boolean doIter(int currentIndex) {
        if (--this.cursor < 0) return true;

        List<RegenChunk> radiusChunks = this.runnable.getRegenChunks();
        RegenChunk regenChunk = radiusChunks.get(this.cursor);

        if (!this.runnable.getRealWorld().isChunkGenerated(regenChunk.x, regenChunk.z)) {
            radiusChunks.remove(this.cursor);
        }

        return true;
    }

    @Override
    public int getTotal() {
        return this.initTotal;
    }

    @Override
    public String getTaskName() {
        return "Excluding ungenerated chunks";
    }
}
