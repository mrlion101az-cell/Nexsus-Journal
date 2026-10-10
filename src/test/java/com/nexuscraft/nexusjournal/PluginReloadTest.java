package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** What /journal reload really reloads, what disabling cleans up, and tips for players who went away. */
public final class PluginReloadTest {

    static void run(CommandExecutor ex, org.bukkit.command.CommandSender who, String... args) {
        ex.onCommand(who, new Command(), "journal", args);
    }

    public static void main(String[] args) throws Exception {
        Kit.reset();
        File dir = Files.createTempDirectory("nexusjournal-reload").toFile();
        JavaPlugin.DATA_FOLDER = dir;
        JavaPlugin.RESOURCES.put("codex.yml", String.join("\n",
                PluginTest.page("welcome", "", "Your Field Journal", "Pages fill in", "join"),
                PluginTest.page("lode", "NexusLode", "Mining", "Mining is a skill", "break:*_ORE"),
                PluginTest.page("rails", "NexusRails", "Rails", "Stations", "place:RAIL")) + "\n");
        File custom = new File(dir, "custom-codex.yml");
        PluginManager.PLUGINS.add(new Kit.NexusLode());

        JournalPlugin plugin = new JournalPlugin();
        plugin.getConfig().set("tip.gap-seconds", 1);
        plugin.onEnable();
        JournalListener listener = null;
        for (Listener l : PluginManager.REGISTERED) {
            if (l instanceof JournalListener jl) {
                listener = jl;
            }
        }
        JournalApi api = Bukkit.getServicesManager().load(JournalApi.class);
        CommandExecutor cmd = plugin.getCommand("journal").getExecutor();
        CommandTest.Console con = new CommandTest.Console();
        con.perms.add("nexusjournal.admin");

        // ---- reload re-reads config and codex
        JavaPlugin.CALLS.clear();
        run(cmd, con, "reload");
        expect(JavaPlugin.CALLS.contains("reloadConfig"), "reload re-reads config.yml: " + JavaPlugin.CALLS);
        expect(con.heard("Reloaded: 3 pages"), "three built-in pages: " + con.lines);
        Files.writeString(custom.toPath(), String.join("\n",
                PluginTest.page("m1", "NexusLode", "One", "t", "break:STONE"),
                PluginTest.page("m2", "NexusLode", "Two", "t", "break:DIRT")) + "\n");
        run(cmd, con, "reload");
        expect(con.heard("Reloaded: 5 pages"), "new custom pages appear on reload: " + con.lines);
        Files.delete(custom.toPath());
        run(cmd, con, "reload");
        expect(con.lines.get(con.lines.size() - 1).contains("Reloaded: 3 pages"), "pages whose file is gone do not linger: " + con.lines);

        // ---- reload notices plugins that arrived without an enable event
        Kit.TestPlayer cy = Kit.online(new Kit.TestPlayer("Cy", 0, 0));
        PluginManager.PLUGINS.add(new Kit.NexusRails());
        run(cmd, con, "reload");
        Block rail = Kit.WORLD.getBlockAt(3, 64, 3);
        rail.setType(Material.RAIL);
        listener.onPlace(new BlockPlaceEvent(rail, cy));
        expect(api.found(cy, "rails"), "reload refreshes the list of running plugins at once");

        // ---- reload applies the tip gap
        plugin.getConfig().set("tip.gap-seconds", 60);
        run(cmd, con, "reload");
        Block ore = Kit.WORLD.getBlockAt(1, 64, 1);
        ore.setType(Material.DIAMOND_ORE);
        Kit.TestPlayer ann = Kit.online(new Kit.TestPlayer("Ann", 0, 0));
        api.discover(ann, "welcome");
        listener.onBreak(new BlockBreakEvent(ore, ann));
        expect(ann.actionBars.size() == 1, "first tip shows at once: " + ann.actionBars);
        Thread.sleep(1200);
        BukkitScheduler.REPEATING.get(0).run();
        expect(ann.actionBars.size() == 1, "the new sixty second gap holds the second tip back: " + ann.actionBars);

        // ---- tips also in chat, when asked
        plugin.getConfig().set("tip.gap-seconds", 1);
        plugin.getConfig().set("tip.also-chat", true);
        run(cmd, con, "reload");
        Kit.TestPlayer dee = Kit.online(new Kit.TestPlayer("Dee", 0, 0));
        listener.onBreak(new BlockBreakEvent(ore, dee));
        expect(dee.heard("New page in your field journal") && dee.heard("Mining"), "also-chat sends a chat line: " + dee.messages);
        plugin.getConfig().set("tip.also-chat", false);
        run(cmd, con, "reload");
        Kit.TestPlayer fay = Kit.online(new Kit.TestPlayer("Fay", 0, 0));
        listener.onBreak(new BlockBreakEvent(ore, fay));
        expect(!fay.heard("New page in your field journal"), "and not otherwise: " + fay.messages);

        // ---- a player who went away does not get the rest of the queue
        Kit.TestPlayer dan = Kit.online(new Kit.TestPlayer("Dan", 0, 0));
        api.discover(dan, "welcome");
        api.discover(dan, "lode");
        api.discover(dan, "rails");
        expect(dan.actionBars.size() == 1, "one shown, two waiting: " + dan.actionBars);
        dan.online = false;
        Thread.sleep(1200);
        BukkitScheduler.REPEATING.get(0).run();
        dan.online = true;
        Thread.sleep(1200);
        BukkitScheduler.REPEATING.get(0).run();
        expect(dan.actionBars.size() == 1, "an offline player's waiting tips are dropped, not used up: " + dan.actionBars);
        // one that is not on the server at all
        Kit.TestPlayer eve = Kit.online(new Kit.TestPlayer("Eve", 0, 0));
        api.discover(eve, "welcome");
        api.discover(eve, "lode");
        Bukkit.PLAYERS.remove(eve.getUniqueId());
        Bukkit.ENTITIES.remove(eve.getUniqueId());
        Thread.sleep(1200);
        BukkitScheduler.REPEATING.get(0).run();
        expect(eve.actionBars.size() == 1, "a player the server no longer knows is skipped without a crash: " + eve.actionBars);

        // ---- disabling
        plugin.onDisable();
        expect(Bukkit.getServicesManager().load(JournalApi.class) == null, "the service is unregistered on disable");
        Kit.finish();
    }
}
