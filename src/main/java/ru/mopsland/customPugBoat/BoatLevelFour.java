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

public class BoatLevelFour implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey hpKey;
    private final NamespacedKey recipeKey;
    private final int MAX3 = 300;
    private final int MAX4 = 400;

    public BoatLevelFour(JavaPlugin plugin) {
        this.plugin = plugin;
        this.hpKey = new NamespacedKey(plugin, "boat_hp");
        this.recipeKey = new NamespacedKey(plugin, "boat_level4");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerRecipe();
    }

    private void registerRecipe() {
        ItemStack result = createLevelFourBoat(Material.OAK_BOAT);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        recipe.shape(" I ", " B ", "   ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('B', Material.OAK_BOAT);
        Bukkit.addRecipe(recipe);
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || !event.getRecipe().getResult().hasItemMeta()) return;

        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean valid = false;
        for (ItemStack item : matrix) {
            if (item != null && item.getType().name().endsWith("_BOAT") && isLevelThreeBoat(item)) {
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
        boolean hasIron = false;

        for (ItemStack item : matrix) {
            if (item == null) continue;
            if (item.getType() == Material.IRON_INGOT) hasIron = true;
            else if (isLevelThreeBoat(item)) hasBoat = true;
        }

        if (!hasBoat || !hasIron) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        p.getInventory().addItem(createLevelFourBoat(getBoatTypeFromMatrix(matrix)));

        // Удаляем ВСЕ предметы из матрицы
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        p.updateInventory();
    }

    private boolean isLevelThreeBoat(ItemStack item) {
        if (!item.hasItemMeta()) return false;
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        return data.has(hpKey, PersistentDataType.INTEGER) &&
                data.get(hpKey, PersistentDataType.INTEGER) == MAX3;
    }

    private Material getBoatTypeFromMatrix(ItemStack[] matrix) {
        for (ItemStack item : matrix) {
            if (item != null && isLevelThreeBoat(item)) {
                return item.getType();
            }
        }
        return Material.OAK_BOAT;
    }

    private ItemStack createLevelFourBoat(Material baseType) {
        ItemStack result = new ItemStack(baseType);
        ItemMeta meta = result.getItemMeta();
        meta.setDisplayName(getBoatDisplayName(baseType) + " 4 Уровень");
        meta.setLore(List.of("HP: " + MAX4 + "/" + MAX4));
        meta.getPersistentDataContainer().set(hpKey, PersistentDataType.INTEGER, MAX4);
        result.setItemMeta(meta);
        return result;
    }

    public static String getBoatDisplayName(Material material) {
        return switch (material) {
            case OAK_BOAT -> "Дубовая лодка";
            case BIRCH_BOAT -> "Берёзовая лодка";
            case SPRUCE_BOAT -> "Еловая лодка";
            case JUNGLE_BOAT -> "Тропическая лодка";
            case ACACIA_BOAT -> "Акациевая лодка";
            case DARK_OAK_BOAT -> "Тёмная лодка";
            case MANGROVE_BOAT -> "Мангровая лодка";
            case BAMBOO_RAFT -> "Бамбуковый плот";
            case CHERRY_BOAT -> "Вишнёвая лодка";
            case OAK_CHEST_BOAT -> "Дубовая лодка с сундуком";
            case BIRCH_CHEST_BOAT -> "Берёзовая лодка с сундуком";
            case SPRUCE_CHEST_BOAT -> "Еловая лодка с сундуком";
            case JUNGLE_CHEST_BOAT -> "Тропическая лодка с сундуком";
            case ACACIA_CHEST_BOAT -> "Акациевая лодка с сундуком";
            case DARK_OAK_CHEST_BOAT -> "Тёмная лодка с сундуком";
            case MANGROVE_CHEST_BOAT -> "Мангровая лодка с сундуком";
            case BAMBOO_CHEST_RAFT -> "Бамбуковый плот с сундуком";
            case CHERRY_CHEST_BOAT -> "Вишнёвая лодка с сундуком";
            default -> "Лодка";
        };
    }
}
