package ru.violence.worldcleaner.regen;

import ru.violence.worldcleaner.RegenChunk;
import ru.violence.worldcleaner.util.IntPair;
import ru.violence.worldcleaner.util.Utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrepareChunkToGenerateList extends TickTimeLimitedIndexIteratorTask {
    private final RegenRunnable runnable;
    private final int genPad;
    private final Set<IntPair> uniqueChunks = new HashSet<>();

    public PrepareChunkToGenerateList(RegenRunnable runnable, long maxNspt, int genPad) {
        super(maxNspt);
        this.runnable = runnable;
        this.genPad = genPad;
    }

    @Override
    boolean doIter(int currentIndex) {
        RegenChunk regenChunk = this.runnable.getRegenChunks().get(currentIndex);
        List<IntPair> paddedChunks = Utils.radiusChunksCoordsReversed(regenChunk.x, regenChunk.z, this.genPad);
        for (IntPair chunkCoord : paddedChunks) {
            if (this.uniqueChunks.add(chunkCoord)) {
                this.runnable.getChunkToGenerate().add(chunkCoord);
            }
        }
        return true;
    }

    @Override
    public int getTotal() {
        return this.runnable.getRegenChunks().size();
    }

    @Override
    public String getTaskName() {
        return "Prepare chunks to generate list";
    }
}
