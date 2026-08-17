package gg.drak.restored.util;

import gg.drak.restored.Restored;
import host.plas.bou.utils.PluginUtils;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public final class NetworkBlockTags {

    public static final String KEY_NETWORK_ID = "network-id";
    public static final String KEY_LINKED_NETWORK_ID = "linked-network-id";

    private NetworkBlockTags() {
    }

    public static void setNetworkId(Block block, UUID networkId) {
        setUuidTag(block, KEY_NETWORK_ID, networkId);
    }

    public static Optional<UUID> getNetworkId(Block block) {
        return getUuidTag(block, KEY_NETWORK_ID);
    }

    public static void clearNetworkId(Block block) {
        clearTag(block, KEY_NETWORK_ID);
    }

    public static void setLinkedNetworkId(Block block, UUID networkId) {
        setUuidTag(block, KEY_LINKED_NETWORK_ID, networkId);
    }

    public static Optional<UUID> getLinkedNetworkId(Block block) {
        return getUuidTag(block, KEY_LINKED_NETWORK_ID);
    }

    public static void clearLinkedNetworkId(Block block) {
        clearTag(block, KEY_LINKED_NETWORK_ID);
    }

    private static void setUuidTag(Block block, String key, UUID networkId) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().set(
                PluginUtils.getPluginKey(Restored.getInstance(), key),
                PersistentDataType.STRING,
                networkId.toString()
        );
        tile.update(true, false);
    }

    private static Optional<UUID> getUuidTag(Block block, String key) {
        if (!(block.getState() instanceof TileState tile)) {
            return Optional.empty();
        }
        String value = tile.getPersistentDataContainer().get(
                PluginUtils.getPluginKey(Restored.getInstance(), key),
                PersistentDataType.STRING
        );
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static void clearTag(Block block, String key) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().remove(PluginUtils.getPluginKey(Restored.getInstance(), key));
        tile.update(true, false);
    }
}
