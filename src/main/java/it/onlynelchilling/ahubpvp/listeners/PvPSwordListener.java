package it.onlynelchilling.ahubpvp.listeners;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.bukkit.event.entity.EntityPotionEffectEvent;
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
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class PvPSwordListener implements Listener {

    private static final int CENTER_SLOT = 4;

    private final HubPvPSword plugin;

    private final Cache<UUID, Integer> activateCountdownsCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Integer> deactivateCountdownsCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Boolean> pvpActiveCache = Caffeine.newBuilder().build();
    private final Cache<UUID, ItemStack[]> savedContentsCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Boolean> savedFlightStateCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Collection<PotionEffect>> savedPotionEffectsCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Integer> combatTagCache = Caffeine.newBuilder().build();
    private final Cache<UUID, Boolean> combatTagNotifiedCache = Caffeine.newBuilder().build();

    private final ConcurrentMap<UUID, Integer> activateCountdowns = activateCountdownsCache.asMap();
    private final ConcurrentMap<UUID, Integer> deactivateCountdowns = deactivateCountdownsCache.asMap();
    private final ConcurrentMap<UUID, Boolean> pvpActive = pvpActiveCache.asMap();
    private final ConcurrentMap<UUID, ItemStack[]> savedContents = savedContentsCache.asMap();
    private final ConcurrentMap<UUID, Boolean> savedFlightState = savedFlightStateCache.asMap();
    private final ConcurrentMap<UUID, Collection<PotionEffect>> savedPotionEffects = savedPotionEffectsCache.asMap();
    private final ConcurrentMap<UUID, Integer> combatTag = combatTagCache.asMap();
    private final ConcurrentMap<UUID, Boolean> combatTagNotified = combatTagNotifiedCache.asMap();

    public PvPSwordListener(HubPvPSword plugin) {
        this.plugin = plugin;
    }

    public ConcurrentMap<UUID, Integer> getActivateCountdowns() {
        return activateCountdowns;
    }

    public ConcurrentMap<UUID, Integer> getDeactivateCountdowns() {
        return deactivateCountdowns;
    }

    public ConcurrentMap<UUID, Integer> getCombatTag() {
        return combatTag;
    }

    public void clearCombatTagNotified(UUID uuid) {
        combatTagNotified.remove(uuid);
    }

    public ConfigCache getCache() {
        return plugin.getConfigCache();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!getCache().isGiveOnJoin()) return;

        Player player = event.getPlayer();
        int delay = getCache().getGiveOnJoinDelay();

        if (delay <= 0) {
            player.getInventory().setItem(getCache().getSwordSlot(), getCache().getSword());
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.getInventory().setItem(getCache().getSwordSlot(), getCache().getSword());
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
            if (pvpActive.containsKey(uuid)) {
                Integer tagSeconds = combatTag.get(uuid);

                if (tagSeconds != null && tagSeconds > 0) {
                    event.setCancelled(true);
                    player.getInventory().setHeldItemSlot(CENTER_SLOT);

                    if (combatTagNotified.putIfAbsent(uuid, true) == null) {
                        MessageUtils.send(player, getCache().getMessage("combat-tagged"), "%seconds%", String.valueOf(tagSeconds));
                    }
                    return;
                }
            }

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

            if (slot == CENTER_SLOT || event.getHotbarButton() == CENTER_SLOT) {
                event.setCancelled(true);
                return;
            }

            if (slot >= 36 && slot <= 39) {
                event.setCancelled(true);
            }

            if (event.getRawSlot() >= 5 && event.getRawSlot() <= 8) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pvpActive.containsKey(uuid)) {
            event.setCancelled(true);
            return;
        }

        activateCountdowns.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        activateCountdowns.remove(uuid);
        deactivateCountdowns.remove(uuid);
        combatTag.remove(uuid);
        combatTagNotified.remove(uuid);

        if (pvpActive.remove(uuid) != null) {
            restoreInventory(player);
            restorePotionEffects(player);

            Boolean hadFlight = savedFlightState.remove(uuid);

            if (hadFlight != null && hadFlight) {
                player.setAllowFlight(true);
            }
        }

        savedContents.remove(uuid);
        savedFlightState.remove(uuid);
        savedPotionEffects.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!pvpActive.containsKey(player.getUniqueId())) return;

        EntityPotionEffectEvent.Action action = event.getAction();

        if (action == EntityPotionEffectEvent.Action.ADDED || action == EntityPotionEffectEvent.Action.CHANGED) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;

        if (!pvpActive.containsKey(attacker.getUniqueId()) || !pvpActive.containsKey(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int tagSeconds = getCache().getCombatTagSeconds();

        combatTag.put(victim.getUniqueId(), tagSeconds);
        deactivateCountdowns.remove(victim.getUniqueId());

        combatTag.put(attacker.getUniqueId(), tagSeconds);
        deactivateCountdowns.remove(attacker.getUniqueId());
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
        combatTag.remove(uuid);
        combatTagNotified.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!savedContents.containsKey(uuid)) return;

        restoreInventory(player);
        restorePotionEffects(player);
        healFull(player);

        if (getCache().isGiveOnJoin()) {
            player.getInventory().setItem(getCache().getSwordSlot(), getCache().getSword());
        }
    }

    public void activatePvP(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        savedContents.put(uuid, inv.getContents().clone());
        savedFlightState.put(uuid, player.getAllowFlight());

        pvpActive.put(uuid, true);
        inv.clear();

        savedPotionEffects.put(uuid, List.copyOf(player.getActivePotionEffects()));

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        player.setFlying(false);
        player.setAllowFlight(false);

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
        restorePotionEffects(player);

        Boolean hadFlight = savedFlightState.remove(uuid);

        if (hadFlight != null && hadFlight) {
            player.setAllowFlight(true);
        }

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

    private void restorePotionEffects(Player player) {
        Collection<PotionEffect> effects = savedPotionEffects.remove(player.getUniqueId());

        if (effects == null) return;

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
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
            restorePotionEffects(player);

            Boolean hadFlight = savedFlightState.remove(uuid);

            if (hadFlight != null && hadFlight) {
                player.setAllowFlight(true);
            }
        }

        savedContents.clear();
        savedFlightState.clear();
        savedPotionEffects.clear();
        combatTag.clear();
        combatTagNotified.clear();
    }
}
