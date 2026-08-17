package gg.drak.restored.gui;

import host.plas.bou.gui.CornerColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Restored GUI item helpers. Chrome delegates to BOU {@link host.plas.bou.gui.GuiItems};
 * confirm/cancel/search are Restored-only.
 */
public final class GuiItems {
    private GuiItems() {
    }

    public static ItemStack cornerPane(CornerColor color) {
        return host.plas.bou.gui.GuiItems.cornerPane(color);
    }

    public static ItemStack filler(Material material) {
        return host.plas.bou.gui.GuiItems.filler(material);
    }

    public static ItemStack button(Material material, String name, List<String> lore) {
        return host.plas.bou.gui.GuiItems.button(material, name, lore);
    }

    public static ItemStack button(Material material, String name, String... lore) {
        return host.plas.bou.gui.GuiItems.button(material, name, lore);
    }

    public static ItemStack returnButton() {
        return host.plas.bou.gui.GuiItems.returnButton();
    }

    public static ItemStack pagePreviousButton(int displayPage) {
        return host.plas.bou.gui.GuiItems.pagePreviousButton(displayPage);
    }

    public static ItemStack pageNextButton(int displayPage) {
        return host.plas.bou.gui.GuiItems.pageNextButton(displayPage);
    }

    public static ItemStack confirmButton() {
        return button(
                Material.LIME_STAINED_GLASS_PANE,
                "#00FC88&lConfirm",
                "#bdc8c9Click to confirm."
        );
    }

    public static ItemStack cancelButton() {
        return button(
                Material.RED_STAINED_GLASS_PANE,
                "#FF5555&lCancel",
                "#bdc8c9Click to cancel."
        );
    }

    public static ItemStack searchButton(String filter) {
        String line = filter == null || filter.isBlank() ? "None" : filter;
        return button(
                Material.OAK_SIGN,
                "#FFED6A&lSearch",
                "#bdc8c9Click to search players.",
                "#AAAAAACurrent: #FFED6A" + line
        );
    }
}
