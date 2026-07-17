package com.shyamstudio.celestCombatPro.protection;

import com.shyamstudio.celestCombatPro.CelestCombatPro;
import com.shyamstudio.celestCombatPro.Scheduler;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies newbie and cause-aware keep-inventory rules and delays the explanation
 * until the player has respawned.
 */
public class DeathInventoryManager {
    private final CelestCombatPro plugin;
    private final NewbieProtectionManager newbieProtectionManager;
    private final Map<UUID, DeathDecision> pendingDecisions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> worldSettings = new ConcurrentHashMap<>();
    private final Set<EntityDamageEvent.DamageCause> naturalCauses = new HashSet<>();
    private final Set<EntityType> naturalMobs = new HashSet<>();
    private List<String> customMobMetadataKeys = List.of();
    private List<String> customMobScoreboardTagPrefixes = List.of();
    private boolean enabled;
    private boolean unknownCausesKeepInventory;

    public DeathInventoryManager(CelestCombatPro plugin, NewbieProtectionManager newbieProtectionManager) {
        this.plugin = plugin;
        this.newbieProtectionManager = newbieProtectionManager;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("semi_keep_inventory.enabled", false);
        unknownCausesKeepInventory =
                config.getBoolean("semi_keep_inventory.unknown_causes_keep_inventory", false);
        customMobMetadataKeys =
                config.getStringList("semi_keep_inventory.custom_mob_detection.metadata_keys");
        customMobScoreboardTagPrefixes =
                config.getStringList("semi_keep_inventory.custom_mob_detection.scoreboard_tag_prefixes");

        worldSettings.clear();
        ConfigurationSection worlds = config.getConfigurationSection("semi_keep_inventory.worlds");
        if (worlds != null) {
            for (String worldName : worlds.getKeys(false)) {
                worldSettings.put(
                        worldName.toLowerCase(Locale.ROOT),
                        worlds.getBoolean(worldName, enabled)
                );
            }
        }

        naturalCauses.clear();
        for (String value : config.getStringList("semi_keep_inventory.natural_damage_causes")) {
            try {
                naturalCauses.add(EntityDamageEvent.DamageCause.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid semi_keep_inventory natural damage cause: " + value);
            }
        }

        naturalMobs.clear();
        for (String value : config.getStringList("semi_keep_inventory.natural_mobs")) {
            try {
                naturalMobs.add(EntityType.valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid semi_keep_inventory natural mob type: " + value);
            }
        }
    }

    public void handleDeath(PlayerDeathEvent event, boolean playerCombatInvolved) {
        Player player = event.getEntity();
        DeathDecision decision = decide(player, playerCombatInvolved);
        if (decision == null) {
            return;
        }

        event.setKeepInventory(decision.keepInventory());
        if (decision.keepInventory()) {
            // Bukkit does not guarantee another plugin has cleared drops when keepInventory changes.
            // Clearing here prevents duplicated items.
            event.getDrops().clear();
        }
        pendingDecisions.put(player.getUniqueId(), decision);
    }

    private DeathDecision decide(Player player, boolean playerCombatInvolved) {
        if (newbieProtectionManager.isKeepInventory() && newbieProtectionManager.hasProtection(player)) {
            return new DeathDecision(true, "death_inventory_kept_newbie", "newbie protection");
        }
        if (!isEnabledInWorld(player.getWorld().getName())) {
            return null;
        }
        if (playerCombatInvolved) {
            return new DeathDecision(false, "death_inventory_lost", "a player was involved in your death");
        }

        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) {
            return unknownDecision("an unknown cause");
        }

        if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
            Entity attacker = resolveAttacker(byEntity.getDamager());
            if (attacker instanceof Player) {
                return new DeathDecision(false, "death_inventory_lost", "a player killed you");
            }
            if (attacker instanceof LivingEntity livingAttacker) {
                String attackerName = readable(livingAttacker.getType().name());
                if (isCustomMob(livingAttacker)) {
                    return new DeathDecision(false, "death_inventory_lost",
                            "a custom mob (" + attackerName + ") killed you");
                }
                if (naturalMobs.contains(livingAttacker.getType())) {
                    return new DeathDecision(true, "death_inventory_kept_natural",
                            "you were killed by a " + attackerName);
                }
                return new DeathDecision(false, "death_inventory_lost",
                        "a non-natural mob (" + attackerName + ") killed you");
            }
            return unknownDecision("an unrecognized entity caused your death");
        }

        String causeName = readable(lastDamage.getCause().name());
        if (naturalCauses.contains(lastDamage.getCause())) {
            return new DeathDecision(true, "death_inventory_kept_natural",
                    "you died from " + causeName);
        }
        return unknownDecision("you died from " + causeName);
    }

    private DeathDecision unknownDecision(String reason) {
        return new DeathDecision(
                unknownCausesKeepInventory,
                unknownCausesKeepInventory ? "death_inventory_kept_natural" : "death_inventory_lost",
                reason
        );
    }

    public boolean isEnabledInWorld(String worldName) {
        if (worldName == null) {
            return enabled;
        }
        return worldSettings.getOrDefault(worldName.toLowerCase(Locale.ROOT), enabled);
    }

    private Entity resolveAttacker(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Entity entity) {
                return entity;
            }
        }
        return damager;
    }

    private boolean isCustomMob(LivingEntity entity) {
        for (String metadataKey : customMobMetadataKeys) {
            if (entity.hasMetadata(metadataKey)) {
                return true;
            }
        }
        for (String tag : entity.getScoreboardTags()) {
            for (String prefix : customMobScoreboardTagPrefixes) {
                if (!prefix.isEmpty() && tag.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void sendPendingReason(Player player) {
        DeathDecision decision = pendingDecisions.remove(player.getUniqueId());
        if (decision == null) {
            return;
        }
        Scheduler.runEntityTaskLater(player, () -> {
            if (!player.isOnline()) {
                pendingDecisions.putIfAbsent(player.getUniqueId(), decision);
                return;
            }
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reason", decision.reason());
            plugin.getMessageService().sendMessage(player, decision.messageKey(), placeholders);
        }, 1L);
    }

    public void shutdown() {
        pendingDecisions.clear();
    }

    private String readable(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private record DeathDecision(boolean keepInventory, String messageKey, String reason) {
    }
}
