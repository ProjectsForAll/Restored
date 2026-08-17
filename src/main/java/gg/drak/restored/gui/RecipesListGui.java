package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.Restored;
import gg.drak.restored.recipes.ConfiguredRecipe;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecipesListGui extends AbstractInventoryGui {
    private int page;

    public RecipesListGui(Player player) {
        super(player, CornerColor.YELLOW);
        this.page = 0;
    }

    @Override
    public void open() {
        render();
    }

    private void render() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lRestored Recipes");
        List<ConfiguredRecipe> recipes = Restored.getRecipesConfig().getRecipes();
        int perPage = perPage(GuiLayout.SIZE_LARGE);
        int start = page * perPage;
        int end = Math.min(start + perPage, recipes.size());
        int[] slots = GuiLayout.listContentSlots(GuiLayout.SIZE_LARGE);

        for (int i = start, slotIndex = 0; i < end && slotIndex < slots.length; i++, slotIndex++) {
            ConfiguredRecipe recipe = recipes.get(i);
            contents[slots[slotIndex]] = recipeIcon(recipe);
            bindSlot(slots[slotIndex], "recipe:" + recipe.getId());
        }

        placePagination(contents, page, recipes.size());
        finishAndOpen(contents);
    }

    private ItemStack recipeIcon(ConfiguredRecipe recipe) {
        ItemStack icon = recipe.createResult();
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(LegacyColors.color("#bdc8c9Type: #AAAAAA" + recipe.getType().name().toLowerCase()));
            lore.add(LegacyColors.color(recipe.isEnabled() ? "#00FC88Enabled" : "#FF5555Disabled"));
            lore.add("");
            lore.add(LegacyColors.color("#bdc8c9Click to view recipe."));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("__pageprev".equals(key)) {
            page = Math.max(0, page - 1);
            render();
            return;
        }
        if ("__pagenext".equals(key)) {
            page++;
            render();
            return;
        }
        if (key.startsWith("recipe:")) {
            String id = key.substring("recipe:".length());
            ConfiguredRecipe recipe = Restored.getRecipesConfig().getRecipe(id);
            if (recipe != null) {
                new RecipeViewGui(player, recipe, () -> new RecipesListGui(player).open()).open();
            }
        }
    }
}
