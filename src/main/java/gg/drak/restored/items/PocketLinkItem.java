package gg.drak.restored.items;

import gg.drak.restored.Restored;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.serialization.PersistedItemCodec;
import gg.drak.restored.util.LegacyColors;
import host.plas.bou.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PocketLinkItem {

    public static final String TAG_LINK_ID = "restored-pocket-link-id";
    public static final String TAG_AUGMENTS = "restored-pocket-augments";
    public static final String TAG_FEED_MODE = "restored-feed-mode";
    public static final String TAG_FEED_SORT = "restored-feed-sort";
    public static final String TAG_FEED_DIR = "restored-feed-dir";
    public static final String TAG_FEED_META = "restored-feed-meta";
    public static final String TAG_FEED_FILTERS = "restored-feed-filters";
    public static final String TAG_QUIVER_MODE = "restored-quiver-mode";
    public static final String TAG_QUIVER_META = "restored-quiver-meta";
    public static final String TAG_QUIVER_FILTERS = "restored-quiver-filters";
    public static final String TAG_BACKPACK_CONTENTS = "restored-backpack-contents";
    public static final String TAG_OPEN_LINK_ID = "restored-open-pocket-link-id";

    private static final String FILTER_SEP = "\u0002";
    public static final int FILTER_SLOTS = 7;

    public enum FeedFilterMode {
        BLACKLIST,
        WHITELIST;

        public FeedFilterMode toggle() {
            return this == BLACKLIST ? WHITELIST : BLACKLIST;
        }
    }

    public enum FeedSortMode {
        SLOT,
        AMOUNT,
        NAME;

        public FeedSortMode next() {
            return switch (this) {
                case SLOT -> AMOUNT;
                case AMOUNT -> NAME;
                case NAME -> SLOT;
            };
        }

        public String display() {
            return switch (this) {
                case SLOT -> "Slot ID";
                case AMOUNT -> "Item Amount";
                case NAME -> "Name";
            };
        }
    }

    public enum FeedSortDir {
        ASCENDING,
        DESCENDING;

        public FeedSortDir toggle() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }

    public enum FeedMetaMode {
        RESPECT,
        ANY;

        public FeedMetaMode toggle() {
            return this == RESPECT ? ANY : RESPECT;
        }

        public String display() {
            return this == RESPECT ? "Respect Meta Data" : "Allow Any Meta Data";
        }
    }

    private PocketLinkItem() {
    }

    public static ItemStack create() {
        ItemStack item = RestoredItems.tagged(
                Material.CRAFTING_TABLE,
                RestoredItems.TYPE_POCKET_LINK,
                "#FFED6A&lPocket Link",
                "#bdc8c9Click to open pocket controls.",
                "#FF5555Not linked.",
                "#bdc8c9Shift-right-click a network chest to link.",
                "#AAAAAAPortable access to a linked network."
        );
        ensureLinkId(item);
        setFeedFilterMode(item, FeedFilterMode.BLACKLIST);
        setFeedSortMode(item, FeedSortMode.SLOT);
        setFeedSortDir(item, FeedSortDir.DESCENDING);
        setFeedMetaMode(item, FeedMetaMode.RESPECT);
        return item;
    }

    public static boolean isType(ItemStack stack) {
        return RestoredItems.isType(stack, RestoredItems.TYPE_POCKET_LINK);
    }

    public static UUID ensureLinkId(ItemStack stack) {
        Optional<String> existing = ItemUtils.getTag(stack, Restored.getInstance(), TAG_LINK_ID);
        if (existing.isPresent() && !existing.get().isBlank()) {
            UUID id = UUID.fromString(existing.get());
            setPdcString(stack, TAG_LINK_ID, id.toString());
            return id;
        }
        UUID id = UUID.randomUUID();
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_LINK_ID, id.toString());
        setPdcString(stack, TAG_LINK_ID, id.toString());
        return id;
    }

    public static Optional<UUID> getLinkId(ItemStack stack) {
        Optional<String> pdc = getPdcString(stack, TAG_LINK_ID);
        return (pdc.isPresent() ? pdc : ItemUtils.getTag(stack, Restored.getInstance(), TAG_LINK_ID))
                .filter(s -> !s.isBlank())
                .map(UUID::fromString);
    }

    /** Reads the link id through this plugin's PDC key for GUI movement protection. */
    public static Optional<UUID> getNamespacedLinkId(ItemStack stack) {
        return getPdcString(stack, TAG_LINK_ID)
                .filter(s -> !s.isBlank())
                .map(value -> {
                    try {
                        return UUID.fromString(value);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                });
    }

    public static void markGuiOpen(Player player, UUID linkId) {
        if (player != null && linkId != null) {
            player.getPersistentDataContainer().set(namespacedKey(TAG_OPEN_LINK_ID), PersistentDataType.STRING, linkId.toString());
        }
    }

    public static Optional<UUID> getOpenGuiLinkId(Player player) {
        if (player == null) {
            return Optional.empty();
        }
        String value = player.getPersistentDataContainer().get(namespacedKey(TAG_OPEN_LINK_ID), PersistentDataType.STRING);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isOpenGuiLink(Player player, ItemStack stack) {
        Optional<UUID> open = getOpenGuiLinkId(player);
        Optional<UUID> item = getNamespacedLinkId(stack);
        return open.isPresent() && item.isPresent() && open.get().equals(item.get());
    }

    public static void clearGuiOpenIf(Player player, UUID linkId) {
        if (player == null || linkId == null) {
            return;
        }
        getOpenGuiLinkId(player).filter(linkId::equals).ifPresent(ignored ->
                player.getPersistentDataContainer().remove(namespacedKey(TAG_OPEN_LINK_ID))
        );
    }

    public static Optional<UUID> getLinkedNetworkId(ItemStack stack) {
        return RestoredItems.getNetworkId(stack);
    }

    public static void linkNetwork(ItemStack stack, UUID networkId) {
        ensureLinkId(stack);
        ItemUtils.setTag(stack, Restored.getInstance(), RestoredItems.TAG_NETWORK_ID, networkId.toString());
        refreshLore(stack);
    }

    public static void unlinkNetwork(ItemStack stack) {
        RestoredItems.clearNetworkId(stack);
        // Ensure bou tag path is cleared even if PDC key namespaces differ.
        ItemUtils.setTag(stack, Restored.getInstance(), RestoredItems.TAG_NETWORK_ID, "");
        refreshLore(stack);
    }

    public static Set<PocketAugmentType> getInstalledAugments(ItemStack stack) {
        Optional<String> raw = ItemUtils.getTag(stack, Restored.getInstance(), TAG_AUGMENTS);
        EnumSet<PocketAugmentType> set = EnumSet.noneOf(PocketAugmentType.class);
        if (raw.isEmpty() || raw.get().isBlank()) {
            return set;
        }
        for (String part : raw.get().split(",")) {
            PocketAugmentType type = PocketAugmentType.fromId(part.trim());
            if (type != null) {
                set.add(type);
            }
        }
        return set;
    }

    public static boolean hasAugment(ItemStack stack, PocketAugmentType type) {
        return type != null && getInstalledAugments(stack).contains(type);
    }

    public static boolean installAugment(ItemStack stack, PocketAugmentType type) {
        Set<PocketAugmentType> set = EnumSet.copyOf(getInstalledAugments(stack));
        if (!set.add(type)) {
            return false;
        }
        writeAugments(stack, set);
        return true;
    }

    public static boolean uninstallAugment(ItemStack stack, PocketAugmentType type) {
        Set<PocketAugmentType> set = EnumSet.copyOf(getInstalledAugments(stack));
        if (!set.remove(type)) {
            return false;
        }
        writeAugments(stack, set);
        return true;
    }

    private static void writeAugments(ItemStack stack, Set<PocketAugmentType> set) {
        StringBuilder builder = new StringBuilder();
        for (PocketAugmentType type : set) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(type.name());
        }
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_AUGMENTS, builder.toString());
    }

    public static FeedFilterMode getFeedFilterMode(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_FEED_MODE), FeedFilterMode.class, FeedFilterMode.BLACKLIST);
    }

    public static void setFeedFilterMode(ItemStack stack, FeedFilterMode mode) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_FEED_MODE, mode.name());
    }

    public static FeedSortMode getFeedSortMode(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_FEED_SORT), FeedSortMode.class, FeedSortMode.SLOT);
    }

    public static void setFeedSortMode(ItemStack stack, FeedSortMode mode) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_FEED_SORT, mode.name());
    }

    public static FeedSortDir getFeedSortDir(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_FEED_DIR), FeedSortDir.class, FeedSortDir.DESCENDING);
    }

    public static void setFeedSortDir(ItemStack stack, FeedSortDir dir) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_FEED_DIR, dir.name());
    }

    public static FeedMetaMode getFeedMetaMode(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_FEED_META), FeedMetaMode.class, FeedMetaMode.RESPECT);
    }

    public static void setFeedMetaMode(ItemStack stack, FeedMetaMode mode) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_FEED_META, mode.name());
    }

    public static List<ItemStack> getFeedFilters(ItemStack stack) {
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(null);
        }
        Optional<String> raw = ItemUtils.getTag(stack, Restored.getInstance(), TAG_FEED_FILTERS);
        if (raw.isEmpty()) {
            return filters;
        }
        String[] parts = raw.get().split(FILTER_SEP, -1);
        for (int i = 0; i < FILTER_SLOTS && i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) {
                continue;
            }
            ItemStack decoded = PersistedItemCodec.deserializePayload(part);
            if (decoded != null && !decoded.getType().isAir() && decoded.getType() != Material.BARRIER) {
                int amount = Math.max(1, Math.min(decoded.getMaxStackSize(), decoded.getAmount()));
                decoded.setAmount(amount);
                filters.set(i, decoded);
            }
        }
        return filters;
    }

    public static void setFeedFilters(ItemStack stack, List<ItemStack> filters) {
        setFilters(stack, TAG_FEED_FILTERS, filters);
    }

    public static List<ItemStack> getQuiverFilters(ItemStack stack) {
        return getFilters(stack, TAG_QUIVER_FILTERS);
    }

    public static void setQuiverFilters(ItemStack stack, List<ItemStack> filters) {
        setFilters(stack, TAG_QUIVER_FILTERS, filters);
    }

    public static FeedFilterMode getQuiverFilterMode(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_QUIVER_MODE), FeedFilterMode.class, FeedFilterMode.BLACKLIST);
    }

    public static void setQuiverFilterMode(ItemStack stack, FeedFilterMode mode) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_QUIVER_MODE, mode.name());
    }

    public static FeedMetaMode getQuiverMetaMode(ItemStack stack) {
        return parseEnum(ItemUtils.getTag(stack, Restored.getInstance(), TAG_QUIVER_META), FeedMetaMode.class, FeedMetaMode.RESPECT);
    }

    public static void setQuiverMetaMode(ItemStack stack, FeedMetaMode mode) {
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_QUIVER_META, mode.name());
    }

    private static List<ItemStack> getFilters(ItemStack stack, String tag) {
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++) {
            filters.add(null);
        }
        Optional<String> raw = ItemUtils.getTag(stack, Restored.getInstance(), tag);
        if (raw.isEmpty()) {
            return filters;
        }
        String[] parts = raw.get().split(FILTER_SEP, -1);
        for (int i = 0; i < FILTER_SLOTS && i < parts.length; i++) {
            String part = parts[i];
            if (part == null || part.isBlank()) {
                continue;
            }
            ItemStack decoded = PersistedItemCodec.deserializePayload(part);
            if (decoded != null && !decoded.getType().isAir() && decoded.getType() != Material.BARRIER) {
                decoded.setAmount(Math.max(1, Math.min(decoded.getMaxStackSize(), decoded.getAmount())));
                filters.set(i, decoded);
            }
        }
        return filters;
    }

    private static void setFilters(ItemStack stack, String tag, List<ItemStack> filters) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (i > 0) {
                builder.append(FILTER_SEP);
            }
            ItemStack filter = (filters != null && i < filters.size()) ? filters.get(i) : null;
            if (filter != null && !filter.getType().isAir()) {
                ItemStack stored = filter.clone();
                stored.setAmount(Math.max(1, Math.min(stored.getMaxStackSize(), stored.getAmount())));
                builder.append(PersistedItemCodec.serializePayload(stored));
            }
        }
        ItemUtils.setTag(stack, Restored.getInstance(), tag, builder.toString());
    }

    public static ItemStack[] getBackpackContents(ItemStack stack) {
        ItemStack[] contents = new ItemStack[27];
        Optional<String> raw = ItemUtils.getTag(stack, Restored.getInstance(), TAG_BACKPACK_CONTENTS);
        if (raw.isEmpty()) {
            return contents;
        }
        String[] parts = raw.get().split(FILTER_SEP, -1);
        for (int i = 0; i < contents.length && i < parts.length; i++) {
            if (parts[i].isBlank()) {
                continue;
            }
            ItemStack decoded = PersistedItemCodec.deserializePayload(parts[i]);
            if (decoded != null && !decoded.getType().isAir() && decoded.getType() != Material.BARRIER) {
                contents[i] = decoded;
            }
        }
        return contents;
    }

    public static void setBackpackContents(ItemStack stack, ItemStack[] contents) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 27; i++) {
            if (i > 0) {
                builder.append(FILTER_SEP);
            }
            ItemStack item = contents != null && i < contents.length ? contents[i] : null;
            if (item != null && !item.getType().isAir()) {
                builder.append(PersistedItemCodec.serializePayload(item));
            }
        }
        ItemUtils.setTag(stack, Restored.getInstance(), TAG_BACKPACK_CONTENTS, builder.toString());
    }

    private static NamespacedKey namespacedKey(String key) {
        return new NamespacedKey(Restored.getInstance(), key);
    }

    private static Optional<String> getPdcString(ItemStack stack, String key) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.getPersistentDataContainer().get(namespacedKey(key), PersistentDataType.STRING));
    }

    private static void setPdcString(ItemStack stack, String key, String value) {
        if (stack == null) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(namespacedKey(key), PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
    }

    public static void refreshLore(ItemStack stack) {
        if (!isType(stack)) {
            return;
        }
        boolean linked = getLinkedNetworkId(stack).isPresent();
        String net = getLinkedNetworkId(stack).map(UUID::toString).orElse("");
        String[] lore = linked
                ? new String[]{
                LegacyColors.color("#bdc8c9Click to open pocket controls."),
                LegacyColors.color("#00FC88Linked: #AAAAAA" + net),
                LegacyColors.color("#bdc8c9Shift-right-click a network chest to unlink."),
                LegacyColors.color("#AAAAAAPortable access to a linked network.")
        }
                : new String[]{
                LegacyColors.color("#bdc8c9Click to open pocket controls."),
                LegacyColors.color("#FF5555Not linked."),
                LegacyColors.color("#bdc8c9Shift-right-click a network chest to link."),
                LegacyColors.color("#AAAAAAPortable access to a linked network.")
        };
        ItemMetaNameKeep(stack, lore);
    }

    private static void ItemMetaNameKeep(ItemStack stack, String[] lore) {
        RestoredItems.storeCanonicalLore(stack, lore);
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta != null && !meta.hasDisplayName()) {
            meta.setDisplayName(LegacyColors.color("#FFED6A&lPocket Link"));
            stack.setItemMeta(meta);
        }
    }

    public static ItemStack findInInventory(Player player, UUID linkId) {
        if (linkId == null) {
            return null;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (matchesLink(stack, linkId)) {
                return stack;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return matchesLink(off, linkId) ? off : null;
    }

    private static boolean matchesLink(ItemStack stack, UUID linkId) {
        return isType(stack) && getLinkId(stack).map(linkId::equals).orElse(false);
    }

    private static <E extends Enum<E>> E parseEnum(Optional<String> raw, Class<E> type, E fallback) {
        if (raw.isEmpty() || raw.get().isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.get());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
