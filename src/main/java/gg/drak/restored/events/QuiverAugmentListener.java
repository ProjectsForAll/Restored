package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.gui.pocket.QuiverAugmentGui;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Supplies a temporary vanilla arrow so bows/crossbows can use network-backed ammo. */
public class QuiverAugmentListener implements Listener {
    private final Map<UUID, PendingShot> pending = new ConcurrentHashMap<>();
    private final Map<UUID, PreparedArrow> prepared = new ConcurrentHashMap<>();

    public QuiverAugmentListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().logInfo("Registered QuiverAugmentListener!");
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(), this::maintainPreparedArrows, 1L, 1L
        );
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Restored.getInstance().getServer().getScheduler().runTask(
                Restored.getInstance(), () -> maintainPreparedArrow(event.getPlayer())
        );
    }

    // RIGHT_CLICK_AIR is commonly pre-cancelled by Bukkit's vanilla interaction
    // prediction, so this must also receive cancelled interaction events.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBowUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack bow = player.getInventory().getItemInMainHand();
        if (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PreparedArrow preparedArrow = prepared.remove(playerId);
        if (preparedArrow != null) {
            if (hasReservedArrow(player, preparedArrow) && !hasNonReservedArrow(player, preparedArrow)) {
                beginPending(player, preparedArrow.network(), preparedArrow.arrow());
                return;
            }
            returnPreparedArrow(player, preparedArrow);
            return;
        }
        if (pending.containsKey(playerId) || hasArrow(player)) {
            return;
        }

        Selection selection = selectArrow(player);
        if (selection == null) {
            return;
        }
        long extracted = selection.network().extract(selection.itemKey(), 1);
        if (extracted <= 0) {
            return;
        }
        selection.network().save();

        ItemStack arrow = selection.template().clone();
        arrow.setAmount(1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(arrow.clone());
        if (!leftovers.isEmpty() && !isEmpty(player.getInventory().getItemInOffHand())) {
            selection.network().forceInsert(arrow, 1);
            selection.network().save();
            player.sendMessage(LegacyColors.color("#FF5555You need an empty inventory slot to use your Quiver."));
            return;
        }
        if (!leftovers.isEmpty()) {
            player.getInventory().setItemInOffHand(arrow.clone());
        }

        // The vanilla bow/crossbow use check runs immediately after this event. Push the
        // temporary projectile to the client now so charging is allowed even when the
        // player's inventory was empty when the interaction started.
        player.updateInventory();

        beginPending(player, selection.network(), arrow);
    }

    private void maintainPreparedArrows() {
        for (Player player : Restored.getInstance().getServer().getOnlinePlayers()) {
            maintainPreparedArrow(player);
        }
    }

    private void maintainPreparedArrow(Player player) {
        UUID playerId = player.getUniqueId();
        PreparedArrow preparedArrow = prepared.get(playerId);
        boolean holdingWeapon = isBow(player.getInventory().getItemInMainHand());

        if (!holdingWeapon || pending.containsKey(playerId)) {
            if (preparedArrow != null && prepared.remove(playerId, preparedArrow)) {
                returnPreparedArrow(player, preparedArrow);
            }
            return;
        }

        if (preparedArrow != null) {
            if (!hasReservedArrow(player, preparedArrow)) {
                prepared.remove(playerId, preparedArrow);
            } else if (hasNonReservedArrow(player, preparedArrow)
                    && prepared.remove(playerId, preparedArrow)) {
                returnPreparedArrow(player, preparedArrow);
            }
            return;
        }

        if (hasArrow(player)) {
            return;
        }

        int slot = player.getInventory().firstEmpty();
        if (slot < 0 && !isEmpty(player.getInventory().getItemInOffHand())) {
            return;
        }
        boolean useOffhand = slot < 0;
        Selection selection = selectArrow(player);
        if (selection == null || selection.network().extract(selection.itemKey(), 1) <= 0) {
            return;
        }
        selection.network().save();

        ItemStack arrow = selection.template().clone();
        arrow.setAmount(1);
        if (useOffhand) {
            player.getInventory().setItemInOffHand(arrow);
        } else {
            player.getInventory().setItem(slot, arrow);
        }
        prepared.put(playerId, new PreparedArrow(selection.network(), arrow, useOffhand ? -2 : slot));
        player.updateInventory();
    }

    private void beginPending(Player player, Network network, ItemStack arrow) {
        PendingShot shot = new PendingShot(network, arrow);
        pending.put(player.getUniqueId(), shot);
        shot.task = Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(), () -> checkForRelease(player, shot), 2L, 1L
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onShoot(EntityShootBowEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        PendingShot shot = pending.get(player.getUniqueId());
        if (shot == null) {
            return;
        }
        if (event.isCancelled() || !(event.getProjectile() instanceof AbstractArrow)) {
            finish(player, shot, false);
            return;
        }
        finish(player, shot, true);
    }

    private void checkForRelease(Player player, PendingShot shot) {
        if (pending.get(player.getUniqueId()) != shot) {
            cancelTask(shot);
            return;
        }
        if (!player.isOnline()) {
           	finish(player, shot, false);
            return;
        }
        if (shot.graceTicks-- > 0) {
            return;
        }
        if (!player.isHandRaised()) {
            finish(player, shot, false);
        }
    }

    private void finish(Player player, PendingShot shot, boolean fired) {
        if (!pending.remove(player.getUniqueId(), shot)) {
            return;
        }
        cancelTask(shot);
        if (fired) {
            // EntityShootBowEvent fires while vanilla is still finishing the shot. Wait
            // one tick before cleaning up so vanilla can consume the temporary arrow.
            Restored.getInstance().getServer().getScheduler().runTask(
                    Restored.getInstance(), () -> removeOne(player, shot.arrow())
            );
            return;
        }

        if (removeOne(player, shot.arrow())) {
            shot.network().forceInsert(shot.arrow(), 1);
            shot.network().save();
        }
    }

    private static void cancelTask(PendingShot shot) {
        if (shot.task != null) {
            shot.task.cancel();
            shot.task = null;
        }
    }

    private static boolean isBow(ItemStack stack) {
        return stack != null && (stack.getType() == Material.BOW || stack.getType() == Material.CROSSBOW);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType().isAir();
    }

    private static boolean hasReservedArrow(Player player, PreparedArrow preparedArrow) {
        return findReservedSlot(player, preparedArrow) != -1;
    }

    private static boolean hasNonReservedArrow(Player player, PreparedArrow preparedArrow) {
        int reservedSlot = findReservedSlot(player, preparedArrow);
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            ItemStack stack = player.getInventory().getContents()[i];
            if (!QuiverAugmentGui.isArrow(stack)) {
                continue;
            }
            if (i == reservedSlot) {
                if (stack.getAmount() > 1) {
                    return true;
                }
                continue;
            }
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!QuiverAugmentGui.isArrow(offhand)) {
            return false;
        }
        return reservedSlot != -2;
    }

    private static void returnPreparedArrow(Player player, PreparedArrow preparedArrow) {
        if (!removePreparedArrow(player, preparedArrow)) {
            return;
        }
        preparedArrow.network().forceInsert(preparedArrow.arrow(), 1);
        preparedArrow.network().save();
        player.updateInventory();
    }

    private static int findReservedSlot(Player player, PreparedArrow preparedArrow) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == preparedArrow.arrow()) {
                return i;
            }
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand == preparedArrow.arrow()) {
            return -2;
        }
        if (preparedArrow.slot() >= 0 && preparedArrow.slot() < contents.length
                && contents[preparedArrow.slot()] != null
                && contents[preparedArrow.slot()].isSimilar(preparedArrow.arrow())) {
            return preparedArrow.slot();
        }
        if (offhand.isSimilar(preparedArrow.arrow())) {
            return -2;
        }
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && contents[i].isSimilar(preparedArrow.arrow())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean removePreparedArrow(Player player, PreparedArrow preparedArrow) {
        int slot = findReservedSlot(player, preparedArrow);
        if (slot == -1) {
            return false;
        }
        if (slot == -2) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getAmount() <= 1) {
                player.getInventory().setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
            }
            return true;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
        return true;
    }

    private static boolean removeOne(Player player, ItemStack template) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !stack.isSimilar(template)) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - 1);
            }
            player.getInventory().setContents(contents);
            return true;
        }
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.isSimilar(template)) {
            if (offhand.getAmount() <= 1) {
                player.getInventory().setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    private static boolean hasArrow(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (QuiverAugmentGui.isArrow(stack)) {
                return true;
            }
        }
        return QuiverAugmentGui.isArrow(player.getInventory().getItemInOffHand());
    }

    private Selection selectArrow(Player player) {
        for (ItemStack link : player.getInventory().getContents()) {
            Selection selection = selectFromLink(player, link);
            if (selection != null) {
                return selection;
            }
        }
        return selectFromLink(player, player.getInventory().getItemInOffHand());
    }

    private Selection selectFromLink(Player player, ItemStack link) {
        if (!PocketLinkItem.isType(link) || !PocketLinkItem.hasAugment(link, gg.drak.restored.data.PocketAugmentType.QUIVER)) {
            return null;
        }
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if (networkId.isEmpty()) {
            return null;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null || !network.canWithdraw(player.getUniqueId())) {
            return null;
        }

        List<ItemStack> filters = PocketLinkItem.getQuiverFilters(link);
        boolean anyFilter = filters.stream().anyMatch(QuiverAugmentGui::isArrow);
        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getQuiverFilterMode(link);
        PocketLinkItem.FeedMetaMode metaMode = PocketLinkItem.getQuiverMetaMode(link);

        for (StoredStack stored : network.getCombinedStacks()) {
            ItemStack template = stored.getTemplate();
            if (!QuiverAugmentGui.isArrow(template)) {
                continue;
            }
            boolean matched = matchesFilter(template, filters, metaMode);
            boolean allowed = !anyFilter && mode == PocketLinkItem.FeedFilterMode.BLACKLIST
                    || mode == PocketLinkItem.FeedFilterMode.WHITELIST && matched
                    || mode == PocketLinkItem.FeedFilterMode.BLACKLIST && !matched;
            if (allowed && stored.getAmount() > 0) {
                return new Selection(network, StoredStack.itemKey(template), template);
            }
        }
        return null;
    }

    private static boolean matchesFilter(ItemStack item, List<ItemStack> filters, PocketLinkItem.FeedMetaMode metaMode) {
        for (ItemStack filter : filters) {
            if (!QuiverAugmentGui.isArrow(filter)) {
                continue;
            }
            if (metaMode == PocketLinkItem.FeedMetaMode.ANY
                    ? item.getType() == filter.getType()
                    : StoredStack.itemKey(item).equals(StoredStack.itemKey(filter))) {
                return true;
            }
        }
        return false;
    }

    private static final class PendingShot {
        private final Network network;
        private final ItemStack arrow;
        private int graceTicks = 3;
        private BukkitTask task;

        private PendingShot(Network network, ItemStack arrow) {
            this.network = network;
            this.arrow = arrow;
        }

        private Network network() {
            return network;
        }

        private ItemStack arrow() {
            return arrow;
        }
    }

    private record PreparedArrow(Network network, ItemStack arrow, int slot) {
    }

    private record Selection(Network network, String itemKey, ItemStack template) {
    }
}
