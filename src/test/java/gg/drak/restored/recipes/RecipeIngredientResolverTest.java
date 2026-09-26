package gg.drak.restored.recipes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeIngredientResolverTest {

    @Test
    void recognizesPlankAliases() {
        assertTrue(RecipeIngredientResolver.isPlanksTag("PLANKS"));
        assertTrue(RecipeIngredientResolver.isPlanksTag("minecraft:planks"));
        assertTrue(RecipeIngredientResolver.isPlanksTag("oak_planks"));
        assertFalse(RecipeIngredientResolver.isPlanksTag("spruce_log"));
    }

    @Test
    void canonicalizesCustomAliases() {
        assertEquals("network_core", RecipeIngredientResolver.canonicalId("core"));
        assertEquals("network_hopper_output", RecipeIngredientResolver.canonicalId("hopper_output"));
        assertEquals("magnet_pocket_augment", RecipeIngredientResolver.canonicalId("pocket_augment_magnet"));
        assertEquals("", RecipeIngredientResolver.canonicalId(" "));
    }
}
