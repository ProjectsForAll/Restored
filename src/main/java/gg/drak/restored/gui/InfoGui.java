package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class InfoGui extends AbstractInventoryGui {

    public InfoGui(Player player) {
        super(player, CornerColor.YELLOW);
    }

    @Override
    public void open() {
        ItemStack[] contents = beginShell(GuiLayout.SIZE_LARGE, "#FFED6A&lRestored Info");

        contents[11] = GuiItems.button(
                Material.CHEST,
                "#FFED6A&lWhat is Restored?",
                List.of(
                        "#bdc8c9Single-chest storage networks.",
                        "#bdc8c9One Network Chest = one network.",
                        "",
                        "#AAAAAAPlace a Network Chest to create it.",
                        "#AAAAAARight-click to open storage.",
                        "#AAAAAAShift-right-click to manage (owner/admin).",
                        "",
                        "#bdc8c9Adjacent chests do #FFED6Anot #bdc8c9merge."
                )
        );

        contents[13] = GuiItems.button(
                Material.HOPPER,
                "#FFED6A&lStorage GUI",
                List.of(
                        "#bdc8c9Browse items in pages of #FFED6A64 #bdc8c9slices.",
                        "",
                        "#00FC88Top bar:",
                        "#AAAAAASearch, filter mode, sort field & direction",
                        "#AAAAAA(your prefs are remembered).",
                        "",
                        "#00FC88Bottom bar:",
                        "#AAAAAADeposit chest, Augments, page controls.",
                        "",
                        "#00FC88Clicks:",
                        "#AAAAAALeft: take a stack  Right: take 1",
                        "#AAAAAAShift-left: take up to 64",
                        "#AAAAAAShift-click your inventory to deposit",
                        "#AAAAAADrop / place items onto content slots"
                )
        );

        contents[15] = GuiItems.button(
                Material.CRAFTING_TABLE,
                "#FFED6A&lCrafting",
                List.of(
                        "#bdc8c9Craft cores, chests, components,",
                        "#bdc8c9upgrades, augments, and pocket items.",
                        "",
                        "#00FC88Run #FFED6A/rrecipes #00FC88for a visual guide.",
                        "",
                        "#AAAAAAClick to open /rrecipes."
                )
        );
        bindSlot(15, "recipes");

        contents[20] = GuiItems.button(
                Material.GOLD_INGOT,
                "#FFED6A&lStorage Upgrades",
                List.of(
                        "#bdc8c9No upgrades: #FFED6A0 #bdc8c9virtual space.",
                        "#bdc8c9Each Network Upgrade: #FFED6A+64 #bdc8c9virtual.",
                        "#bdc8c9Linked chests add physical storage.",
                        "",
                        "#00FC88Install:",
                        "#AAAAAALeft-click the chest holding an upgrade,",
                        "#AAAAAAor apply one from #FFED6A/networks#AAAAAA.",
                        "",
                        "#FF5555Uninstall:",
                        "#AAAAAAShift-left-click the chest to remove",
                        "#AAAAAAone upgrade (returned to you).",
                        "#AAAAAAWon't uninstall if storage wouldn't fit."
                )
        );

        contents[22] = GuiItems.button(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "#FFED6A&lNetwork Augments",
                List.of(
                        "#bdc8c9Add workstations to a network:",
                        "#AAAAAACrafting, Furnace, Blast, Smoker,",
                        "#AAAAAAGrindstone, Anvil, Smithing, Stonecutter,",
                        "#AAAAAALoom, Cartography, Brewing, Enchanting.",
                        "",
                        "#00FC88Install:",
                        "#AAAAAAOpen storage → Augments, then",
                        "#AAAAAAshift-click the matching augment item",
                        "#AAAAAAor place it on the empty slot.",
                        "",
                        "#bdc8c9In a workstation you can:",
                        "#AAAAAA• Pull ingredients from the network",
                        "#AAAAAA• Place items from your inventory",
                        "#AAAAAA• Toggle output: network (green) or slot (gray)",
                        "",
                        "#AAAAAAMembers & Admins can use augments."
                )
        );

        contents[24] = GuiItems.button(
                Material.ENDER_EYE,
                "#FFED6A&lPocket Link",
                List.of(
                        "#bdc8c9A portable link to a network.",
                        "",
                        "#00FC88Link / unlink:",
                        "#AAAAAAHold a Pocket Link and",
                        "#AAAAAAshift-right-click a Network Chest.",
                        "",
                        "#00FC88Feeding Augment:",
                        "#AAAAAAInstall on a Pocket Link to auto-eat",
                        "#AAAAAAvanilla food from the linked network",
                        "#AAAAAAwhen hungry (custom items are skipped).",
                        "",
                        "#bdc8c9Craft Pocket Link & Feeding Augment",
                        "#bdc8c9via #FFED6A/rrecipes#bdc8c9."
                )
        );

        contents[29] = GuiItems.button(
                Material.PLAYER_HEAD,
                "#FFED6A&lManagement",
                List.of(
                        "#bdc8c9Owners and admins manage networks.",
                        "",
                        "#00FC88Open management:",
                        "#AAAAAAShift-right-click the chest, or",
                        "#AAAAAArun #FFED6A/networks #AAAAAAand pick one.",
                        "",
                        "#FFED6AYou can:",
                        "#AAAAAA• Manage players & transfer ownership",
                        "#AAAAAA• View network info / open counts",
                        "#AAAAAA• Open storage (within 5 blocks)",
                        "#AAAAAA• Move the chest (keeps all items)",
                        "",
                        "#AAAAAAClick to open /networks."
                )
        );
        bindSlot(29, "networks");

        contents[31] = GuiItems.button(
                Material.NAME_TAG,
                "#FFED6A&lRoles",
                List.of(
                        "#FF5555Blocked #AAAAAA— no access (default)",
                        "#bdc8c9Read Only #AAAAAA— view only",
                        "#00FC88Member #AAAAAA— deposit, withdraw, augments",
                        "#FFED6AAdmin #AAAAAA— full manage access",
                        "",
                        "#bdc8c9Owner always has full access.",
                        "#AAAAAASet roles under Manage Players."
                )
        );

        contents[33] = GuiItems.button(
                Material.BOOK,
                "#FFED6A&lQuick Tips",
                List.of(
                        "#bdc8c9• Empty a network before breaking it",
                        "#bdc8c9• Breaking an empty chest deletes it",
                        "#bdc8c9• Move Network keeps items safe",
                        "#bdc8c9• Different item meta = separate stacks",
                        "#bdc8c9• Deposit GUI dumps everything on close",
                        "#bdc8c9• Soft-depends: Nexo, ItemsAdder, Mythic",
                        "",
                        "#AAAAAACommands:",
                        "#FFED6A/rinfo #AAAAAA— this help",
                        "#FFED6A/rrecipes #AAAAAA— recipe browser",
                        "#FFED6A/networks #AAAAAA— manage your networks"
                )
        );

        contents[40] = GuiItems.button(
                Material.OAK_SIGN,
                "#FFED6A&lCommands",
                List.of(
                        "#FFED6A/rinfo #AAAAAA(/rhelp, /restoredinfo)",
                        "#bdc8c9Open this guide.",
                        "",
                        "#FFED6A/rrecipes #AAAAAA(/rrecipe)",
                        "#bdc8c9Browse & view Restored recipes.",
                        "",
                        "#FFED6A/networks #AAAAAA(/network, /rn)",
                        "#bdc8c9List and manage your networks."
                )
        );

        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if ("recipes".equals(key)) {
            new RecipesListGui(player).open();
        } else if ("networks".equals(key)) {
            new NetworksListGui(player).open();
        }
    }
}
