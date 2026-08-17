package gg.drak.restored.commands;

import gg.drak.restored.Restored;
import gg.drak.restored.gui.RecipesListGui;
import gg.drak.restored.recipes.RecipeRegistrar;
import host.plas.bou.commands.CommandContext;
import host.plas.bou.commands.SimplifiedCommand;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentSkipListSet;

public class RRecipesCMD extends SimplifiedCommand {

    public RRecipesCMD() {
        super("rrecipes", Restored.getInstance());
    }

    @Override
    public boolean command(CommandContext context) {
        if (!context.getArgs().isEmpty() && "reload".equalsIgnoreCase(context.getStringArg(0))) {
            Player player = context.getSender().getPlayer().orElse(null);
            boolean allowed = context.isConsole()
                    || (player != null && (player.hasPermission("restored.command.rrecipes.reload")
                    || player.hasPermission("restored.admin")
                    || player.isOp()));
            if (!allowed) {
                context.sendMessage("&cNo permission.");
                return true;
            }
            Restored.getRecipesConfig().reloadCached();
            RecipeRegistrar.register();
            context.sendMessage("&aReloaded recipes.yml and re-registered recipes.");
            return true;
        }

        if (context.isConsole()) {
            context.sendMessage("You must be a player to view recipes. Use /rrecipes reload from console.");
            return true;
        }

        Player player = context.getSender().getPlayer().orElse(null);
        if (player == null) {
            context.sendMessage("You must be a player to use this command.");
            return true;
        }

        new RecipesListGui(player).open();
        return true;
    }

    @Override
    public ConcurrentSkipListSet<String> tabComplete(CommandContext context) {
        ConcurrentSkipListSet<String> completions = new ConcurrentSkipListSet<>();
        if (context.getArgs().size() == 1) {
            completions.add("reload");
        }
        return completions;
    }
}
