package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** A NexusJournal wired to a stub server: a small codex, installed plugins, a clock and a log of what opened. */
final class Env {

    final JournalConfig cfg = new JournalConfig();
    final Codex codex = new Codex();
    final JournalStore store = new JournalStore(null, w -> { });
    final Set<String> installed = new LinkedHashSet<>();
    final Set<String> hidden = new HashSet<>();
    final AtomicLong clock = new AtomicLong(1_000_000L);
    final List<String> opened = new ArrayList<>();
    final JournalService service;

    Env() {
        Kit.reset();
        codex.load(sample(), "test");
        codex.finish();
        installed.addAll(List.of("NexusLode", "NexusAlchemy", "NexusRime", "NexusFarming", "NexusAngler", "NexusSmelting",
                "NexusRails", "NexusHorror", "NexusPath", "Vault"));
        service = new JournalService(cfg, codex, store, () -> (Collection<String>) installed,
                (p, plugin) -> hidden.contains(p.getName() + ":" + plugin.toLowerCase()),
                (p, e) -> opened.add(p.getName() + ":" + e.id()), clock::get);
    }

    static Kit.MapSection sample() {
        Kit.MapSection m = new Kit.MapSection();
        page(m, "welcome", "", "Your Field Journal", "BOOK", "Start", "Pages fill in as you play", "Welcome.", "Join.", "join");
        page(m, "lode", "NexusLode", "Mining", "DIAMOND_ORE", "Make", "Mining is a skill", "Level up mining.", "Mine ore.", "command", "break:*_ORE|ANCIENT_DEBRIS");
        page(m, "alchemy", "NexusAlchemy", "Brewing", "BREWING_STAND", "Make", "Order matters", "Brew strange potions.", "Open a stand.", "open:BREWING");
        page(m, "rime", "NexusRime", "The Long Winter", "SNOW_BLOCK", "Fight", "Winter hordes", "Frost boss.", "Survive.", "command");
        page(m, "farming", "NexusFarming", "Farming", "WHEAT", "Farm", "Golden hoe", "Replants.", "Harvest.", "break:WHEAT", "use:GOLDEN_HOE");
        page(m, "angler", "NexusAngler", "Fishing", "FISHING_ROD", "Farm", "Ponds run dry", "Bait.", "Cast.", "fish:FISHING");
        page(m, "smelting", "NexusSmelting", "Furnaces", "FURNACE", "Make", "Smelting skill", "Bonus ingots.", "Open a furnace.", "open:FURNACE|BLAST_FURNACE|SMOKER");
        page(m, "rails", "NexusRails", "Rails", "RAIL", "Go", "Stations", "Routes.", "Lay rail.", "place:*rail*");
        page(m, "horror", "NexusHorror", "Sanity", "WITHER_ROSE", "Strange", "Blood moons", "Sanity meter.", "Stay out late.", "api");
        page(m, "ghost", "NexusGhost", "Ghosts", "BONE", "Strange", "Boo", "Not installed.", "Boo.", "command");
        return m;
    }

    static void page(Kit.MapSection m, String id, String plugin, String title, String icon, String group, String tip, String body,
                     String hint, String... triggers) {
        String b = "entries." + id + ".";
        if (!plugin.isEmpty()) {
            m.put(b + "plugin", plugin);
        }
        m.put(b + "title", title).put(b + "icon", icon).put(b + "group", group).put(b + "tip", tip).put(b + "body", body)
                .put(b + "hint", hint).put(b + "triggers", List.of(triggers));
    }

    Kit.TestPlayer player(String name) {
        return Kit.online(new Kit.TestPlayer(name, 0, 0));
    }

    boolean has(Player p, String id) {
        return service.has(p.getUniqueId(), id);
    }

    /** Registers a command with aliases the way a plugin.yml would, owned by a plugin. */
    static void command(org.bukkit.plugin.Plugin owner, String name, String... aliases) {
        org.bukkit.command.Command c = new org.bukkit.command.PluginCommand(name, owner);
        Bukkit.COMMANDS.put(name, c);
        for (String a : aliases) {
            Bukkit.COMMANDS.put(a, c);
        }
    }

    static Collection<? extends Player> everyone() {
        return Bukkit.getOnlinePlayers();
    }
}
