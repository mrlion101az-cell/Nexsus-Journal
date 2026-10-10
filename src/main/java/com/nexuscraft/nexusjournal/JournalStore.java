package com.nexuscraft.nexusjournal;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Every player's journal, in memory and in players.yml. */
final class JournalStore {

    private final File file;
    private final Consumer<String> warn;
    private final Map<UUID, Progress> all = new HashMap<>();
    private boolean dirty;

    JournalStore(File file, Consumer<String> warn) {
        this.file = file;
        this.warn = warn;
    }

    void load() {
        all.clear();
        dirty = false;
        if (file == null || !file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection s = players.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                Progress p = new Progress();
                p.bookGiven = s.getBoolean("book", false);
                ConfigurationSection found = s.getConfigurationSection("found");
                if (found != null) {
                    for (String id : found.getKeys(false)) {
                        p.add(Codex.key(id), found.getLong(id, 0L));
                    }
                }
                all.put(UUID.fromString(key), p);
            } catch (IllegalArgumentException bad) {
                warn.accept("players.yml: '" + key + "' is not a player id -- skipped.");
            }
        }
    }

    /** The player's record, or null if they have none yet. */
    Progress peek(UUID id) {
        return all.get(id);
    }

    /** The player's record; made (and marked for saving) on first use. */
    Progress of(UUID id) {
        Progress p = all.get(id);
        if (p == null) {
            p = new Progress();
            all.put(id, p);
        }
        return p;
    }

    void markDirty() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    int size() {
        return all.size();
    }

    void flush() {
        if (!dirty || file == null) {
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Progress> e : all.entrySet()) {
            Progress p = e.getValue();
            if (p.isBlank()) {
                continue;
            }
            String base = "players." + e.getKey() + ".";
            yml.set(base + "book", p.bookGiven);
            for (Map.Entry<String, Long> f : p.all().entrySet()) {
                yml.set(base + "found." + f.getKey(), f.getValue());
            }
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            warn.accept("Could not save players.yml: " + ex.getMessage());
        }
    }
}
