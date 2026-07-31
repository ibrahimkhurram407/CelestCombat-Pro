package com.shyamstudio.celestCombatPro.listeners;

import com.shyamstudio.celestCombatPro.CelestCombatPro;
import com.shyamstudio.celestCombatPro.combat.CombatManager;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ItemRestrictionListener implements Listener {

    private final CelestCombatPro plugin;
    private final CombatManager combatManager;

    private boolean itemRestrictions;
    private List<String> disabledItems = Collections.emptyList();
    private Map<String, Boolean> elytraDisabledWorlds = Collections.emptyMap();
    private Map<String, Boolean> fireworkDisabledWorlds = Collections.emptyMap();

    public ItemRestrictionListener(CelestCombatPro plugin,  CombatManager combatManager) {
        this.plugin = plugin;
        this.combatManager = combatManager;

        this.reloadConfig();
    }

    public void reloadConfig() {
        this.itemRestrictions = plugin.getConfig().getBoolean("combat.item_restrictions.enabled", true);
        this.disabledItems = plugin.getConfig().getStringList("combat.item_restrictions.disabled_items");
        this.elytraDisabledWorlds = loadElytraDisabledWorlds();
        this.fireworkDisabledWorlds = loadFireworkDisabledWorlds();
    }

    public static String formatItemName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }

        // Convert from UPPERCASE_WITH_UNDERSCORES to Title Case
        String[] words = material.name().split("_");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            // Capitalize first letter, rest lowercase
            formattedName
                    .append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }

        return formattedName.toString().trim();
    }

    // NOTE: This method is now registered dynamically with configurable priority
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) {
            return;
        }

        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            if (combatManager.isEnchantedGoldenAppleOnCooldown(player)) {
                event.setCancelled(true);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("time", String.valueOf(combatManager.getRemainingEnchantedGoldenAppleCooldown(player)));
                plugin.getMessageService().sendMessage(player, "enchanted_golden_apple_cooldown", placeholders);
                return;
            }
        }

        // Check if item restrictions are enabled
        if (itemRestrictions && combatManager.isInCombat(player)) {
            // Check if the consumed item is in the disabled items list
            if (isItemDisabled(item.getType())) {
                event.setCancelled(true);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", formatItemName(item.getType()));
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
                return;
            }
        }

        if (item.getType() == Material.ENCHANTED_GOLDEN_APPLE && !event.isCancelled()) {
            combatManager.setEnchantedGoldenAppleCooldown(player);
        }
    }

    /**
     * Firework rockets are activated rather than consumed, so they need their
     * own interaction handler to participate in the disabled-items system.
     * Cancelling this event blocks both placing a firework and Elytra boosting.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.FIREWORK_ROCKET) {
            return;
        }

        Player player = event.getPlayer();
        if (!isItemRestrictedForPlayer(player, Material.FIREWORK_ROCKET)) {
            return;
        }

        event.setCancelled(true);
        sendItemBlockedMessage(player, Material.FIREWORK_ROCKET);
    }

    /**
     * Paper fires a dedicated event when a rocket boosts an Elytra. Handling
     * it directly prevents the boost even when another plugin or a server
     * implementation bypasses the generic interaction cancellation.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();
        if (!isItemRestrictedForPlayer(player, Material.FIREWORK_ROCKET)) {
            return;
        }

        event.setCancelled(true);
        sendItemBlockedMessage(player, Material.FIREWORK_ROCKET);
    }

    /**
     * Also prevent pre-loaded firework rockets from being fired from a
     * crossbow. This closes the remaining player-controlled firework path.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireworkCrossbowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof Firework)
                || !isItemRestrictedForPlayer(player, Material.FIREWORK_ROCKET)) {
            return;
        }

        event.setCancelled(true);
        sendItemBlockedMessage(player, Material.FIREWORK_ROCKET);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean elytraBlockedInWorld = isElytraDisabledInWorld(player.getWorld());

        if (!itemRestrictions && !elytraBlockedInWorld) {
            return;
        }

        boolean elytraBlockedInCombat = combatManager.isInCombat(player) && disabledItems.contains("ELYTRA");

        if (elytraBlockedInWorld) {
            ensureElytraUnequipped(player, "elytra_disabled_world");
        }

        if (elytraBlockedInCombat || elytraBlockedInWorld) {
            if (player.isGliding()) {
                player.setGliding(false);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", "Elytra");
                plugin.getMessageService().sendMessage(player,
                        elytraBlockedInWorld ? "elytra_disabled_world" : "item_use_blocked_in_combat",
                        placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean elytraBlockedInWorld = isElytraDisabledInWorld(player.getWorld());
        if (!itemRestrictions && !elytraBlockedInWorld) {
            return;
        }

        boolean elytraBlockedInCombat = combatManager.isInCombat(player) && disabledItems.contains("ELYTRA");
        if (!elytraBlockedInCombat && !elytraBlockedInWorld) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Prevent equipping Elytra to chestplate slot
        if (event.getSlot() == 38 && event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
            if ((clickedItem != null && clickedItem.getType() == Material.ELYTRA)
                    || (cursorItem != null && cursorItem.getType() == Material.ELYTRA)) {
                event.setCancelled(true);
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", "Elytra");
                plugin.getMessageService().sendMessage(player,
                        elytraBlockedInWorld ? "elytra_disabled_world" : "item_use_blocked_in_combat",
                        placeholders);
                return;
            }
        }

        // Prevent shift-clicking Elytra to equip it
        if (event.isShiftClick() && clickedItem != null && clickedItem.getType() == Material.ELYTRA) {
            PlayerInventory inv = player.getInventory();
            if (inv.getChestplate() == null || inv.getChestplate().getType() == Material.AIR) {
                event.setCancelled(true);
                
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", "Elytra");
                plugin.getMessageService().sendMessage(player,
                        elytraBlockedInWorld ? "elytra_disabled_world" : "item_use_blocked_in_combat",
                        placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        boolean elytraBlockedInWorld = isElytraDisabledInWorld(player.getWorld());

        if (!itemRestrictions && !elytraBlockedInWorld) {
            return;
        }

        boolean elytraBlockedInCombat = combatManager.isInCombat(player) && disabledItems.contains("ELYTRA");

        // Check if ELYTRA is disabled and player is trying to start gliding
        if ((elytraBlockedInCombat || elytraBlockedInWorld) && event.isFlying() &&
            player.getInventory().getChestplate() != null && 
            player.getInventory().getChestplate().getType() == Material.ELYTRA) {
            
            event.setCancelled(true);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("item", "Elytra");
            plugin.getMessageService().sendMessage(player,
                    elytraBlockedInWorld ? "elytra_disabled_world" : "item_use_blocked_in_combat",
                    placeholders);
        }
    }

    /**
     * Removes Elytra from player when they enter combat
     */
    public void handleCombatStart(Player player) {
        // Check if item restrictions are enabled
        if (!itemRestrictions) {
            return;
        }
        
        if (!disabledItems.contains("ELYTRA")) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack chestplate = inventory.getChestplate();

        // If player has Elytra equipped, remove it
        if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
            // Stop gliding immediately
            if (player.isGliding()) {
                player.setGliding(false);
            }

            // Remove Elytra from chestplate slot
            inventory.setChestplate(null);

            // Try to add it back to inventory, drop if full
            HashMap<Integer, ItemStack> leftover = inventory.addItem(chestplate);
            if (!leftover.isEmpty()) {
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("player", player.getName());
            placeholders.put("item", "Elytra");
            plugin.getMessageService().sendMessage(player, "elytra_removed_combat_start", placeholders);
        }
    }

    private boolean isItemDisabled(Material itemType) {
        return disabledItems.stream()
                .anyMatch(disabledItem ->
                        itemType.name().equalsIgnoreCase(disabledItem) ||
                                itemType.name().contains(disabledItem)
                );
    }

    private boolean isItemRestrictedForPlayer(Player player, Material itemType) {
        if (itemType == Material.FIREWORK_ROCKET && isFireworkDisabledInWorld(player.getWorld())) {
            return true;
        }

        return itemRestrictions
                && combatManager.isInCombat(player)
                && isItemDisabled(itemType);
    }

    private void sendItemBlockedMessage(Player player, Material itemType) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("item", formatItemName(itemType));
        plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
    }

    private Map<String, Boolean> loadElytraDisabledWorlds() {
        if (!plugin.getConfig().isConfigurationSection("combat.item_restrictions.elytra_disabled_worlds")) {
            return Collections.emptyMap();
        }

        Map<String, Boolean> worlds = new HashMap<>();
        for (String worldName : plugin.getConfig()
                .getConfigurationSection("combat.item_restrictions.elytra_disabled_worlds")
                .getKeys(false)) {
            worlds.put(worldName.toLowerCase(Locale.ROOT), plugin.getConfig().getBoolean(
                    "combat.item_restrictions.elytra_disabled_worlds." + worldName, false));
        }
        return worlds;
    }

    private boolean isElytraDisabledInWorld(World world) {
        return world != null
                && elytraDisabledWorlds.getOrDefault(world.getName().toLowerCase(Locale.ROOT), false);
    }

    private Map<String, Boolean> loadFireworkDisabledWorlds() {
        if (!plugin.getConfig().isConfigurationSection("combat.item_restrictions.firework_disabled_worlds")) {
            return Collections.emptyMap();
        }

        Map<String, Boolean> worlds = new HashMap<>();
        for (String worldName : plugin.getConfig()
                .getConfigurationSection("combat.item_restrictions.firework_disabled_worlds")
                .getKeys(false)) {
            worlds.put(worldName.toLowerCase(Locale.ROOT), plugin.getConfig().getBoolean(
                    "combat.item_restrictions.firework_disabled_worlds." + worldName, false));
        }
        return worlds;
    }

    private boolean isFireworkDisabledInWorld(World world) {
        return world != null
                && fireworkDisabledWorlds.getOrDefault(world.getName().toLowerCase(Locale.ROOT), false);
    }

    private void ensureElytraUnequipped(Player player, String messageKey) {
        PlayerInventory inventory = player.getInventory();
        ItemStack chestplate = inventory.getChestplate();

        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            return;
        }

        inventory.setChestplate(null);

        HashMap<Integer, ItemStack> leftover = inventory.addItem(chestplate);
        if (!leftover.isEmpty()) {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", player.getName());
        placeholders.put("item", "Elytra");
        plugin.getMessageService().sendMessage(player, messageKey, placeholders);
    }
}
