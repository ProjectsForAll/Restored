package gg.drak.restored.gui;

import gg.drak.restored.Restored;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Per-player search/filter/sort/combine prefs for network item browsers and augment pickers.
 * Cached in memory and persisted to {@code browser-prefs.yml}.
 */
public final class NetworkBrowserPrefs {

    public enum FilterMode {
        NAME_PLAIN,
        NAME_FULL,
        MINECRAFT_ID;

        public FilterMode next() {
            FilterMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum SortMode {
        NAME,
        ITEM_COUNT,
        MINECRAFT_ID;

        public SortMode next() {
            SortMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum SortDirection {
        ASCENDING,
        DESCENDING;

        public SortDirection toggle() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }

    public record State(
            String searchFilter,
            FilterMode filterMode,
            SortMode sortMode,
            SortDirection sortDirection,
            boolean combineStacks
    ) {
        public static State defaults() {
            return new State("", FilterMode.NAME_PLAIN, SortMode.NAME, SortDirection.ASCENDING, true);
        }

        public State withSearch(String search) {
            return new State(search == null ? "" : search, filterMode, sortMode, sortDirection, combineStacks);
        }
    }

    private static final ConcurrentHashMap<UUID, State> BY_PLAYER = new ConcurrentHashMap<>();
    private static File file;
    private static FileConfiguration config;

    private NetworkBrowserPrefs() {
    }

    public static void init() {
        Restored plugin = Restored.getInstance();
        file = new File(plugin.getDataFolder(), "browser-prefs.yml");
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for browser prefs.");
        }
        config = YamlConfiguration.loadConfiguration(file);
        BY_PLAYER.clear();
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                BY_PLAYER.put(id, readState(players.getConfigurationSection(key)));
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUID keys
            }
        }
    }

    public static State get(UUID playerId) {
        if (playerId == null) {
            return State.defaults();
        }
        return BY_PLAYER.computeIfAbsent(playerId, id -> State.defaults());
    }

    public static void set(UUID playerId, State state) {
        if (playerId == null || state == null) {
            return;
        }
        BY_PLAYER.put(playerId, state);
        writeState(playerId, state);
        save();
    }

    private static State readState(ConfigurationSection section) {
        if (section == null) {
            return State.defaults();
        }
        State defaults = State.defaults();
        String search = section.getString("search", defaults.searchFilter());
        FilterMode filterMode = parseEnum(section.getString("filter-mode"), FilterMode.class, defaults.filterMode());
        SortMode sortMode = parseEnum(section.getString("sort-mode"), SortMode.class, defaults.sortMode());
        SortDirection sortDirection = parseEnum(section.getString("sort-direction"), SortDirection.class, defaults.sortDirection());
        boolean combineStacks = section.getBoolean("combine-stacks", defaults.combineStacks());
        return new State(search == null ? "" : search, filterMode, sortMode, sortDirection, combineStacks);
    }

    private static void writeState(UUID playerId, State state) {
        if (config == null) {
            return;
        }
        String base = "players." + playerId;
        config.set(base + ".search", state.searchFilter() == null ? "" : state.searchFilter());
        config.set(base + ".filter-mode", state.filterMode().name());
        config.set(base + ".sort-mode", state.sortMode().name());
        config.set(base + ".sort-direction", state.sortDirection().name());
        config.set(base + ".combine-stacks", state.combineStacks());
    }

    private static void save() {
        if (file == null || config == null) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            Restored.getInstance().getLogger().log(Level.WARNING, "Failed to save browser-prefs.yml", e);
        }
    }

    private static <E extends Enum<E>> E parseEnum(String raw, Class<E> type, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
