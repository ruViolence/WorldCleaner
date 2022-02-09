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
        // If the last iteration
        if (currentIndex + 1 == getTotal()) {
            this.runnable.getChunkToGenerate().sort((a, b) -> {
                if (a.equals(b)) {
                    return 0;
                }

                // Subtract current position to set center point
                int ax = a.left - runnable.getMiddleX();
                int az = a.right - runnable.getMiddleZ();
                int bx = b.left - runnable.getMiddleX();
                int bz = b.right - runnable.getMiddleZ();

                int result = ((ax - bx) * (ax + bx)) + ((az - bz) * (az + bz));
                if (result != 0) {
                    return result;
                }

                if (ax < 0) {
                    if (bx < 0) {
                        return bz - az;
                    } else {
                        return -1;
                    }
                } else {
                    if (bx < 0) {
                        return 1;
                    } else {
                        return az - bz;
                    }
                }
            });
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
