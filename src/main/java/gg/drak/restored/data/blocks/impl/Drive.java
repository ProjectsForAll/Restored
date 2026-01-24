package gg.drak.restored.data.blocks.impl;

import gg.drak.thebase.lib.leonhard.storage.sections.FlatFileSection;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.gui.screens.events.BlockCloseEvent;
import host.plas.bou.gui.slots.Slot;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.datablock.DataBlock;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class Drive extends NetworkBlock implements InventoryBlock {
    private ConcurrentSkipListMap<Integer, StorageDisk> disks; // Set of disks in the drive

    public Drive(Network network, Location location) {
        super(BlockType.DRIVE, network, location, DriveItem::new);

        onLoad();
    }

    public Drive(Network network, Location location, DataBlock block) {
        super(BlockType.DRIVE, network, location, DriveItem::new, block);

        onLoad();
    }

    @Override
    public void onLoad() {
        disks = new ConcurrentSkipListMap<>();

        FlatFileSection section = getDataBlock().getSection();

        section.singleLayerKeySet("disks").forEach(s -> {
            int index = Integer.parseInt(s);
            String identifier = section.getString("disks." + s);

            StorageDisk disk = new StorageDisk(this, identifier);

            disks.put(index, disk);
        });
    }

    @Override
    public void onSave() {
        disks.forEach((i, d) -> {
            getDataBlock().write("disks." + i, d.getIdentifier());
        });
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        Restored.getInstance().logInfo("Building inventory sheet for drive...");

        InventorySheet sheet = new InventorySheet(getType().getSlots());

        sheet.forEachSlot(this::buildDriveIcon);

        return sheet;
    }

    public void buildDriveIcon(Slot slot) {
        Restored.getInstance().logInfo("Building drive icon for slot: " + slot.getIndex());

        int index = slot.getIndex();

        getDriveIcon(index).ifPresent(slot::setIcon);
    }

    @Override
    public void onClose(BlockCloseEvent event) {
        InventoryView view = event.getPlayer().getOpenInventory();
        Inventory inventory = view.getTopInventory();
        int index = 0;

        prepareDisks();

        for (ItemStack stack : inventory.getContents()) {
            IndexedItem item = new IndexedItem(stack, index, inventory, event.getPlayer());
            trySaveItem(item);
            index ++;
        }
    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        Restored.getInstance().logInfo("Trying to add item to drive...");

        if (stack == null || stack.getType() == Material.AIR) {
            Restored.getInstance().logInfo("Item is null or air.");

            return stack;
        }

        ItemStack r;
        if (stack.getAmount() > 1) {
            r = stack.clone();
            r.setAmount(stack.getAmount() - 1);
        } else {
            r = null;
        }

        if (! isCompatibleDisk(stack)) {
            Restored.getInstance().logInfo("Item is not a disk.");

            return stack;
        }

        addDisk(stack, disks.size());

        redraw();

        return r;
    }

    // Prepare disks to be saved.
    public void prepareDisks() {
        setDisks(new ConcurrentSkipListMap<>());
    }

    public void trySaveItem(IndexedItem item) {
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        if (! isCompatibleDisk(stack)) {
            ItemStack toGive = stack.clone();
            item.getViewer().getInventory().addItem(toGive);

            item.remove();

            return;
        }

        if (stack.getAmount() != 1) {
            int toGiveBack = stack.getAmount() - 1;
            stack.setAmount(1);
            item.replace(stack);

            ItemStack toGive = stack.clone();
            toGive.setAmount(toGiveBack);
            item.getViewer().getInventory().addItem(toGive);

            return;
        }

        addDisk(stack, item.getSlotNumber());
    }

    public boolean isCompatibleDisk(ItemStack stack) {
        return ItemManager.isDiskItem(stack);
    }

    public void addDisk(ItemStack stack, int index) {
        Restored.getInstance().logInfo("Adding disk to drive...");

        ItemManager.readItem(stack).ifPresentOrElse(item -> {
            if (item instanceof FourKDiskItem) {
                FourKDiskItem diskItem = (FourKDiskItem) item;
                FourKDisk disk = new FourKDisk(this, diskItem.getIdentifier());
                putDisk(index, disk);
            } else if (item instanceof GenericDiskItem) {
                GenericDiskItem diskItem = (GenericDiskItem) item;
                StorageDisk disk = new StorageDisk(this, diskItem.getIdentifier());
                putDisk(index, disk);
            }
        }, () -> {
            Restored.getInstance().logInfo("Item is not a disk.");
        });
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
        AtomicReference<Optional<StorageDisk>> disk = new AtomicReference<>(Optional.empty());

        disks.forEach((i, d) -> {
            if (i == index) {
                disk.set(Optional.of(d));
            }
        });

        return disk.get();
    }

    public void putDisk(int index, StorageDisk disk) {
        Restored.getInstance().logInfo("Putting disk in drive...");

        AtomicBoolean hasDisk = new AtomicBoolean(false);
        getNetwork().ifPresentOrElse(network -> {
            if (network.getDisks().contains(disk)) {
                Restored.getInstance().logInfo("Disk is in the network already!");
                hasDisk.set(true);
            }
        }, () -> {
            Restored.getInstance().logInfo("No network found for drive.");
        });

        if (hasDisk.get()) return;

        disks.put(index, disk);
        onSave();

        Restored.getInstance().logInfo("Disk put in drive.");
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&cDrive");
    }
}
