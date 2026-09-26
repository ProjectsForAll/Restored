package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import host.plas.bou.gui.GuiLayout;

import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkRole;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.PlayerHeadService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerPermGui extends AbstractInventoryGui {
    private final Network network;
    private final UUID targetUuid;
    private final Runnable backAction;
    private final AtomicBoolean open = new AtomicBoolean(false);
    private volatile String targetName;

    public PlayerPermGui(Player player, Network network, UUID targetUuid, Runnable backAction) {
        super(player, CornerColor.YELLOW);
        this.network = network;
        this.targetUuid = targetUuid;
        this.backAction = backAction;
        this.targetName = PlayerHeadService.getCached(targetUuid)
                .map(PlayerHeadService.CachedPlayer::name)
                .orElse(targetUuid.toString());
    }

    @Override
    public void open() {
        open.set(true);
        render(targetName);
        PlayerHeadService.resolveNameAsync(targetUuid, targetName, name -> {
            if (!open.get() || inventory == null) {
                return;
            }
            if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)) {
                return;
            }
            if (name.equals(targetName)) {
                return;
            }
            targetName = name;
            // Title is fixed at open; refresh the current-role button label instead.
            inventory.setItem(4, GuiItems.button(
                    Material.NAME_TAG,
                    "#FFED6A" + name,
                    List.of("#AAAAAA" + network.getRole(targetUuid).displayName())
            ));
        });
    }

    private void render(String name) {
        NetworkRole role = network.getRole(targetUuid);

        ItemStack[] contents = beginShell(GuiLayout.SIZE_MEDIUM, "#FFED6A&l" + name);
        contents[10] = GuiItems.button(Material.BARRIER, "#FF5555&lBlocked", List.of("#bdc8c9No access."));
        contents[12] = GuiItems.button(Material.PAPER, "#FFED6A&lRead Only", List.of("#bdc8c9View items only."));
        contents[14] = GuiItems.button(Material.IRON_INGOT, "#FFED6A&lMember", List.of("#bdc8c9Deposit and withdraw."));
        contents[16] = GuiItems.button(Material.NETHER_STAR, "#00FC88&lAdmin", List.of("#bdc8c9Full management access."));
        contents[22] = GuiItems.button(Material.GOLDEN_APPLE, "#FFED6A&lTransfer Ownership", List.of("#FF5555Give this player ownership."));

        bindSlot(10, "role:BLOCKED");
        bindSlot(12, "role:READ_ONLY");
        bindSlot(14, "role:MEMBER");
        bindSlot(16, "role:ADMIN");
        bindSlot(22, "transfer");

        contents[4] = GuiItems.button(
                Material.NAME_TAG,
                "#FFED6A" + name,
                List.of("#AAAAAA" + role.displayName())
        );

        placeReturnButton(contents, "back");
        finishAndOpen(contents);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(inventory)) {
            return;
        }
        event.setCancelled(true);
        String key = getKeyAtSlot(event.getRawSlot());
        if (key == null) {
            return;
        }

        if ("back".equals(key)) {
            open.set(false);
            player.closeInventory();
            backAction.run();
            return;
        }

        if (key.startsWith("role:")) {
            if (!network.canManage(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555You cannot manage permissions."));
                return;
            }
            if (network.isOwner(targetUuid)) {
                player.sendMessage(LegacyColors.color("#FF5555Cannot change the owner's role."));
                return;
            }
            NetworkRole role = NetworkRole.valueOf(key.substring(5));
            network.setRole(targetUuid, role);
            network.save();
            player.sendMessage(LegacyColors.color("#00FC88Role updated to " + role.displayName() + "."));
            open();
            return;
        }

        if ("transfer".equals(key)) {
            if (!network.actsAsOwner(player.getUniqueId())) {
                player.sendMessage(LegacyColors.color("#FF5555Only the owner can transfer ownership."));
                return;
            }
            String name = targetName;
            new ConfirmGui(
                    player,
                    "#FFED6A&lTransfer to " + name + "?",
                    p -> {
                        network.transferOwnership(targetUuid);
                        network.save();
                        p.sendMessage(LegacyColors.color("#00FC88Ownership transferred."));
                        backAction.run();
                    },
                    () -> open()
            ).open();
        }
    }
}
