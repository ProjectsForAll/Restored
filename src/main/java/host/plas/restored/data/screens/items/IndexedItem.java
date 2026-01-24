package host.plas.restored.data.screens.items;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

@Getter @Setter
public class IndexedItem {
    private ItemStack itemStack;
    private int slotNumber;
    private Inventory inventory;
    private Player viewer;

    public IndexedItem(ItemStack itemStack, int slotNumber, Inventory inventory, Player viewer) {
        this.itemStack = itemStack;
        this.slotNumber = slotNumber;
        this.inventory = inventory;
        this.viewer = viewer;
    }

    public void replace(ItemStack stack) {
        inventory.setItem(slotNumber, stack);
    }

    public void remove() {
        inventory.setItem(slotNumber, new ItemStack(Material.AIR));
    }
}
