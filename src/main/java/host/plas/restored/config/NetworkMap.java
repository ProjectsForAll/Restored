package host.plas.restored.config;

import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;
import tv.quaint.storage.documents.SimpleJsonDocument;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class NetworkMap extends SimpleJsonDocument {
    public NetworkMap() {
        super("network-map.json", Restored.getInstance(), false);
    }

    @Override
    public void onInit() {

    }

    @Override
    public void onSave() {

    }

    public static String getKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public void saveNetwork(Network network) {
        String owner = network.getOwnerUuid();
        String identifier = network.getIdentifier();
        String locationalController = getKey(network.getController().getBlock());
    }

    public void setNetworkWithOwner(String ownerUuid, String networkUuid) {
        write("networks." + networkUuid + ".owner", ownerUuid);
    }

    public String getOwnerForNetwork(String networkUuid) {
        return getOrSetDefault("networks." + networkUuid + ".owner", "");
    }

    public ConcurrentSkipListSet<String> getNetworks() {
        reloadResource();

        return new ConcurrentSkipListSet<>(singleLayerKeySet("networks"));
    }

    public void addNetworkToPlayer(String ownerUuid, String networkUuid) {
        ConcurrentSkipListSet<String> networks = getNetworksForPlayer(ownerUuid);

        networks.add(networkUuid);

        write("players." + ownerUuid, new ArrayList<>(networks));
    }

    public void removeNetworkFromPlayer(String ownerUuid, String networkUuid) {
        ConcurrentSkipListSet<String> networks = getNetworksForPlayer(ownerUuid);

        networks.remove(networkUuid);

        write("players." + ownerUuid, new ArrayList<>(networks));
    }

    public ConcurrentSkipListSet<String> getNetworksForPlayer(String ownerUuid) {
        reloadResource();

        if (! getResource().contains(ownerUuid)) {
            return new ConcurrentSkipListSet<>();
        }

        return new ConcurrentSkipListSet<>(getOrSetDefault("players." + ownerUuid, new ArrayList<>()));
    }
}
