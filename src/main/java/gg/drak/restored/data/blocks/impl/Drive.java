package gg.drak.restored.data.blocks.impl;

import com.google.gson.JsonObject;
import gg.drak.restored.Restored;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.gui.screens.events.BlockCloseEvent;
import host.plas.bou.gui.slots.Slot;
import host.plas.bou.items.ItemUtils;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.disks.StorageDisk;
import gg.drak.restored.data.disks.impl.FourKDisk;
import gg.drak.restored.data.items.ItemManager;
import gg.drak.restored.data.items.impl.DriveItem;
import gg.drak.restored.data.items.impl.FourKDiskItem;
import gg.drak.restored.data.items.impl.GenericDiskItem;
import gg.drak.restored.data.screens.items.IndexedItem;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class Drive extends NetworkBlock implements InventoryBlock {
    private ConcurrentSkipListMap<Integer, StorageDisk> disks; // Set of disks in the drive

    public Drive(Network network, Location location) {
        super(BlockType.DRIVE, network, location, DriveItem::new);

        onLoad();
    }

    public Drive(java.util.UUID uuid, Network network, Location location, com.google.gson.JsonObject data) {
        super(BlockType.DRIVE, uuid, network, location, DriveItem::new, data);

        onLoad();
    }

    @Override
    public void onLoad() {
        disks = new ConcurrentSkipListMap<>();

        JsonObject data = getData();
        if (data.has("disks")) {
            JsonObject disksJson = data.getAsJsonObject("disks");
            disksJson.entrySet().forEach(entry -> {
                int index = Integer.parseInt(entry.getKey());
                String identifier = entry.getValue().getAsString();

                StorageDisk disk = new StorageDisk(this, identifier, index);

                disks.put(index, disk);
            });
        }
    }

    public void onSaveSpecific() {
        JsonObject disksJson = new JsonObject();
        disks.forEach((i, d) -> {
            if (d != null) {
                disksJson.addProperty(String.valueOf(i), d.getIdentifier());
            }
        });
        getData().add("disks", disksJson);
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(getType().getSlots());

        // Populate with empty icons to start so forEachSlot works
        for (int i = 0; i < sheet.getSize(); i++) {
            sheet.addIcon(i, new BasicIcon(new ItemStack(Material.AIR)));
        }

        // Iterate over slots and build icons for disks
        sheet.forEachSlot(this::buildDriveIcon);

        return sheet;
    }

    public void buildDriveIcon(Slot slot) {
        int index = slot.getIndex();

        getDisk(index).ifPresentOrElse(disk -> {
            ItemStack stack = disk.getItem();
            Icon i = new Icon(stack);
            slot.setIcon(i);

            i.onClick(event -> {
                Player player = (Player) event.getWhoClicked();
                
                removeDisk(index);
                player.getInventory().addItem(stack);
                
                redraw();
            });
        }, () -> {
            // slot.setIcon(null); // Clear icon if no disk
        });
    }

    public void removeDisk(int index) {
        disks.remove(index);
        onSave();
        getNetwork().ifPresent(Network::updateCache);
    }

    @Override
    public void onClose(BlockCloseEvent event) {
        // Do not clear disks here if we are using Icons for display,
        // because inventory.getContents() will not contain the Icons.
        // The disks are already saved via putDisk and onSave.
        
        // Update the network cache after closing the drive
        getNetwork().ifPresent(Network::updateCache);
    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return stack;
        }

        if (! isCompatibleDisk(stack)) {
            return stack;
        }

        ItemStack toAdd = stack.clone();
        toAdd.setAmount(1);

        // Find the first empty slot
        int index = -1;
        for (int i = 0; i < getType().getSlots(); i++) {
            if (! disks.containsKey(i)) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            addDisk(toAdd, index);
            redraw();

            ItemStack r = stack.clone();
            r.setAmount(stack.getAmount() - 1);
            if (r.getAmount() <= 0) {
                r = null;
            }

            return r;
        }

        return stack;
    }

    public boolean isCompatibleDisk(ItemStack stack) {
        return ItemManager.isDiskItem(stack);
    }

    public void addDisk(ItemStack stack, int index) {
        ItemManager.readItem(stack).ifPresentOrElse(item -> {
            if (item instanceof FourKDiskItem) {
                FourKDiskItem diskItem = (FourKDiskItem) item;
                FourKDisk disk = new FourKDisk(this, diskItem.getIdentifier(), index);
                putDisk(index, disk);
            } else if (item instanceof GenericDiskItem) {
                GenericDiskItem diskItem = (GenericDiskItem) item;
                StorageDisk disk = new StorageDisk(this, diskItem.getIdentifier(), index);
                disk.setCapacity(diskItem.getSize());
                putDisk(index, disk);
            }
        }, () -> {});
    }

    public Optional<Icon> getDriveIcon(int index) {
        AtomicReference<Optional<Icon>> icon = new AtomicReference<>(Optional.empty());

        getDisk(index).ifPresent(disk -> {
            ItemStack stack = disk.getItem();

            Icon i = new Icon(stack);

            icon.set(Optional.of(i));
        });

        return icon.get();
    }

    public Optional<StorageDisk> getDisk(int index) {
        return Optional.ofNullable(disks.get(index));
    }

    public void putDisk(int index, StorageDisk disk) {
        disks.put(index, disk);
        onSave();
        
        // Update the network cache to include the new disk's capacity
        getNetwork().ifPresent(net -> {
            net.updateCache();
            // Also save the disk itself to ensure its drive association is updated
            disk.save();
        });
        
        redraw();
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&cDrive");
    }
}
