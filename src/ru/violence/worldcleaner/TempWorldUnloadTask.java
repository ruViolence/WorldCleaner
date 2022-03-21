package ru.violence.worldcleaner;

import org.bukkit.scheduler.BukkitRunnable;

public class TempWorldUnloadTask extends BukkitRunnable {
    @Override
    public void run() {
        for (TempWorld tempWorld : TempWorld.getAll()) {
            if (tempWorld.isCanBeUnloaded()) {
                tempWorld.unloadWorld();
            }
        }
    }
}
