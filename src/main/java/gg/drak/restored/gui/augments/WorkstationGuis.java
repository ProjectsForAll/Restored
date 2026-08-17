package gg.drak.restored.gui.augments;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.Network;
import gg.drak.restored.gui.augments.workstations.AnvilWorkstationGui;
import gg.drak.restored.gui.augments.workstations.BrewingWorkstationGui;
import gg.drak.restored.gui.augments.workstations.CartographyWorkstationGui;
import gg.drak.restored.gui.augments.workstations.CraftingWorkstationGui;
import gg.drak.restored.gui.augments.workstations.EnchantingWorkstationGui;
import gg.drak.restored.gui.augments.workstations.FurnaceWorkstationGui;
import gg.drak.restored.gui.augments.workstations.GrindstoneWorkstationGui;
import gg.drak.restored.gui.augments.workstations.LoomWorkstationGui;
import gg.drak.restored.gui.augments.workstations.SmithingWorkstationGui;
import gg.drak.restored.gui.augments.workstations.StonecutterWorkstationGui;
import org.bukkit.entity.Player;

public final class WorkstationGuis {

    private WorkstationGuis() {
    }

    public static void open(Player player, Network network, AugmentType type) {
        switch (type) {
            case CRAFTING -> new CraftingWorkstationGui(player, network).open();
            case SMELTING, BLASTING, SMOKING -> new FurnaceWorkstationGui(player, network, type).open();
            case GRINDSTONE -> new GrindstoneWorkstationGui(player, network).open();
            case ANVIL -> new AnvilWorkstationGui(player, network).open();
            case SMITHING -> new SmithingWorkstationGui(player, network).open();
            case STONECUTTER -> new StonecutterWorkstationGui(player, network).open();
            case LOOM -> new LoomWorkstationGui(player, network).open();
            case CARTOGRAPHY -> new CartographyWorkstationGui(player, network).open();
            case BREWING -> new BrewingWorkstationGui(player, network).open();
            case ENCHANTING -> new EnchantingWorkstationGui(player, network).open();
            case ENDER_CHEST -> player.openInventory(player.getEnderChest());
        }
    }
}
