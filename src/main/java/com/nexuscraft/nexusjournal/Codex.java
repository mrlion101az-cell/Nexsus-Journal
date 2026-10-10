package com.nexuscraft.nexusjournal;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every page the journal can hold. Loaded from the built-in codex.yml, then custom-codex.yml on top (change a
 * page field by field, add pages, hide pages), in file order.
 */
final class Codex {

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<String, List<Entry>> byKind = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    void clear() {
        entries.clear();
        byKind.clear();
        warnings.clear();
    }

    Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    Entry get(String id) {
        return entries.get(key(id));
    }

    int size() {
        return entries.size();
    }

    List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    /** Entries that list at least one trigger of this kind. */
    List<Entry> withKind(String kind) {
        return byKind.getOrDefault(kind, List.of());
    }

    List<Entry> forPlugin(String plugin) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (e.hasPlugin() && e.plugin().equalsIgnoreCase(plugin)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Adds or changes pages from a file's root. {@code source} names the file in warnings. */
    void load(ConfigurationSection root, String source) {
        if (root == null) {
            return;
        }
        ConfigurationSection sec = root.getConfigurationSection("entries");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    warnings.add(source + ": entry '" + id + "' is not a section -- skipped.");
                    continue;
                }
                put(Codex.key(id), s, source);
            }
        }
        for (String id : root.getStringList("hidden-entries")) {
            if (entries.remove(key(id)) == null) {
                warnings.add(source + ": hidden-entries names '" + id + "', which is not a page.");
            }
        }
    }

    private void put(String id, ConfigurationSection s, String source) {
        Entry old = entries.get(id);
        String title = s.getString("title", old == null ? "" : old.title());
        if (title == null || title.isBlank()) {
            warnings.add(source + ": entry '" + id + "' has no title -- skipped.");
            return;
        }
        List<Trigger> triggers = old == null ? new ArrayList<>() : new ArrayList<>(old.triggers());
        if (s.contains("triggers")) {
            triggers.clear();
            addTriggers(triggers, s.getStringList("triggers"), id, source);
        }
        addTriggers(triggers, s.getStringList("add-triggers"), id, source);
        if (triggers.isEmpty()) {
            triggers.add(new Trigger("api", "*"));
        }
        entries.put(id, new Entry(id,
                s.getString("plugin", old == null ? "" : old.plugin()),
                title,
                s.getString("icon", old == null ? "BOOK" : old.icon()),
                s.getString("group", old == null ? "Other" : old.group()),
                s.getString("tip", old == null ? "" : old.tip()),
                s.getString("body", old == null ? "" : old.body()),
                s.getString("hint", old == null ? "" : old.hint()),
                List.copyOf(triggers)));
    }

    private void addTriggers(List<Trigger> out, List<String> raw, String id, String source) {
        for (String t : raw) {
            Trigger tr = Trigger.parse(t);
            if (tr == null) {
                warnings.add(source + ": entry '" + id + "' has an empty trigger -- skipped.");
            } else if (!tr.known()) {
                warnings.add(source + ": entry '" + id + "': '" + tr.kind() + "' is not a kind of trigger -- skipped.");
            } else if (!out.contains(tr)) {
                out.add(tr);
            }
        }
    }

    /** Call after every load: builds the lookup by kind and reports pages nothing can open. */
    void finish() {
        byKind.clear();
        for (Entry e : entries.values()) {
            for (Trigger t : e.triggers()) {
                List<Entry> list = byKind.computeIfAbsent(t.kind(), k -> new ArrayList<>());
                if (!list.contains(e)) {
                    list.add(e);
                }
            }
            if (e.tip().isBlank()) {
                warnings.add("entry '" + e.id() + "' has no tip -- it will open silently.");
            }
        }
    }

    /** Pages only a plugin or staff can open (no trigger of their own). */
    List<Entry> apiOnly() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) {
            if (!e.reachable()) {
                out.add(e);
            }
        }
        return out;
    }
}
