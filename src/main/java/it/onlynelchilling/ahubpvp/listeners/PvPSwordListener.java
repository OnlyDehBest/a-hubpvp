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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PvPSwordListener implements Listener {

    private static final int CENTER_SLOT = 4;

    private final HubPvPSword plugin;
    private final ConfigCache cache;
    private final BukkitTask tickTask;

    private final Map<UUID, Integer> activateCountdowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> deactivateCountdowns = new ConcurrentHashMap<>();
    private final Set<UUID> pvpActive = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack[]> savedContents = new ConcurrentHashMap<>();

    public PvPSwordListener(HubPvPSword plugin) {
        this.plugin = plugin;
        this.cache = plugin.getConfigCache();
        this.tickTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        ConfigCache c = this.cache;

        Iterator<Map.Entry<UUID, Integer>> actIt = activateCountdowns.entrySet().iterator();
        while (actIt.hasNext()) {
            Map.Entry<UUID, Integer> entry = actIt.next();
            UUID uuid = entry.getKey();
            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline() || !c.isPvPSword(player.getInventory().getItemInMainHand())) {
                actIt.remove();
                continue;
            }

            int remaining = entry.getValue();
            if (remaining <= 0) {
                actIt.remove();
                plugin.getServer().getScheduler().runTask(plugin, () -> activatePvP(player));
                continue;
            }

            MessageUtils.send(player, c.getMessage("countdown"), "%seconds%", String.valueOf(remaining));
            if (c.isCountdownSoundEnabled()) {
                player.playSound(player.getLocation(), c.getCountdownSoundType(), c.getCountdownSoundVolume(), c.getCountdownSoundPitch());
            }
            entry.setValue(remaining - 1);
        }

        Iterator<Map.Entry<UUID, Integer>> deactIt = deactivateCountdowns.entrySet().iterator();
        while (deactIt.hasNext()) {
            Map.Entry<UUID, Integer> entry = deactIt.next();
            UUID uuid = entry.getKey();
            Player player = plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                deactIt.remove();
                continue;
            }

            int remaining = entry.getValue();
            if (remaining <= 0) {
                deactIt.remove();
                plugin.getServer().getScheduler().runTask(plugin, () -> deactivatePvP(player));
                continue;
            }

            MessageUtils.send(player, c.getMessage("countdown-deactivate"), "%seconds%", String.valueOf(remaining));
            if (c.isCountdownSoundEnabled()) {
                player.playSound(player.getLocation(), c.getCountdownSoundType(), c.getCountdownSoundVolume(), c.getCountdownSoundPitch());
            }
            entry.setValue(remaining - 1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ConfigCache c = this.cache;
        if (!c.isGiveOnJoin()) return;
        event.getPlayer().getInventory().setItem(c.getSwordSlot(), c.getSword());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ConfigCache c = this.cache;

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        boolean holdingSword = c.isPvPSword(newItem);

        if (holdingSword) {
            deactivateCountdowns.remove(uuid);

            if (pvpActive.contains(uuid) || activateCountdowns.containsKey(uuid)) return;

            activateCountdowns.put(uuid, c.getHoldTimeSeconds());
        } else {
            activateCountdowns.remove(uuid);

            if (!pvpActive.contains(uuid) || deactivateCountdowns.containsKey(uuid)) return;

            deactivateCountdowns.put(uuid, c.getDeactivateTimeSeconds());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (cache.isPvPSword(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (cache.isPvPSword(event.getCurrentItem()) || cache.isPvPSword(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (pvpActive.contains(uuid)) {
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
        if (pvpActive.remove(uuid)) {
            restoreInventory(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (!pvpActive.contains(attacker.getUniqueId()) || !pvpActive.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        ConfigCache c = this.cache;

        event.getDrops().clear();
        event.setDroppedExp(0);

        if (c.isDeathParticleEnabled()) {
            Location loc = victim.getLocation();
            loc.getWorld().spawnParticle(c.getDeathParticleType(), loc.add(0, 1, 0),
                    c.getDeathParticleCount(), 0.5, 0.5, 0.5, 0.1);
        }

        if (pvpActive.contains(victim.getUniqueId())) {
            Player killer = victim.getKiller();
            if (killer != null && pvpActive.contains(killer.getUniqueId())) {
                Component msg = MessageUtils.parse(c.getMessage("kill-message")
                        .replace("%killer%", killer.getName())
                        .replace("%victim%", victim.getName()));
                MessageUtils.broadcast(plugin.getServer().getOnlinePlayers(), msg);
                healFull(killer);
            }
            pvpActive.remove(victim.getUniqueId());
            savedContents.remove(victim.getUniqueId());
        }

        if (c.isInstantRespawn()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isDead()) {
                    victim.spigot().respawn();
                }
                if (c.isGiveOnJoin()) {
                    victim.getInventory().setItem(c.getSwordSlot(), c.getSword());
                }
            }, 1L);
        }
    }

    private void activatePvP(Player player) {
        UUID uuid = player.getUniqueId();
        ConfigCache c = this.cache;

        PlayerInventory inv = player.getInventory();
        savedContents.put(uuid, inv.getContents().clone());

        pvpActive.add(uuid);
        inv.clear();

        inv.setHelmet(c.getHelmet());
        inv.setChestplate(c.getChestplate());
        inv.setLeggings(c.getLeggings());
        inv.setBoots(c.getBoots());
        inv.setItem(CENTER_SLOT, c.getSword());
        inv.setHeldItemSlot(CENTER_SLOT);

        healFull(player);
        MessageUtils.send(player, c.getMessage("pvp-activated"));
        if (c.isActivatedSoundEnabled()) {
            player.playSound(player.getLocation(), c.getActivatedSoundType(), c.getActivatedSoundVolume(), c.getActivatedSoundPitch());
        }
    }

    private void deactivatePvP(Player player) {
        UUID uuid = player.getUniqueId();
        ConfigCache c = this.cache;
        pvpActive.remove(uuid);
        restoreInventory(player);

        healFull(player);
        MessageUtils.send(player, c.getMessage("pvp-deactivated"));
        if (c.isDeactivatedSoundEnabled()) {
            player.playSound(player.getLocation(), c.getDeactivatedSoundType(), c.getDeactivatedSoundVolume(), c.getDeactivatedSoundPitch());
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
        tickTask.cancel();
        activateCountdowns.clear();
        deactivateCountdowns.clear();

        for (UUID uuid : Set.copyOf(pvpActive)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreInventory(player);
            }
        }

        pvpActive.clear();
        savedContents.clear();
    }
}
