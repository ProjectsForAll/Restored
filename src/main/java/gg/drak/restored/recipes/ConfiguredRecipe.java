package gg.drak.restored.recipes;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class ConfiguredRecipe {
    public enum Type {
        SHAPED,
        SHAPELESS
    }

    private final String id;
    private final boolean enabled;
    private final Type type;
    private final String resultId;
    private final List<String> shape;
    private final Map<Character, String> shapedIngredients;
    private final List<String> shapelessIngredients;

    public ConfiguredRecipe(
            String id,
            boolean enabled,
            Type type,
            String resultId,
            List<String> shape,
            Map<Character, String> shapedIngredients,
            List<String> shapelessIngredients
    ) {
        this.id = id;
        this.enabled = enabled;
        this.type = type;
        this.resultId = resultId;
        this.shape = shape == null ? List.of() : List.copyOf(shape);
        this.shapedIngredients = shapedIngredients == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(shapedIngredients));
        this.shapelessIngredients = shapelessIngredients == null
                ? List.of()
                : List.copyOf(shapelessIngredients);
    }

    public ItemStack createResult() {
        return RecipeIngredientResolver.resolve(resultId);
    }

    /**
     * 3x3 matrix of stacks for GUI display (null = empty).
     */
    public ItemStack[][] toDisplayMatrix() {
        ItemStack[][] matrix = new ItemStack[3][3];
        String[][] ids = toDisplayIngredientIds();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                String ingredientId = ids[row][col];
                if (ingredientId != null) {
                    matrix[row][col] = RecipeIngredientResolver.resolve(ingredientId);
                }
            }
        }
        return matrix;
    }

    /**
     * 3x3 matrix of ingredient ids for GUI binding (null = empty).
     */
    public String[][] toDisplayIngredientIds() {
        String[][] matrix = new String[3][3];
        if (type == Type.SHAPED) {
            for (int row = 0; row < 3; row++) {
                String line = row < shape.size() ? shape.get(row) : "   ";
                for (int col = 0; col < 3; col++) {
                    char key = col < line.length() ? line.charAt(col) : ' ';
                    if (key == ' ') {
                        continue;
                    }
                    matrix[row][col] = shapedIngredients.get(key);
                }
            }
            return matrix;
        }

        List<String> ingredients = shapelessIngredients;
        int index = 0;
        for (int row = 0; row < 3 && index < ingredients.size(); row++) {
            for (int col = 0; col < 3 && index < ingredients.size(); col++) {
                matrix[row][col] = ingredients.get(index++);
            }
        }
        return matrix;
    }

    public List<ItemStack> toIngredientStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (String ingredientId : getAllIngredientIds()) {
            stacks.add(RecipeIngredientResolver.resolve(ingredientId));
        }
        return stacks;
    }

    public List<String> getAllIngredientIds() {
        List<String> ids = new ArrayList<>();
        if (type == Type.SHAPED) {
            for (String line : shape) {
                for (int i = 0; i < line.length(); i++) {
                    char key = line.charAt(i);
                    if (key == ' ') {
                        continue;
                    }
                    String ingredientId = shapedIngredients.get(key);
                    if (ingredientId != null) {
                        ids.add(ingredientId);
                    }
                }
            }
            return ids;
        }
        ids.addAll(shapelessIngredients);
        return ids;
    }

    /**
     * Restored type tags (e.g. {@code component}) this recipe accepts as custom ExactChoice ingredients.
     */
    public List<String> getAllowedRestoredTypes() {
        List<String> types = new ArrayList<>();
        for (String ingredientId : getAllIngredientIds()) {
            String typeTag = RecipeIngredientResolver.restoredTypeOfIngredientId(ingredientId);
            if (typeTag != null && !types.contains(typeTag)) {
                types.add(typeTag);
            }
        }
        return types;
    }
}
