package ru.violence.worldcleaner;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.violence.worldcleaner.regen.RegenRunnable;
import ru.violence.worldcleaner.util.Utils;

public class WorldCleanerPlugin extends JavaPlugin implements Listener {
    private static @Getter WorldCleanerPlugin instance;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        for (String tempWorldFolderName : Validate.notNull(Bukkit.getWorldContainer().list((dir, name) -> dir.isDirectory() && name.endsWith(TempWorld.TEMP_WORLD_SUFFIX)))) {
            Bukkit.unloadWorld(tempWorldFolderName, false);
            Utils.deleteWorldFolder(tempWorldFolderName);
        }

        Bukkit.getPluginManager().registerEvents(new TempWorldInitListener(), this);
        new TempWorldUnloadTask().runTaskTimer(this, 0, 60 * 20 /* 1 Minute */);
    }

    @Override
    public void onDisable() {
        TempWorld.terminate(this);
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player) || !sender.hasPermission("worldcleaner.use")) return true;

        Player player = (Player) sender;
        World realWorld = player.getWorld();

        if (args.length == 0) {
            player.sendMessage("§cUsage: /worldcleaner <radius> [max-mspt] [gen-pad]\n§eMax MSPT default is: 45\n§eGeneration padding default is: 1");
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            RegenRunnable runnable = RegenRunnable.get(player);
            if (runnable == null) {
                player.sendMessage("§cYou are not processing a task!");
                return true;
            }

            runnable.cancelRegen();
            player.sendMessage("§eTask was cancelled.");
            return true;
        }

        if (RegenRunnable.get(player) != null) {
            player.sendMessage("§cYou are already processing a task!");
            return true;
        }

        int chunkX = player.getChunk().getX();
        int chunkZ = player.getChunk().getZ();

        int radius;
        int maxMspt;
        int genPad;

        radius = Utils.parseInt(args[0], 10);
        radius = Utils.clamp(radius, 0, 4096);
        maxMspt = Utils.parseInt(args.length > 1 ? args[1] : null, 45);
        maxMspt = Utils.clamp(maxMspt, 25, 50);
        genPad = Utils.parseInt(args.length > 2 ? args[2] : null, 1);
        genPad = Utils.clamp(genPad, 1, 10);

        try {
            TempWorld tempWorld = TempWorld.getOrCreate(realWorld);
            RegenRunnable.start(player, tempWorld, chunkX, chunkZ, radius, maxMspt, genPad);
        } catch (TempWorldCreateException e) {
            player.sendMessage("§cError: " + e.getReason());
        } catch (TempWorldNotLoadedException e) {
            player.sendMessage("§cError: " + e.getClass().getName());
            e.printStackTrace();
        }
        return true;
    }
}
