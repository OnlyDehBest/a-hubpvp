package it.onlynelchilling.ahubpvp.listeners;

import it.onlynelchilling.ahubpvp.HubPvPSword;
import it.onlynelchilling.ahubpvp.config.ConfigCache;
import it.onlynelchilling.ahubpvp.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvPSwordListener implements Listener {

    private static final int CENTER_SLOT = 4;

    private final HubPvPSword plugin;

    private final Map<UUID, Integer> activateCountdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> deactivateCountdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pvpActive = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> savedContents = new ConcurrentHashMap<>();

    public PvPSwordListener(HubPvPSword plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Integer> getActivateCountdowns() {
        return activateCountdowns;
    }

    public Map<UUID, Integer> getDeactivateCountdowns() {
        return deactivateCountdowns;
    }

    public ConfigCache getCache() {
        return plugin.getConfigCache();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getCache().isGiveOnJoin()) return;

        event.getPlayer().getInventory().setItem(getCache().getSwordSlot(), getCache().getSword());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        boolean holdingSword = getCache().isPvPSword(newItem);

        if (holdingSword) {
            deactivateCountdowns.remove(uuid);

            if (pvpActive.containsKey(uuid) || activateCountdowns.containsKey(uuid)) return;

            activateCountdowns.put(uuid, getCache().getHoldTimeSeconds());
        } else {
            activateCountdowns.remove(uuid);

            if (!pvpActive.containsKey(uuid) || deactivateCountdowns.containsKey(uuid)) return;

            deactivateCountdowns.put(uuid, getCache().getDeactivateTimeSeconds());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (getCache().isPvPSword(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (getCache().isPvPSword(event.getCurrentItem()) || getCache().isPvPSword(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (pvpActive.containsKey(uuid)) {
            int slot = event.getSlot();

            if (slot >= 36 && slot <= 39) {
                event.setCancelled(true);
            }

            if (event.getRawSlot() >= 5 && event.getRawSlot() <= 8) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        activateCountdowns.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        activateCountdowns.remove(uuid);
        deactivateCountdowns.remove(uuid);

        if (pvpActive.remove(uuid) != null) {
            restoreInventory(player);
        }

        savedContents.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;

        if (!pvpActive.containsKey(attacker.getUniqueId()) || !pvpActive.containsKey(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID uuid = victim.getUniqueId();

        if (!pvpActive.containsKey(uuid)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (getCache().isDeathParticleEnabled()) {
            Location loc = victim.getLocation();

            loc.getWorld().spawnParticle(getCache().getDeathParticleType(), loc.add(0, 1, 0),
                    getCache().getDeathParticleCount(), 0.5, 0.5, 0.5, 0.1);
        }

        Player killer = victim.getKiller();

        if (killer != null && pvpActive.containsKey(killer.getUniqueId())) {
            Component msg = MessageUtils.parse(getCache().getMessage("kill-message")
                    .replace("%killer%", killer.getName())
                    .replace("%victim%", victim.getName()));

            MessageUtils.broadcast(plugin.getServer().getOnlinePlayers(), msg);
            healFull(killer);
        }

        pvpActive.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!savedContents.containsKey(uuid)) return;

        restoreInventory(player);
        healFull(player);

        if (getCache().isGiveOnJoin()) {
            player.getInventory().setItem(getCache().getSwordSlot(), getCache().getSword());
        }
    }

    public void activatePvP(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        savedContents.put(uuid, inv.getContents().clone());

        pvpActive.put(uuid, true);
        inv.clear();

        inv.setHelmet(getCache().getHelmet());
        inv.setChestplate(getCache().getChestplate());
        inv.setLeggings(getCache().getLeggings());
        inv.setBoots(getCache().getBoots());
        inv.setItem(CENTER_SLOT, getCache().getSword());
        inv.setHeldItemSlot(CENTER_SLOT);

        healFull(player);
        MessageUtils.send(player, getCache().getMessage("pvp-activated"));

        if (getCache().isActivatedSoundEnabled()) {
            player.playSound(player.getLocation(), getCache().getActivatedSoundType(), getCache().getActivatedSoundVolume(), getCache().getActivatedSoundPitch());
        }
    }

    public void deactivatePvP(Player player) {
        UUID uuid = player.getUniqueId();

        pvpActive.remove(uuid);
        restoreInventory(player);

        healFull(player);
        MessageUtils.send(player, getCache().getMessage("pvp-deactivated"));

        if (getCache().isDeactivatedSoundEnabled()) {
            player.playSound(player.getLocation(), getCache().getDeactivatedSoundType(), getCache().getDeactivatedSoundVolume(), getCache().getDeactivatedSoundPitch());
        }
    }

    private void healFull(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);

        if (attr != null) {
            player.setHealth(attr.getValue());
        }
    }

    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        inv.clear();

        ItemStack[] contents = savedContents.remove(uuid);

        if (contents != null) {
            inv.setContents(contents);
        }
    }

    public void cleanup() {
        activateCountdowns.clear();
        deactivateCountdowns.clear();

        var iterator = pvpActive.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();

            UUID uuid = entry.getKey();
            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            iterator.remove();
            restoreInventory(player);
        }

        savedContents.clear();
    }
}
