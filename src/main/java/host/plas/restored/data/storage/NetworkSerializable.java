package host.plas.restored.data.storage;

import host.plas.restored.Restored;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.impl.Controller;
import host.plas.restored.data.blocks.NetworkBlock;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import tv.quaint.storage.documents.SimpleJsonDocument;
import tv.quaint.thebase.lib.leonhard.storage.sections.FlatFileSection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class NetworkSerializable extends SimpleJsonDocument {
    private Network network;

    public NetworkSerializable(Network network) {
        super(network.getIdentifier() + ".json", getNetworkStorage(), false);

        this.network = network;
    }

    public static File getNetworkStorage() {
        File storage = new File(Restored.getInstance().getDataFolder(), "storage");
        if (!storage.exists()) {
            storage.mkdirs();
        }
        File disks = new File(storage, "networks");
        if (!disks.exists()) {
            disks.mkdirs();
        }

        return disks;
    }

    @Override
    public void onInit() {
    }

    @Override
    public void onSave() {
        saveController();
        savePermissions();
        saveOwner();
    }

    public void onLoad() {
        reloadResource();
        if (getResource().getData().isEmpty()) {
            return;
        }

        loadController();
        loadPermissions();
        loadOwner();
    }

    public void saveOwner() {
        write("owner", network.getOwnerUuid());
    }

    public void loadOwner() {
        reloadResource();
        network.setOwnerUuid(getResource().getString("owner"));
    }

    public void loadPermissions() {
        reloadResource();

        getNetwork().getPermissionSystem().load(getPermissionSection());
    }

    public void savePermissions() {
        reloadResource();

        getNetwork().getPermissionSystem().save(getPermissionSection());
    }

    public FlatFileSection getPermissionSection() {
        reloadResource();

        return getResource().getSection("permissions");
    }

    public void loadController() {
        reloadResource();

        String world = getOrSetDefault("controller.location.world", "");
        int x = getOrSetDefault("controller.location.x", 0);
        int y = getOrSetDefault("controller.location.y", 0);
        int z = getOrSetDefault("controller.location.z", 0);

        Location location = new Location(Bukkit.getWorld(world), x, y, z);

        Controller controller = new Controller(network, location);

        network.setController(controller);
    }

    public void saveController() {
        Controller controller = network.getController();

        write("controller.location.world", controller.getLocation().getWorld().getName());
        write("controller.location.x", controller.getLocation().getBlockX());
        write("controller.location.y", controller.getLocation().getBlockY());
        write("controller.location.z", controller.getLocation().getBlockZ());
    }
}
