package gg.drak.restored.gui.augments;

import gg.drak.restored.data.AugmentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AugmentRecipeService {

    private AugmentRecipeService() {
    }

    public static ItemStack matchCrafting(ItemStack[] matrix3x3) {
        if (matrix3x3 == null || matrix3x3.length != 9) {
            return null;
        }
        ItemStack[] craftMatrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = matrix3x3[i];
            craftMatrix[i] = stack == null || stack.getType().isAir() ? null : stack.clone();
        }

        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof ShapedRecipe shaped && matchesShaped(shaped, craftMatrix)) {
                return recipe.getResult().clone();
            }
            if (recipe instanceof ShapelessRecipe shapeless && matchesShapeless(shapeless, craftMatrix)) {
                return recipe.getResult().clone();
            }
        }
        return null;
    }

    public static CookingMatch matchCooking(AugmentType type, ItemStack input) {
        if (input == null || input.getType().isAir()) {
            return null;
        }
        Class<? extends CookingRecipe<?>> recipeClass = switch (type) {
            case SMELTING -> FurnaceRecipe.class;
            case BLASTING -> BlastingRecipe.class;
            case SMOKING -> SmokingRecipe.class;
            default -> null;
        };
        if (recipeClass == null) {
            return null;
        }

        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (!recipeClass.isInstance(recipe)) {
                continue;
            }
            CookingRecipe<?> cooking = (CookingRecipe<?>) recipe;
            if (cooking.getInputChoice().test(input)) {
                return new CookingMatch(cooking.getResult().clone(), cooking.getCookingTime(), cooking.getExperience());
            }
        }
        return null;
    }

    public static List<ItemStack> matchStonecutter(ItemStack input) {
        List<ItemStack> results = new ArrayList<>();
        if (input == null || input.getType().isAir()) {
            return results;
        }
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof StonecuttingRecipe cutting && cutting.getInputChoice().test(input)) {
                results.add(cutting.getResult().clone());
            }
        }
        return results;
    }

    public static ItemStack matchSmithing(ItemStack template, ItemStack base, ItemStack addition) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof SmithingTransformRecipe transform) {
                if (testChoice(transform.getTemplate(), template)
                        && testChoice(transform.getBase(), base)
                        && testChoice(transform.getAddition(), addition)) {
                    return transform.getResult().clone();
                }
            } else if (recipe instanceof SmithingRecipe smithing) {
                if (testChoice(smithing.getBase(), base) && testChoice(smithing.getAddition(), addition)) {
                    return smithing.getResult().clone();
                }
            }
        }
        return null;
    }

    public static int fuelBurnTicks(ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir()) {
            return 0;
        }
        // Common vanilla furnace fuels (ticks). Kept local for Folia API parity.
        return switch (fuel.getType()) {
            case LAVA_BUCKET -> 20000;
            case COAL_BLOCK -> 16000;
            case DRIED_KELP_BLOCK -> 4000;
            case BLAZE_ROD -> 2400;
            case COAL, CHARCOAL -> 1600;
            case OAK_BOAT, SPRUCE_BOAT, BIRCH_BOAT, JUNGLE_BOAT,
                 ACACIA_BOAT, DARK_OAK_BOAT, MANGROVE_BOAT, CHERRY_BOAT, BAMBOO_RAFT -> 400;
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 0;
            default -> {
                String name = fuel.getType().name();
                if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS")
                        || name.endsWith("_SAPLING") || name.endsWith("_FENCE")
                        || name.endsWith("_DOOR") || name.endsWith("_SIGN")
                        || name.endsWith("_STAIRS") || name.endsWith("_SLAB")
                        || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")
                        || name.endsWith("_TRAPDOOR") || name.contains("BANNER")
                        || name.contains("CARPET") || name.equals("STICK")
                        || name.equals("BOWL") || name.equals("LADDER")
                        || name.equals("CRAFTING_TABLE") || name.equals("CHEST")
                        || name.equals("TRAPPED_CHEST") || name.equals("DAYLIGHT_DETECTOR")
                        || name.equals("NOTE_BLOCK") || name.equals("JUKEBOX")
                        || name.equals("BOOKSHELF") || name.equals("CHISELED_BOOKSHELF")
                        || name.equals("LECTERN") || name.equals("COMPOSTER")
                        || name.equals("BARREL") || name.equals("SMITHING_TABLE")
                        || name.equals("FLETCHING_TABLE") || name.equals("LOOM")
                        || name.equals("CARTOGRAPHY_TABLE")) {
                    yield name.endsWith("_SLAB") ? 150 : 300;
                }
                if (name.endsWith("_WOOL") || name.equals("DRIED_KELP")) {
                    yield 100;
                }
                if (fuel.getType() == Material.BAMBOO || name.equals("SCAFFOLDING")) {
                    yield 50;
                }
                yield 0;
            }
        };
    }

    private static boolean testChoice(org.bukkit.inventory.RecipeChoice choice, ItemStack stack) {
        if (choice == null) {
            return stack == null || stack.getType().isAir();
        }
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return choice.test(stack);
    }

    private static boolean matchesShaped(ShapedRecipe recipe, ItemStack[] matrix) {
        String[] shape = recipe.getShape();
        ItemStack[][] recipeGrid = new ItemStack[3][3];
        for (int row = 0; row < shape.length && row < 3; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                char key = line.charAt(col);
                if (key == ' ') {
                    continue;
                }
                var choiceMap = recipe.getChoiceMap();
                org.bukkit.inventory.RecipeChoice choice = choiceMap.get(key);
                if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice materialChoice) {
                    List<Material> materials = materialChoice.getChoices();
                    if (!materials.isEmpty()) {
                        recipeGrid[row][col] = new ItemStack(materials.get(0));
                    }
                } else if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exactChoice) {
                    List<ItemStack> choices = exactChoice.getChoices();
                    if (!choices.isEmpty()) {
                        recipeGrid[row][col] = choices.get(0).clone();
                    }
                }
            }
        }

        // Try all offsets within 3x3
        for (int rowOff = 0; rowOff < 3; rowOff++) {
            for (int colOff = 0; colOff < 3; colOff++) {
                if (shapedFits(recipe, matrix, rowOff, colOff)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean shapedFits(ShapedRecipe recipe, ItemStack[] matrix, int rowOff, int colOff) {
        String[] shape = recipe.getShape();
        boolean[][] used = new boolean[3][3];
        for (int row = 0; row < shape.length; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length(); col++) {
                int r = row + rowOff;
                int c = col + colOff;
                if (r >= 3 || c >= 3) {
                    return false;
                }
                char key = line.charAt(col);
                ItemStack inSlot = matrix[r * 3 + c];
                if (key == ' ') {
                    if (inSlot != null && !inSlot.getType().isAir()) {
                        return false;
                    }
                    used[r][c] = true;
                    continue;
                }
                org.bukkit.inventory.RecipeChoice choice = recipe.getChoiceMap().get(key);
                if (choice == null || inSlot == null || !choice.test(inSlot)) {
                    return false;
                }
                used[r][c] = true;
            }
        }
        for (int i = 0; i < 9; i++) {
            int r = i / 3;
            int c = i % 3;
            if (!used[r][c]) {
                ItemStack extra = matrix[i];
                if (extra != null && !extra.getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(ShapelessRecipe recipe, ItemStack[] matrix) {
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack stack : matrix) {
            if (stack != null && !stack.getType().isAir()) {
                inputs.add(stack);
            }
        }
        List<org.bukkit.inventory.RecipeChoice> choices = new ArrayList<>(recipe.getChoiceList());
        if (inputs.size() != choices.size()) {
            return false;
        }
        boolean[] used = new boolean[choices.size()];
        for (ItemStack input : inputs) {
            boolean matched = false;
            for (int i = 0; i < choices.size(); i++) {
                if (used[i]) {
                    continue;
                }
                if (choices.get(i).test(input)) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    public record CookingMatch(ItemStack result, int cookTimeTicks, float experience) {
    }
}
