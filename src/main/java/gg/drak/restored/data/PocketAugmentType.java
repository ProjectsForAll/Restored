package gg.drak.restored.data;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public enum PocketAugmentType {
    FEEDING("feeding", "Feeding", Material.GOLDEN_CARROT),
    QUIVER("quiver", "Quiver", Material.ARROW),
    ROCKET_DISTRIBUTER("rocket_distributer", "Rocket Distributer", Material.FIREWORK_ROCKET),
    BACKPACK("backpack", "Backpack", Material.CHEST),
    MAGNET("magnet", "Magnet", Material.COMPASS);

    private final String id;
    private final String displayName;
    private final Material icon;

    PocketAugmentType(String id, String displayName, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String itemTypeTag() {
        return "pocket_augment_" + id;
    }

    public String recipeId() {
        return "pocket_augment_" + id;
    }

    public static PocketAugmentType fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim().toLowerCase();
        if (key.startsWith("pocket_augment_")) {
            key = key.substring("pocket_augment_".length());
        }
        for (PocketAugmentType type : values()) {
            if (type.id.equals(key) || type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }

    public static PocketAugmentType fromItemTypeTag(String tag) {
        if (tag == null) {
            return null;
        }
        for (PocketAugmentType type : values()) {
            if (type.itemTypeTag().equals(tag)) {
                return type;
            }
        }
        return null;
    }
}
