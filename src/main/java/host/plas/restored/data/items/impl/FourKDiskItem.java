package host.plas.restored.data.items.impl;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;

@Getter @Setter
public class FourKDiskItem extends GenericDiskItem {
    private BigInteger size;

    public FourKDiskItem() {
        super(BigInteger.valueOf(4096));
    }

    public static String getSizedName(BigInteger size) {
        return "&a" + size + " &7Item Disk";
    }

    @Override
    public void updateLore() {

    }
}
