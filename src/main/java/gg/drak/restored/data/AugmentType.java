package gg.drak.restored.data;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public enum AugmentType {
    CRAFTING("crafting", "Crafting", Material.CRAFTING_TABLE, Layout.CRAFTING),
    SMELTING("smelting", "Smelting", Material.FURNACE, Layout.FURNACE),
    BLASTING("blasting", "Blasting", Material.BLAST_FURNACE, Layout.FURNACE),
    SMOKING("smoking", "Smoking", Material.SMOKER, Layout.FURNACE),
    GRINDSTONE("grindstone", "Grindstone", Material.GRINDSTONE, Layout.GRINDSTONE),
    ANVIL("anvil", "Anvil", Material.ANVIL, Layout.ANVIL),
    SMITHING("smithing", "Smithing", Material.SMITHING_TABLE, Layout.SMITHING),
    STONECUTTER("stonecutter", "Stonecutter", Material.STONECUTTER, Layout.STONECUTTER),
    LOOM("loom", "Loom", Material.LOOM, Layout.LOOM),
    CARTOGRAPHY("cartography", "Cartography", Material.CARTOGRAPHY_TABLE, Layout.CARTOGRAPHY),
    BREWING("brewing", "Brewing", Material.BREWING_STAND, Layout.BREWING),
    ENCHANTING("enchanting", "Enchanting", Material.ENCHANTING_TABLE, Layout.ENCHANTING),
    ENDER_CHEST("enderchest", "Ender Chest", Material.ENDER_CHEST, Layout.ENDER_CHEST),
    COMPACTOR("compactor", "Compactor", Material.PISTON, Layout.COMPACTOR);

    private final String id;
    private final String displayName;
    private final Material workstationMaterial;
    private final Layout layout;

    AugmentType(String id, String displayName, Material workstationMaterial, Layout layout) {
        this.id = id;
        this.displayName = displayName;
        this.workstationMaterial = workstationMaterial;
        this.layout = layout;
    }

    public String itemTypeTag() {
        return "augment_" + id;
    }

    public String recipeId() {
        return "augment_" + id;
    }

    public static AugmentType fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String key = id.trim().toLowerCase();
        if (key.startsWith("augment_")) {
            key = key.substring("augment_".length());
        }
        for (AugmentType type : values()) {
            if (type.id.equals(key) || type.itemTypeTag().equals(key)) {
                return type;
            }
        }
        return null;
    }

    public static AugmentType fromItemTypeTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return null;
        }
        for (AugmentType type : values()) {
            if (type.itemTypeTag().equals(tag)) {
                return type;
            }
        }
        return null;
    }

    public enum Layout {
        CRAFTING,
        FURNACE,
        GRINDSTONE,
        ANVIL,
        SMITHING,
        STONECUTTER,
        LOOM,
        CARTOGRAPHY,
        BREWING,
        ENCHANTING,
        ENDER_CHEST,
        COMPACTOR
    }
}
