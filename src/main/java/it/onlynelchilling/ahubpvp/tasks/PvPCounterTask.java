package it.onlynelchilling.ahubpvp.tasks;

import it.onlynelchilling.ahubpvp.HubPvPSword;
import it.onlynelchilling.ahubpvp.listeners.PvPSwordListener;
import it.onlynelchilling.ahubpvp.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class PvPCounterTask implements Runnable {

    private final HubPvPSword plugin;
    private final PvPSwordListener service;

    public PvPCounterTask(HubPvPSword plugin, PvPSwordListener service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public void run() {
        var actIt = service.getActivateCountdowns().entrySet().iterator();

        while (actIt.hasNext()) {
            var entry = actIt.next();

            UUID uuid = entry.getKey();
            int seconds = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline() || !service.getCache().isPvPSword(player.getInventory().getItemInMainHand())) {
                actIt.remove();
                continue;
            }

            if (seconds > 0) {
                entry.setValue(seconds - 1);
                MessageUtils.send(player, service.getCache().getMessage("countdown"), "%seconds%", String.valueOf(seconds));

                if (service.getCache().isCountdownSoundEnabled()) {
                    player.playSound(player.getLocation(), service.getCache().getCountdownSoundType(), service.getCache().getCountdownSoundVolume(), service.getCache().getCountdownSoundPitch());
                }
            } else {
                actIt.remove();
                Bukkit.getScheduler().runTask(plugin, () -> service.activatePvP(player));
            }
        }

        var tagIt = service.getCombatTag().entrySet().iterator();

        while (tagIt.hasNext()) {
            var entry = tagIt.next();

            UUID uuid = entry.getKey();
            int seconds = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                tagIt.remove();
                continue;
            }

            if (seconds > 1) {
                entry.setValue(seconds - 1);
            } else {
                tagIt.remove();
                service.clearCombatTagNotified(uuid);

                ItemStack held = player.getInventory().getItemInMainHand();

                if (!service.getCache().isPvPSword(held) && !service.getDeactivateCountdowns().containsKey(uuid)) {
                    service.getDeactivateCountdowns().put(uuid, service.getCache().getDeactivateTimeSeconds());
                }
            }
        }

        var deactIt = service.getDeactivateCountdowns().entrySet().iterator();

        while (deactIt.hasNext()) {
            var entry = deactIt.next();

            UUID uuid = entry.getKey();
            int seconds = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                deactIt.remove();
                continue;
            }

            if (seconds > 0) {
                entry.setValue(seconds - 1);
                MessageUtils.send(player, service.getCache().getMessage("countdown-deactivate"), "%seconds%", String.valueOf(seconds));

                if (service.getCache().isCountdownSoundEnabled()) {
                    player.playSound(player.getLocation(), service.getCache().getCountdownSoundType(), service.getCache().getCountdownSoundVolume(), service.getCache().getCountdownSoundPitch());
                }
            } else {
                deactIt.remove();
                Bukkit.getScheduler().runTask(plugin, () -> service.deactivatePvP(player));
            }
        }
    }
}
