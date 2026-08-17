package gg.drak.restored.integration;

import gg.drak.restored.Restored;
import gg.drak.restored.items.RestoredItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Soft-depend bridge for Nexo / ItemsAdder / MythicItems custom stacks used as GUI icons or item types.
 * Vanilla fallbacks are always available when soft-deps are absent.
 */
public final class CustomItemBridge {

    private static Boolean nexoPresent;
    private static Boolean itemsAdderPresent;
    private static Boolean mythicItemsPresent;

    private CustomItemBridge() {
    }

    public static boolean hasNexo() {
        if (nexoPresent == null) {
            nexoPresent = Bukkit.getPluginManager().getPlugin("Nexo") != null;
        }
        return nexoPresent;
    }

    public static boolean hasItemsAdder() {
        if (itemsAdderPresent == null) {
            itemsAdderPresent = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        }
        return itemsAdderPresent;
    }

    public static boolean hasMythicItems() {
        if (mythicItemsPresent == null) {
            mythicItemsPresent = Bukkit.getPluginManager().getPlugin("MythicItems") != null
                    || Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        }
        return mythicItemsPresent;
    }

    /**
     * True for Restored-tagged items or ItemsAdder / Nexo / Mythic custom stacks.
     */
    public static boolean isCustomItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (RestoredItems.isRestoredItem(stack)) {
            return true;
        }
        if (hasItemsAdder() && isItemsAdderItem(stack)) {
            return true;
        }
        if (hasNexo() && isNexoItem(stack)) {
            return true;
        }
        if (hasMythicItems() && isMythicItem(stack)) {
            return true;
        }
        return false;
    }

    private static boolean isItemsAdderItem(ItemStack stack) {
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object custom = api.getMethod("byItemStack", ItemStack.class).invoke(null, stack);
            return custom != null;
        } catch (Throwable ignored) {
            return hasForeignPdcNamespace(stack, "itemsadder");
        }
    }

    private static boolean isNexoItem(ItemStack stack) {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object id = api.getMethod("idFromItem", ItemStack.class).invoke(null, stack);
            return id != null;
        } catch (Throwable ignored) {
            return hasForeignPdcNamespace(stack, "nexo");
        }
    }

    private static boolean isMythicItem(ItemStack stack) {
        try {
            Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = api.getMethod("inst").invoke(null);
            Object itemManager = inst.getClass().getMethod("getItemManager").invoke(inst);
            Boolean isMythic = (Boolean) itemManager.getClass()
                    .getMethod("isMythicItem", ItemStack.class)
                    .invoke(itemManager, stack);
            return Boolean.TRUE.equals(isMythic);
        } catch (Throwable ignored) {
            return hasForeignPdcNamespace(stack, "mythicmobs") || hasForeignPdcNamespace(stack, "mythic");
        }
    }

    private static boolean hasForeignPdcNamespace(ItemStack stack, String namespace) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (var key : pdc.getKeys()) {
            if (namespace.equalsIgnoreCase(key.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a custom item id from optional integrations, falling back to vanilla material.
     */
    public static ItemStack resolveIcon(String customId, Material fallback) {
        if (customId == null || customId.isBlank()) {
            return new ItemStack(fallback);
        }

        if (hasItemsAdder()) {
            ItemStack ia = fromItemsAdder(customId);
            if (ia != null) {
                return ia;
            }
        }
        if (hasNexo()) {
            ItemStack nexo = fromNexo(customId);
            if (nexo != null) {
                return nexo;
            }
        }
        if (hasMythicItems()) {
            ItemStack mythic = fromMythic(customId);
            if (mythic != null) {
                return mythic;
            }
        }
        return new ItemStack(fallback);
    }

    private static ItemStack fromItemsAdder(String customId) {
        try {
            Class<?> api = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object stack = api.getMethod("getInstance", String.class).invoke(null, customId);
            if (stack != null) {
                ItemStack item = (ItemStack) stack.getClass().getMethod("getItemStack").invoke(stack);
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            }
        } catch (ReflectiveOperationException e) {
            Restored.getInstance().logWarning("ItemsAdder icon lookup failed for " + customId);
        }
        return null;
    }

    private static ItemStack fromNexo(String customId) {
        try {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = api.getMethod("itemFromId", String.class).invoke(null, customId);
            if (builder != null) {
                ItemStack item = (ItemStack) builder.getClass().getMethod("build").invoke(builder);
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            }
        } catch (ReflectiveOperationException e) {
            Restored.getInstance().logWarning("Nexo icon lookup failed for " + customId);
        }
        return null;
    }

    private static ItemStack fromMythic(String customId) {
        try {
            Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = api.getMethod("inst").invoke(null);
            Object itemManager = inst.getClass().getMethod("getItemManager").invoke(inst);
            Object optional = itemManager.getClass().getMethod("getItem", String.class).invoke(itemManager, customId);
            if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                Object mythicItem = opt.get();
                ItemStack item = (ItemStack) mythicItem.getClass().getMethod("generateItemStack", int.class).invoke(mythicItem, 1);
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            }
        } catch (ReflectiveOperationException e) {
            Restored.getInstance().logWarning("MythicItems icon lookup failed for " + customId);
        }
        return null;
    }
}
