package host.plas.restored.data.items.impl;

import host.plas.restored.data.items.ItemType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.util.UUID;

@Getter @Setter
public class FourKDiskItem extends GenericDiskItem {
    public FourKDiskItem(String identifier) {
        super(ItemType.FOUR_K_DISK, BigInteger.valueOf(4096), identifier);
    }

    public FourKDiskItem() {
        super(ItemType.FOUR_K_DISK, BigInteger.valueOf(4096), UUID.randomUUID().toString());
    }

    public static String getSizedName(BigInteger size) {
        return "&a" + size + " &7Item Disk";
    }

    @Override
    public void updateLore() {

    }
}
