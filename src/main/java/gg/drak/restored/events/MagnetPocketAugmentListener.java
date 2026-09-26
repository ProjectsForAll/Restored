package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.data.PlayerPreferences;
import gg.drak.restored.items.PocketLinkItem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MagnetPocketAugmentListener implements Listener {

    public MagnetPocketAugmentListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(), this::tick, 1L, interval()
        );
        Restored.getInstance().logInfo("Registered MagnetPocketAugmentListener!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!hasActiveMagnet(player)) {
            return;
        }
        event.setCancelled(true);
        collect(player, event.getItem());
    }

    private static long interval() {
        return Restored.getMainConfig() == null
                ? gg.drak.restored.config.MainConfig.DEFAULT_MAGNET_INTERVAL
                : Restored.getMainConfig().getMagnetInterval();
    }

    private void tick() {
        for (Player player : Restored.getInstance().getServer().getOnlinePlayers()) {
            // One inventory scan per player. hasActiveMagnet + findActiveLink used to walk
            // the whole inventory twice, copying ItemMeta for every slot, every tick.
            if (findActiveLink(player) == null) {
                continue;
            }
            for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
                if (!(entity instanceof Item item) || item.isDead()
                        || item.getLocation().distanceSquared(player.getLocation()) > 100) {
                    continue;
                }
                item.teleport(player.getLocation());
                collect(player, item);
            }
        }
    }

    private boolean hasActiveMagnet(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isActiveMagnet(stack)) {
                return true;
            }
        }
        return isActiveMagnet(player.getInventory().getItemInOffHand());
    }

    private boolean isActiveMagnet(ItemStack link) {
        return PocketLinkItem.isType(link)
                && PocketLinkItem.hasAugment(link, PocketAugmentType.MAGNET)
                && PocketLinkItem.isMagnetEnabled(link);
    }

    private void collect(Player player, Item entity) {
        if (entity == null || entity.isDead()) {
            return;
        }
        ItemStack source = entity.getItemStack();
        if (source == null || source.getType().isAir()) {
            return;
        }
        ItemStack link = findActiveLink(player);
        if (link == null) {
            return;
        }
        Network network = linkedNetwork(link, player);
        boolean toNetwork = PlayerPreferences.isMagnetToNetwork(player.getUniqueId()) && network != null;
        boolean onlyIfFull = PocketLinkItem.isMagnetOnlyIfInventoryFull(link) && network != null;
        ItemStack remaining = source.clone();

        if (onlyIfFull) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(remaining.clone());
            int added = source.getAmount() - amountOf(leftovers);
            if (added > 0) {
                remaining.setAmount(amountOf(leftovers));
            }
            if (amountOf(leftovers) > 0 && added == 0) {
                long inserted = network.insert(remaining, remaining.getAmount());
                remaining.setAmount((int) Math.max(0, remaining.getAmount() - inserted));
            }
        } else {
            if (toNetwork) {
                long inserted = network.insert(remaining, remaining.getAmount());
                remaining.setAmount((int) Math.max(0, remaining.getAmount() - inserted));
            }
            if (remaining.getAmount() > 0 && (!toNetwork || network == null)) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(remaining.clone());
                remaining.setAmount(amountOf(leftovers));
            } else if (remaining.getAmount() > 0 && toNetwork) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(remaining.clone());
                remaining.setAmount(amountOf(leftovers));
            }
        }

        if (remaining.getAmount() <= 0) {
            entity.remove();
        } else {
            entity.setItemStack(remaining);
        }
    }

    private ItemStack findActiveLink(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isActiveMagnet(stack)) {
                return stack;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return isActiveMagnet(off) ? off : null;
    }

    private Network linkedNetwork(ItemStack link, Player player) {
        Optional<UUID> id = PocketLinkItem.getLinkedNetworkId(link);
        if (id.isEmpty()) {
            return null;
        }
        Network network = NetworkManager.get(id.get());
        return network != null && network.canDeposit(player.getUniqueId()) ? network : null;
    }

    private static int amountOf(Map<Integer, ItemStack> stacks) {
        int amount = 0;
        for (ItemStack stack : stacks.values()) {
            if (stack != null) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }
}
