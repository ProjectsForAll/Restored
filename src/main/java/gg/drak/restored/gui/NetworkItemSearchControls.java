package gg.drak.restored.gui;

import gg.drak.restored.data.StoredStack;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Shared search/filter/sort chrome and matching rules for network item pickers. */
public final class NetworkItemSearchControls {
    public static final int SEARCH_SLOT = 2;
    public static final int FILTER_MODE_SLOT = 3;
    public static final int SORT_MODE_SLOT = 4;
    public static final int SORT_DIR_SLOT = 5;
    public static final int COMBINE_SLOT = 6;

    private NetworkItemSearchControls() {
    }

    public static void place(AbstractInventoryGui gui, ItemStack[] contents, NetworkBrowserPrefs.State state) {
        contents[SEARCH_SLOT] = GuiItems.button(
                Material.OAK_SIGN,
                "#FFED6A&lSearch Filter",
                List.of(
                        "#bdc8c9Click, then type a filter in chat.",
                        "#bdc8c9Shift-click to clear the filter.",
                        "#bdc8c9Type #FFED6Aclear #bdc8c9or #FFED6Acancel #bdc8c9to reset/abort.",
                        "#AAAAAACurrent: #FFED6A" + (state.searchFilter().isBlank() ? "None" : state.searchFilter())
                )
        );
        gui.bindGuiSlot(SEARCH_SLOT, "search");

        contents[FILTER_MODE_SLOT] = GuiItems.button(
                Material.NAME_TAG,
                "#FFED6A&lFilter Mode",
                List.of(
                        "#bdc8c9Click to cycle how search matches.",
                        "",
                        modeLine(state.filterMode(), NetworkBrowserPrefs.FilterMode.NAME_PLAIN, "Name (No Formatting & Lore)"),
                        modeLine(state.filterMode(), NetworkBrowserPrefs.FilterMode.NAME_FULL, "Name (Formatting & Lore)"),
                        modeLine(state.filterMode(), NetworkBrowserPrefs.FilterMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        gui.bindGuiSlot(FILTER_MODE_SLOT, "filter_mode");

        contents[SORT_MODE_SLOT] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lSort By",
                List.of(
                        "#bdc8c9Click to cycle sort field.",
                        "",
                        modeLine(state.sortMode(), NetworkBrowserPrefs.SortMode.NAME, "Name"),
                        modeLine(state.sortMode(), NetworkBrowserPrefs.SortMode.ITEM_COUNT, "Item Count / Quantity"),
                        modeLine(state.sortMode(), NetworkBrowserPrefs.SortMode.MINECRAFT_ID, "Minecraft ID")
                )
        );
        gui.bindGuiSlot(SORT_MODE_SLOT, "sort_mode");

        contents[SORT_DIR_SLOT] = GuiItems.button(
                Material.IRON_NUGGET,
                "#FFED6A&lSort Direction",
                List.of(
                        "#bdc8c9Click to toggle ascending/descending.",
                        "",
                        modeLine(state.sortDirection(), NetworkBrowserPrefs.SortDirection.ASCENDING, "Ascending"),
                        modeLine(state.sortDirection(), NetworkBrowserPrefs.SortDirection.DESCENDING, "Descending")
                )
        );
        gui.bindGuiSlot(SORT_DIR_SLOT, "sort_dir");

        contents[COMBINE_SLOT] = GuiItems.button(
                state.combineStacks() ? Material.SLIME_BALL : Material.MAGMA_CREAM,
                "#FFED6A&lQuantity Display",
                List.of(
                        "#bdc8c9Click to toggle stack display.",
                        "#bdc8c9Combined: one entry with the total quantity.",
                        "#bdc8c9Split: entries display up to 64 per stack.",
                        "",
                        state.combineStacks() ? "#00FC88▶ Combined" : "#AAAAAA  Combined",
                        state.combineStacks() ? "#AAAAAA  Split" : "#00FC88▶ Split"
                )
        );
        gui.bindGuiSlot(COMBINE_SLOT, "combine");
    }

    public static List<StoredStack> filterAndSort(Collection<StoredStack> source, NetworkBrowserPrefs.State state) {
        List<StoredStack> result = new ArrayList<>();
        if (source != null) {
            for (StoredStack stack : source) {
                if (stack != null && matches(stack, state)) {
                    result.add(stack);
                }
            }
        }
        Comparator<StoredStack> comparator = switch (state.sortMode()) {
            case NAME -> Comparator.comparing(stack -> strip(plainName(stack.getTemplate())), String.CASE_INSENSITIVE_ORDER);
            case ITEM_COUNT -> Comparator.comparingLong(StoredStack::getAmount);
            case MINECRAFT_ID -> Comparator.comparing(NetworkItemSearchControls::minecraftId, String.CASE_INSENSITIVE_ORDER);
        };
        if (state.sortDirection() == NetworkBrowserPrefs.SortDirection.DESCENDING) {
            comparator = comparator.reversed();
        }
        result.sort(comparator.thenComparing(StoredStack::itemKey));
        return result;
    }

    public static boolean matches(StoredStack stack, NetworkBrowserPrefs.State state) {
        if (state.searchFilter() == null || state.searchFilter().isBlank()) {
            return true;
        }
        String query = state.searchFilter().toLowerCase(Locale.ROOT);
        ItemStack template = stack.getTemplate();
        return switch (state.filterMode()) {
            case NAME_PLAIN -> strip(plainName(template)).toLowerCase(Locale.ROOT).contains(query);
            case NAME_FULL -> searchableFullName(template).toLowerCase(Locale.ROOT).contains(query);
            case MINECRAFT_ID -> minecraftId(stack).toLowerCase(Locale.ROOT).contains(query);
        };
    }

    private static String modeLine(Enum<?> current, Enum<?> option, String label) {
        return (current == option ? "#00FC88▶ " : "#AAAAAA  ") + label;
    }

    private static String plainName(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String searchableFullName(ItemStack stack) {
        StringBuilder builder = new StringBuilder(plainName(stack));
        ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                builder.append(' ').append(line);
            }
        }
        return strip(builder.toString());
    }

    private static String minecraftId(StoredStack stack) {
        return stack.getTemplate() == null ? "" : stack.getTemplate().getType().getKey().toString();
    }

    private static String strip(String input) {
        return input == null ? "" : ChatColor.stripColor(input.replace('&', '§'));
    }
}
