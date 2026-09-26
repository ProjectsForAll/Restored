package gg.drak.restored.commands;

import gg.drak.restored.Restored;
import gg.drak.restored.data.AdminAccess;
import gg.drak.restored.gui.AdminNetworksGui;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * {@code /networkadmin (manage (radius)|adminmode|deletemode)}.
 */
public class NetworkAdminCMD extends SimplifiedCommand {
    private static final String USAGE = "&cUsage: /networkadmin <manage (radius)|adminmode|deletemode>";

    public NetworkAdminCMD() {
        super("networkadmin", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext context) {
        Player player = context.getPlayerOrNull();
        if (player == null) {
            context.sendMessage("&cYou must be a player to use this command.");
            return true;
        }
        if (!context.isArgUsable(0)) {
            context.sendMessage(USAGE);
            return true;
        }

        switch (context.getStringArg(0).toLowerCase(Locale.ROOT)) {
            case "manage" -> {
                Integer radius = null;
                if (context.isArgUsable(1)) {
                    try {
                        radius = Integer.parseInt(context.getStringArg(1));
                    } catch (NumberFormatException e) {
                        context.sendMessage("&cRadius must be a whole number of blocks.");
                        return true;
                    }
                    if (radius < 0) {
                        context.sendMessage("&cRadius cannot be negative.");
                        return true;
                    }
                }
                new AdminNetworksGui(player, radius).open();
            }
            case "adminmode" -> {
                boolean enabled = AdminAccess.toggleAdminMode(player.getUniqueId());
                context.sendMessage(enabled
                        ? "&aAdmin mode enabled: &eyou can access every network as its owner."
                        : "&eAdmin mode disabled.");
            }
            case "deletemode" -> {
                boolean enabled = AdminAccess.toggleDeleteMode(player.getUniqueId());
                context.sendMessage(enabled
                        ? "&cDelete mode enabled: &ebreaking any network chest deletes that network. "
                                + "Its virtual items are destroyed; linked chests are only unlinked."
                        : "&eDelete mode disabled.");
            }
            default -> context.sendMessage(USAGE);
        }
        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext context) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();
        if (context.getArgs().size() <= 1) {
            completions.add("manage");
            completions.add("adminmode");
            completions.add("deletemode");
        } else if (context.getArgs().size() == 2
                && "manage".equalsIgnoreCase(context.getStringArg(0))) {
            completions.add("50");
            completions.add("100");
            completions.add("500");
        }
        return completions;
    }
}
