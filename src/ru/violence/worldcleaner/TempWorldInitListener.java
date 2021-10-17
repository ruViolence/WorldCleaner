package ru.violence.worldcleaner;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

public class TempWorldInitListener implements Listener {
    @EventHandler
    public void onTempWorldInit(WorldInitEvent event) {
        World world = event.getWorld();
        if (world.getKeepSpawnInMemory() && TempWorld.isTempWorld(world)) {
            world.setKeepSpawnInMemory(false);
        }
    }
}
