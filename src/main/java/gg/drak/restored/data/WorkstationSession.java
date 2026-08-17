package gg.drak.restored.data;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory workstation GUI state for a network augment.
 * Survives closing/reopening the GUI until cleared or the augment is uninstalled.
 */
public final class WorkstationSession {
    private final Map<Integer, ItemStack> craftSlots = new HashMap<>();
    private ItemStack[] depositBuffer = new ItemStack[0];
    private ItemStack outputBuffer;
    private boolean sendToNetwork = true;
    private int enchantingBookshelves;

    public Map<Integer, ItemStack> getCraftSlots() {
        return craftSlots;
    }

    public ItemStack[] getDepositBuffer() {
        return depositBuffer;
    }

    public void setDepositBuffer(ItemStack[] depositBuffer) {
        this.depositBuffer = depositBuffer == null ? new ItemStack[0] : depositBuffer;
    }

    public ItemStack getOutputBuffer() {
        return outputBuffer;
    }

    public void setOutputBuffer(ItemStack outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    public boolean isSendToNetwork() {
        return sendToNetwork;
    }

    public void setSendToNetwork(boolean sendToNetwork) {
        this.sendToNetwork = sendToNetwork;
    }

    public int getEnchantingBookshelves() {
        return enchantingBookshelves;
    }

    public void setEnchantingBookshelves(int enchantingBookshelves) {
        this.enchantingBookshelves = Math.max(0, Math.min(15, enchantingBookshelves));
    }

    public boolean isEmpty() {
        for (ItemStack stack : craftSlots.values()) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        if (outputBuffer != null && !outputBuffer.getType().isAir()) {
            return false;
        }
        for (ItemStack stack : depositBuffer) {
            if (stack != null && !stack.getType().isAir()) {
                return false;
            }
        }
        return enchantingBookshelves <= 0;
    }

    /**
     * Snapshot of all held items (craft + deposit + output) for returning to the network.
     */
    public java.util.List<ItemStack> drainAllItems() {
        java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>();
        for (ItemStack stack : craftSlots.values()) {
            if (stack != null && !stack.getType().isAir()) {
                out.add(stack.clone());
            }
        }
        craftSlots.clear();
        if (outputBuffer != null && !outputBuffer.getType().isAir()) {
            out.add(outputBuffer.clone());
            outputBuffer = null;
        }
        for (int i = 0; i < depositBuffer.length; i++) {
            ItemStack stack = depositBuffer[i];
            if (stack != null && !stack.getType().isAir()) {
                out.add(stack.clone());
            }
            depositBuffer[i] = null;
        }
        if (enchantingBookshelves > 0) {
            out.add(new ItemStack(org.bukkit.Material.BOOKSHELF, enchantingBookshelves));
            enchantingBookshelves = 0;
        }
        return out;
    }
}
