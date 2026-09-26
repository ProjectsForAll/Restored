package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.NetworkHopperRole;
import gg.drak.restored.gui.NetworkHopperConfigGui;
import gg.drak.restored.items.ChestLinkingToolItem;
import gg.drak.restored.util.LegacyColors;
import gg.drak.restored.util.NetworkBlockTags;
import gg.drak.restored.util.NetworkHopperStorage;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class NetworkHopperListener implements Listener {

    public NetworkHopperListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(),
                this::tick,
                1L,
                Restored.getMainConfig() == null
                        ? gg.drak.restored.config.MainConfig.DEFAULT_HOPPER_INTERVAL
                        : Restored.getMainConfig().getHopperInterval()
        );
        // A previous version could remove output links while resolving input
        // hoppers. Recover any still-tagged hoppers that are already loaded.
        Restored.getInstance().getServer().getScheduler().runTask(
                Restored.getInstance(),
                this::restoreLoadedHoppers
        );
        Restored.getInstance().logInfo("Registered NetworkHopperListener!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        restoreHoppers(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        NetworkHopperRole role = NetworkHopperStorage.role(block);
        if (role == null) {
            return;
        }
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        // Let vanilla place ordinary blocks against/on the hopper. This is especially
        // important while sneaking: shift-right-clicking with a block must not open
        // the output configuration or be swallowed by the input no-GUI guard.
        if (hand != null && !hand.getType().isAir() && hand.getType().isBlock()) {
            return;
        }
        if (ChestLinkingToolItem.isType(hand)) {
            return;
        }
        // Input hoppers are ordinary, editable chest inventories. Leave the event
        // uncancelled so Bukkit opens the chest GUI; the scheduled input processor
        // drains anything placed there into the linked network.
        if (role == NetworkHopperRole.INPUT) {
            return;
        }
        if (event.getPlayer().isSneaking()) {
            event.setCancelled(true);
            Network network = NetworkHopperStorage.resolveNetwork(block);
            if (network == null) {
                event.getPlayer().sendMessage(LegacyColors.color("#FF5555Link this output hopper to a network first."));
                return;
            }
            if (!network.canManage(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(LegacyColors.color("#FF5555You must be an admin or owner to configure this hopper."));
                return;
            }
            new NetworkHopperConfigGui(event.getPlayer(), block).open();
        }
        // Normal right-click is deliberately left uncancelled so the chest inventory opens.
    }

    private void tick() {
        for (Network network : NetworkManager.getNetworks()) {
            if (network.getLinkedHopperCount() <= 0) {
                continue;
            }
            NetworkHopperStorage.process(network);
        }
    }

    private void restoreLoadedHoppers() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                restoreHoppers(chunk);
            }
        }
    }

    private void restoreHoppers(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            Block block = state.getBlock();
            NetworkHopperRole role = NetworkHopperStorage.role(block);
            if (role == null) {
                continue;
            }
            UUID networkId = NetworkBlockTags.getLinkedNetworkId(block).orElse(null);
            Network network = networkId == null ? null : NetworkManager.get(networkId);
            if (network == null) {
                continue;
            }
            String key = NetworkManager.locationKey(block.getLocation());
            if (!network.hasLinkedHopperKey(key)) {
                NetworkHopperStorage.link(block, network);
                network.save();
            }
        }
    }
}
