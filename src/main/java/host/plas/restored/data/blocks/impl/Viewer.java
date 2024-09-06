package host.plas.restored.data.blocks.impl;

import host.plas.restored.Restored;
import host.plas.restored.data.Network;
import host.plas.restored.data.blocks.BlockType;
import host.plas.restored.data.blocks.ScreenBlock;
import host.plas.restored.data.blocks.datablock.DataBlock;
import host.plas.restored.data.items.impl.ViewerItem;
import host.plas.restored.data.screens.InventorySheet;
import host.plas.restored.data.screens.ScreenInstance;
import host.plas.restored.data.screens.ScreenManager;
import host.plas.restored.data.screens.items.ViewerPage;
import host.plas.restored.utils.MessageUtils;
import io.streamlined.bukkit.commands.Sender;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.Icon;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Getter @Setter
public class Viewer extends ScreenBlock {
    public Viewer(Network network, Location location) {
        super(BlockType.VIEWER, network, location, ViewerItem::new);
    }

    public Viewer(Network network, Location location, DataBlock block) {
        super(BlockType.VIEWER, network, location, ViewerItem::new, block);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSave() {

    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        InventorySheet sheet = new InventorySheet(54);

        Optional<Network> networkOptional = getNetwork();
        if (! networkOptional.isPresent()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return sheet;
        }
        Network network = networkOptional.get();

        ViewerPage page = network.getPage(1);

        if (page == null) {
            return sheet;
        }

        buildPage(sheet, page);

        return sheet;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return MessageUtils.colorize("&bNetwork Viewer");
    }

    public void showPage(Player player, int pageIndex, ScreenBlock block) {
        Optional<Network> networkOptional = getNetwork();
        if (! networkOptional.isPresent()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return;
        }
        Network network = networkOptional.get();

        ViewerPage page = network.getPage(pageIndex);

        if (page == null) {
            return;
        }

        InventorySheet sheet = new InventorySheet(54);

        buildPage(sheet, page);

        InventorySheet inventorySheet = buildInventorySheet(player, block);
        String title = buildTitle(player, block);

        ScreenInstance screen = new ScreenInstance(player, this.getIdentifier(), title, inventorySheet, block);

        screen.open();
    }

    public void buildPage(InventorySheet sheet, ViewerPage page) {
        AtomicInteger index = new AtomicInteger(0);

        page.getContents().forEach(i -> {
            sheet.addIcon(index.getAndIncrement(), i.asPageItem());
        });

        sheet.forEachSlot(slot -> {
            if (slot.getIndex() >= 9 * 5 && slot.getIndex() < 9 * 6) {
                slot.setIcon(new Icon(new ItemStack(Material.BLUE_STAINED_GLASS_PANE)));
            }
        });

        drawBottomBar(sheet, page.getIndex());
    }

    public void drawBottomBar(InventorySheet sheet, int currentPage) {
        int offset = sheet.getSize() - 9;

        sheet.addIcon(offset + 1, getPageLeft(sheet, currentPage));
        sheet.addIcon(offset + 9, getPageRight(sheet, currentPage));
    }

    public Icon getPageLeft(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 9;

        ItemStack stack = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fPrevious Page");

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, page);
            container.set(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, page - 1);

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            Player player = (Player) event.getViewers().get(0);

            ScreenManager.getScreen(player).ifPresent(screen -> {
                int currentPage = -1;
                int actionPage = -1;

                ItemStack s = screen.getItems().get(offset + 1).getItem();
                if (s == null) {
                    return;
                }
                ItemMeta m = s.getItemMeta();
                if (m != null) {
                    PersistentDataContainer c = m.getPersistentDataContainer();
                    currentPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, -1);
                    actionPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, -1);

                    if (currentPage == -1 && actionPage == -1) {
                        return;
                    }
                }

                ScreenBlock screenBlock = screen.getBlock();

                if (screenBlock instanceof Viewer) {
                    Viewer viewer = (Viewer) screenBlock;

                    viewer.showPage(player, actionPage, screenBlock);
                }
            });
        });

        return icon;
    }

    public Icon getPageRight(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 9;

        ItemStack stack = new ItemStack(Material.ARROW, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§fNext Page");

            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, page);
            container.set(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, page + 1);

            stack.setItemMeta(meta);
        }

        Icon icon = new Icon(stack);

        icon.onClick(event -> {
            Player player = (Player) event.getViewers().get(0);

            ScreenManager.getScreen(player).ifPresent(screen -> {
                int currentPage = -1;
                int actionPage = -1;

                ItemStack s = screen.getItems().get(offset + 9).getItem();
                if (s == null) {
                    return;
                }
                ItemMeta m = s.getItemMeta();
                if (m != null) {
                    PersistentDataContainer c = m.getPersistentDataContainer();
                    currentPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "current-page"), PersistentDataType.INTEGER, -1);
                    actionPage = c.getOrDefault(new NamespacedKey(Restored.getInstance(), "action-page"), PersistentDataType.INTEGER, -1);

                    if (currentPage == -1 && actionPage == -1) {
                        return;
                    }
                }

                ScreenBlock screenBlock = screen.getBlock();

                if (screenBlock instanceof Viewer) {
                    Viewer viewer = (Viewer) screenBlock;

                    viewer.showPage(player, actionPage, screenBlock);
                }
            });
        });

        return icon;
    }
}
