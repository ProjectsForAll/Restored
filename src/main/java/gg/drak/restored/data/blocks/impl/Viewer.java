package gg.drak.restored.data.blocks.impl;

import host.plas.bou.commands.Sender;
import host.plas.bou.gui.InventorySheet;
import host.plas.bou.gui.ScreenManager;
import host.plas.bou.gui.icons.BasicIcon;
import host.plas.bou.gui.screens.ScreenInstance;
import host.plas.bou.gui.screens.blocks.ScreenBlock;
import host.plas.bou.items.ItemUtils;
import host.plas.bou.utils.ColorUtils;
import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.blocks.BlockType;
import gg.drak.restored.data.blocks.NetworkBlock;
import gg.drak.restored.data.blocks.inventory.InventoryBlock;
import gg.drak.restored.data.items.impl.ViewerItem;
import gg.drak.restored.data.screens.items.ViewerPage;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter @Setter
public class Viewer extends NetworkBlock implements InventoryBlock {
    public Viewer(Network network, Location location) {
        super(BlockType.VIEWER, network, location, ViewerItem::new);
    }

    public Viewer(java.util.UUID uuid, Network network, Location location, com.google.gson.JsonObject data) {
        super(BlockType.VIEWER, uuid, network, location, ViewerItem::new, data);
    }

    @Override
    public void onLoad() {

    }

    @Override
    public void onSaveSpecific() {

    }

    @Override
    public ItemStack tryAddItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return stack;
        }

        if (! canAddItem(stack)) {
            return stack;
        }

        ItemStack toAdd = stack.clone();
        toAdd.setAmount(1);
        
        if (addItem(toAdd)) {
            redraw();

            ItemStack r = stack.clone();
            r.setAmount(stack.getAmount() - 1);
            if (r.getAmount() <= 0) {
                r = null;
            }

            return r;
        }

        return stack;
    }

    public boolean canAddItem(ItemStack stack) {
        AtomicBoolean canAdd = new AtomicBoolean(false);

        getNetwork().ifPresent(network -> {
            canAdd.set(network.canInsert(stack));
        });

        return canAdd.get();
    }

    public boolean addItem(ItemStack stack) {
        AtomicBoolean added = new AtomicBoolean(false);

        getNetwork().ifPresent(network -> {
            added.set(network.insert(stack));

            if (added.get()) {
                network.getBlocks().forEach(block -> {
                    if (block instanceof Drive) {
                        redraw();
                    }
                });
            }
        });

        return added.get();
    }

    @Override
    public InventorySheet buildInventorySheet(Player player, ScreenBlock block) {
        Restored.getInstance().logInfo("Building inventory sheet for viewer...");

        InventorySheet sheet = new InventorySheet(54);

        Optional<Network> networkOptional = getNetwork();
        if (networkOptional.isEmpty()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return sheet;
        }
        Network network = networkOptional.get();

        Optional<ViewerPage> pageOptional = network.getPage(1);

        if (pageOptional.isEmpty()) {
            return sheet;
        }
        ViewerPage page = pageOptional.get();

        buildPage(sheet, page);

        return sheet;
    }

    @Override
    public String buildTitle(Player player, ScreenBlock block) {
        return ColorUtils.colorizeHard("&bNetwork Viewer");
    }

    public void showPage(Player player, int pageIndex, ScreenBlock block) {
        Optional<Network> networkOptional = getNetwork();
        if (! networkOptional.isPresent()) {
            Sender playerSender = new Sender(player);
            playerSender.sendMessage("&cThis block is not part of a network.");
            return;
        }
        Network network = networkOptional.get();

        Optional<ViewerPage> pageOptional = network.getPage(pageIndex);

        if (pageOptional.isEmpty()) {
            return;
        }
        ViewerPage page = pageOptional.get();

        InventorySheet sheet = new InventorySheet(54);

        buildPage(sheet, page);

        InventorySheet inventorySheet = buildInventorySheet(player, block);
        String title = buildTitle(player, block);

        ScreenInstance screen = new ScreenInstance(player, this.getType(), inventorySheet);
        screen.setTitle(title);
        screen.setBlock(block);

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
        sheet.addIcon(sheet.getSize() - 8 - 1, getPageLeft(sheet, currentPage));
        sheet.addIcon(sheet.getSize() - 4 - 1, getPageMiddle(sheet, currentPage));
        sheet.addIcon(sheet.getSize() - 0 - 1, getPageRight(sheet, currentPage));
    }

    public Icon getPageMiddle(InventorySheet sheet, int page) {
        ItemStack stack = ItemUtils.make(Material.KNOWLEDGE_BOOK, "&bPage&7: &a" + page);

        return new BasicIcon(stack);
    }

    public Icon getPageLeft(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 8 - 1;

        ItemStack stack = ItemUtils.make(Material.ARROW, "&7<<< &ePrevious Page");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {

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

                ItemStack s = screen.getItems().get(offset).getItem();
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

                Optional<ScreenBlock> screenBlock = screen.getScreenBlock();
                if (screenBlock.isEmpty()) return;
                ScreenBlock block = screenBlock.get();

                if (block instanceof Viewer) {
                    Viewer viewer = (Viewer) block;

                    viewer.showPage(player, actionPage, viewer);
                }
            });
        });

        return icon;
    }

    public Icon getPageRight(InventorySheet sheet, int page) {
        int offset = sheet.getSize() - 0 - 1;

        ItemStack stack = ItemUtils.make(Material.ARROW, "&eNext Page &7>>>");
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
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

                ItemStack s = screen.getItems().get(offset).getItem();
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

                Optional<ScreenBlock> screenBlock = screen.getScreenBlock();
                if (screenBlock.isEmpty()) return;
                ScreenBlock block = screenBlock.get();

                if (block instanceof Viewer) {
                    Viewer viewer = (Viewer) block;

                    viewer.showPage(player, actionPage, viewer);
                }
            });
        });

        return icon;
    }
}
