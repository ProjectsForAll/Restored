package host.plas.restored.data.storage;

import host.plas.restored.Restored;
import host.plas.restored.data.disks.StorageDisk;
import host.plas.restored.data.screens.items.ItemData;
import host.plas.restored.data.screens.items.StoredItem;
import lombok.Getter;
import lombok.Setter;
import tv.quaint.storage.documents.SimpleJsonDocument;

import java.io.File;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class DiskSerializable extends SimpleJsonDocument {
    private StorageDisk disk;

    public DiskSerializable(StorageDisk disk) {
        super(disk.getIdentifier() + ".json", getDiskStorage(), false);

        this.disk = disk;
    }

    public static File getDiskStorage() {
        File storage = new File(Restored.getInstance().getDataFolder(), "storage");
        if (!storage.exists()) {
            storage.mkdirs();
        }
        File disks = new File(storage, "disks");
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
        setCapacity(disk.getCapacity());
        saveItems();
    }

    public BigInteger getCapacity() {
        return BigInteger.valueOf(getResource().getInt("capacity"));
    }

    public void setCapacity(BigInteger capacity) {
        write("capacity", capacity.intValue());
    }

    public void saveItems() {
        singleLayerKeySet().forEach(s -> {
            if (s.startsWith("contents.")) {
                getResource().remove(s);
            }
        });

        ConcurrentSkipListSet<StoredItem> items = getDisk().getContents();

        items.forEach(i -> {
            String path = "contents." + i.getIdentifier();

            write(path + ".data", i.toData().getData());
            write(path + ".amount", i.getAmount());
        });
    }

    public ConcurrentSkipListSet<StoredItem> getItems() {
        ConcurrentSkipListSet<StoredItem> items = new ConcurrentSkipListSet<>();

        singleLayerKeySet().forEach(s -> {
            if (s.startsWith("contents.")) {
                String identifier = s.replace("contents.", "");
                BigInteger amount = BigInteger.valueOf(getResource().getInt(s + ".amount"));
                String stringData = getResource().getString(s);

                ItemData data = new ItemData(identifier, amount, stringData);

                items.add(data.toStoredItem());
            }
        });

        return items;
    }
}
