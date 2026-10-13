package com.evilsmp.abilities;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EvilSMPAbilitiesPlugin extends JavaPlugin implements Listener, TabExecutor {
    private NamespacedKey itemKey, armourKey;
    private final Map<UUID, Long> tickleCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> suppressCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> restrictedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedHealth = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> healthTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> armourTasks = new ConcurrentHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        itemKey = new NamespacedKey(this, "ability_item");
        armourKey = new NamespacedKey(this, "armour_breaker");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("evilsmpitems")).setExecutor(this);
        Objects.requireNonNull(getCommand("evilsmpitems")).setTabCompleter(this);
    }

    @Override public void onDisable() {
        healthTasks.values().forEach(BukkitTask::cancel);
        armourTasks.values().forEach(BukkitTask::cancel);
        for (var e : savedHealth.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
                if (a != null) {
                    a.setBaseValue(e.getValue());
                    if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
                }
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance a = p.getAttribute(Attribute.ARMOR);
            if (a != null) a.removeModifier(armourKey);
        }
    }

    private ItemStack makeItem(String type) {
        Material material;
        String name;
        List<String> lore;
        switch (type) {
            case "tickle" -> {
                material = Material.FEATHER;
                name = ChatColor.AQUA + "" + ChatColor.BOLD + "Tickle Feather";
                lore = List.of(ChatColor.GRAY + "Hit a player to make them float.", "",
                    ChatColor.YELLOW + "Slow Falling: " + ChatColor.WHITE + "3 minutes",
                    ChatColor.YELLOW + "Cooldown: " + ChatColor.WHITE + "5 minutes");
            }
            case "suppressor" -> {
                material = Material.NETHERITE_SWORD;
                name = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Ability Suppressor";
                lore = List.of(ChatColor.GRAY + "Removes positive potion effects.", ChatColor.GRAY + "Reduces max health to 10 hearts for 5 minutes.");
            }
            case "restrictor" -> {
                material = Material.COMPARATOR;
                name = ChatColor.GOLD + "" + ChatColor.BOLD + "Restrictor";
                lore = List.of(ChatColor.GRAY + "Hit to restrict Elytra, Spear and XP bottles.", ChatColor.YELLOW + "Duration: " + ChatColor.WHITE + "5 minutes");
            }
            case "armourbreaker" -> {
                material = Material.DIAMOND_SWORD;
                name = ChatColor.RED + "" + ChatColor.BOLD + "Armour Breaker";
                lore = List.of(ChatColor.GRAY + "Temporarily weakens target armour.", ChatColor.YELLOW + "Armour reduction: " + ChatColor.WHITE + "8 for 10 seconds");
            }
            default -> { return null; }
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player target)) return;
        String type = typeOf(attacker.getInventory().getItemInMainHand());
        if (type == null) return;
        long now = System.currentTimeMillis();

        if (type.equals("tickle")) {
            long ready = tickleCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
            if (now < ready) {
                attacker.sendMessage(ChatColor.GRAY + "Tickle Feather cooldown: " + remaining(ready - now));
                return;
            }
            int duration = getConfig().getInt("tickle-feather.slow-falling-seconds", 180);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration * 20, 0));
            tickleCooldowns.put(attacker.getUniqueId(), now + getConfig().getLong("tickle-feather.cooldown-seconds", 300) * 1000L);
            attacker.sendMessage(ChatColor.AQUA + "Tickle Feather activated!");
        } else if (type.equals("suppressor")) {
            long ready = suppressCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
            if (now < ready) {
                attacker.sendMessage(ChatColor.GRAY + "Ability Suppressor cooldown: " + remaining(ready - now));
                return;
            }
            int duration = getConfig().getInt("ability-suppressor.duration-seconds", 300);
            suppressCooldowns.put(attacker.getUniqueId(), now + duration * 1000L);
            for (PotionEffect effect : target.getActivePotionEffects()) {
                if (isBeneficial(effect.getType())) target.removePotionEffect(effect.getType());
            }
            suppressHealth(target, getConfig().getDouble("ability-suppressor.temporary-max-health", 20.0), duration);
            attacker.sendMessage(ChatColor.DARK_PURPLE + "Ability Suppressor activated!");
            target.sendMessage(ChatColor.DARK_PURPLE + "Positive potion effects removed; maximum health suppressed for 5 minutes.");
        } else if (type.equals("restrictor")) {
            restrictedUntil.put(target.getUniqueId(), now + getConfig().getLong("restrictor.duration-seconds", 300) * 1000L);
            if (target.isGliding()) target.setGliding(false);
            attacker.sendMessage(ChatColor.GOLD + "Restrictor activated!");
            target.sendMessage(ChatColor.RED + "Your Elytra, spear and XP bottles are disabled for 5 minutes.");
        } else if (type.equals("armourbreaker")) {
            weakenArmour(target, getConfig().getDouble("armour-breaker.armour-reduction", 8.0),
                getConfig().getInt("armour-breaker.duration-seconds", 10));
        }
    }

    private boolean restricted(Player player) {
        UUID id = player.getUniqueId();
        long until = restrictedUntil.getOrDefault(id, 0L);
        if (until <= System.currentTimeMillis()) {
            restrictedUntil.remove(id);
            return false;
        }
        return true;
    }

    private boolean isSpear(ItemStack item) {
        if (item == null) return false;
        String name = item.getType().name();
        return name.equals("SPEAR") || name.endsWith("_SPEAR");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpearHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (restricted(attacker) && isSpear(attacker.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Your spear is restricted!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpearInteract(PlayerInteractEvent event) {
        if (!restricted(event.getPlayer())) return;
        if (isSpear(event.getItem())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Your spear is restricted!");
        } else if (event.getItem() != null && event.getItem().getType() == Material.EXPERIENCE_BOTTLE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You cannot throw XP bottles while restricted!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && event.isGliding() && restricted(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Your Elytra is restricted!");
        }
    }

    private boolean isBeneficial(PotionEffectType t) {
        return t.equals(PotionEffectType.SPEED) || t.equals(PotionEffectType.SLOW_FALLING)
            || t.equals(PotionEffectType.JUMP_BOOST) || t.equals(PotionEffectType.REGENERATION)
            || t.equals(PotionEffectType.RESISTANCE) || t.equals(PotionEffectType.FIRE_RESISTANCE)
            || t.equals(PotionEffectType.WATER_BREATHING) || t.equals(PotionEffectType.NIGHT_VISION)
            || t.equals(PotionEffectType.INVISIBILITY) || t.equals(PotionEffectType.STRENGTH)
            || t.equals(PotionEffectType.ABSORPTION) || t.equals(PotionEffectType.HASTE)
            || t.equals(PotionEffectType.HEALTH_BOOST) || t.equals(PotionEffectType.SATURATION)
            || t.equals(PotionEffectType.LUCK) || t.equals(PotionEffectType.DOLPHINS_GRACE)
            || t.equals(PotionEffectType.CONDUIT_POWER) || t.equals(PotionEffectType.HERO_OF_THE_VILLAGE);
    }

    private void suppressHealth(Player p, double temp, int seconds) {
        UUID id = p.getUniqueId();
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        savedHealth.putIfAbsent(id, attr.getBaseValue());
        attr.setBaseValue(temp);
        if (p.getHealth() > attr.getValue()) p.setHealth(attr.getValue());
        BukkitTask old = healthTasks.remove(id);
        if (old != null) old.cancel();
        healthTasks.put(id, Bukkit.getScheduler().runTaskLater(this, () -> {
            Player online = Bukkit.getPlayer(id);
            Double original = savedHealth.remove(id);
            healthTasks.remove(id);
            if (online != null && original != null) {
                AttributeInstance max = online.getAttribute(Attribute.MAX_HEALTH);
                if (max != null) max.setBaseValue(original);
                online.sendMessage(ChatColor.GREEN + "Your original maximum health has been restored.");
            }
        }, seconds * 20L));
    }

    private void weakenArmour(Player p, double reduction, int seconds) {
        UUID id = p.getUniqueId();
        AttributeInstance attr = p.getAttribute(Attribute.ARMOR);
        if (attr == null) return;
        attr.removeModifier(armourKey);
        attr.addModifier(new AttributeModifier(armourKey, -Math.abs(reduction),
            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        BukkitTask old = armourTasks.remove(id);
        if (old != null) old.cancel();
        armourTasks.put(id, Bukkit.getScheduler().runTaskLater(this, () -> {
            Player online = Bukkit.getPlayer(id);
            armourTasks.remove(id);
            if (online != null) {
                AttributeInstance a = online.getAttribute(Attribute.ARMOR);
                if (a != null) a.removeModifier(armourKey);
                online.sendMessage(ChatColor.GREEN + "Your armour protection has recovered.");
            }
        }, seconds * 20L));
    }

    private String remaining(long ms) {
        long sec = Math.max(0, (ms + 999) / 1000);
        return (sec / 60) + "m " + (sec % 60) + "s";
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("evilsmpabilities.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission."); return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /evilsmpitems give <player> <tickle|suppressor|armourbreaker|restrictor>"); return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player must be online."); return true; }
        ItemStack item = makeItem(args[2].toLowerCase(Locale.ROOT));
        if (item == null) { sender.sendMessage(ChatColor.RED + "Unknown item type."); return true; }
        for (ItemStack leftover : target.getInventory().addItem(item).values())
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        sender.sendMessage(ChatColor.GREEN + "Item given to " + target.getName() + ".");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("give"))
            return List.of("tickle", "suppressor", "armourbreaker", "restrictor");
        return Collections.emptyList();
    }
}
