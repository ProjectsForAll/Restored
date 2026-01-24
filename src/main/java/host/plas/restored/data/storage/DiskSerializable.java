package host.plas.restored.data.storage;

import gg.drak.thebase.storage.documents.SimpleJsonDocument;
import host.plas.bou.gui.items.ItemData;
import host.plas.restored.data.disks.StorageDisk;
import host.plas.restored.data.screens.items.StoredItem;
import host.plas.restored.utils.IOUtils;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.math.BigInteger;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter @Setter
public class DiskSerializable extends SimpleJsonDocument {
    private StorageDisk disk;

    public DiskSerializable(StorageDisk disk) {
        super(disk.getIdentifier() + ".json", IOUtils.getDisksFolder(), false);

        this.disk = disk;
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

                StoredItem item = new StoredItem(data);
                items.add(item);
            }
        });

        return items;
    }
}
