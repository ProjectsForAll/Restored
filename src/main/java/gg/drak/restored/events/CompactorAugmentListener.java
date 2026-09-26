package gg.drak.restored.events;

import gg.drak.restored.Restored;
import gg.drak.restored.data.CompactConfiguration;
import gg.drak.restored.data.CompactionResolver;
import gg.drak.restored.data.CompactingAction;
import gg.drak.restored.data.Network;
import gg.drak.restored.data.NetworkManager;
import gg.drak.restored.data.QuantityOperand;
import gg.drak.restored.data.StoredStack;
import gg.drak.restored.util.LinkedChestStorage;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Applies enabled compactor configurations to every network that owns the augment. */
public final class CompactorAugmentListener implements Listener {

    public CompactorAugmentListener() {
        Restored.getInstance().registerListener(this);
        Restored.getInstance().getServer().getScheduler().runTaskTimer(
                Restored.getInstance(),
                () -> {
                    for (Network network : NetworkManager.getNetworks()) {
                        compact(network);
                    }
                },
                20L,
                Restored.getMainConfig() == null
                        ? gg.drak.restored.config.MainConfig.DEFAULT_COMPACTOR_INTERVAL
                        : Restored.getMainConfig().getCompactorInterval()
        );
        Restored.getInstance().logInfo("Registered CompactorAugmentListener!");
    }

    public static void compact(Network network) {
        if (network == null || !network.hasAugment(gg.drak.restored.data.AugmentType.COMPACTOR)) {
            return;
        }
        List<CompactConfiguration> configurations = network.getCompactConfigurations();
        if (configurations.isEmpty()) {
            return;
        }
        // Only configured+enabled entries can do anything, so skip the whole pass (and the
        // linked-chest resolve) when nothing is actionable.
        boolean anyActionable = false;
        for (CompactConfiguration configuration : configurations) {
            if (configuration != null && configuration.isEnabled() && configuration.isConfigured()) {
                anyActionable = true;
                break;
            }
        }
        if (!anyActionable) {
            return;
        }
        List<Inventory> linkedInventories = LinkedChestStorage.resolveInventories(network);
        // Each configuration consults exactly one item key, and its Material is known from the
        // configured item. Looking those up individually avoids getCombinedAmounts(), which
        // hashes every slot of every linked chest to build a map that is almost all discarded.
        Map<String, Long> availableAmounts = new java.util.HashMap<>();
        for (CompactConfiguration configuration : configurations) {
            apply(network, configuration, linkedInventories, availableAmounts);
        }
        // NetworkSaveTimer persists dirty networks in coalesced snapshots. Do not
        // capture the complete network once per compactor pass on the main thread.
    }

    private static boolean apply(
            Network network,
            CompactConfiguration configuration,
            List<Inventory> linkedInventories,
            Map<String, Long> availableAmounts
    ) {
        if (configuration == null || !configuration.isEnabled() || !configuration.isConfigured()) {
            return false;
        }
        CompactionResolver.Conversion conversion = CompactionResolver.resolve(
                configuration.getItem(), configuration.getAction());
        if (conversion == null) {
            return false;
        }

        String inputKey = StoredStack.itemKey(configuration.getItem());
        long available = availableAmounts.computeIfAbsent(inputKey, key ->
                network.getCombinedAmount(key, linkedInventories, configuration.getItem().getType()));
        QuantityOperand operand = configuration.getOperand();
        if (operand == null || !operand.test(available, configuration.getQuantity())) {
            return false;
        }

        long operations = configuration.getAction() == CompactingAction.COMPACT
                ? available / conversion.inputAmount()
                : available;
        if (operations <= 0) {
            return false;
        }

        // A decompaction increases virtual storage by the ratio delta. Calculate the
        // maximum safe batch once instead of extracting/inserting one operation at a time.
        if (conversion.outputAmount() > conversion.inputAmount()) {
            long delta = conversion.outputAmount() - conversion.inputAmount();
            long virtualRoom = network.getCapacity() - network.getTotalItems();
            operations = Math.min(operations, Math.max(0, virtualRoom / delta));
        }
        if (operations <= 0) {
            return false;
        }

        long requestedInput = operations * conversion.inputAmount();
        long taken = network.extract(inputKey, requestedInput, linkedInventories);
        long completedOperations = taken / conversion.inputAmount();
        long remainder = taken - completedOperations * conversion.inputAmount();
        if (remainder > 0) {
            network.forceInsert(configuration.getItem(), remainder);
        }
        if (completedOperations <= 0) {
            return false;
        }

        long requestedOutput = completedOperations * conversion.outputAmount();
        ItemStack output = conversion.outputStack();
        long inserted = network.insert(output, requestedOutput, linkedInventories);
        if (inserted < requestedOutput) {
            // Roll back the output by amount and restore the input batch. Amounts are
            // restored rather than individual slots because both stores are aggregate.
            network.extract(StoredStack.itemKey(output), inserted, linkedInventories);
            network.forceInsert(configuration.getItem(), completedOperations * conversion.inputAmount());
            return false;
        }
        availableAmounts.merge(inputKey, -completedOperations * conversion.inputAmount(), Long::sum);
        // The map is now populated lazily, so only adjust the output key if a previous
        // configuration already resolved its absolute amount. Seeding it with a relative
        // delta would make that later lookup read a bogus total instead of a real one.
        availableAmounts.computeIfPresent(
                StoredStack.itemKey(output), (key, current) -> current + requestedOutput);
        return true;
    }
}
