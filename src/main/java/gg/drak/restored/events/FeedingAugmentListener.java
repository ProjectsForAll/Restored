package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.PocketAugmentType;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.integration.CustomItemBridge;
import gg.drak.restored.items.PocketLinkItem;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class FeedingAugmentListener implements Listener {

    public FeedingAugmentListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(),
                () -> {
                    for (Player player : Restored.getInstance().getServer().getOnlinePlayers()) {
                        if (player.getFoodLevel() < 20) {
                            tryFeed(player);
                        }
                    }
                },
                40L,
                40L
        );
        Restored.getInstance().logInfo("Registered FeedingAugmentListener!");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        // After the change applies, feed if still hungry. Schedule next tick.
        int newLevel = event.getFoodLevel();
        if (newLevel >= 20) {
            return;
        }
        Restored.getInstance().getServer().getScheduler().runTask(Restored.getInstance(), () -> tryFeed(player));
    }

    public static void tryFeed(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (player.getFoodLevel() >= 20) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        for (ItemStack stack : player.getInventory().getContents()) {
            if (!PocketLinkItem.isType(stack) || !PocketLinkItem.hasAugment(stack, PocketAugmentType.FEEDING)) {
                continue;
            }
            if (feedFromLink(player, stack)) {
                return;
            }
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (PocketLinkItem.isType(off) && PocketLinkItem.hasAugment(off, PocketAugmentType.FEEDING)) {
            feedFromLink(player, off);
        }
    }

    private static boolean feedFromLink(Player player, ItemStack link) {
        Optional<UUID> networkId = PocketLinkItem.getLinkedNetworkId(link);
        if (networkId.isEmpty()) {
            return false;
        }
        Network network = NetworkManager.get(networkId.get());
        if (network == null || !network.canWithdraw(player.getUniqueId())) {
            return false;
        }

        List<Candidate> candidates = selectFood(network, link);
        if (candidates.isEmpty()) {
            return false;
        }

        while (player.getFoodLevel() < 20 && !candidates.isEmpty()) {
            Candidate next = candidates.get(0);
            long available = network.getCombinedAmount(next.itemKey());
            if (available <= 0) {
                candidates.remove(0);
                continue;
            }

            ItemStack food = next.template().clone();
            food.setAmount(1);
            FoodValues values = foodValues(food);
            if (values.nutrition() <= 0 && values.saturation() <= 0) {
                candidates.remove(0);
                continue;
            }

            PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(player, food, EquipmentSlot.HAND);
            Restored.getInstance().getServer().getPluginManager().callEvent(consumeEvent);
            if (consumeEvent.isCancelled()) {
                return false;
            }

            long taken = network.extract(next.itemKey(), 1);
            if (taken <= 0) {
                candidates.remove(0);
                continue;
            }

            int newFood = Math.min(20, player.getFoodLevel() + values.nutrition());
            player.setFoodLevel(newFood);
            player.setSaturation(Math.min(newFood, player.getSaturation() + values.saturation()));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);

            // Refresh candidate amount / remove if depleted
            long after = network.getCombinedAmount(next.itemKey());
            if (after <= 0) {
                candidates.remove(0);
            } else {
                candidates.set(0, new Candidate(next.itemKey(), next.template(), after, next.filterSlot()));
            }

            if (player.getFoodLevel() >= 20) {
                return true;
            }
        }
        return player.getFoodLevel() > 0;
    }

    private static List<Candidate> selectFood(Network network, ItemStack link) {
        List<ItemStack> filters = PocketLinkItem.getFeedFilters(link);
        boolean anyFilter = filters.stream().anyMatch(f -> f != null && !f.getType().isAir());
        PocketLinkItem.FeedFilterMode mode = PocketLinkItem.getFeedFilterMode(link);
        PocketLinkItem.FeedMetaMode metaMode = PocketLinkItem.getFeedMetaMode(link);
        PocketLinkItem.FeedSortMode sortMode = PocketLinkItem.getFeedSortMode(link);
        PocketLinkItem.FeedSortDir sortDir = PocketLinkItem.getFeedSortDir(link);

        List<Candidate> candidates = new ArrayList<>();
        for (StoredStack stored : network.getCombinedStacks(org.bukkit.Material::isEdible)) {
            ItemStack template = stored.getTemplate();
            if (!isConsumableFood(template)) {
                continue;
            }
            // Feeding only uses vanilla food — skip Restored / IA / Nexo / Mythic customs.
            if (CustomItemBridge.isCustomItem(template)) {
                continue;
            }
            int filterSlot = matchFilterSlot(template, filters, metaMode);
            boolean matched = filterSlot >= 0;
            boolean allowed;
            if (!anyFilter && mode == PocketLinkItem.FeedFilterMode.BLACKLIST) {
                allowed = true;
            } else if (mode == PocketLinkItem.FeedFilterMode.WHITELIST) {
                allowed = matched;
            } else {
                allowed = !matched;
            }
            if (!allowed) {
                continue;
            }
            candidates.add(new Candidate(
                    StoredStack.itemKey(template),
                    template,
                    stored.getAmount(),
                    matched ? filterSlot : Integer.MAX_VALUE
            ));
        }

        Comparator<Candidate> comparator = switch (sortMode) {
            case SLOT -> Comparator.comparingInt(Candidate::filterSlot)
                    .thenComparing(c -> StoredStack.itemKey(c.template()));
            case AMOUNT -> Comparator.comparingLong(Candidate::amount);
            case NAME -> Comparator.comparing(c -> plainName(c.template()), String.CASE_INSENSITIVE_ORDER);
        };
        if (sortDir == PocketLinkItem.FeedSortDir.DESCENDING) {
            comparator = comparator.reversed();
        }
        candidates.sort(comparator.thenComparing(Candidate::itemKey));
        return candidates;
    }

    private static int matchFilterSlot(ItemStack item, List<ItemStack> filters, PocketLinkItem.FeedMetaMode metaMode) {
        for (int i = 0; i < filters.size(); i++) {
            ItemStack filter = filters.get(i);
            if (filter == null || filter.getType().isAir()) {
                continue;
            }
            if (metaMode == PocketLinkItem.FeedMetaMode.ANY) {
                if (item.getType() == filter.getType()) {
                    return i;
                }
            } else if (StoredStack.itemKey(item).equals(StoredStack.itemKey(filter))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isConsumableFood(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (!stack.getType().isEdible()) {
            return false;
        }
        FoodValues values = foodValues(stack);
        return values.nutrition() > 0 || values.saturation() > 0;
    }

    private static FoodValues foodValues(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            try {
                if (meta.hasFood()) {
                    var food = meta.getFood();
                    return new FoodValues(food.getNutrition(), food.getSaturation());
                }
            } catch (NoSuchMethodError | Exception ignored) {
            }
        }
        return vanillaFallback(stack.getType());
    }

    private static FoodValues vanillaFallback(Material material) {
        return switch (material) {
            case APPLE, CHORUS_FRUIT -> new FoodValues(4, 2.4f);
            case BAKED_POTATO -> new FoodValues(5, 6f);
            case BEEF -> new FoodValues(3, 1.8f);
            case BEETROOT -> new FoodValues(1, 1.2f);
            case BEETROOT_SOUP -> new FoodValues(6, 7.2f);
            case BREAD -> new FoodValues(5, 6f);
            case CARROT -> new FoodValues(3, 3.6f);
            case CHICKEN -> new FoodValues(2, 1.2f);
            case COOKED_BEEF -> new FoodValues(8, 12.8f);
            case COOKED_CHICKEN -> new FoodValues(6, 7.2f);
            case COOKED_COD -> new FoodValues(5, 6f);
            case COOKED_MUTTON -> new FoodValues(6, 9.6f);
            case COOKED_PORKCHOP -> new FoodValues(8, 12.8f);
            case COOKED_RABBIT -> new FoodValues(5, 6f);
            case COOKED_SALMON -> new FoodValues(6, 9.6f);
            case COOKIE -> new FoodValues(2, 0.4f);
            case DRIED_KELP -> new FoodValues(1, 0.6f);
            case ENCHANTED_GOLDEN_APPLE -> new FoodValues(4, 9.6f);
            case GOLDEN_APPLE -> new FoodValues(4, 9.6f);
            case GOLDEN_CARROT -> new FoodValues(6, 14.4f);
            case HONEY_BOTTLE -> new FoodValues(6, 1.2f);
            case MELON_SLICE -> new FoodValues(2, 1.2f);
            case MUSHROOM_STEW, RABBIT_STEW, SUSPICIOUS_STEW -> new FoodValues(6, 7.2f);
            case MUTTON -> new FoodValues(2, 1.2f);
            case POISONOUS_POTATO -> new FoodValues(2, 1.2f);
            case PORKCHOP -> new FoodValues(3, 1.8f);
            case POTATO -> new FoodValues(1, 0.6f);
            case PUFFERFISH -> new FoodValues(1, 0.2f);
            case PUMPKIN_PIE -> new FoodValues(8, 4.8f);
            case RABBIT -> new FoodValues(3, 1.8f);
            case ROTTEN_FLESH -> new FoodValues(4, 0.8f);
            case SPIDER_EYE -> new FoodValues(2, 3.2f);
            case SWEET_BERRIES, GLOW_BERRIES -> new FoodValues(2, 0.4f);
            case TROPICAL_FISH -> new FoodValues(1, 0.2f);
            case COD -> new FoodValues(2, 0.4f);
            case SALMON -> new FoodValues(2, 0.4f);
            default -> new FoodValues(material.isEdible() ? 1 : 0, material.isEdible() ? 0.2f : 0f);
        };
    }

    private static String plainName(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return ChatColor.stripColor(meta.getDisplayName());
        }
        return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private record Candidate(String itemKey, ItemStack template, long amount, int filterSlot) {
    }

    private record FoodValues(int nutrition, float saturation) {
    }
}
