package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.Restored;
import gg.drak.restored.recipes.ConfiguredRecipe;
import gg.drak.restored.recipes.RecipeIngredientResolver;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecipeViewGui extends AbstractInventoryGui {
    private static final int[] GRID_SLOTS = {
            11, 12, 13,
            20, 21, 22,
            29, 30, 31
    };
    private static final int ARROW_SLOT = 24;
    private static final int RESULT_SLOT = 25;

    private final ConfiguredRecipe recipe;
    private final Runnable backAction;

    public RecipeViewGui(Player player, ConfiguredRecipe recipe, Runnable backAction) {
        super(player, CornerColor.YELLOW);
        this.recipe = recipe;
        this.backAction = backAction;
    }

    @Override
    public void open() {
        String title = "#FFED6A&lRecipe: " + prettyName(recipe.getId());
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, title);

        ItemStack[][] matrix = recipe.toDisplayMatrix();
        String[][] ingredientIds = recipe.toDisplayIngredientIds();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = GRID_SLOTS[row * 3 + col];
                ItemStack stack = matrix[row][col];
                String ingredientId = ingredientIds[row][col];
                if (stack == null) {
                    contents[slot] = GuiItems.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                    continue;
                }
                ConfiguredRecipe linked = linkedRecipe(ingredientId);
                contents[slot] = linked == null ? stack : withViewRecipeLore(stack);
                if (linked != null) {
                    bindSlot(slot, "ingredient:" + ingredientId);
                }
            }
        }

        contents[ARROW_SLOT] = GuiItems.button(
                Material.ARROW,
                "#FFED6A&lCrafts Into",
                List.of(
                        "#bdc8c9Type: #AAAAAA" + recipe.getType().name().toLowerCase(),
                        recipe.isEnabled() ? "#00FC88Enabled in recipes.yml" : "#FF5555Disabled in recipes.yml"
                )
        );
        contents[RESULT_SLOT] = recipe.createResult();

        placeReturnButton(contents, "back");
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }
        if ("back".equals(key)) {
            player.closeInventory();
            if (backAction != null) {
                backAction.run();
            }
            return;
        }
        if (key.startsWith("ingredient:")) {
            String ingredientId = key.substring("ingredient:".length());
            ConfiguredRecipe linked = linkedRecipe(ingredientId);
            if (linked == null) {
                return;
            }
            new RecipeViewGui(player, linked, () -> new RecipeViewGui(player, recipe, backAction).open()).open();
        }
    }

    private ConfiguredRecipe linkedRecipe(String ingredientId) {
        if (ingredientId == null || !RecipeIngredientResolver.isExactCustom(ingredientId)) {
            return null;
        }
        ConfiguredRecipe linked = Restored.getRecipesConfig().findRecipeForIngredient(ingredientId);
        if (linked == null || linked.getId().equalsIgnoreCase(recipe.getId())) {
            return null;
        }
        return linked;
    }

    private static ItemStack withViewRecipeLore(ItemStack stack) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add(LegacyColors.color("#bdc8c9Click to view this item's recipe."));
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private static String prettyName(String id) {
        String[] parts = id.replace('_', ' ').split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
