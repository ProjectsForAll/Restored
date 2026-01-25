package gg.drak.restored.data.disks.impl;

import gg.drak.restored.data.blocks.impl.Drive;
import gg.drak.restored.data.disks.StorageDisk;

import java.math.BigInteger;

public class FourKDisk extends StorageDisk {
    public FourKDisk(Drive drive, String identifier) {
        super(drive, identifier);

        setCapacity(BigInteger.valueOf(4096));
    }

    public FourKDisk(Drive drive, String identifier, int slot) {
        super(drive, identifier, slot);

        setCapacity(BigInteger.valueOf(4096));
    }
}
