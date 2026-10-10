package com.nexuscraft.nexusjournal;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** The journal menu: tiles, filters, paging, buttons. */
public final class GuiTest {

    static String name(ItemStack i) {
        return i == null ? null : i.getItemMeta().getDisplayName();
    }

    static String lore(ItemStack i) {
        return i == null ? "" : String.join("\n", i.getItemMeta().getLore());
    }

    static JournalGui gui(Kit.TestPlayer p) {
        return (JournalGui) p.opened.getHolder();
    }

    public static void main(String[] args) {
        Env e = new Env();
        Kit.TestPlayer ann = e.player("Ann");
        JournalService s = e.service;

        // ---- nothing found yet
        JournalGui.open(s, ann, 0, JournalGui.Filter.ALL);
        expect(ann.opened != null && ann.opened.getHolder() instanceof JournalGui, "the menu opens for the player");
        ItemStack t0 = ann.opened.getItem(0);
        expect(t0 != null && t0.getType() == Material.GRAY_DYE, "an unfound page is a grey dye");
        expect(name(t0).contains("Not found yet") && name(t0).contains("Start"), "named by its group, not by what it is: " + name(t0));
        expect(!name(t0).contains("Field Journal") && !name(t0).contains("Your Field"), "the closed tile does not give the title away");
        expect(lore(t0).contains("Join."), "the closed tile carries the hint: " + lore(t0));
        expect(ann.opened.getItem(7) != null && ann.opened.getItem(8) == null, "eight tiles for eight pages she could earn");
        ItemStack info = ann.opened.getItem(JournalGui.INFO);
        expect(info.getType() == Material.BOOK && lore(info).contains("Found 0 of 8 pages."), "progress: " + lore(info));
        expect(lore(info).contains("Showing: All pages"), "and the filter");
        expect(ann.opened.getItem(JournalGui.FILTER).getType() == Material.COMPASS && name(ann.opened.getItem(JournalGui.FILTER)).contains("All pages"), "filter button");
        expect(ann.opened.getItem(JournalGui.CLOSE).getType() == Material.BARRIER, "close button");
        expect(ann.opened.getItem(JournalGui.PREV) == null && ann.opened.getItem(JournalGui.NEXT) == null, "one page: no paging buttons");

        // ---- found pages
        s.discover(ann, "lode", false);
        s.discover(ann, "welcome", false);
        JournalGui.open(s, ann, 0, JournalGui.Filter.ALL);
        ItemStack welcome = ann.opened.getItem(0);
        expect(welcome.getType() == Material.BOOK && name(welcome).contains("Your Field Journal") && name(welcome).contains("✔"), "a found page shows its icon, title and a tick: " + name(welcome));
        expect(lore(welcome).contains("Welcome.") && lore(welcome).contains("Start"), "and its text and group: " + lore(welcome));
        ItemStack lode = ann.opened.getItem(1);
        expect(lode.getType() == Material.DIAMOND_ORE && lore(lode).contains("Level up mining.") && lore(lode).contains("Make - NexusLode"), "plugin pages say which plugin: " + lore(lode));
        expect(ann.opened.getItem(2).getType() == Material.GRAY_DYE, "the rest stay closed");
        expect(lore(ann.opened.getItem(JournalGui.INFO)).contains("Found 2 of 8 pages."), "progress counts");

        // a page without a body falls back to its tip; unknown icons to a book
        Kit.MapSection m = new Kit.MapSection();
        m.put("entries.nobody.title", "No body").put("entries.nobody.tip", "Just a tip").put("entries.nobody.icon", "NOT_AN_ITEM").put("entries.nobody.triggers", List.of("join"));
        m.put("entries.nohint.title", "No hint").put("entries.nohint.tip", "t").put("entries.nohint.triggers", List.of("join"));
        Codex odd = new Codex();
        odd.load(m, "t");
        odd.finish();
        JournalService os = new JournalService(e.cfg, odd, new JournalStore(null, w -> { }), () -> e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
        os.discover(ann, "nobody", false);
        JournalGui.open(os, ann, 0, JournalGui.Filter.ALL);
        expect(ann.opened.getItem(0).getType() == Material.BOOK && lore(ann.opened.getItem(0)).contains("Just a tip"), "no body: the tip is shown; unknown icon: a book");
        expect(lore(ann.opened.getItem(1)).contains("Keep playing and you will come across it."), "no hint: a default line: " + lore(ann.opened.getItem(1)));

        // ---- filters
        JournalGui.open(s, ann, 0, JournalGui.Filter.FOUND);
        expect(ann.opened.getItem(0) != null && ann.opened.getItem(1) != null && ann.opened.getItem(2) == null, "found only: two tiles");
        expect(ann.opened.getItem(0).getType() != Material.GRAY_DYE && ann.opened.getItem(1).getType() != Material.GRAY_DYE, "and both are real pages");
        expect(lore(ann.opened.getItem(JournalGui.INFO)).contains("Showing: Found"), "the info tile names the filter");
        JournalGui.open(s, ann, 0, JournalGui.Filter.MISSING);
        int missing = 0;
        for (int i = 0; i < JournalGui.PER_PAGE; i++) {
            ItemStack it = ann.opened.getItem(i);
            if (it != null) {
                missing++;
                expect(it.getType() == Material.GRAY_DYE, "missing only: tile " + i + " is closed");
            }
        }
        expect(missing == 6, "six pages still to find: " + missing);
        expect(JournalGui.Filter.ALL.next() == JournalGui.Filter.FOUND && JournalGui.Filter.FOUND.next() == JournalGui.Filter.MISSING
                && JournalGui.Filter.MISSING.next() == JournalGui.Filter.ALL, "the filter cycles");

        // ---- clicks
        JournalGui.open(s, ann, 0, JournalGui.Filter.ALL);
        gui(ann).click(s, ann, 3);
        expect(gui(ann).filter == JournalGui.Filter.ALL, "a click on a tile does nothing");
        gui(ann).click(s, ann, JournalGui.INFO);
        gui(ann).click(s, ann, 30);
        expect(gui(ann).filter == JournalGui.Filter.ALL && gui(ann).page == 0, "nor on the info tile or an empty slot");
        gui(ann).click(s, ann, JournalGui.FILTER);
        expect(gui(ann).filter == JournalGui.Filter.FOUND, "the filter button switches to found");
        gui(ann).click(s, ann, JournalGui.FILTER);
        expect(gui(ann).filter == JournalGui.Filter.MISSING, "then to not found");
        gui(ann).click(s, ann, JournalGui.FILTER);
        expect(gui(ann).filter == JournalGui.Filter.ALL, "then back to all");
        gui(ann).click(s, ann, JournalGui.CLOSE);
        expect(ann.opened == null, "close closes");

        // ---- paging
        Kit.MapSection big = new Kit.MapSection();
        for (int i = 0; i < 100; i++) {
            Env.page(big, "p" + String.format("%03d", i), "", "Page " + i, "BOOK", "g", "t", "body " + i, "hint " + i, "join");
        }
        Codex hundred = new Codex();
        hundred.load(big, "t");
        hundred.finish();
        JournalService bs = new JournalService(e.cfg, hundred, new JournalStore(null, w -> { }), () -> e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
        Kit.TestPlayer bo = e.player("Bo");
        JournalGui.open(bs, bo, 0, JournalGui.Filter.ALL);
        expect(bo.opened.getItem(44) != null && bo.opened.getItem(JournalGui.NEXT) != null && bo.opened.getItem(JournalGui.PREV) == null, "first of three pages: 45 tiles, next only");
        expect(lore(bo.opened.getItem(JournalGui.NEXT)).contains("Page 2 of 3"), "next says where it goes: " + lore(bo.opened.getItem(JournalGui.NEXT)));
        gui(bo).click(bs, bo, JournalGui.NEXT);
        expect(gui(bo).page == 1 && bo.opened.getItem(JournalGui.PREV) != null && bo.opened.getItem(JournalGui.NEXT) != null, "second page has both buttons");
        expect(lore(bo.opened.getItem(JournalGui.PREV)).contains("Page 1 of 3"), "prev says where it goes: " + lore(bo.opened.getItem(JournalGui.PREV)));
        gui(bo).click(bs, bo, JournalGui.NEXT);
        expect(gui(bo).page == 2 && bo.opened.getItem(9) != null && bo.opened.getItem(JournalGui.NEXT) == null && bo.opened.getItem(JournalGui.PREV) != null, "third page: 10 tiles, prev only");
        expect(bo.opened.getItem(10) == null, "nothing after the last tile");
        gui(bo).click(bs, bo, JournalGui.PREV);
        expect(gui(bo).page == 1, "prev goes back");
        JournalGui.open(bs, bo, 99, JournalGui.Filter.ALL);
        expect(gui(bo).page == 2, "a page past the end shows the last");
        JournalGui.open(bs, bo, -4, JournalGui.Filter.ALL);
        expect(gui(bo).page == 0, "a page before the start shows the first");
        gui(bo).click(bs, bo, JournalGui.NEXT);
        gui(bo).click(bs, bo, JournalGui.FILTER);
        expect(gui(bo).page == 0 && gui(bo).filter == JournalGui.Filter.FOUND, "changing the filter starts again at page one");
        expect(bo.opened.getItem(0) == null && lore(bo.opened.getItem(JournalGui.INFO)).contains("Found 0 of 100 pages."), "no pages found: the found filter is empty");
        JournalGui.open(bs, bo, 0, JournalGui.Filter.ALL);
        JournalGui g0 = gui(bo);
        g0.click(bs, bo, JournalGui.PREV);
        expect(gui(bo) == g0 || gui(bo).page == 0, "there is no prev button on the first page; clicking its slot does nothing");

        // ---- an empty game
        Env empty = new Env();
        Codex none = new Codex();
        none.finish();
        JournalService ns = new JournalService(empty.cfg, none, empty.store, () -> empty.installed, (p, pl) -> false, (p, en) -> { }, empty.clock::get);
        Kit.TestPlayer cy = empty.player("Cy");
        JournalGui.open(ns, cy, 0, JournalGui.Filter.ALL);
        expect(lore(cy.opened.getItem(JournalGui.INFO)).contains("Nothing to find in your game yet."), "no pages at all says so: " + lore(cy.opened.getItem(JournalGui.INFO)));
        JournalGui.open(ns, cy, 0, JournalGui.Filter.FOUND);
        expect(!lore(cy.opened.getItem(JournalGui.INFO)).contains("Nothing to find"), "only the all view says that");

        // ---- wrapping and materials
        List<String> w = JournalGui.wrap("&7short line", 44);
        expect(w.equals(List.of("&7short line")), "a short line is untouched");
        w = JournalGui.wrap("&7" + "word ".repeat(20).trim(), 44);
        expect(w.size() == 3, "100 characters wrap into three: " + w);
        boolean ok = true;
        for (String piece : w) {
            ok &= piece.startsWith("&7") && piece.length() - 2 <= 44;
        }
        expect(ok, "every piece keeps the colour and fits: " + w);
        w = JournalGui.wrap("&a&lx".repeat(1) + " " + "y".repeat(60), 44);
        expect(w.size() == 2 && w.get(0).equals("&a&lx") && w.get(1).startsWith("&a&l" + "y"), "codes are repeated on each piece; a long word gets its own line: " + w);
        w = JournalGui.wrap("a".repeat(60), 44);
        expect(w.size() == 1 && w.get(0).length() == 60, "one unbreakable word stays whole");
        expect(JournalGui.wrap("", 44).equals(List.of("")), "an empty line stays");
        expect(JournalGui.material("DIAMOND_ORE", Material.BOOK) == Material.DIAMOND_ORE, "a known item");
        expect(JournalGui.material("diamond_ore", Material.BOOK) == Material.DIAMOND_ORE, "in any case");
        expect(JournalGui.material("NOPE", Material.BOOK) == Material.BOOK, "unknown: the fallback");
        expect(JournalGui.material(null, Material.BOOK) == Material.BOOK, "null: the fallback");
        expect(JournalGui.material("AIR", Material.BOOK) == Material.BOOK, "air is no icon");
        List<String> longBody = new ArrayList<>();
        Kit.MapSection lm = new Kit.MapSection();
        Env.page(lm, "long", "", "Long", "BOOK", "g", "t", "word ".repeat(40).trim(), "h", "join");
        Codex lc = new Codex();
        lc.load(lm, "t");
        lc.finish();
        JournalService ls = new JournalService(e.cfg, lc, new JournalStore(null, w2 -> { }), () -> e.installed, (p, pl) -> false, (p, en) -> { }, e.clock::get);
        Kit.TestPlayer di = e.player("Di");
        ls.discover(di, "long", false);
        JournalGui.open(ls, di, 0, JournalGui.Filter.ALL);
        for (String line : di.opened.getItem(0).getItemMeta().getLore()) {
            longBody.add(line);
        }
        expect(longBody.size() > 4, "a long body wraps in the tile: " + longBody.size() + " lines");
        Kit.finish();
    }
}
