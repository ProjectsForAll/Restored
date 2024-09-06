package host.plas.restored.data.items;

import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.data.permission.PermissionNode;
import host.plas.restored.utils.MessageUtils;
import io.streamlined.bukkit.commands.Sender;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;

public interface IPlaceable {
    default void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlockAgainst();
        if (block == null) return;

        Block placedBlock = event.getBlockPlaced();

        MessageUtils.logInfo("Handling block placement for " + getClass().getSimpleName() + "...");

        NetworkManager.getAdjacentNetwork(placedBlock).ifPresentOrElse(network -> {
            MessageUtils.logInfo("Found network for block placement for " + getClass().getSimpleName() + "...");

            if (! network.hasPermission(player, PermissionNode.NETWORK_PLACE)) {
                Sender sender = new Sender(player);
                sender.sendMessage("&cYou do not have permission to place this block here!");

                event.setCancelled(true);
                return;
            }

            onNetworkPlace(placedBlock, player, network);

//            event.setCancelled(true);
        }, () -> {
            MessageUtils.logInfo("No network found for block placement for " + getClass().getSimpleName() + "...");

            onNoNetworkPlace(placedBlock, player);

//            event.setCancelled(true);
        });
    }

    void onNetworkPlace(Block atBlock, Player placedBy, Network network);

    void onNoNetworkPlace(Block atBlock, Player placedBy);

    void placeAsBlock(Block atBlock, Player placedBy);
}
