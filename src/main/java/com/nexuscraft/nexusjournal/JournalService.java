package com.nexuscraft.nexusjournal;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** The rules: which pages a player can open, what opens them, and what happens when one opens. */
final class JournalService implements JournalApi {

    enum Result { FOUND, ALREADY, UNKNOWN, NOT_ELIGIBLE }

    private final JournalConfig cfg;
    private final Codex codex;
    private final JournalStore store;
    private final Supplier<Collection<String>> installedNames;
    private final BiPredicate<Player, String> hidden;
    private final BiConsumer<Player, Entry> onFound;
    private final LongSupplier clock;
    private Set<String> installed = new HashSet<>();

    /**
     * @param installedNames names of the plugins that are enabled right now
     * @param hidden         has this player hidden that plugin (NexusPath says so)?
     * @param onFound        told after a page opens, to show the tip
     */
    JournalService(JournalConfig cfg, Codex codex, JournalStore store, Supplier<Collection<String>> installedNames,
                   BiPredicate<Player, String> hidden, BiConsumer<Player, Entry> onFound, LongSupplier clock) {
        this.cfg = cfg;
        this.codex = codex;
        this.store = store;
        this.installedNames = installedNames;
        this.hidden = hidden;
        this.onFound = onFound;
        this.clock = clock;
        refreshInstalled();
    }

    JournalConfig cfg() {
        return cfg;
    }

    Codex codex() {
        return codex;
    }

    JournalStore store() {
        return store;
    }

    void refreshInstalled() {
        Set<String> now = new HashSet<>();
        for (String n : installedNames.get()) {
            now.add(n.toLowerCase(Locale.ROOT));
        }
        installed = now;
    }

    boolean isInstalled(String plugin) {
        return plugin != null && installed.contains(plugin.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ who may open what

    /** A page can open for a player when its plugin is running and the player did not hide it (a page about nobody always can). */
    boolean eligible(Player p, Entry e) {
        if (!e.hasPlugin()) {
            return true;
        }
        return isInstalled(e.plugin()) && !hidden.test(p, e.plugin());
    }

    boolean has(UUID id, String pageId) {
        Progress pr = store.peek(id);
        return pr != null && pr.has(Codex.key(pageId));
    }

    /**
     * What the menu lists: every page the player has, plus every unfound page they could still find (one that
     * nothing a player does can open stays out of sight until it opens, unless menu.show-unreachable is on).
     * In codex order.
     */
    List<Entry> visibleFor(Player p) {
        List<Entry> out = new ArrayList<>();
        Progress pr = store.peek(p.getUniqueId());
        for (Entry e : codex.entries()) {
            if ((pr != null && pr.has(e.id())) || (eligible(p, e) && (e.reachable() || cfg.showUnreachable))) {
                out.add(e);
            }
        }
        return out;
    }

    int foundCount(UUID id) {
        Progress pr = store.peek(id);
        return pr == null ? 0 : pr.count();
    }

    // ------------------------------------------------------------------ opening pages

    /** {@code force}: staff granting a page skips the "is it in your game" test. */
    Result discover(Player p, String pageId, boolean force) {
        Entry e = codex.get(pageId);
        if (e == null) {
            return Result.UNKNOWN;
        }
        if (has(p.getUniqueId(), e.id())) {
            return Result.ALREADY;
        }
        if (!force && !eligible(p, e)) {
            return Result.NOT_ELIGIBLE;
        }
        open(p, e);
        return Result.FOUND;
    }

    private void open(Player p, Entry e) {
        if (store.of(p.getUniqueId()).add(e.id(), clock.getAsLong())) {
            store.markDirty();
            onFound.accept(p, e);
        }
    }

    /** Something happened ({@code kind}, {@code value}); opens every page it is a trigger for. Returns how many opened. */
    int happened(Player p, String kind, String value) {
        List<Entry> list = codex.withKind(kind);
        if (list.isEmpty()) {
            return 0;
        }
        int opened = 0;
        Progress pr = store.peek(p.getUniqueId());
        for (Entry e : list) {
            if (pr != null && pr.has(e.id())) {
                continue;
            }
            if (matches(e, kind, value) && eligible(p, e)) {
                open(p, e);
                pr = store.peek(p.getUniqueId());
                opened++;
            }
        }
        return opened;
    }

    /** A command was typed: opens the pages of the plugin that owns it whose command trigger fits the label. */
    int commandTyped(Player p, String label, String owner) {
        if (owner == null) {
            return 0;
        }
        List<Entry> list = codex.withKind("command");
        int opened = 0;
        Progress pr = store.peek(p.getUniqueId());
        for (Entry e : list) {
            if (!e.hasPlugin() || !e.plugin().equalsIgnoreCase(owner) || (pr != null && pr.has(e.id()))) {
                continue;
            }
            if (matches(e, "command", label) && eligible(p, e)) {
                open(p, e);
                pr = store.peek(p.getUniqueId());
                opened++;
            }
        }
        return opened;
    }

    private static boolean matches(Entry e, String kind, String value) {
        for (Trigger t : e.triggers()) {
            if (t.matches(kind, value)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ staff

    /** Takes a page (or, with {@code all}, every page) back. Returns how many were removed. */
    int revoke(UUID id, String pageId, boolean all) {
        Progress pr = store.peek(id);
        if (pr == null) {
            return 0;
        }
        int n;
        if (all) {
            n = pr.clear();
        } else {
            n = pr.remove(Codex.key(pageId)) ? 1 : 0;
        }
        if (n > 0) {
            store.markDirty();
        }
        return n;
    }

    /** Gives every page the player does not have (staff testing). Returns how many were opened. */
    int grantAll(Player p) {
        int n = 0;
        for (Entry e : new ArrayList<>(codex.entries())) {
            if (!has(p.getUniqueId(), e.id())) {
                open(p, e);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ JournalApi

    @Override
    public boolean discover(Player player, String pageId) {
        return player != null && discover(player, pageId, false) == Result.FOUND;
    }

    @Override
    public boolean found(Player player, String pageId) {
        return player != null && has(player.getUniqueId(), pageId);
    }
}
