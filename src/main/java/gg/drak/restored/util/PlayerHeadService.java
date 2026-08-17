package gg.drak.restored.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import gg.drak.restored.Restored;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Short-lived cache + async loader for player heads/profiles.
 */
public final class PlayerHeadService {
    private static final long TTL_MS = 2 * 60 * 1000L; // 2 minutes
    private static final int MAX_CONCURRENT_FETCHES = 6;

    private static final ConcurrentHashMap<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final Semaphore FETCH_LIMIT = new Semaphore(MAX_CONCURRENT_FETCHES);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "restored-player-heads");
        thread.setDaemon(true);
        return thread;
    });

    private PlayerHeadService() {
    }

    public static Optional<CachedPlayer> getCached(UUID uuid) {
        CacheEntry entry = CACHE.get(uuid);
        if (entry == null || entry.expired()) {
            if (entry != null) {
                CACHE.remove(uuid, entry);
            }
            return Optional.empty();
        }
        return Optional.of(entry.toCached());
    }

    /**
     * Resolve display name off-thread (uses cache when present), then invoke callback on the main thread.
     */
    public static void resolveNameAsync(UUID uuid, String fallbackName, Consumer<String> callback) {
        Optional<CachedPlayer> cached = getCached(uuid);
        if (cached.isPresent()) {
            runSync(() -> callback.accept(cached.get().name()));
            return;
        }
        resolveAsync(uuid, fallbackName, player -> callback.accept(player.name()));
    }

    /**
     * Resolve name + textured head off-thread, then invoke {@code callback} on the main thread.
     */
    public static void resolveAsync(UUID uuid, String fallbackName, Consumer<CachedPlayer> callback) {
        Optional<CachedPlayer> cached = getCached(uuid);
        if (cached.isPresent()) {
            runSync(() -> callback.accept(cached.get()));
            return;
        }

        CompletableFuture.supplyAsync(() -> fetch(uuid, fallbackName), EXECUTOR)
                .whenComplete((result, error) -> {
                    CachedPlayer resolved = result;
                    if (error != null || resolved == null) {
                        resolved = fallbackPlayer(uuid, fallbackName);
                    } else if (hasTexturedHead(resolved.head())) {
                        // Only cache successfully textured heads so failed lookups retry soon.
                        CACHE.put(uuid, new CacheEntry(
                                resolved.uuid(),
                                resolved.name(),
                                resolved.head().clone(),
                                System.currentTimeMillis() + TTL_MS
                        ));
                    }
                    CachedPlayer deliver = resolved;
                    runSync(() -> callback.accept(deliver));
                });
    }

    /**
     * Load the offline-player list off the main thread.
     */
    public static void discoverOfflinePlayersAsync(Consumer<List<OfflinePlayer>> callback) {
        CompletableFuture.supplyAsync(() -> {
            OfflinePlayer[] all = Bukkit.getOfflinePlayers();
            return Arrays.asList(all);
        }, EXECUTOR).whenComplete((players, error) -> {
            List<OfflinePlayer> result = (error != null || players == null) ? List.of() : players;
            runSync(() -> callback.accept(result));
        });
    }

    public static void clearExpired() {
        long now = System.currentTimeMillis();
        CACHE.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
    }

    private static CachedPlayer fetch(UUID uuid, String fallbackName) {
        boolean permit = false;
        try {
            permit = FETCH_LIMIT.tryAcquire(5, TimeUnit.SECONDS);

            String name = resolveName(uuid, fallbackName);
            PlayerProfile profile = loadTexturedProfile(uuid, name);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            }

            String resolvedName = profile.getName() != null ? profile.getName() : name;
            return new CachedPlayer(uuid, resolvedName, head);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallbackPlayer(uuid, fallbackName);
        } catch (Exception e) {
            Restored.getInstance().logWarning("Failed to load player head for " + uuid + ": " + e.getMessage());
            return fallbackPlayer(uuid, fallbackName);
        } finally {
            if (permit) {
                FETCH_LIMIT.release();
            }
        }
    }

    private static String resolveName(UUID uuid, String fallbackName) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.getName() != null) {
            return offline.getName();
        }
        if (fallbackName != null && !fallbackName.isBlank() && isValidPlayerName(fallbackName)) {
            return fallbackName;
        }
        return null;
    }

    private static PlayerProfile loadTexturedProfile(UUID uuid, String name) {
        PlayerProfile profile = name != null
                ? Bukkit.createProfile(uuid, name)
                : Bukkit.createProfile(uuid);

        // Prefer already-filled textures from an online player (safe copy onto our profile).
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            PlayerProfile onlineProfile = online.getPlayerProfile();
            if (onlineProfile != null && onlineProfile.hasTextures()) {
                profile.setTextures(onlineProfile.getTextures());
                return profile;
            }
        }

        // Blocking Mojang/cache fill — only safe off the main thread.
        if (!profile.hasTextures()) {
            profile.complete(true);
        }
        if (!profile.hasTextures()) {
            // Retry with online-mode Mojang lookup (needed on some offline-mode servers).
            profile.complete(true, true);
        }
        return profile;
    }

    private static boolean hasTexturedHead(ItemStack head) {
        if (head == null || !(head.getItemMeta() instanceof SkullMeta meta)) {
            return false;
        }
        PlayerProfile profile = meta.getPlayerProfile();
        return profile != null && profile.hasTextures();
    }

    private static boolean isValidPlayerName(String name) {
        if (name == null || name.isBlank() || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }
        return true;
    }

    private static CachedPlayer fallbackPlayer(UUID uuid, String fallbackName) {
        String name = fallbackName != null && !fallbackName.isBlank() ? fallbackName : uuid.toString();
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                PlayerProfile profile = isValidPlayerName(name)
                        ? Bukkit.createProfile(uuid, name)
                        : Bukkit.createProfile(uuid);
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            }
        } catch (Exception ignored) {
            // Keep blank steve head.
        }
        return new CachedPlayer(uuid, name, head);
    }

    private static void runSync(Runnable runnable) {
        Restored plugin = Restored.getInstance();
        if (plugin == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public record CachedPlayer(UUID uuid, String name, ItemStack head) {
        public ItemStack headClone() {
            return head.clone();
        }
    }

    private record CacheEntry(UUID uuid, String name, ItemStack head, long expiresAt) {
        boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }

        CachedPlayer toCached() {
            return new CachedPlayer(uuid, name, head.clone());
        }
    }
}
