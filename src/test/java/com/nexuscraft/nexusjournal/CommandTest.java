package com.nexuscraft.nexusjournal;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** /journal and everything staff can do with it. */
public final class CommandTest {

    /** Something that is not a player: the console. */
    static final class Console implements CommandSender {
        final List<String> lines = new ArrayList<>();
        final Set<String> perms = new HashSet<>();

        @Override
        public boolean hasPermission(String node) {
            return perms.contains(node);
        }

        @Override
        public void sendMessage(String message) {
            lines.add(message);
        }

        @Override
        public void sendMessage(net.kyori.adventure.text.Component message) {
            lines.add(message.toString());
        }

        @Override
        public String getName() {
            return "CONSOLE";
        }

        boolean heard(String s) {
            return lines.stream().anyMatch(l -> l.contains(s));
        }
    }

    static boolean reloaded;

    static void run(JournalCommand c, CommandSender s, String... args) {
        expect(c.onCommand(s, new Command(), "journal", args), "the command always reports success: " + String.join(" ", args));
    }

    public static void main(String[] args) {
        Env e = new Env();
        JournalBook book = new JournalBook(new NamespacedKey(new Kit.TestPlugin(), "book"));
        reloaded = false;
        JournalCommand c = new JournalCommand(e.service, book, () -> reloaded = true);
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer bob = e.player("Bob");
        Kit.TestPlayer staff = e.player("Staff");
        staff.perms.add("nexusjournal.admin");
        Console con = new Console();

        // ---- open
        run(c, ann);
        expect(ann.opened != null && ann.opened.getHolder() instanceof JournalGui, "/journal opens the menu");
        ann.opened = null;
        run(c, ann, "open");
        expect(ann.opened != null, "/journal open too");
        ann.opened = null;
        run(c, ann, "OPEN");
        expect(ann.opened != null, "in any case");
        run(c, con);
        expect(con.heard("Players only") && con.lines.size() == 1, "the console cannot open a menu");
        run(c, ann, "nonsense");
        expect(ann.heard("Try") && ann.heard("/journal"), "an unknown word gets a hint");

        // ---- book
        run(c, ann, "book");
        expect(book.carries(ann) && ann.heard("field journal"), "/journal book gives a book");
        ann.messages.clear();
        run(c, ann, "book");
        expect(ann.heard("already") && CommandTestCount(book, ann) == 1, "a second one is refused");
        run(c, con, "book");
        expect(con.heard("Players only"), "console has no inventory");
        Kit.TestPlayer full = e.player("Full");
        for (int i = 0; i < 54; i++) {
            full.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        }
        run(c, full, "book");
        expect(full.heard("inventory is full") && !book.carries(full), "a full inventory gets a message, not a book");

        // ---- stats
        run(c, ann, "stats");
        expect(ann.heard("Found") && ann.heard("0") && ann.heard("8"), "stats on a fresh player: " + ann.lastMessage());
        e.service.discover(ann, "lode", false);
        ann.messages.clear();
        run(c, ann, "stats");
        expect(ann.lastMessage().contains("1") && ann.lastMessage().contains("8"), "stats counts: " + ann.lastMessage());
        con.lines.clear();
        run(c, con, "stats");
        expect(con.heard("Players only"), "console has no stats");

        // ---- staff only
        for (String sub : new String[] {"discover", "grant", "revoke", "show", "reload", "check"}) {
            ann.messages.clear();
            run(c, ann, sub, "Bob", "lode");
            expect(ann.heard("Only staff") && !e.has(bob, "lode"), "a player cannot " + sub);
        }
        expect(!reloaded, "and reload did not run");

        // ---- discover
        run(c, staff, "discover");
        expect(staff.heard("Usage"), "discover needs a player");
        staff.messages.clear();
        run(c, staff, "discover", "Nobody", "lode");
        expect(staff.heard("not online"), "an unknown player");
        staff.messages.clear();
        run(c, staff, "discover", "Bob");
        expect(staff.heard("Usage"), "discover needs a page");
        staff.messages.clear();
        run(c, staff, "discover", "Bob", "lode");
        expect(e.has(bob, "lode") && staff.heard("Opened 'lode' for Bob"), "discover opens the page: " + staff.lastMessage());
        staff.messages.clear();
        run(c, staff, "discover", "Bob", "lode");
        expect(staff.heard("already has"), "twice");
        staff.messages.clear();
        run(c, staff, "discover", "Bob", "nope");
        expect(staff.heard("no page 'nope'"), "an unknown page");
        staff.messages.clear();
        e.hidden.add("Bob:nexusrime");
        run(c, staff, "discover", "Bob", "rime");
        expect(!e.has(bob, "rime") && staff.heard("not in Bob's game") && staff.heard("/journal grant"), "a hidden system's page is refused, with a hint: " + staff.lastMessage());
        e.hidden.clear();
        con.perms.add("nexusjournal.admin");
        con.lines.clear();
        run(c, con, "discover", "Ann", "rime");
        expect(e.has(ann, "rime") && con.heard("Opened"), "the console can run it (that is how other plugins call it)");

        // ---- grant
        staff.messages.clear();
        e.hidden.add("Bob:nexusrime");
        run(c, staff, "grant", "Bob", "rime");
        expect(e.has(bob, "rime") && staff.heard("Opened 'rime'"), "grant forces a page");
        e.hidden.clear();
        staff.messages.clear();
        run(c, staff, "grant", "Bob");
        expect(staff.heard("Usage") && staff.heard("all"), "grant needs a page");
        staff.messages.clear();
        run(c, staff, "grant", "Bob", "ALL");
        expect(staff.heard("Opened 8 pages for Bob") && e.service.foundCount(bob.getUniqueId()) == 10, "grant all: " + staff.lastMessage() + " / " + e.service.foundCount(bob.getUniqueId()));

        // ---- revoke
        staff.messages.clear();
        run(c, staff, "revoke", "Bob");
        expect(staff.heard("Usage"), "revoke needs a page");
        staff.messages.clear();
        run(c, staff, "revoke", "Bob", "lode");
        expect(!e.has(bob, "lode") && staff.heard("Took back 1 page from Bob"), "revoke one: " + staff.lastMessage());
        staff.messages.clear();
        run(c, staff, "revoke", "Bob", "lode");
        expect(staff.heard("nothing to take back"), "revoke what is not there");
        staff.messages.clear();
        run(c, staff, "revoke", "Bob", "all");
        expect(staff.heard("Took back 9 pages from Bob") && e.service.foundCount(bob.getUniqueId()) == 0, "revoke all: " + staff.lastMessage());

        // ---- show
        staff.messages.clear();
        run(c, staff, "show", "Bob");
        expect(staff.heard("Bob has") && staff.heard("0") && !staff.heard(":"), "show an empty journal: " + staff.lastMessage());
        e.service.discover(bob, "lode", false);
        e.service.discover(bob, "rime", false);
        staff.messages.clear();
        run(c, staff, "show", "Bob");
        expect(staff.heard("lode, rime"), "show lists the ids in order: " + staff.lastMessage());
        run(c, staff, "show");
        expect(staff.heard("Usage"), "show needs a player");

        // ---- reload and check
        staff.messages.clear();
        run(c, staff, "reload");
        expect(reloaded && staff.heard("Reloaded: 10 pages"), "reload runs the reload: " + staff.lastMessage());
        staff.messages.clear();
        run(c, staff, "check");
        expect(staff.heard("10 pages") && staff.heard("1 are about plugins that are not running"), "check counts pages and missing plugins: " + staff.messages);
        expect(staff.heard("1 pages open only when") && staff.heard("horror"), "and lists the pages only a plugin can open: " + staff.messages);
        Kit.MapSection warn = new Kit.MapSection();
        warn.put("entries.x.title", "X").put("entries.x.triggers", List.of("dance"));
        e.codex.load(warn, "custom.yml");
        e.codex.finish();
        staff.messages.clear();
        run(c, staff, "check");
        expect(staff.heard("custom.yml") && staff.heard("not a kind of trigger"), "check repeats the load warnings: " + staff.messages);
        Env clean = new Env();
        Codex good = new Codex();
        Kit.MapSection gm = new Kit.MapSection();
        Env.page(gm, "only", "NexusLode", "Only", "BOOK", "g", "t", "b", "h", "command");
        good.load(gm, "t");
        good.finish();
        JournalService gs = new JournalService(clean.cfg, good, clean.store, () -> clean.installed, (p, pl) -> false, (p, en) -> { }, clean.clock::get);
        JournalCommand gc = new JournalCommand(gs, book, () -> { });
        Console gcon = new Console();
        gcon.perms.add("nexusjournal.admin");
        run(gc, gcon, "check");
        expect(gcon.heard("Every page can open from something a player does"), "a clean codex says so: " + gcon.lines);

        // ---- tab completion (the clean Env above reset the stub server; put the players back)
        for (Kit.TestPlayer back : List.of(ann, bob, staff)) {
            Kit.online(back);
        }
        List<String> t = c.onTabComplete(ann, new Command(), "journal", new String[] {""});
        expect(t.containsAll(List.of("open", "book", "stats")) && !t.contains("grant") && !t.contains("reload"), "players see player subcommands: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {""});
        expect(t.containsAll(List.of("open", "grant", "revoke", "discover", "show", "reload", "check")), "staff see all: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"DI"});
        expect(t.equals(List.of("discover")), "prefix match ignores case: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"grant", ""});
        expect(t.contains("Ann") && t.contains("Bob"), "second word: players: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"grant", "an"});
        expect(t.equals(List.of("Ann")), "players by prefix: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"reload", ""});
        expect(t.isEmpty(), "reload takes nothing more");
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"check", ""});
        expect(t.isEmpty(), "check takes nothing more");
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"grant", "Ann", "r"});
        expect(t.contains("rime") && t.contains("rails") && !t.contains("lode"), "third word: page ids by prefix: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"grant", "Ann", "a"});
        expect(t.contains("all") && t.contains("alchemy") && t.contains("angler"), "grant offers all: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"revoke", "Ann", "al"});
        expect(t.contains("all") && t.contains("alchemy"), "revoke offers all: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"discover", "Ann", "al"});
        expect(t.contains("alchemy") && !t.contains("all"), "discover does not offer all: " + t);
        t = c.onTabComplete(staff, new Command(), "journal", new String[] {"show", "Ann", ""});
        expect(t.isEmpty(), "show takes no page");
        t = c.onTabComplete(ann, new Command(), "journal", new String[] {"grant", ""});
        expect(t.isEmpty(), "players get no staff completions");
        t = c.onTabComplete(ann, new Command(), "journal", new String[] {"book", ""});
        expect(t.isEmpty(), "nothing after book");
        Kit.finish();
    }

    static int CommandTestCount(JournalBook book, org.bukkit.entity.Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (book.isBook(s)) {
                n++;
            }
        }
        return n;
    }
}
