package gg.drak.restored.data.permission;

import gg.drak.thebase.lib.leonhard.storage.sections.FlatFileSection;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.NetworkMap;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class PermissionSystem implements Comparable<PermissionSystem> {
    private String networkId;
    private ConcurrentSkipListMap<PermissionNode, ConcurrentSkipListSet<String>> trusted; // uuids

    public PermissionSystem(String networkId) {
        this.networkId = networkId;
        this.trusted = new ConcurrentSkipListMap<>();
    }

    public PermissionSystem(Network network) {
        this(network.getIdentifier());
    }

    public Optional<Network> getNetwork() {
        return NetworkMap.getNetwork(networkId);
    }

    @Override
    public int compareTo(@NotNull PermissionSystem o) {
        return networkId.compareTo(o.networkId);
    }

    public void load(FlatFileSection section) {
        Arrays.stream(PermissionNode.values()).forEach(node -> {
            List<String> trusted = section.getStringList(node.getNode());
            this.trusted.put(node, new ConcurrentSkipListSet<>(trusted));
        });
    }

    public void save(FlatFileSection section) {
        trusted.forEach((node, uuids) -> {
            section.set(node.getNode(), uuids);
        });
    }

    public void trust(PermissionNode node, String uuid) {
        trusted.computeIfAbsent(node, k -> new ConcurrentSkipListSet<>()).add(uuid);
    }

    public void removeTrust(String uuid) {
        trusted.forEach((node, uuids) -> {
            uuids.remove(uuid);
        });
    }

    public void removeTrust(PermissionNode node, String uuid) {
        trusted.getOrDefault(node, new ConcurrentSkipListSet<>()).remove(uuid);
    }

    public void trust(PermissionNode node, Player player) {
        trust(node, player.getUniqueId().toString());
    }

    public void removeTrust(Player player) {
        removeTrust(player.getUniqueId().toString());
    }

    public void removeTrust(PermissionNode node, Player player) {
        removeTrust(node, player.getUniqueId().toString());
    }

    public void trustWithAll(String uuid) {
        Arrays.stream(PermissionNode.values()).forEach(node -> {
            if (node.equals(PermissionNode.ERROR)) return;
            trust(node, uuid);
        });
    }

    public void removeTrustWithAll(String uuid) {
        Arrays.stream(PermissionNode.values()).forEach(node -> {
            if (node.equals(PermissionNode.ERROR)) return;
            removeTrust(node, uuid);
        });
    }

    public void trustWithAll(Player player) {
        trustWithAll(player.getUniqueId().toString());
    }

    public void removeTrustWithAll(Player player) {
        removeTrustWithAll(player.getUniqueId().toString());
    }

    public boolean hasTrust(PermissionNode node, String uuid) {
        return trusted.getOrDefault(node, new ConcurrentSkipListSet<>()).contains(uuid) || isOwner(uuid);
    }

    public boolean isOwner(String uuid) {
        return getNetwork().map(network -> network.getOwnerUuid().equals(uuid)).orElse(false);
    }

    public boolean hasPermission(Player player, PermissionNode permission) {
        return hasTrust(permission, player.getUniqueId().toString());
    }
}
