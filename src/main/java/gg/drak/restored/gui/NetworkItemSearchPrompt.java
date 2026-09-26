package gg.drak.restored.gui;

import gg.drak.restored.Restored;
import gg.drak.restored.util.LegacyColors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Shared chat prompt for all network item search screens. */
public final class NetworkItemSearchPrompt {
    private static final ConcurrentHashMap<UUID, Consumer<String>> PENDING = new ConcurrentHashMap<>();
    private static boolean registered;

    private NetworkItemSearchPrompt() {
    }

    public static void open(Player player, Consumer<String> result) {
        ensureListener();
        PENDING.put(player.getUniqueId(), result);
        player.closeInventory();
        player.sendMessage(LegacyColors.color("#FFED6AEnter a search filter in chat."));
        player.sendMessage(LegacyColors.color("#AAAAAAType 'clear' to clear, or 'cancel' to abort."));
    }

    private static void ensureListener() {
        if (registered) {
            return;
        }
        registered = true;
        Restored.getInstance().registerListener(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onChat(AsyncPlayerChatEvent event) {
                Consumer<String> callback = PENDING.remove(event.getPlayer().getUniqueId());
                if (callback == null) {
                    return;
                }
                event.setCancelled(true);
                String message = event.getMessage().trim();
                String result = message.equalsIgnoreCase("cancel") ? null
                        : message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("none") ? "" : message;
                Bukkit.getScheduler().runTask(Restored.getInstance(), () -> callback.accept(result));
            }
        });
    }
}
