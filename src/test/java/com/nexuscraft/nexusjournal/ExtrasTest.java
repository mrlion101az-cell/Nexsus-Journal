package com.nexuscraft.nexusjournal;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** Edge cases found by mutation testing: reload hygiene, label parsing, the book's own text. */
public final class ExtrasTest {

    public static void main(String[] args) throws Exception {
        // clear() really empties pages, the by-kind index and the warnings
        Codex c = new Codex();
        c.load(Env.sample(), "test");
        c.finish();
        expect(c.size() == 10 && !c.withKind("break").isEmpty(), "loaded");
        c.clear();
        expect(c.size() == 0 && c.entries().isEmpty() && c.get("lode") == null, "clear() drops every page");
        expect(c.withKind("break").isEmpty() && c.withKind("join").isEmpty(), "clear() drops the by-kind index too");
        expect(c.warnings().isEmpty(), "clear() drops warnings");

        // finish() rebuilds the index from scratch instead of piling onto the old one
        Codex d = new Codex();
        d.load(Env.sample(), "test");
        d.finish();
        int before = d.withKind("break").size();
        d.finish();
        d.finish();
        expect(d.withKind("break").size() == before, "finishing twice does not duplicate index entries: " + d.withKind("break").size());
        d.clear();
        d.finish();
        expect(d.withKind("break").isEmpty(), "an empty codex has an empty index after finish()");

        // labels
        expect(CommandOwner.label("/ x").isEmpty(), "a slash then a space has no label");
        expect(CommandOwner.label("/").isEmpty() && CommandOwner.label("").isEmpty() && CommandOwner.label(null).isEmpty(), "empty labels");
        expect(CommandOwner.label("  /Farm  list").equals("farm"), "trimmed, lower-cased, first word");
        expect(CommandOwner.label("farm").equals("farm"), "no slash, no space");
        Kit.reset();
        expect(CommandOwner.pluginOf(null) == null && CommandOwner.pluginOf("") == null, "null and empty labels own nothing");
        expect(CommandOwner.pluginOf("nosuchcommand") == null, "unknown label owns nothing");

        // the book looks like a book
        JournalBook book = new JournalBook(new NamespacedKey(new Kit.TestPlugin(), "book"));
        ItemStack item = book.create();
        expect(item.getType() == Material.BOOK, "a book is a book");
        expect(item.getItemMeta().getDisplayName() != null && item.getItemMeta().getDisplayName().contains("Field Journal"), "named: " + item.getItemMeta().getDisplayName());
        List<String> lore = item.getItemMeta().getLore();
        expect(lore != null && lore.size() == 2, "two lore lines: " + lore);
        expect(lore != null && lore.get(0).contains("Right-click") && lore.get(1).contains("Pages fill in"), "lore text: " + lore);
        expect(book.isBook(item), "recognised by its tag");

        // glob: a wildcard that has to try later positions, and one that matches nothing
        expect(Glob.matchesAny("a*c", "abxc") && Glob.matchesAny("a*c", "ac") && !Glob.matchesAny("a*c", "abx"), "wildcard in the middle");
        expect(Glob.matchesAny("*", "") && Glob.matchesAny("a*", "a"), "wildcard matches nothing");

        config();
        store();
        gui();
        revokeAndWelcome();
        craftFallback();
        tipperTidy();
        consoleBook();
        Kit.finish();
    }

    static String lore(ItemStack i) {
        return i == null ? "" : String.join("\n", i.getItemMeta().getLore());
    }

    static void config() {
        JournalConfig d = new JournalConfig();
        expect(d.bookOnJoin && d.bookDelaySeconds == 10 && d.tipGapSeconds == 4 && !d.tipChat && d.respectPath && d.pathSource.equals("data") && !d.showUnreachable, "defaults");
        d.load(new Kit.MapSection());
        expect(d.bookOnJoin && d.bookDelaySeconds == 10 && d.tipGapSeconds == 4 && !d.tipChat && d.respectPath && d.pathSource.equals("data") && !d.showUnreachable, "an empty config keeps every default");
        Kit.MapSection m = new Kit.MapSection()
                .put("book.give-on-join", false).put("book.delay-seconds", 3).put("tip.format", "T {title}").put("tip.gap-seconds", 9)
                .put("tip.also-chat", true).put("nexuspath.respect-hidden", false).put("nexuspath.source", "Permission")
                .put("menu.show-unreachable", true).put("messages.prefix", "P ").put("messages.found", "F").put("messages.book", "B").put("messages.no-book", "N");
        JournalConfig c = new JournalConfig();
        c.load(m);
        expect(!c.bookOnJoin && c.bookDelaySeconds == 3 && c.tipFormat.equals("T {title}") && c.tipGapSeconds == 9 && c.tipChat && !c.respectPath
                && c.pathSource.equals("permission") && c.showUnreachable, "every setting is read");
        expect(c.prefix.equals("P ") && c.msgFound.equals("F") && c.msgBook.equals("B") && c.msgNoBook.equals("N"), "every message is read");
        JournalConfig w = new JournalConfig();
        w.load(new Kit.MapSection().put("book.delay-seconds", 9999).put("tip.gap-seconds", 0).put("nexuspath.source", "weird"));
        expect(w.bookDelaySeconds == 600 && w.tipGapSeconds == 1 && w.pathSource.equals("data"), "out of range is held to range, unknown source falls back");
        JournalConfig n = new JournalConfig();
        n.load(new Kit.MapSection().put("book.delay-seconds", -5).put("tip.gap-seconds", 500));
        expect(n.bookDelaySeconds == 0 && n.tipGapSeconds == 60, "and below range too");
    }

    static void store() throws Exception {
        File dir = Files.createTempDirectory("journal-extras").toFile();
        File f = new File(dir, "players.yml");
        java.util.UUID ann = java.util.UUID.randomUUID();
        java.util.UUID bob = java.util.UUID.randomUUID();
        Files.writeString(f.toPath(), String.join("\n",
                "players.junk\ts\tnot a section",
                "players." + ann + ".found.lode\tn\t5",
                "players." + bob + ".book\tb\ttrue") + "\n");
        List<String> warns = new ArrayList<>();
        JournalStore st = new JournalStore(f, warns::add);
        st.load();
        expect(st.peek(ann) != null && st.peek(ann).has("lode"), "a player with only found pages loads");
        expect(!st.peek(ann).bookGiven, "no book line means the book was not given");
        expect(st.peek(bob) != null && st.peek(bob).bookGiven, "a saved book flag loads");
        expect(st.size() == 2 && warns.isEmpty(), "a stray value under players is skipped quietly: size " + st.size() + " " + warns);
    }

    static void gui() {
        Env e = new Env();
        for (int total : new int[] {45, 46, 90, 91}) {
            Kit.MapSection big = new Kit.MapSection();
            for (int i = 0; i < total; i++) {
                Env.page(big, "p" + String.format("%03d", i), "", "Page " + i, "BOOK", "g", "t", "body " + i, "hint " + i, "join");
            }
            Codex cx = new Codex();
            cx.load(big, "t");
            cx.finish();
            JournalService s = new JournalService(e.cfg, cx, new JournalStore(null, w -> { }), () -> e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
            Kit.TestPlayer p = e.player("P" + total);
            JournalGui.open(s, p, 0, JournalGui.Filter.ALL);
            int pages = (total + 44) / 45;
            expect((p.opened.getItem(JournalGui.NEXT) != null) == (pages > 1), total + " pages: next button only when there is more: pages " + pages);
            if (pages > 1) {
                expect(lore(p.opened.getItem(JournalGui.NEXT)).contains("Page 2 of " + pages), total + " says where next goes: " + lore(p.opened.getItem(JournalGui.NEXT)));
            }
            JournalGui.open(s, p, 99, JournalGui.Filter.ALL);
            expect(((JournalGui) p.opened.getHolder()).page == pages - 1, total + " entries make exactly " + pages + " pages");
        }
        // a found tile: text, a blank line, then the group
        Kit.TestPlayer q = e.player("Q");
        e.service.discover(q, "lode", true);
        JournalGui.open(e.service, q, 0, JournalGui.Filter.FOUND);
        List<String> tl = q.opened.getItem(0).getItemMeta().getLore();
        expect(tl.size() == 3 && tl.get(1).isEmpty() && tl.get(2).contains("Make"), "found tile has a spacer line: " + tl);
        // wrapping at the edges
        expect(JournalGui.wrap("&", 44).equals(List.of("&")), "a lone colour character is left alone");
        expect(JournalGui.wrap("&7", 44).equals(List.of("&7")), "colour code only");
        String a = "a".repeat(21), b = "b".repeat(22);
        List<String> w = JournalGui.wrap(a + " " + b + " ccccc", 44);
        expect(w.size() == 2 && w.get(0).equals(a + " " + b) && w.get(1).equals("ccccc"), "a piece may be exactly the width: " + w);
        w = JournalGui.wrap("a".repeat(40) + " ddddd " + "z".repeat(3), 44);
        expect(w.size() == 2 && w.get(0).equals("a".repeat(40)) && w.get(1).equals("ddddd zzz"), "a word that would pass the width starts a new piece: " + w);
    }

    static void revokeAndWelcome() throws Exception {
        Env e = new Env();
        File f = new File(Files.createTempDirectory("journal-extras2").toFile(), "players.yml");
        JournalStore st = new JournalStore(f, w -> { });
        JournalService svc = new JournalService(e.cfg, e.codex, st, () -> (Collection<String>) e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
        Kit.TestPlayer ann = e.player("Ann");
        svc.discover(ann, "welcome", false);
        st.flush();
        expect(!st.dirty(), "flushed");
        expect(svc.revoke(ann.getUniqueId(), "lode", false) == 0 && !st.dirty(), "revoking a page she never had changes nothing and saves nothing");
        expect(svc.revoke(ann.getUniqueId(), "welcome", false) == 1 && st.dirty(), "revoking one marks the record for saving");
        st.flush();
        svc.discover(ann, "lode", true);
        st.flush();
        expect(svc.revoke(ann.getUniqueId(), "", true) == 1 && st.dirty(), "revoke all marks it too");
        st.flush();
        expect(svc.revoke(ann.getUniqueId(), "", true) == 0 && !st.dirty(), "revoke all on an empty record is quiet");

        // the first book marks the record for saving
        JournalStore st2 = new JournalStore(f, w -> { });
        JournalService svc2 = new JournalService(e.cfg, e.codex, st2, () -> (Collection<String>) e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
        JournalBook book = new JournalBook(new NamespacedKey(new Kit.TestPlugin(), "book"));
        JournalListener l = new JournalListener(new Kit.TestPlugin(), svc2, book, new Tipper((id, line) -> true, 0));
        Kit.TestPlayer bea = e.player("Bea");
        expect(!st2.dirty(), "fresh store");
        l.welcome(bea);
        expect(st2.of(bea.getUniqueId()).bookGiven && st2.dirty(), "getting the first book marks the record for saving");
    }

    static void craftFallback() {
        Env e = new Env();
        Kit.MapSection m = new Kit.MapSection();
        Env.page(m, "k-craft", "", "Craft", "BOOK", "g", "t", "b", "h", "craft:CROSSBOW");
        Codex cx = new Codex();
        cx.load(m, "t");
        cx.finish();
        List<String> opened = new ArrayList<>();
        JournalService svc = new JournalService(e.cfg, cx, new JournalStore(null, w -> { }), () -> e.installed, (p, pl) -> false, (p, en) -> opened.add(en.id()), e.clock::get);
        JournalListener l = new JournalListener(new Kit.TestPlugin(), svc, new JournalBook(new NamespacedKey(new Kit.TestPlugin(), "book")), new Tipper((id, line) -> true, 0));
        Kit.TestPlayer ann = e.player("Ann");
        InventoryView v = new InventoryView();
        v.setTopInventory(new org.bukkit.inventory.Inventory());
        v.setPlayer(ann);
        l.onCraft(new CraftItemEvent(() -> new ItemStack(Material.CROSSBOW), v, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(opened.equals(List.of("k-craft")), "when the result slot reads empty the recipe's own result counts: " + opened);
        // and a recipe is not consulted when the slot has the item
        opened.clear();
        Kit.TestPlayer ben = e.player("Ben");
        org.bukkit.inventory.Inventory top = new org.bukkit.inventory.Inventory();
        top.setItem(0, new ItemStack(Material.STONE));
        InventoryView v2 = new InventoryView();
        v2.setTopInventory(top);
        v2.setPlayer(ben);
        l.onCraft(new CraftItemEvent(() -> new ItemStack(Material.CROSSBOW), v2, InventoryType.SlotType.RESULT, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        expect(opened.isEmpty(), "the slot's item wins over the recipe: " + opened);
    }

    static void tipperTidy() {
        Tipper t = new Tipper((id, line) -> true, 0);
        java.util.UUID id = java.util.UUID.randomUUID();
        t.offer(id, "x", 1000L);
        expect(t.queued() == 0, "a tip shown straight away leaves no queue behind: " + t.queued());
        Tipper slow = new Tipper((i, line) -> true, 10_000);
        slow.offer(id, "a", 1000L);
        slow.offer(id, "b", 1001L);
        expect(slow.queued() == 1 && slow.waiting(id) == 1, "one waiting");
        slow.pump(20_000L);
        expect(slow.queued() == 0 && slow.waiting(id) == 0, "once shown the queue is dropped");
    }

    static void consoleBook() {
        Env e = new Env();
        JournalCommand c = new JournalCommand(e.service, new JournalBook(new NamespacedKey(new Kit.TestPlugin(), "book")), () -> { });
        CommandTest.Console con = new CommandTest.Console();
        c.onCommand(con, new org.bukkit.command.Command(), "journal", new String[] {"book"});
        expect(con.lines.size() == 1 && con.heard("Players only"), "the console gets told it cannot hold a book: " + con.lines);
    }
}
