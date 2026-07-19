package gg.drak.restored.data.blocks;

import host.plas.bou.gui.GuiType;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.impl.*;
import lombok.Getter;
import org.bukkit.Location;

@Getter
public enum BlockType implements GuiType {
    CONTROLLER(9, "Controller"),
    DRIVE(18, "Drive"),
    VIEWER(54, "Viewer"),
    CRAFTING_VIEWER(54, "Crafting Viewer"),
    EXTERNAL_STORAGE(9, "External Storage"),
    IMPORTER(9, "Importer"),
    EXPORTER(9, "Exporter"),
    CRAFTER(54, "Crafter"),

    NONE,
    ;

    private final int slots;
    private final String title;

    BlockType(int slots, String title) {
        this.slots = slots;
        this.title = title;
    }

    BlockType() {
        this(-1, null);
    }

    public static NetworkBlock getBlock(BlockType type, java.util.UUID uuid, Network network, Location location, com.google.gson.JsonObject data) {
        switch (type) {
            case CONTROLLER:
                return new Controller(uuid, network, location, data);
            case DRIVE:
                return new Drive(uuid, network, location, data);
            case VIEWER:
                return new Viewer(uuid, network, location, data);
            case CRAFTING_VIEWER:
                return new CraftingViewer(uuid, network, location, data);
            case EXTERNAL_STORAGE:
                return new ExternalStorage(uuid, network, location, data);
            case IMPORTER:
                return new Importer(uuid, network, location, data);
            case EXPORTER:
                return new Exporter(uuid, network, location, data);
            case CRAFTER:
                return new Crafter(uuid, network, location, data);
            default:
                return null;
        }
    }

    public static NetworkBlock getBlock(BlockType type, Network network, Location location) {
        switch (type) {
            case CONTROLLER:
                return new Controller(network, location);
            case DRIVE:
                return new Drive(network, location);
            case VIEWER:
                return new Viewer(network, location);
            case CRAFTING_VIEWER:
                return new CraftingViewer(network, location);
            case EXTERNAL_STORAGE:
                return new ExternalStorage(network, location);
            case IMPORTER:
                return new Importer(network, location);
            case EXPORTER:
                return new Exporter(network, location);
            case CRAFTER:
                return new Crafter(network, location);
            default:
                return null;
        }
    }
}
