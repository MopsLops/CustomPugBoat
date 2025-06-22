package ru.mopsland.customPugBoat;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CustomPugBoat extends JavaPlugin implements Listener {
    private final int MAX_HP = 100;
    private final int MAX_HP_LEVEL_2 = 200;
    private final int MAX_HP_LEVEL_3 = 300;
    private final int MAX_HP_LEVEL_4 = 400;
    private final int MAX_HP_LEVEL_5 = 500;
    private final int MAX_HP_LEVEL_6 = 600;
    private NamespacedKey hpKey;
    private FileConfiguration boatDataConfig;
    private File boatDataFile;
    private final Map<UUID, PlayerExp> playerExpMap = new HashMap<>();

    private static class PlayerExp {
        final int level;
        final float exp;
        PlayerExp(int level, float exp) {
            this.level = level;
            this.exp = exp;
        }
    }

    @Override
    public void onEnable() {
        hpKey = new NamespacedKey(this, "boat_hp");
        Bukkit.getPluginManager().registerEvents(this, this);
        new BoatLevelTwo(this);
        new BoatLevelThree(this);
        new BoatLevelFour(this);
        new BoatLevelFive(this);
        new BoatLevelSix(this);
        loadBoatData();
        restoreBoatData();
    }

    @Override
    public void onDisable() {
        saveBoatData();
    }

    private void loadBoatData() {
        boatDataFile = new File(getDataFolder(), "boats.yml");
        if (!boatDataFile.exists()) {
            boatDataFile.getParentFile().mkdirs();
            saveResource("boats.yml", false);
        }
        boatDataConfig = YamlConfiguration.loadConfiguration(boatDataFile);
    }

    private void saveBoatData() {
        try {
            boatDataConfig.save(boatDataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void restoreBoatData() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isBoatEntity(entity)) {
                    UUID id = entity.getUniqueId();
                    int hp = boatDataConfig.getInt("boats." + id, MAX_HP);
                    entity.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, hp);
                    updateBoatName(entity, hp);
                }
            }
        }
    }

    @EventHandler
    public void onBoatPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !isBoatMaterial(item.getType())) return;

        int hpFromItem = extractHpFromItem(item);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Entity ent : event.getPlayer().getWorld().getNearbyEntities(event.getClickedBlock().getLocation(), 2, 2, 2)) {
                    if (isBoatEntity(ent)) {
                        ent.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, hpFromItem);
                        saveBoatHp(ent.getUniqueId(), hpFromItem);
                        updateBoatName(ent, hpFromItem);
                        ent.setCustomNameVisible(true);
                        break;
                    }
                }
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler
    public void onBoatSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (!isBoatEntity(entity)) return;
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (!container.has(hpKey, PersistentDataType.INTEGER)) {
            container.set(hpKey, PersistentDataType.INTEGER, MAX_HP);
            saveBoatHp(entity.getUniqueId(), MAX_HP);
        }
    }

    @EventHandler
    public void onBoatMove(VehicleMoveEvent event) {
        Entity boat = event.getVehicle();
        if (!isBoatEntity(boat)) return;

        PersistentDataContainer data = boat.getPersistentDataContainer();
        int currentHP = data.getOrDefault(hpKey, PersistentDataType.INTEGER, MAX_HP);

        if (event.getFrom().distanceSquared(event.getTo()) > 0.001) {
            if (currentHP > 0) {
                currentHP--;
                data.set(hpKey, PersistentDataType.INTEGER, currentHP);
                saveBoatHp(boat.getUniqueId(), currentHP);
            }

            if (currentHP <= 0) {
                boat.remove();
                boat.getWorld().createExplosion(boat.getLocation(), 0F);
                removeBoatHp(boat.getUniqueId());
            } else {
                int maxHp = getMaxHpFor(currentHP);
                float progress = Math.max(0.0f, Math.min(1.0f, (float) currentHP / maxHp));
                for (Entity passenger : boat.getPassengers()) {
                    if (passenger instanceof Player player) {
                        player.setLevel(0);
                        player.setExp(progress);
                    }
                }
            }
        }
    }

    private int getMaxHpFor(int hp) {
        if (hp > MAX_HP_LEVEL_5) return MAX_HP_LEVEL_6;
        if (hp > MAX_HP_LEVEL_4) return MAX_HP_LEVEL_5;
        if (hp > MAX_HP_LEVEL_3) return MAX_HP_LEVEL_4;
        if (hp > MAX_HP_LEVEL_2) return MAX_HP_LEVEL_3;
        if (hp > MAX_HP) return MAX_HP_LEVEL_2;
        return MAX_HP;
    }

    @EventHandler
    public void onBoatDamage(EntityDamageEvent event) {
        Entity boat = event.getEntity();
        if (!isBoatEntity(boat)) return;
        PersistentDataContainer data = boat.getPersistentDataContainer();
        int currentHP = data.getOrDefault(hpKey, PersistentDataType.INTEGER, MAX_HP);
        currentHP -= (int) event.getFinalDamage();
        if (currentHP <= 0) {
            boat.remove();
            boat.getWorld().createExplosion(boat.getLocation(), 0F);
            removeBoatHp(boat.getUniqueId());
        } else {
            data.set(hpKey, PersistentDataType.INTEGER, currentHP);
            saveBoatHp(boat.getUniqueId(), currentHP);
        }
    }

    @EventHandler
    public void onBoatBreak(VehicleDestroyEvent event) {
        Entity boat = event.getVehicle();
        if (!isBoatEntity(boat)) return;
        PersistentDataContainer data = boat.getPersistentDataContainer();
        int currentHP = data.getOrDefault(hpKey, PersistentDataType.INTEGER, MAX_HP);
        event.setCancelled(true);
        removeBoatHp(boat.getUniqueId());
        boat.remove();
        if (currentHP > 0) {
            dropBoatItem(boat, currentHP);
        }
    }

    @EventHandler
    public void onEnterBoat(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        Entity boat = event.getVehicle();
        if (!isBoatEntity(boat)) return;
        playerExpMap.put(player.getUniqueId(), new PlayerExp(player.getLevel(), player.getExp()));
        boat.setCustomNameVisible(false);
    }

    @EventHandler
    public void onExitBoat(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        Entity boat = event.getVehicle();
        if (!isBoatEntity(boat)) return;
        PlayerExp saved = playerExpMap.remove(player.getUniqueId());
        if (saved != null) {
            player.setLevel(saved.level);
            player.setExp(saved.exp);
        }
        int hp = boat.getPersistentDataContainer().getOrDefault(hpKey, PersistentDataType.INTEGER, MAX_HP);
        updateBoatName(boat, hp);
        boat.setCustomNameVisible(true);
    }

    private void updateBoatName(Entity boat, int hp) {
        String levelStr;
        int max;
        if (hp > MAX_HP_LEVEL_5) {
            levelStr = "шестого уровня";
            max = MAX_HP_LEVEL_6;
        } else if (hp > MAX_HP_LEVEL_4) {
            levelStr = "пятого уровня";
            max = MAX_HP_LEVEL_5;
        } else if (hp > MAX_HP_LEVEL_3) {
            levelStr = "четвёртого уровня";
            max = MAX_HP_LEVEL_4;
        } else if (hp > MAX_HP_LEVEL_2) {
            levelStr = "третьего уровня";
            max = MAX_HP_LEVEL_3;
        } else if (hp > MAX_HP) {
            levelStr = "второго уровня";
            max = MAX_HP_LEVEL_2;
        } else {
            levelStr = "первого уровня";
            max = MAX_HP;
        }
        boat.customName(Component.text("Лодка " + levelStr + " HP: " + hp + "/" + max));
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !isBoatMaterial(result.getType())) return;
        ItemMeta meta = result.getItemMeta();
        meta.setLore(List.of("HP: " + MAX_HP + "/" + MAX_HP));
        meta.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, MAX_HP);
        result.setItemMeta(meta);
    }

    @EventHandler
    public void onCreativeInventory(InventoryCreativeEvent event) {
        ItemStack item = event.getCursor();
        if (item == null || !isBoatMaterial(item.getType())) return;
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of("HP: " + MAX_HP + "/" + MAX_HP));
        meta.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, MAX_HP);
        item.setItemMeta(meta);
        event.setCursor(item);
    }

    private void dropBoatItem(Entity boat, int hp) {
        if (hp <= 0) return;
        Material material = getBoatMaterial(boat);
        if (material != null) {
            ItemStack drop = new ItemStack(material);
            ItemMeta meta = drop.getItemMeta();
            String baseName = BoatLevelTwo.getBoatDisplayName(material);
            String level = switch (getMaxHpFor(hp)) {
                case 600 -> " 6 Уровень";
                case 500 -> " 5 Уровень";
                case 400 -> " 4 Уровень";
                case 300 -> " 3 Уровень";
                case 200 -> " 2 Уровень";
                default -> "";
            };
            meta.setDisplayName(baseName + level);
            meta.setLore(List.of("HP: " + hp + "/" + getMaxHpFor(hp)));
            meta.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, hp);
            drop.setItemMeta(meta);
            boat.getWorld().dropItemNaturally(boat.getLocation(), drop);
        }
    }

    private int extractHpFromItem(ItemStack item) {
        if (item == null) return MAX_HP;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return MAX_HP;
        PersistentDataContainer itemData = meta.getPersistentDataContainer();
        if (itemData.has(hpKey, PersistentDataType.INTEGER)) {
            Integer val = itemData.get(hpKey, PersistentDataType.INTEGER);
            if (val != null) return val;
        }
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (line.startsWith("HP: ")) {
                    try {
                        return Integer.parseInt(line.substring(4).split("/")[0].trim());
                    } catch (Exception ignored) {}
                }
            }
        }
        return MAX_HP;
    }

    private boolean isBoatEntity(Entity entity) {
        return (entity instanceof Boat || entity instanceof ChestBoat);
    }

    private boolean isBoatMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT");
    }

    private Material getBoatMaterial(Entity boat) {
        if (boat instanceof ChestBoat cb) {
            return Material.valueOf(cb.getBoatType().name() + "_CHEST_BOAT");
        } else if (boat instanceof Boat b) {
            return Material.valueOf(b.getBoatType().name() + "_BOAT");
        }
        return null;
    }

    private void saveBoatHp(UUID boatId, int hp) {
        boatDataConfig.set("boats." + boatId.toString(), hp);
        saveBoatData();
    }

    private void removeBoatHp(UUID boatId) {
        boatDataConfig.set("boats." + boatId.toString(), null);
        saveBoatData();
    }
}