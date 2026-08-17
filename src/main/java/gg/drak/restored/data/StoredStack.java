package gg.drak.restored.data;

import gg.drak.restored.serialization.PersistedItemCodec;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

@Getter @Setter
public class StoredStack {
    private final ItemStack template;
    private long amount;
    private transient String cachedSerialization;
    private transient String cachedItemKey;

    public StoredStack(ItemStack template, long amount) {
        this.template = flatten(template);
        this.amount = amount;
    }

    public static ItemStack flatten(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    public static String itemKey(ItemStack stack) {
        return PersistedItemCodec.itemKey(stack);
    }

    public String itemKey() {
        if (cachedItemKey == null) {
            cachedItemKey = itemKey(template);
        }
        return cachedItemKey;
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || template == null) {
            return false;
        }
        return itemKey(stack).equals(itemKey());
    }

    public ItemStack asDisplayStack() {
        ItemStack display = template.clone();
        display.setAmount((int) Math.min(amount, display.getMaxStackSize()));
        return display;
    }

    /**
     * Serialized template payload for DB writes. Cached because the template never changes.
     */
    public String serializedTemplate() {
        if (cachedSerialization == null) {
            cachedSerialization = PersistedItemCodec.serializePayload(template);
        }
        return cachedSerialization;
    }
}
