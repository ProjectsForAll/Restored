package gg.drak.restored.util;

import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PotionTypeSupportTest {

    @Test
    void stripsLongAndStrongVariants() {
        assertEquals(PotionType.SWIFTNESS, PotionTypeSupport.basePotionType(PotionType.LONG_SWIFTNESS));
        assertEquals(PotionType.STRENGTH, PotionTypeSupport.basePotionType(PotionType.STRONG_STRENGTH));
        assertEquals(PotionType.WATER, PotionTypeSupport.basePotionType(null));
    }

    @Test
    void appliesPotionModifiers() {
        assertEquals(PotionType.LONG_SWIFTNESS,
                PotionTypeSupport.modifiedPotionType(PotionType.SWIFTNESS, true, false));
        assertEquals(PotionType.STRONG_SWIFTNESS,
                PotionTypeSupport.modifiedPotionType(PotionType.LONG_SWIFTNESS, false, true));
        assertEquals(PotionType.SWIFTNESS,
                PotionTypeSupport.modifiedPotionType(PotionType.SWIFTNESS, false, false));
        assertEquals(PotionType.WATER,
                PotionTypeSupport.modifiedPotionType(PotionType.WATER, true, false));
    }

    @Test
    void appliesFermentedSpiderEyeConversions() {
        assertEquals(PotionType.INVISIBILITY, PotionTypeSupport.corrupt(PotionType.NIGHT_VISION));
        assertEquals(PotionType.SLOWNESS, PotionTypeSupport.corrupt(PotionType.LONG_SWIFTNESS));
        assertEquals(PotionType.WEAKNESS, PotionTypeSupport.corrupt(PotionType.WATER));
    }
}
