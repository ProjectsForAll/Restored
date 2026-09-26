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
    public static final String KEY_NETWORK_HOPPER_ROLE = "network-hopper-role";

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

    public static void setHopperRole(Block block, String role) {
        setStringTag(block, KEY_NETWORK_HOPPER_ROLE, role);
    }

    public static Optional<String> getHopperRole(Block block) {
        return getStringTag(block, KEY_NETWORK_HOPPER_ROLE);
    }

    public static void clearHopperRole(Block block) {
        clearTag(block, KEY_NETWORK_HOPPER_ROLE);
    }

    /**
     * Tag reads against an already-obtained {@link TileState}. Callers that need several
     * tags (or the inventory) from one block should snapshot {@code block.getState()} once
     * and use these; the Block overloads each take a full tile-entity copy.
     */
    public static Optional<String> getHopperRole(TileState tile) {
        return readString(tile, KEY_NETWORK_HOPPER_ROLE);
    }

    public static Optional<UUID> getLinkedNetworkId(TileState tile) {
        return readString(tile, KEY_LINKED_NETWORK_ID).flatMap(UuidUtils::parse);
    }

    private static Optional<String> readString(TileState tile, String key) {
        if (tile == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tile.getPersistentDataContainer().get(
                PluginUtils.getPluginKey(Restored.getInstance(), key),
                PersistentDataType.STRING
        ));
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

    private static void setStringTag(Block block, String key, String value) {
        if (!(block.getState() instanceof TileState tile) || value == null) {
            return;
        }
        tile.getPersistentDataContainer().set(
                PluginUtils.getPluginKey(Restored.getInstance(), key),
                PersistentDataType.STRING,
                value
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
        return UuidUtils.parse(value);
    }

    private static Optional<String> getStringTag(Block block, String key) {
        if (!(block.getState() instanceof TileState tile)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tile.getPersistentDataContainer().get(
                PluginUtils.getPluginKey(Restored.getInstance(), key),
                PersistentDataType.STRING
        ));
    }

    private static void clearTag(Block block, String key) {
        if (!(block.getState() instanceof TileState tile)) {
            return;
        }
        tile.getPersistentDataContainer().remove(PluginUtils.getPluginKey(Restored.getInstance(), key));
        tile.update(true, false);
    }
}
