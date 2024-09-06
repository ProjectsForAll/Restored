package host.plas.restored.commands;

import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import host.plas.restored.Restored;
import host.plas.restored.data.items.ItemManager;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;

public class GetItemCMD extends SimplifiedCommand {
    public GetItemCMD() {
        super("get-item", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext commandContext) {
        if (commandContext.isConsole()) {
            commandContext.sendMessage("You must be a player to use this command.");
            return true;
        }

        Optional<Player> playerOptional = commandContext.getSender().getPlayer();
        if (playerOptional.isEmpty()) {
            commandContext.sendMessage("You must be a player to use this command.");
            return true;
        }

        Player player = playerOptional.get();

        if (commandContext.getArgs().isEmpty()) {
            commandContext.sendMessage("Usage: /get-item <item>");
            return true;
        }

        String item = commandContext.getStringArg(0);
        String[] args = new String[0];
        if (commandContext.isArgUsable(1)) {
            args = new String[1];
            args[0] = commandContext.getStringArg(1);
        }
        ItemManager.getItem(item, args).ifPresentOrElse(
            r -> {
                player.getInventory().addItem(r.getItem());
                commandContext.sendMessage("&eItem added to inventory&8.");
            },
            () -> commandContext.sendMessage("&cItem not found.")
        );

        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext commandContext) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();

        if (commandContext.getArgs().size() == 1) {
            completions.addAll(ItemManager.getRegisteredItems());
        }

        return completions;
    }
}
