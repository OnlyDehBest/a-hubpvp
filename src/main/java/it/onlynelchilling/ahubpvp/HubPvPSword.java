package it.onlynelchilling.ahubpvp;

import co.aikar.commands.PaperCommandManager;
import it.onlynelchilling.ahubpvp.commands.HubPvPCommand;
import it.onlynelchilling.ahubpvp.config.ConfigCache;
import it.onlynelchilling.ahubpvp.listeners.PvPSwordListener;
import it.onlynelchilling.ahubpvp.tasks.PvPCounterTask;
import org.bukkit.plugin.java.JavaPlugin;

public final class HubPvPSword extends JavaPlugin {

    private ConfigCache configCache;
    private PvPSwordListener swordListener;

    @Override
    public void onEnable() {
        this.configCache = new ConfigCache(this);

        this.swordListener = new PvPSwordListener(this);
        getServer().getPluginManager().registerEvents(swordListener, this);
        getServer().getScheduler().runTaskTimerAsynchronously(this, new PvPCounterTask(this, swordListener), 20L, 20L);

        PaperCommandManager commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new HubPvPCommand(this));
    }

    @Override
    public void onDisable() {
        if (swordListener != null) {
            swordListener.cleanup();
        }

        getServer().getScheduler().cancelTasks(this);
    }

    public ConfigCache getConfigCache() {
        return configCache;
    }
}
