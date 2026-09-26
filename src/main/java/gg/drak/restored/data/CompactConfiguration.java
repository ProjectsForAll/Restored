package gg.drak.restored.data;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Persisted configuration for one automatic compactor rule. */
@Getter
public final class CompactConfiguration {
    private final UUID identifier;
    private boolean enabled;
    private ItemStack item;
    private long quantity;
    private CompactingAction action;
    private QuantityOperand operand;

    public CompactConfiguration(UUID identifier) {
        this(identifier, true, null, 9, CompactingAction.COMPACT, QuantityOperand.MORE_THAN_OR_EQUAL_TO);
    }

    public CompactConfiguration(
            UUID identifier,
            boolean enabled,
            ItemStack item,
            long quantity,
            CompactingAction action,
            QuantityOperand operand
    ) {
        this.identifier = identifier == null ? UUID.randomUUID() : identifier;
        this.enabled = enabled;
        this.item = copyItem(item);
        this.quantity = Math.max(0, quantity);
        this.action = action == null ? CompactingAction.COMPACT : action;
        this.operand = operand == null ? QuantityOperand.MORE_THAN_OR_EQUAL_TO : operand;
    }

    public CompactConfiguration copy() {
        return new CompactConfiguration(identifier, enabled, item, quantity, action, operand);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setItem(ItemStack item) {
        this.item = copyItem(item);
    }

    public void clearItem() {
        this.item = null;
    }

    public void setQuantity(long quantity) {
        this.quantity = Math.max(0, quantity);
    }

    public void setAction(CompactingAction action) {
        if (action != null) {
            this.action = action;
        }
    }

    public void setOperand(QuantityOperand operand) {
        if (operand != null) {
            this.operand = operand;
        }
    }

    public boolean isConfigured() {
        return item != null && !item.getType().isAir();
    }

    private static ItemStack copyItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }
}
