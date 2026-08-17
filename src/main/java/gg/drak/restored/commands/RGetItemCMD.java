package gg.drak.restored.commands;

import gg.drak.restored.Restored;
import gg.drak.restored.items.RestoredItemRegistry;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import host.plas.bou.items.InventoryUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.concurrent.ConcurrentSkipListSet;

public class RGetItemCMD extends SimplifiedCommand {

    public RGetItemCMD() {
        super("rgetitem", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext context) {
        Player player = context.getPlayerOrNull();
        if (player == null) {
            context.sendMessage("&cYou must be a player to use this command.");
            return true;
        }

        if (!context.isArgUsable(0)) {
            context.sendMessage("&cUsage: /rgetitem <item> [amount]");
            return true;
        }

        String key = context.getStringArg(0);
        ItemStack stack = RestoredItemRegistry.create(key).orElse(null);
        if (stack == null) {
            context.sendMessage("&cUnknown Restored item: &e" + key);
            return true;
        }

        int amount = 1;
        if (context.isArgUsable(1)) {
            try {
                amount = Integer.parseInt(context.getStringArg(1));
            } catch (NumberFormatException ex) {
                context.sendMessage("&cAmount must be a number.");
                return true;
            }
            if (amount < 1 || amount > 64) {
                context.sendMessage("&cAmount must be between 1 and 64.");
                return true;
            }
        }
        stack.setAmount(amount);

        InventoryUtils.addItemToPlayer(player, stack);
        context.sendMessage("&eGave &b" + amount + "x &e" + key.toLowerCase(Locale.ROOT) + "&8.");
        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext context) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();
        if (context.getArgs().size() <= 1) {
            completions.addAll(RestoredItemRegistry.keys());
        } else if (context.getArgs().size() == 2) {
            completions.add("1");
            completions.add("16");
            completions.add("32");
            completions.add("64");
        }
        return completions;
    }
}
