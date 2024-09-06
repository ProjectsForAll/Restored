package host.plas.restored.data.blocks.impl;

import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.ScreenBlock;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.disks.StorageDisk;
import host.plas.restored.data.disks.impl.FourKDisk;
import host.plas.restored.data.items.impl.DriveItem;
import host.plas.restored.data.items.impl.FourKDiskItem;
import host.plas.restored.data.screens.InventorySheet;
import host.plas.restored.data.screens.Slot;
import host.plas.restored.utils.MessageUtils;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import tv.quaint.thebase.lib.leonhard.storage.sections.FlatFileSection;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

@Getter @Setter
public class Drive extends ScreenBlock {
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

        section.singleLayerKeySet("disks.").forEach(s -> {
            int index = Integer.parseInt(s);
            String identifier = section.getString("disks." + s);

            StorageDisk disk = new StorageDisk(identifier);

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
        InventorySheet sheet = new InventorySheet(getType().getSlots());

        sheet.forEachSlot(this::buildDriveIcon);

        return sheet;
    }

    public void buildDriveIcon(Slot slot) {
        int index = slot.getIndex();

        getDriveIcon(index).ifPresentOrElse(slot::setIcon, () -> {
            slot.setIcon(getClickableAir());
        });
    }

    public Icon getClickableAir() {
        Icon icon = new Icon(new ItemStack(Material.AIR));

        icon.onClick(event -> {
            ItemStack stack = event.getCursor();
            if (stack == null || stack.getType() == Material.AIR) {
                return;
            }

            if (stack.getAmount() != 1) return;

            if (! isCompatibleDisk(stack)) {
                return;
            }

            event.setCancelled(false);
        });

        return icon;
    }

    public boolean isCompatibleDisk(ItemStack stack) {
        FourKDiskItem disk = new FourKDiskItem();
        return disk.isSimilar(stack);
    }

    public void addDisk(ItemStack stack, int index) {
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        if (stack.getAmount() != 1) return;

        if (! isCompatibleDisk(stack)) {
            return;
        }

        FourKDisk disk = new FourKDisk(UUID.randomUUID().toString());

        putDisk(index, disk);
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
        disks.put(index, disk);
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return MessageUtils.colorize("&cDrive");
    }
}
