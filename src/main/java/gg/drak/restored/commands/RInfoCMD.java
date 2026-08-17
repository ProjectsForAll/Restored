package gg.drak.restored.commands;

import gg.drak.restored.Restored;
import gg.drak.restored.gui.InfoGui;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentSkipListSet;

public class RInfoCMD extends SimplifiedCommand {

    public RInfoCMD() {
        super("rinfo", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext context) {
        if (context.isConsole()) {
            context.sendMessage("You must be a player to view plugin info.");
            return true;
        }

        Player player = context.getSender().getPlayer().orElse(null);
        if (player == null) {
            context.sendMessage("You must be a player to use this command.");
            return true;
        }

        new InfoGui(player).open();
        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext context) {
        return new ConcurrentSkipListSet<>();
    }
}
