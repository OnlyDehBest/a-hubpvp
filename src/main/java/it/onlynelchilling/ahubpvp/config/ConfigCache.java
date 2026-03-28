package it.onlynelchilling.ahubpvp.config;

import it.onlynelchilling.ahubpvp.HubPvPSword;
import it.onlynelchilling.ahubpvp.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigCache {

    private final HubPvPSword plugin;
    private final NamespacedKey pvpKey;
    private final ConfigUtils configFile;

    private ItemStack sword;
    private Material swordMaterial;
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;
    private int holdTimeTicks;
    private int holdTimeSeconds;
    private int deactivateTimeSeconds;
    private int swordSlot;
    private boolean giveOnJoin;
    private boolean instantRespawn;
    private boolean deathParticleEnabled;
    private Particle deathParticleType;
    private int deathParticleCount;
    private boolean countdownSoundEnabled;
    private Sound countdownSoundType;
    private float countdownSoundVolume;
    private float countdownSoundPitch;
    private boolean activatedSoundEnabled;
    private Sound activatedSoundType;
    private float activatedSoundVolume;
    private float activatedSoundPitch;
    private boolean deactivatedSoundEnabled;
    private Sound deactivatedSoundType;
    private float deactivatedSoundVolume;
    private float deactivatedSoundPitch;
    private final Map<String, String> messages = new HashMap<>();

    public ConfigCache(HubPvPSword plugin) {
        this.plugin = plugin;
        this.pvpKey = new NamespacedKey(plugin, "pvp_sword");
        this.configFile = new ConfigUtils(plugin, "config");
        reload();
    }

    public void reload() {
        configFile.reload();

        this.holdTimeSeconds = configFile.getInt("sword.hold-time-seconds", 5);
        this.holdTimeTicks = this.holdTimeSeconds * 20;
        this.deactivateTimeSeconds = configFile.getInt("sword.deactivate-time-seconds", 5);
        this.swordSlot = configFile.getInt("sword.slot", 0);
        this.giveOnJoin = configFile.getBoolean("settings.give-on-join", true);
        this.instantRespawn = configFile.getBoolean("settings.instant-respawn", true);
        this.deathParticleEnabled = configFile.getBoolean("settings.death-particle.enabled", true);
        this.deathParticleType = Particle.valueOf(configFile.getString("settings.death-particle.type", "EXPLOSION").toUpperCase());
        this.deathParticleCount = configFile.getInt("settings.death-particle.count", 30);
        this.countdownSoundEnabled = configFile.getBoolean("settings.sounds.countdown.enabled", true);
        this.countdownSoundType = Sound.valueOf(configFile.getString("settings.sounds.countdown.type", "BLOCK_NOTE_BLOCK_PLING").toUpperCase());
        this.countdownSoundVolume = (float) configFile.getDouble("settings.sounds.countdown.volume", 1.0);
        this.countdownSoundPitch = (float) configFile.getDouble("settings.sounds.countdown.pitch", 1.5);
        this.activatedSoundEnabled = configFile.getBoolean("settings.sounds.pvp-activated.enabled", true);
        this.activatedSoundType = Sound.valueOf(configFile.getString("settings.sounds.pvp-activated.type", "ENTITY_ENDER_DRAGON_GROWL").toUpperCase());
        this.activatedSoundVolume = (float) configFile.getDouble("settings.sounds.pvp-activated.volume", 0.5);
        this.activatedSoundPitch = (float) configFile.getDouble("settings.sounds.pvp-activated.pitch", 1.2);
        this.deactivatedSoundEnabled = configFile.getBoolean("settings.sounds.pvp-deactivated.enabled", true);
        this.deactivatedSoundType = Sound.valueOf(configFile.getString("settings.sounds.pvp-deactivated.type", "ENTITY_EXPERIENCE_ORB_PICKUP").toUpperCase());
        this.deactivatedSoundVolume = (float) configFile.getDouble("settings.sounds.pvp-deactivated.volume", 1.0);
        this.deactivatedSoundPitch = (float) configFile.getDouble("settings.sounds.pvp-deactivated.pitch", 0.8);
        this.sword = buildItem("sword", true);
        this.swordMaterial = this.sword.getType();
        this.helmet = buildItem("armor.helmet", false);
        this.chestplate = buildItem("armor.chestplate", false);
        this.leggings = buildItem("armor.leggings", false);
        this.boots = buildItem("armor.boots", false);

        messages.clear();
        ConfigurationSection msgSection = configFile.getConfigurationSection("messages");
        if (msgSection != null) {
            for (String key : msgSection.getKeys(false)) {
                messages.put(key, MessageUtils.convert(msgSection.getString(key)));
            }
        }
    }

    private ItemStack buildItem(String path, boolean tagAsPvP) {
        Material material = Material.valueOf(configFile.getString(path + ".material", "STONE").toUpperCase());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtils.itemText(configFile.getString(path + ".display-name", "")));
            List<String> lore = configFile.getStringList(path + ".lore");
            lore.removeIf(s -> s == null || s.trim().isEmpty());
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(MessageUtils::itemText).toList());
            } else {
                meta.lore(null);
            }
            if (tagAsPvP) {
                meta.getPersistentDataContainer().set(pvpKey, PersistentDataType.BYTE, (byte) 1);
            }
            meta.setUnbreakable(true);
            applyTrim(meta, path);
            item.setItemMeta(meta);
        }
        ConfigurationSection enchSection = configFile.getConfigurationSection(path + ".enchantments");
        if (enchSection != null) {
            for (String enchName : enchSection.getKeys(false)) {
                Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchName.toLowerCase()));
                if (ench != null) {
                    item.addUnsafeEnchantment(ench, enchSection.getInt(enchName));
                }
            }
        }
        return item;
    }

    private void applyTrim(ItemMeta meta, String path) {
        if (!(meta instanceof ArmorMeta armorMeta)) return;
        if (!configFile.getBoolean(path + ".trim.enabled", false)) return;

        String patternName = configFile.getString(path + ".trim.pattern", "sentry").toLowerCase();
        String materialName = configFile.getString(path + ".trim.material", "netherite").toLowerCase();

        TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(patternName));
        TrimMaterial trimMat = Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(materialName));

        if (pattern != null && trimMat != null) {
            armorMeta.setTrim(new ArmorTrim(trimMat, pattern));
        }
    }

    public boolean isPvPSword(ItemStack item) {
        if (item == null || item.getType() != swordMaterial || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(pvpKey, PersistentDataType.BYTE);
    }

    public ItemStack getSword() {
        return sword.clone();
    }

    public ItemStack getHelmet() {
        return helmet.clone();
    }

    public ItemStack getChestplate() {
        return chestplate.clone();
    }

    public ItemStack getLeggings() {
        return leggings.clone();
    }

    public ItemStack getBoots() {
        return boots.clone();
    }

    public int getHoldTimeTicks() {
        return holdTimeTicks;
    }

    public int getHoldTimeSeconds() {
        return holdTimeSeconds;
    }

    public int getDeactivateTimeSeconds() {
        return deactivateTimeSeconds;
    }

    public int getSwordSlot() {
        return swordSlot;
    }

    public boolean isGiveOnJoin() {
        return giveOnJoin;
    }

    public boolean isInstantRespawn() {
        return instantRespawn;
    }

    public boolean isDeathParticleEnabled() {
        return deathParticleEnabled;
    }

    public Particle getDeathParticleType() {
        return deathParticleType;
    }

    public int getDeathParticleCount() {
        return deathParticleCount;
    }

    public boolean isCountdownSoundEnabled() { return countdownSoundEnabled; }
    public Sound getCountdownSoundType() { return countdownSoundType; }
    public float getCountdownSoundVolume() { return countdownSoundVolume; }
    public float getCountdownSoundPitch() { return countdownSoundPitch; }

    public boolean isActivatedSoundEnabled() { return activatedSoundEnabled; }
    public Sound getActivatedSoundType() { return activatedSoundType; }
    public float getActivatedSoundVolume() { return activatedSoundVolume; }
    public float getActivatedSoundPitch() { return activatedSoundPitch; }

    public boolean isDeactivatedSoundEnabled() { return deactivatedSoundEnabled; }
    public Sound getDeactivatedSoundType() { return deactivatedSoundType; }
    public float getDeactivatedSoundVolume() { return deactivatedSoundVolume; }
    public float getDeactivatedSoundPitch() { return deactivatedSoundPitch; }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "<red>Messaggio mancante: " + key);
    }

    public NamespacedKey getPvpKey() {
        return pvpKey;
    }
}
