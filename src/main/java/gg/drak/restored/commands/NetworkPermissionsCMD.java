package gg.drak.restored.commands;

import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import gg.drak.restored.Restored;
import gg.drak.restored.data.NetworkManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class NetworkPermissionsCMD extends SimplifiedCommand {
    public NetworkPermissionsCMD() {
        super("network-permissions", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext commandContext) {
        if (commandContext.isConsole()) {
            commandContext.sendMessage("&cYou must be a player to use this command.");
            return true;
        }

        Optional<Player> playerOptional = commandContext.getSender().getPlayer();
        if (playerOptional.isEmpty()) {
            commandContext.sendMessage("&cYou must be a player to use this command.");
            return true;
        }

        Player player = playerOptional.get();

        if (! commandContext.isArgUsable(1)) {
            commandContext.sendMessage("&cUsage: /get-item <network> <player> <permission> [true/false]");
            return true;
        }

        String networkIdentifier = commandContext.getStringArg(0);
        String playerName = commandContext.getStringArg(1);

        if (! NetworkManager.isNetwork(networkIdentifier)) {
            commandContext.sendMessage("&cNetwork not found.");
            return true;
        }

        if (! NetworkManager.isOwnerOf(networkIdentifier, player)) {
            commandContext.sendMessage("&cYou do not own this network.");
            return true;
        }

        Player other = Bukkit.getPlayer(playerName);
        if (other == null) {
            commandContext.sendMessage("&cPlayer not found.");
            return true;
        }

        String permission = commandContext.getStringArg(2);

        if (! NetworkManager.isValidPermission(permission) && ! permission.equalsIgnoreCase("all")) {
            commandContext.sendMessage("&cInvalid permission.");
            return true;
        }

        boolean value = true;
        if (commandContext.isArgUsable(3)) {
            Optional<Boolean> optional = commandContext.getBooleanArg(3);
            if (optional.isEmpty()) {
                commandContext.sendMessage("&cInvalid value.");
                return true;
            }

            value = optional.get();
        }

        NetworkManager.setPermission(networkIdentifier, other, permission, value);

        commandContext.sendMessage("&eSet permission for &6" + other.getName() + " &eto &6" + value + " &efor &6" + permission + " &ein network &6" + networkIdentifier + "&e.");

        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext commandContext) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();

        if (commandContext.isConsole()) return completions;

        Optional<Player> playerOptional = commandContext.getSender().getPlayer();
        if (playerOptional.isEmpty()) return completions;

        Player player = playerOptional.get();

        if (commandContext.getArgs().size() == 1) {
            completions.addAll(NetworkManager.getOwnedNetworkUuids(player));
        }

        if (commandContext.getArgs().size() == 2) {
            ConcurrentSkipListSet<String> playerNames = Restored.getInstance().getOnlinePlayers().values().stream().map(Player::getName).collect(Collectors.toCollection(ConcurrentSkipListSet::new));
            completions.addAll(playerNames);
        }

        if (commandContext.getArgs().size() == 3) {
            completions.addAll(NetworkManager.getValidPermissions());
        }

        if (commandContext.getArgs().size() == 4) {
            completions.add("true");
            completions.add("false");
        }

        return completions;
    }
}
