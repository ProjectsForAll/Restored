package gg.drak.restored.items;

import gg.drak.restored.Restored;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.UuidUtils;
import host.plas.bou.items.ItemUtils;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RestoredItems {

    public static final String TAG_TYPE = "restored-type";
    public static final String TAG_NETWORK_ID = "restored-network-id";
    public static final String TAG_LORE = "restored-lore";
    private static final String LORE_SEPARATOR = "\u0001";

    public static final String TYPE_CORE = "core";
    public static final String TYPE_CHEST = "chest";
    public static final String TYPE_COMPONENT = "component";
    public static final String TYPE_UPGRADE = "upgrade";
    public static final String TYPE_AUGMENT_COMPONENT = "augment_component";
    public static final String TYPE_POCKET_LINK = "pocket_link";
    public static final String TYPE_POCKET_AUGMENT = "pocket_augment";
    public static final String TYPE_CHEST_LINKING_TOOL = "chest_linking_tool";
    public static final String TYPE_NETWORK_HOPPER_INPUT = "network_hopper_input";
    public static final String TYPE_NETWORK_HOPPER_OUTPUT = "network_hopper_output";
    public static final String TYPE_MAGNET_CORE = "magnet_core";

    private static volatile NamespacedKey typeKey;

    private RestoredItems() {
    }

    public static Optional<String> getType(ItemStack stack) {
        // ItemUtils.getTag copies the whole ItemMeta. Restored items always carry meta, so
        // this rejects ordinary stacks (nearly every slot in the inventory scans that run
        // every tick) before any copy is made.
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return readString(stack, typeKey());
    }

    /**
     * Reads a string tag through the stack's read-only persistent data view. Unlike
     * {@code ItemUtils.getTag}, which clones the ItemMeta (twice) per call, this copies nothing;
     * the inventory scans that run for every player every tick made that copy the plugin's
     * biggest cost in profiles.
     */
    private static Optional<String> readString(ItemStack stack, NamespacedKey key) {
        PersistentDataContainerView data = stack.getPersistentDataContainer();
        return data.has(key, PersistentDataType.STRING)
                ? Optional.ofNullable(data.get(key, PersistentDataType.STRING))
                : Optional.empty();
    }

    private static NamespacedKey typeKey() {
        NamespacedKey key = typeKey;
        if (key == null) {
            key = new NamespacedKey(Restored.getInstance(), TAG_TYPE);
            typeKey = key;
        }
        return key;
    }

    public static boolean isType(ItemStack stack, String type) {
        return getType(stack).map(type::equals).orElse(false);
    }

    public static boolean isRestoredItem(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && getType(stack).isPresent();
    }

    public static Optional<UUID> getNetworkId(ItemStack stack) {
        return ItemUtils.getTag(stack, Restored.getInstance(), TAG_NETWORK_ID)
                .flatMap(UuidUtils::parse);
    }

    public static ItemStack withNetworkId(ItemStack stack, UUID networkId) {
        ItemStack copy = stack.clone();
        ItemUtils.setTag(copy, Restored.getInstance(), TAG_NETWORK_ID, networkId.toString());
        return copy;
    }

    public static void clearNetworkId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(new NamespacedKey(Restored.getInstance(), TAG_NETWORK_ID));
        stack.setItemMeta(meta);
    }

    static ItemStack tagged(Material material, String type, String name, String... lore) {
        String[] coloredLore = new String[lore.length];
        for (int i = 0; i < lore.length; i++) {
            coloredLore[i] = LegacyColors.color(lore[i]);
        }
        ItemStack item = ItemUtils.make(material, LegacyColors.color(name), coloredLore);
        ItemUtils.setTag(item, Restored.getInstance(), TAG_TYPE, type);
        storeCanonicalLore(item, coloredLore);
        return item;
    }

    public static void storeCanonicalLore(ItemStack stack, String... loreLines) {
        if (stack == null || !stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        String joined = String.join(LORE_SEPARATOR, loreLines == null ? new String[0] : loreLines);
        meta.getPersistentDataContainer().set(loreKey(), PersistentDataType.STRING, joined);
        meta.setLore(loreLines == null || loreLines.length == 0 ? null : Arrays.asList(loreLines));
        stack.setItemMeta(meta);
    }

    /**
     * Restores plugin lore on a Restored item if another plugin changed it.
     * @return true if lore was restored
     */
    public static boolean restoreLoreIfTampered(ItemStack stack) {
        if (!isRestoredItem(stack) || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        String stored = meta.getPersistentDataContainer().get(loreKey(), PersistentDataType.STRING);
        if (stored == null) {
            return false;
        }
        List<String> canonical = stored.isEmpty()
                ? List.of()
                : Arrays.asList(stored.split(LORE_SEPARATOR, -1));
        List<String> current = meta.getLore();
        if (current == null) {
            current = List.of();
        }
        if (current.equals(canonical)) {
            return false;
        }
        meta.setLore(canonical.isEmpty() ? null : new ArrayList<>(canonical));
        stack.setItemMeta(meta);
        return true;
    }

    public static void protectInventory(Iterable<ItemStack> items) {
        if (items == null) {
            return;
        }
        for (ItemStack stack : items) {
            restoreLoreIfTampered(stack);
        }
    }

    private static NamespacedKey loreKey() {
        return new NamespacedKey(Restored.getInstance(), TAG_LORE);
    }
}
