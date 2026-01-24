package gg.drak.restored.utils;

import gg.drak.restored.Restored;

import java.io.File;

public class IOUtils {
    public static File getPluginFolder() {
        return Restored.getInstance().getDataFolder();
    }

    public static File getStorageFolder() {
        File folder = new File(getPluginFolder(), "storage");
        if (! folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    public static File getDisksFolder() {
        File folder = new File(getStorageFolder(), "disks");
        if (! folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }

    public static File getNetworkFolder() {
        File folder = new File(getStorageFolder(), "networks");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return folder;
    }
}
