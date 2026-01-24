package host.plas.restored.data.disks.impl;

import host.plas.restored.data.blocks.impl.Drive;
import host.plas.restored.data.disks.StorageDisk;

import java.math.BigInteger;

public class FourKDisk extends StorageDisk {
    public FourKDisk(Drive drive, String identifier) {
        super(drive, identifier);

        setCapacity(BigInteger.valueOf(4096));
    }
}
