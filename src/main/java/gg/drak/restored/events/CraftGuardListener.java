package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.items.RestoredItems;
import gg.drak.restored.recipes.ConfiguredRecipe;
import gg.drak.restored.recipes.RecipeRegistrar;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Restored custom items and vanilla items from substituting for each other in crafting.
 * <ul>
 *   <li>Custom items cannot craft vanilla results</li>
 *   <li>Custom items cannot fill vanilla MaterialChoice slots (e.g. component as iron ingot)</li>
 *   <li>Vanilla items cannot fill Restored ExactChoice slots</li>
 * </ul>
 */
public class CraftGuardListener implements Listener {

    public CraftGuardListener() {
        Restored.getInstance().registerListener(this);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }
        if (isIllegalCraft(inventory, event.getRecipe(), result)) {
            inventory.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }
        if (isIllegalCraft(event.getInventory(), event.getRecipe(), result)) {
            event.setCancelled(true);
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    private static boolean isIllegalCraft(CraftingInventory inventory, Recipe recipe, ItemStack result) {
        ItemStack[] matrix = inventory.getMatrix();
        if (matrix == null) {
            return false;
        }

        List<ItemStack> restoredInMatrix = new ArrayList<>();
        for (ItemStack stack : matrix) {
            if (RestoredItems.isRestoredItem(stack)) {
                restoredInMatrix.add(stack);
            }
        }

        boolean restoredResult = RestoredItems.isRestoredItem(result);
        boolean ourRecipe = RecipeRegistrar.isRestoredRecipe(recipe);

        // Custom ingredients must never produce a vanilla result.
        if (!restoredInMatrix.isEmpty() && !restoredResult) {
            return true;
        }

        // Non-Restored recipes must never consume Restored custom items.
        if (!restoredInMatrix.isEmpty() && !ourRecipe) {
            return true;
        }

        // Restored recipes: custom matrix items may only fill declared custom ingredients
        // (e.g. network_component recipe has only vanilla slots → no custom items allowed).
        if (!restoredInMatrix.isEmpty() && ourRecipe) {
            ConfiguredRecipe configured = RecipeRegistrar.getConfigured(recipe);
            if (configured == null) {
                return true;
            }
            List<String> allowedTypes = configured.getAllowedRestoredTypes();
            for (ItemStack custom : restoredInMatrix) {
                String type = RestoredItems.getType(custom).orElse(null);
                if (type == null || !allowedTypes.contains(type)) {
                    return true;
                }
            }
        }

        return false;
    }
}
