package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.items.PocketLinkItem;
import gg.drak.restored.gui.pocket.RocketDistributerAugmentGui;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Periodically keeps the configured firework rocket reserve in a player's inventory. */
public final class RocketDistributerAugmentListener implements Listener {

    private static final int OFF_HAND = -1;

    public RocketDistributerAugmentListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(),
                () -> {
                    for (Player player : Restored.getInstance().getServer().getOnlinePlayers()) {
                        distribute(player);
                    }
                },
                20L,
                Restored.getMainConfig() == null
                        ? gg.drak.restored.config.MainConfig.DEFAULT_ROCKET_INTERVAL
                        : Restored.getMainConfig().getRocketInterval()
        );
        Restored.getInstance().logInfo("Registered RocketDistributerAugmentListener!");
    }

    public static void distribute(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        for (ItemStack link : linksInPriorityOrder(player)) {
            if (!PocketLinkItem.isType(link)
                    || !PocketLinkItem.hasAugment(link, PocketAugmentType.ROCKET_DISTRIBUTER)
                    || !PocketLinkItem.isRocketEnabled(link)) {
                continue;
            }
            if (replenishFromLink(player, link)) {
                // One link is enough for this pass. Multiple links are still supported,
                // but they will each get a chance on the next pass if this one is full.
                return;
            }
        }
    }

    private static boolean replenishFromLink(Player player, ItemStack link) {
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if (networkId.isEmpty()) {
            return false;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null || !network.canWithdraw(player.getUniqueId())) {
            return false;
        }

        long keep = PocketLinkItem.getRocketKeepAmount(link);
        if (keep <= 0) {
            return false;
        }
        List<ItemStack> filters = PocketLinkItem.getRocketFilters(link);
        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getRocketFilterMode(link);
        PocketLinkItem.FeedMetaMode metaMode = PocketLinkItem.getRocketMetaMode(link);

        List<Slot> slots = inventorySlots(player);
        List<Candidate> candidates = candidates(network, filters, mode, metaMode);
        if (candidates.isEmpty()) {
            return false;
        }

        Candidate selected = selectCandidate(slots, candidates);
        if (selected == null) {
            return false;
        }

        long current = countSimilar(slots, selected.template());
        long needed = keep - current;
        if (needed <= 0) {
            return false;
        }
        long free = freeSpace(slots, selected.template());
        long toTake = Math.min(needed, free);
        if (toTake <= 0) {
            return false;
        }

        long taken = network.extract(selected.itemKey(), toTake);
        if (taken <= 0) {
            return false;
        }
        long placed = place(slots, selected.template(), taken);
        if (placed < taken) {
            // This should only be possible if another inventory action raced the tick.
            network.forceInsert(selected.template(), taken - placed);
        }
        if (placed <= 0) {
            return false;
        }
        player.updateInventory();
        return true;
    }

    private static List<ItemStack> linksInPriorityOrder(Player player) {
        List<ItemStack> links = new ArrayList<>();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (PocketLinkItem.isType(offHand)) {
            links.add(offHand);
        }
        int held = player.getInventory().getHeldItemSlot();
        ItemStack main = player.getInventory().getItem(held);
        if (PocketLinkItem.isType(main) && main != offHand) {
            links.add(main);
        }
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (PocketLinkItem.isType(stack) && stack != main && stack != offHand) {
                links.add(stack);
            }
        }
        return links;
    }

    private static List<Slot> inventorySlots(Player player) {
        List<Slot> slots = new ArrayList<>(38);
        slots.add(new Slot(player.getInventory(), OFF_HAND, player.getInventory().getItemInOffHand()));
        int held = player.getInventory().getHeldItemSlot();
        slots.add(new Slot(player.getInventory(), held, player.getInventory().getItem(held)));
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (i != held) {
                slots.add(new Slot(player.getInventory(), i, storage[i]));
            }
        }
        return slots;
    }

    private static List<Candidate> candidates(
            Network network,
            List<ItemStack> filters,
            PocketLinkItem.FeedFilterMode mode,
            PocketLinkItem.FeedMetaMode metaMode
    ) {
        boolean anyFilter = filters.stream().anyMatch(RocketDistributerAugmentGui::isRocket);
        List<Candidate> result = new ArrayList<>();
        for (StoredStack stored : network.getCombinedStacks(m -> m == org.bukkit.Material.FIREWORK_ROCKET)) {
            ItemStack template = stored.getTemplate();
            if (!RocketDistributerAugmentGui.isRocket(template) || stored.getAmount() <= 0) {
                continue;
            }
            boolean matched = matchesFilter(template, filters, metaMode);
            boolean allowed = !anyFilter && mode == PocketLinkItem.FeedFilterMode.BLACKLIST
                    || mode == PocketLinkItem.FeedFilterMode.WHITELIST && matched
                    || mode == PocketLinkItem.FeedFilterMode.BLACKLIST && !matched;
            if (allowed) {
                result.add(new Candidate(stored.itemKey(), template, stored.getAmount()));
            }
        }
        result.sort(Comparator.comparing(Candidate::itemKey));
        return result;
    }

    private static Candidate selectCandidate(List<Slot> slots, List<Candidate> candidates) {
        // Preserve the first existing filtered rocket stack whenever the network has it.
        for (Slot slot : slots) {
            if (!isRocket(slot.stack())) {
                continue;
            }
            String key = StoredStack.itemKey(slot.stack());
            for (Candidate candidate : candidates) {
                if (candidate.itemKey().equals(key)) {
                    return candidate;
                }
            }
        }
        return candidates.get(0);
    }

    private static boolean matchesFilter(
            ItemStack item,
            List<ItemStack> filters,
            PocketLinkItem.FeedMetaMode metaMode
    ) {
        for (ItemStack filter : filters) {
            if (!RocketDistributerAugmentGui.isRocket(filter)) {
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

    private static long countSimilar(List<Slot> slots, ItemStack template) {
        long total = 0;
        for (Slot slot : slots) {
            if (slot.stack() != null && slot.stack().isSimilar(template)) {
                total += slot.stack().getAmount();
            }
        }
        return total;
    }

    private static long freeSpace(List<Slot> slots, ItemStack template) {
        long total = 0;
        for (Slot slot : slots) {
            ItemStack stack = slot.stack();
            if (stack == null || stack.getType().isAir()) {
                // Do not claim the off-hand as a new storage slot. An empty main-hand
                // slot remains eligible because it is an inventory slot by id.
                if (slot.id() != OFF_HAND) {
                    total += template.getMaxStackSize();
                }
            } else if (stack.isSimilar(template)) {
                total += Math.max(0, template.getMaxStackSize() - stack.getAmount());
            }
        }
        return total;
    }

    private static long place(List<Slot> slots, ItemStack template, long amount) {
        long placed = 0;
        for (Slot slot : slots) {
            ItemStack stack = slot.stack();
            if (stack == null || stack.getType().isAir() || !stack.isSimilar(template)) {
                continue;
            }
            int move = (int) Math.min(amount - placed, template.getMaxStackSize() - stack.getAmount());
            if (move > 0) {
                stack.setAmount(stack.getAmount() + move);
                placed += move;
            }
            if (placed >= amount) {
                return placed;
            }
        }
        for (Slot slot : slots) {
            if (slot.id() == OFF_HAND) {
                continue;
            }
            ItemStack stack = slot.stack();
            if (stack != null && !stack.getType().isAir()) {
                continue;
            }
            int move = (int) Math.min(amount - placed, template.getMaxStackSize());
            ItemStack created = template.clone();
            created.setAmount(move);
            slot.set(created);
            placed += move;
            if (placed >= amount) {
                break;
            }
        }
        return placed;
    }

    private static boolean isRocket(ItemStack stack) {
        return RocketDistributerAugmentGui.isRocket(stack);
    }

    private static final class Slot {
        private final PlayerInventory inventory;
        private final int id;
        private ItemStack stack;

        private Slot(PlayerInventory inventory, int id, ItemStack stack) {
            this.inventory = inventory;
            this.id = id;
            this.stack = stack;
        }

        private int id() {
            return id;
        }

        private ItemStack stack() {
            return stack;
        }

        private void set(ItemStack next) {
            stack = next;
            if (id == OFF_HAND) {
                inventory.setItemInOffHand(next);
            } else {
                inventory.setItem(id, next);
            }
        }
    }

    private record Candidate(String itemKey, ItemStack template, long amount) {
    }
}
