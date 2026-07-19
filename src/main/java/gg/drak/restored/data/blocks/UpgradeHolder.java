package gg.drak.restored.data.blocks;

import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.ItemType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Utility for blocks that support upgrade cards (Speed Card, Stack Card).
 */
public interface UpgradeHolder {

    List<ItemStack> getUpgradeCards();

    default boolean hasSpeedCard() {
        for (ItemStack card : getUpgradeCards()) {
            if (card != null && ItemManager.getTypeFrom(card) == ItemType.SPEED_CARD) {
                return true;
            }
        }
        return false;
    }

    default boolean hasStackCard() {
        for (ItemStack card : getUpgradeCards()) {
            if (card != null && ItemManager.getTypeFrom(card) == ItemType.STACK_CARD) {
                return true;
            }
        }
        return false;
    }

    default int countSpeedCards() {
        int count = 0;
        for (ItemStack card : getUpgradeCards()) {
            if (card != null && ItemManager.getTypeFrom(card) == ItemType.SPEED_CARD) {
                count++;
            }
        }
        return count;
    }

    /**
     * Base tick rate divided by (1 + speedCardCount), minimum 1 tick.
     */
    default int getEffectiveTickRate(int baseTickRate) {
        int speedCards = countSpeedCards();
        return Math.max(1, baseTickRate / (1 + speedCards));
    }

    /**
     * Returns 1 normally, or 64 if a Stack Card is present.
     */
    default int getTransferAmount() {
        return hasStackCard() ? 64 : 1;
    }
}
