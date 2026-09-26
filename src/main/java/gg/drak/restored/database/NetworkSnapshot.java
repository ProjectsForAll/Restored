package gg.drak.restored.database;

import gg.drak.restored.data.AugmentType;
import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkHopperRole;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.NetworkRole;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.util.LinkedChestStorage;
import lombok.Getter;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable DB write payload captured on the server thread so persistence can run async.
 */
@Getter
public final class NetworkSnapshot {
    private final String identifier;
    private final String ownerUuid;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final int upgradeCount;
    private final int enchantingBookshelves;
    private final long totalOpens;
    private final List<ItemEntry> items;
    private final List<PermissionEntry> permissions;
    private final List<OpenStatEntry> openStats;
    private final List<AugmentType> augments;
    private final List<CompactConfiguration> compactConfigurations;
    private final List<LinkedChestEntry> linkedChests;
    private final List<LinkedHopperEntry> linkedHoppers;

    private NetworkSnapshot(
            String identifier,
            String ownerUuid,
            String world,
            int x,
            int y,
            int z,
            int upgradeCount,
            int enchantingBookshelves,
            long totalOpens,
            List<ItemEntry> items,
            List<PermissionEntry> permissions,
            List<OpenStatEntry> openStats,
            List<AugmentType> augments,
            List<CompactConfiguration> compactConfigurations,
            List<LinkedChestEntry> linkedChests,
            List<LinkedHopperEntry> linkedHoppers
    ) {
        this.identifier = identifier;
        this.ownerUuid = ownerUuid;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.upgradeCount = upgradeCount;
        this.enchantingBookshelves = enchantingBookshelves;
        this.totalOpens = totalOpens;
        this.items = items;
        this.permissions = permissions;
        this.openStats = openStats;
        this.augments = augments;
        this.compactConfigurations = compactConfigurations;
        this.linkedChests = linkedChests;
        this.linkedHoppers = linkedHoppers;
    }

    public static NetworkSnapshot capture(Network network) {
        List<ItemEntry> items = new ArrayList<>();
        for (Map.Entry<String, StoredStack> entry : network.getItems().entrySet()) {
            StoredStack stack = entry.getValue();
            items.add(new ItemEntry(
                    entry.getKey(),
                    stack.serializedTemplate(),
                    stack.getAmount()
            ));
        }

        List<PermissionEntry> permissions = new ArrayList<>();
        for (Map.Entry<UUID, NetworkRole> entry : network.getRoles().entrySet()) {
            permissions.add(new PermissionEntry(entry.getKey().toString(), entry.getValue()));
        }

        List<OpenStatEntry> openStats = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : network.getOpenCounts().entrySet()) {
            openStats.add(new OpenStatEntry(entry.getKey().toString(), entry.getValue()));
        }

        List<AugmentType> augments = new ArrayList<>(network.getInstalledAugments());

        List<CompactConfiguration> compactConfigurations = new ArrayList<>();
        for (CompactConfiguration configuration : network.getCompactConfigurations()) {
            compactConfigurations.add(configuration.copy());
        }

        List<LinkedChestEntry> linkedChests = new ArrayList<>();
        for (String key : network.getLinkedChestKeys()) {
            Location location = LinkedChestStorage.parseLocationKey(key);
            if (location != null && location.getWorld() != null) {
                linkedChests.add(new LinkedChestEntry(
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()
                ));
            } else {
                // Fallback parse without loaded world
                String[] parts = key.split(":");
                if (parts.length >= 4) {
                    try {
                        int z = Integer.parseInt(parts[parts.length - 1]);
                        int y = Integer.parseInt(parts[parts.length - 2]);
                        int x = Integer.parseInt(parts[parts.length - 3]);
                        StringBuilder worldBuilder = new StringBuilder(parts[0]);
                        for (int i = 1; i < parts.length - 3; i++) {
                            worldBuilder.append(':').append(parts[i]);
                        }
                        linkedChests.add(new LinkedChestEntry(worldBuilder.toString(), x, y, z));
                    } catch (NumberFormatException ignored) {
                        // skip malformed
                    }
                }
            }
        }

        List<LinkedHopperEntry> linkedHoppers = new ArrayList<>();
        for (String key : network.getLinkedHopperKeys()) {
            Location location = LinkedChestStorage.parseLocationKey(key);
            NetworkHopperRole role = network.getLinkedHopperRole(key);
            if (location != null && location.getWorld() != null && role != null) {
                linkedHoppers.add(new LinkedHopperEntry(location.getWorld().getName(),
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(), role.id()));
            } else {
                String[] parts = key.split(":");
                if (parts.length >= 4 && role != null) {
                    try {
                        int z = Integer.parseInt(parts[parts.length - 1]);
                        int y = Integer.parseInt(parts[parts.length - 2]);
                        int x = Integer.parseInt(parts[parts.length - 3]);
                        StringBuilder worldBuilder = new StringBuilder(parts[0]);
                        for (int i = 1; i < parts.length - 3; i++) {
                            worldBuilder.append(':').append(parts[i]);
                        }
                        linkedHoppers.add(new LinkedHopperEntry(worldBuilder.toString(), x, y, z, role.id()));
                    } catch (NumberFormatException ignored) {
                        // skip malformed
                    }
                }
            }
        }

        return new NetworkSnapshot(
                network.getIdentifierString(),
                network.getOwnerUuid().toString(),
                network.getWorld(),
                network.getX(),
                network.getY(),
                network.getZ(),
                network.getUpgradeCount(),
                network.getEnchantingBookshelves(),
                network.getTotalOpens(),
                List.copyOf(items),
                List.copyOf(permissions),
                List.copyOf(openStats),
                List.copyOf(augments),
                List.copyOf(compactConfigurations),
                List.copyOf(linkedChests),
                List.copyOf(linkedHoppers)
        );
    }

    @Getter
    public static final class ItemEntry {
        private final String itemKey;
        private final String itemData;
        private final long amount;

        public ItemEntry(String itemKey, String itemData, long amount) {
            this.itemKey = itemKey;
            this.itemData = itemData;
            this.amount = amount;
        }
    }

    @Getter
    public static final class PermissionEntry {
        private final String playerUuid;
        private final NetworkRole role;

        public PermissionEntry(String playerUuid, NetworkRole role) {
            this.playerUuid = playerUuid;
            this.role = role;
        }
    }

    @Getter
    public static final class OpenStatEntry {
        private final String playerUuid;
        private final long opens;

        public OpenStatEntry(String playerUuid, long opens) {
            this.playerUuid = playerUuid;
            this.opens = opens;
        }
    }

    @Getter
    public static final class LinkedChestEntry {
        private final String world;
        private final int x;
        private final int y;
        private final int z;

        public LinkedChestEntry(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String locationKey() {
            return NetworkManager.locationKey(world, x, y, z);
        }
    }

    @Getter
    public static final class LinkedHopperEntry {
        private final String world;
        private final int x;
        private final int y;
        private final int z;
        private final String role;

        public LinkedHopperEntry(String world, int x, int y, int z, String role) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.role = role;
        }
    }
}
