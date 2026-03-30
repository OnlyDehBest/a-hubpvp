package it.onlynelchilling.ahubpvp.tasks;

import it.onlynelchilling.ahubpvp.listeners.PvPSwordListener;
import it.onlynelchilling.ahubpvp.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PvPCounterTask implements Runnable {

    private final PvPSwordListener service;

    public PvPCounterTask(PvPSwordListener service) {
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
                service.activatePvP(player);
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
                service.deactivatePvP(player);
            }
        }
    }
}
