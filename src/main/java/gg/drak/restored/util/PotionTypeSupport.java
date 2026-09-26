package gg.drak.restored.util;

import org.bukkit.potion.PotionType;

/** Version-stable helpers for the modern PotionMeta base potion type API. */
public final class PotionTypeSupport {

    private PotionTypeSupport() {
    }

    public static PotionType basePotionType(PotionType type) {
        if (type == null) {
            return PotionType.WATER;
        }
        String name = type.name();
        if (name.startsWith("LONG_") || name.startsWith("STRONG_")) {
            return PotionType.valueOf(name.substring(name.indexOf('_') + 1));
        }
        return type;
    }

    public static PotionType modifiedPotionType(PotionType type, boolean extended, boolean upgraded) {
        String prefix = upgraded ? "STRONG_" : extended ? "LONG_" : "";
        PotionType base = basePotionType(type);
        if (prefix.isEmpty()) {
            return base;
        }
        try {
            return PotionType.valueOf(prefix + base.name());
        } catch (IllegalArgumentException ignored) {
            // Not every potion type has a long or strong variant.
            return base;
        }
    }

    public static PotionType corrupt(PotionType type) {
        return switch (basePotionType(type)) {
            case NIGHT_VISION -> PotionType.INVISIBILITY;
            case SWIFTNESS -> PotionType.SLOWNESS;
            case LEAPING -> PotionType.SLOWNESS;
            case HEALING -> PotionType.HARMING;
            case POISON -> PotionType.HARMING;
            case REGENERATION -> PotionType.WEAKNESS;
            default -> PotionType.WEAKNESS;
        };
    }
}
