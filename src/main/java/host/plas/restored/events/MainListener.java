package host.plas.restored.events;

import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import host.plas.restored.data.NetworkManager;
import host.plas.restored.managers.NotificationTimer;
import host.plas.restored.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

public class MainListener implements Listener {
    public MainListener() {
        Bukkit.getPluginManager().registerEvents(this, Restored.getInstance());

        MessageUtils.logInfo("Registered MainListener!");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        NetworkManager.onBreakBlock(event);
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        if (! NotificationTimer.hasNotification("click", event.getPlayer())) {
            NetworkManager.onBlockClick(event);

            NotificationTimer.addNotification("click", event.getPlayer());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        NetworkManager.onBlockPlace(event);
    }
}
