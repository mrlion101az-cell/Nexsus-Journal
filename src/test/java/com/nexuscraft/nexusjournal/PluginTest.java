package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** The plugin class itself, started and stopped against the stub server, with a real codex and saved journals. */
public final class PluginTest {

    static String line(String path, String type, String value) {
        return path + "\t" + type + "\t" + value;
    }

    static String page(String id, String plugin, String title, String tip, String triggers) {
        List<String> lines = new ArrayList<>();
        if (!plugin.isEmpty()) {
            lines.add(line("entries." + id + ".plugin", "s", plugin));
        }
        lines.add(line("entries." + id + ".title", "s", title));
        lines.add(line("entries." + id + ".tip", "s", tip));
        lines.add(line("entries." + id + ".triggers", "l", triggers));
        return String.join("\n", lines);
    }

    static void run(CommandExecutor ex, org.bukkit.command.CommandSender who, String... args) {
        ex.onCommand(who, new Command(), "journal", args);
    }

    public static void main(String[] args) throws Exception {
        Kit.reset();
        File dir = Files.createTempDirectory("nexusjournal-plugin").toFile();
        JavaPlugin.DATA_FOLDER = dir;
        // the codex "inside the jar" (in the stub's own file format)
        JavaPlugin.RESOURCES.put("codex.yml", String.join("\n",
                page("welcome", "", "Your Field Journal", "Pages fill in as you play", "join"),
                page("lode", "NexusLode", "Mining", "Mining is a skill", "break:*_ORE"),
                page("alchemy", "NexusAlchemy", "Brewing", "Order matters", "open:BREWING"),
                page("smelting", "NexusSmelting", "Furnaces", "Smelting skill", "open:FURNACE"),
                page("rails", "NexusRails", "Rails", "Stations and routes", "place:RAIL")) + "\n");
        // the server's own additions
        Files.writeString(new File(dir, "custom-codex.yml").toPath(), String.join("\n",
                line("entries.lode.tip", "s", "Dig deep"),
                page("mine", "NexusLode", "My page", "Mine!", "break:STONE"),
                line("entries.bad.title", "s", "Bad"),
                line("entries.bad.tip", "s", "t"),
                line("entries.bad.triggers", "l", "dance")) + "\n");

        java.util.logging.Logger log = java.util.logging.Logger.getLogger("stub");
        List<String> infos = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord r) {
                (r.getLevel() == java.util.logging.Level.WARNING ? warnings : infos).add(r.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };
        log.addHandler(capture);

        PluginManager.PLUGINS.add(new Kit.NexusLode());
        PluginManager.PLUGINS.add(new Kit.NexusAlchemy());

        // Ann has played before: one page found, book already given
        Kit.TestPlayer ann = Kit.online(new Kit.TestPlayer("Ann", 0, 0));
        Files.writeString(new File(dir, "players.yml").toPath(), String.join("\n",
                line("players." + ann.getUniqueId() + ".book", "b", "true"),
                line("players." + ann.getUniqueId() + ".found.alchemy", "n", "1700000000000")) + "\n");

        JournalPlugin plugin = new JournalPlugin();
        plugin.getConfig().set("tip.gap-seconds", 1);
        plugin.onEnable();

        expect(JavaPlugin.CALLS.contains("saveDefaultConfig") && JavaPlugin.CALLS.contains("saveResource:custom-codex.yml:false"), "default files are written: " + JavaPlugin.CALLS);
        JournalListener listener = null;
        boolean hasPlugin = false;
        for (Listener l : PluginManager.REGISTERED) {
            if (l instanceof JournalListener jl) {
                listener = jl;
            }
            if (l == plugin) {
                hasPlugin = true;
            }
        }
        expect(listener != null && hasPlugin, "both listeners are registered");
        expect(BukkitScheduler.REPEATING.size() == 2, "the tick and save tasks are scheduled");
        expect(infos.contains("NexusJournal enabled -- 7 pages."), "the start-up line says what was loaded (5 built in + 1 custom + the bad one, which falls back to api only): " + infos);
        expect(warnings.size() == 1 && warnings.get(0).contains("'dance' is not a kind of trigger") && warnings.get(0).contains("custom-codex.yml"), "codex problems are warned about, naming the file: " + warnings);
        expect(Bukkit.getServicesManager().load(JournalApi.class) != null, "the API is registered as a service");
        expect(plugin.getCommand("journal").getExecutor() instanceof JournalCommand && plugin.getCommand("journal").getTabCompleter() instanceof JournalCommand, "the command and its completer are wired");

        // ---- Ann's saved journal was loaded
        JournalApi api = Bukkit.getServicesManager().load(JournalApi.class);
        expect(api.found(ann, "alchemy") && !api.found(ann, "lode"), "Ann's saved page is hers again");

        // ---- joining: the page opens, the tip shows on the action bar; she already has her book
        BukkitScheduler.LATER.clear();
        listener.onJoin(new PlayerJoinEvent(ann, ""));
        expect(api.found(ann, "welcome"), "joining opens the welcome page");
        expect(BukkitScheduler.LATER.isEmpty(), "no second book for someone who has had one");
        expect(ann.actionBars.size() == 1 && ann.actionBars.get(0).contains("Your Field Journal") && ann.actionBars.get(0).contains("Pages fill in as you play"),
                "the tip appears on the action bar: " + ann.actionBars);
        expect(ann.actionBars.get(0).contains("§e") && !ann.actionBars.get(0).contains("&e"), "with its colours translated: " + ann.actionBars);

        // ---- a second page right away waits its turn; the tick shows it
        Block ore = Kit.WORLD.getBlockAt(1, 64, 1);
        ore.setType(Material.DIAMOND_ORE);
        listener.onBreak(new BlockBreakEvent(ore, ann));
        expect(api.found(ann, "lode") && ann.actionBars.size() == 1, "found at once, shown later: " + ann.actionBars);
        Thread.sleep(1100);
        BukkitScheduler.REPEATING.get(0).run();
        expect(ann.actionBars.size() == 2 && ann.actionBars.get(1).contains("Dig deep"), "the custom tip replaced the built-in one, and shows after the gap: " + ann.actionBars);

        // ---- NexusPath hides: through the player data it publishes
        org.bukkit.NamespacedKey hiddenKey = org.bukkit.NamespacedKey.fromString("nexuspath:hidden");
        Kit.TestPlayer bob = Kit.online(new Kit.TestPlayer("Bob", 0, 0));
        bob.getPersistentDataContainer().set(hiddenKey, org.bukkit.persistence.PersistentDataType.STRING, "NexusAlchemy,NexusLode");
        listener.onBreak(new BlockBreakEvent(ore, bob));
        expect(!api.found(bob, "lode"), "a player who hid mining does not get its page");
        Kit.TestPlayer root = Kit.online(new Kit.TestPlayer("Root", 0, 0));
        root.perms.add("nexuspath.hidden.nexuslode");
        listener.onBreak(new BlockBreakEvent(ore, root));
        expect(api.found(root, "lode"), "a permission alone does not hide anything by default (a * wildcard would otherwise hide every page from an admin)");
        CommandTest.Console con = new CommandTest.Console();
        con.perms.add("nexusjournal.admin");
        plugin.getConfig().set("nexuspath.source", "permission");
        run(plugin.getCommand("journal").getExecutor(), con, "reload");
        expect(con.heard("Reloaded: 7 pages"), "reload reports the page count: " + con.lines);
        Kit.TestPlayer eli = Kit.online(new Kit.TestPlayer("Eli", 0, 0));
        eli.perms.add("nexuspath.hidden.nexuslode");
        listener.onBreak(new BlockBreakEvent(ore, eli));
        expect(!api.found(eli, "lode"), "with source: permission the node hides the page");
        plugin.getConfig().set("nexuspath.respect-hidden", false);
        run(plugin.getCommand("journal").getExecutor(), con, "reload");
        listener.onBreak(new BlockBreakEvent(ore, eli));
        expect(api.found(eli, "lode"), "with respect-hidden off nothing NexusPath says matters");
        plugin.getConfig().set("nexuspath.respect-hidden", true);
        plugin.getConfig().set("nexuspath.source", "data");
        run(plugin.getCommand("journal").getExecutor(), con, "reload");
        listener.onBreak(new BlockBreakEvent(ore, bob));
        expect(!api.found(bob, "lode"), "back to data: Bob's page is shut again");
        bob.getPersistentDataContainer().remove(hiddenKey);
        listener.onBreak(new BlockBreakEvent(ore, bob));
        expect(api.found(bob, "lode"), "and opens once he shows mining again in NexusPath");

        // ---- a plugin that arrives later
        Kit.TestPlayer cy = Kit.online(new Kit.TestPlayer("Cy", 0, 0));
        org.bukkit.inventory.Inventory furnace = new org.bukkit.inventory.Inventory() {
            @Override
            public org.bukkit.event.inventory.InventoryType getType() {
                return org.bukkit.event.inventory.InventoryType.FURNACE;
            }
        };
        org.bukkit.inventory.InventoryView fv = new org.bukkit.inventory.InventoryView();
        fv.setTopInventory(furnace);
        fv.setPlayer(cy);
        listener.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(fv));
        expect(!api.found(cy, "smelting"), "Smelting is not installed: no page");
        Kit.NexusSmelting smelting = new Kit.NexusSmelting();
        PluginManager.PLUGINS.add(smelting);
        plugin.onPluginEnable(new PluginEnableEvent(smelting));
        listener.onOpen(new org.bukkit.event.inventory.InventoryOpenEvent(fv));
        expect(api.found(cy, "smelting"), "once it enables, its page can open");
        // a plugin that is there but is only noticed on the five-second refresh
        Kit.NexusRails rails = new Kit.NexusRails();
        PluginManager.PLUGINS.add(rails);
        Block rail = Kit.WORLD.getBlockAt(2, 64, 2);
        rail.setType(Material.RAIL);
        listener.onPlace(new org.bukkit.event.block.BlockPlaceEvent(rail, cy));
        expect(!api.found(cy, "rails"), "a plugin nobody told the journal about is not noticed straight away");
        // one tick has run so far (the tip above), so three more make four
        for (int i = 0; i < 3; i++) {
            BukkitScheduler.REPEATING.get(0).run();
        }
        listener.onPlace(new org.bukkit.event.block.BlockPlaceEvent(rail, cy));
        expect(!api.found(cy, "rails"), "and not before the fifth tick");
        BukkitScheduler.REPEATING.get(0).run();
        listener.onPlace(new org.bukkit.event.block.BlockPlaceEvent(rail, cy));
        expect(api.found(cy, "rails"), "but on the fifth the refresh sees it");

        // ---- saving
        File saved = new File(dir, "players.yml");
        String before = Files.readString(saved.toPath());
        BukkitScheduler.REPEATING.get(1).run();
        String after = Files.readString(saved.toPath());
        expect(!before.equals(after) && after.contains("found.lode") && after.contains("found.welcome") && after.contains("found.smelting"), "the save task writes players.yml: " + after);
        expect(after.contains("found.alchemy\tn\t1700000000000"), "and keeps the old page's time");

        // ---- stop
        run(plugin.getCommand("journal").getExecutor(), con, "grant", "Cy", "mine");
        String beforeDisable = Files.readString(saved.toPath());
        plugin.onDisable();
        expect(BukkitScheduler.HANDLES.size() == 2 && BukkitScheduler.HANDLES.stream().allMatch(org.bukkit.scheduler.BukkitTask::isCancelled), "both repeating tasks are cancelled");
        expect(!Files.readString(saved.toPath()).equals(beforeDisable) && Files.readString(saved.toPath()).contains("found.mine"), "disable saves what was found since the last save");

        // ---- a second start reads it all back, and a plugin with no resources still starts
        Kit.reset();
        JavaPlugin.DATA_FOLDER = dir;
        JavaPlugin.RESOURCES.clear();
        warnings.clear();
        infos.clear();
        Kit.TestPlayer ann2 = Kit.online(new Kit.TestPlayer("Ann", 0, 0));
        JournalPlugin second = new JournalPlugin();
        second.onEnable();
        expect(infos.contains("NexusJournal enabled -- 2 pages."), "custom-codex.yml alone gives only its own complete pages (mine, bad; the lode tweak has no title): " + infos + " " + warnings);
        second.onDisable();
        log.removeHandler(capture);
        expect(ann2 != null && Files.readString(saved.toPath()).contains("found.lode"), "the second start did not lose anything");
        Kit.finish();
    }
}
