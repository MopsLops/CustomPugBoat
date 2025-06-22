package ru.mopsland.customPugBoat;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class BoatLevelThree implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey hpKey;
    private final int MAX2 = 200;
    private final int MAX3 = 300;

    public BoatLevelThree(JavaPlugin plugin) {
        this.plugin = plugin;
        this.hpKey = new NamespacedKey(plugin, "boat_hp");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || !event.getRecipe().getResult().hasItemMeta()) return;

        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean valid = false;
        for (ItemStack item : matrix) {
            if (item != null && item.getType().name().endsWith("_BOAT") && isLevelTwoBoat(item)) {
                valid = true;
                break;
            }
        }

        if (!valid) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!(event.getInventory() instanceof CraftingInventory inv)) return;
        if (event.getRecipe() == null || !event.getRecipe().getResult().hasItemMeta()) return;

        ItemStack[] matrix = inv.getMatrix();
        boolean hasBoat = false;
        boolean hasGold = false;

        for (ItemStack item : matrix) {
            if (item == null) continue;
            if (item.getType() == Material.GOLD_INGOT) hasGold = true;
            else if (isLevelTwoBoat(item)) hasBoat = true;
        }

        if (!hasBoat || !hasGold) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        p.getInventory().addItem(createLevelThreeBoat(getBoatTypeFromMatrix(matrix)));

        // Удаляем ВСЕ предметы из матрицы
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        p.updateInventory();
    }

    private boolean isLevelTwoBoat(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(hpKey, PersistentDataType.INTEGER) &&
                data.get(hpKey, PersistentDataType.INTEGER) == MAX2;
    }

    private Material getBoatTypeFromMatrix(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && isLevelTwoBoat(item)) {
                return item.getType();
            }
        }
        return Material.OAK_BOAT;
    }

    private ItemStack createLevelThreeBoat(Material baseType) {
        ItemStack result = new ItemStack(baseType);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(BoatLevelTwo.getBoatDisplayName(baseType) + " 3 Уровень");
        meta.setLore(List.of("HP: " + MAX3 + "/" + MAX3));
        meta.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, MAX3);
        result.setItemMeta(meta);
        return result;
    }
}
