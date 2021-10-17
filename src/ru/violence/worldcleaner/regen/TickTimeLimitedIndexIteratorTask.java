package ru.violence.worldcleaner.regen;

import lombok.Getter;
import net.minecraft.server.v1_12_R1.MinecraftServer;

public abstract class TickTimeLimitedIndexIteratorTask implements RegenTask {
    private final long maxNspt;
    private @Getter int currentIndex = 0;
    private long selfLastTime = 0;
    private final int[] speedArray = new int[5 * 20];
    private int speedCursor = 0;

    protected TickTimeLimitedIndexIteratorTask(long maxNspt) {
        this.maxNspt = maxNspt;
    }

    @Override
    public final boolean run() {
        long endTime = calcEndTime();
        long startTime = System.nanoTime();
        int done = 0;

        do {
            if (this.currentIndex >= getTotal()) {
                return true;
            }

            if (doIter(this.currentIndex)) {
                ++this.currentIndex;
                ++done;
            }
        } while (System.nanoTime() < endTime);

        if (this.speedCursor >= this.speedArray.length) this.speedCursor = 0;
        this.speedArray[this.speedCursor++] = done;
        this.selfLastTime = (endTime - startTime);

        return false;
    }

    abstract boolean doIter(int currentIndex);

    private long calcEndTime() {
        MinecraftServer server = MinecraftServer.getServer();

        long freeLastTickTime = this.maxNspt - (server.h[(server.aq() - 1) % 100] - this.selfLastTime - 5);
        return freeLastTickTime > 0 ? System.nanoTime() + freeLastTickTime : System.nanoTime();
    }

    public int getDone() {
        return this.currentIndex;
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    public double getSpeed() {
        int[] speedArray = this.speedArray;
        long l = 0;
        int length = speedArray.length;
        for (int i = 0; i < length; i++) {
            l += speedArray[i];
        }
        return (l * 20) / (double) length;
    }
}
