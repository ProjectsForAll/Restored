package gg.drak.restored.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/** Resolves the vanilla compact/decompact counterpart and recipe ratio. */
public final class CompactionResolver {
    private static final Map<Material, Pair> PAIRS = new EnumMap<>(Material.class);

    static {
        pair(Material.COAL, Material.COAL_BLOCK, 9);
        pair(Material.IRON_INGOT, Material.IRON_BLOCK, 9);
        pair(Material.GOLD_INGOT, Material.GOLD_BLOCK, 9);
        pair(Material.COPPER_INGOT, Material.COPPER_BLOCK, 9);
        pair(Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, 9);
        pair(Material.DIAMOND, Material.DIAMOND_BLOCK, 9);
        pair(Material.EMERALD, Material.EMERALD_BLOCK, 9);
        pair(Material.REDSTONE, Material.REDSTONE_BLOCK, 9);
        pair(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK, 9);
        pair(Material.RAW_IRON, Material.RAW_IRON_BLOCK, 9);
        pair(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK, 9);
        pair(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK, 9);
        pair(Material.SLIME_BALL, Material.SLIME_BLOCK, 9);
        pair(Material.WHEAT, Material.HAY_BLOCK, 9);
        pair(Material.DRIED_KELP, Material.DRIED_KELP_BLOCK, 9);
        pair(Material.BAMBOO, Material.BAMBOO_BLOCK, 9);
        pair(Material.QUARTZ, Material.QUARTZ_BLOCK, 4);
        pair(Material.AMETHYST_SHARD, Material.AMETHYST_BLOCK, 4);
        pair(Material.HONEYCOMB, Material.HONEYCOMB_BLOCK, 4);
        pair(Material.SNOWBALL, Material.SNOW_BLOCK, 4);
        pair(Material.CLAY_BALL, Material.CLAY, 4);
    }

    private CompactionResolver() {
    }

    private static void pair(Material base, Material block, int ratio) {
        PAIRS.put(base, new Pair(block, ratio, true));
        PAIRS.put(block, new Pair(base, ratio, false));
    }

    public static Conversion resolve(ItemStack selected, CompactingAction action) {
        if (selected == null || selected.getType().isAir() || action == null) {
            return null;
        }
        Pair pair = PAIRS.get(selected.getType());
        if (pair == null) {
            return null;
        }
        boolean compacting = action == CompactingAction.COMPACT;
        if (compacting != pair.base()) {
            return null;
        }
        if (compacting) {
            return new Conversion(selected.getType(), pair.counterpart(), pair.ratio(), 1);
        }
        return new Conversion(selected.getType(), pair.counterpart(), 1, pair.ratio());
    }

    public record Conversion(Material input, Material output, int inputAmount, int outputAmount) {
        public ItemStack outputStack() {
            return new ItemStack(output);
        }
    }

    private record Pair(Material counterpart, int ratio, boolean base) {
    }
}
