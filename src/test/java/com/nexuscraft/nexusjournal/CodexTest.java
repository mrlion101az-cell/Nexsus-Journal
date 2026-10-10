package com.nexuscraft.nexusjournal;

import java.util.List;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** Loading, merging and checking the codex. */
public final class CodexTest {

    static boolean warned(Codex c, String fragment) {
        for (String w : c.warnings()) {
            if (w.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        Codex c = new Codex();
        c.load(Env.sample(), "test");
        c.finish();
        expect(c.size() == 10, "ten sample pages: " + c.size());
        expect(c.entries().iterator().next().id().equals("welcome"), "file order is kept");
        Entry lode = c.get("LODE");
        expect(lode != null && lode.title().equals("Mining") && lode.plugin().equals("NexusLode") && lode.icon().equals("DIAMOND_ORE"), "fields read: " + lode);
        expect(lode.group().equals("Make") && lode.tip().equals("Mining is a skill") && lode.body().equals("Level up mining.") && lode.hint().equals("Mine ore."), "text fields read");
        expect(lode.triggers().size() == 2 && lode.triggers().get(0).kind().equals("command"), "triggers read in order");
        expect(c.get(" Lode ") == lode, "ids are looked up in lower case, trimmed");
        expect(c.get("nope") == null && c.get(null) == null, "unknown id");
        expect(Codex.key(" AbC ").equals("abc") && Codex.key(null).isEmpty(), "key()");
        expect(c.get("welcome").plugin().isEmpty(), "a page can belong to nobody");
        expect(c.warnings().isEmpty(), "the sample loads without warnings: " + c.warnings());

        // lookups
        expect(c.withKind("break").size() == 2, "two pages break something: " + c.withKind("break").size());
        expect(c.withKind("open").size() == 2 && c.withKind("join").size() == 1, "open and join indexes");
        expect(c.withKind("dance").isEmpty(), "no such kind");
        expect(c.withKind("api").size() == 1 && c.withKind("api").get(0).id().equals("horror"), "api pages indexed too");
        expect(c.forPlugin("nexuslode").size() == 1 && c.forPlugin("NexusNope").isEmpty(), "pages of a plugin, any case");
        expect(c.apiOnly().size() == 1 && c.apiOnly().get(0).id().equals("horror"), "api-only pages: " + c.apiOnly());

        // defaults
        Kit.MapSection m = new Kit.MapSection();
        m.put("entries.bare.title", "Bare");
        Codex d = new Codex();
        d.load(m, "t");
        d.finish();
        Entry bare = d.get("bare");
        expect(bare != null && bare.icon().equals("BOOK") && bare.group().equals("Other") && bare.tip().isEmpty(), "defaults: " + bare);
        expect(bare.triggers().size() == 1 && bare.triggers().get(0).kind().equals("api"), "no triggers means api only");
        expect(!bare.reachable(), "so it is not reachable");
        expect(warned(d, "no tip"), "a page without a tip warns");

        // errors
        m = new Kit.MapSection();
        m.put("entries.notitle.plugin", "NexusX");
        m.put("entries.badkind.title", "B").put("entries.badkind.triggers", List.of("dance:now", "break:STONE"));
        m.put("entries.empty.title", "E").put("entries.empty.triggers", List.of("", "  ", "join"));
        m.put("entries.dup.title", "D").put("entries.dup.triggers", List.of("break:STONE", "BREAK:STONE", "break:STONE"));
        d = new Codex();
        d.load(m, "t.yml");
        d.finish();
        expect(d.get("notitle") == null && warned(d, "'notitle' has no title"), "a page without a title is skipped, with a warning");
        expect(warned(d, "'dance' is not a kind of trigger"), "an unknown kind warns");
        expect(d.get("badkind").triggers().size() == 1 && d.get("badkind").triggers().get(0).kind().equals("break"), "and is dropped, the rest kept");
        expect(warned(d, "'empty' has an empty trigger"), "an empty trigger warns");
        expect(d.get("empty").triggers().size() == 1, "and is dropped: " + d.get("empty").triggers());
        expect(d.get("dup").triggers().size() == 1, "the same trigger twice is kept once: " + d.get("dup").triggers());
        expect(warned(d, "t.yml"), "warnings name the file");

        // a section that is not a section
        m = new Kit.MapSection();
        m.put("entries.oops", "text");
        d = new Codex();
        d.load(m, "x.yml");
        expect(d.size() == 0 && warned(d, "'oops' is not a section"), "a non-section entry is skipped");
        d.load(null, "null");
        expect(d.size() == 0, "a null root is ignored");

        // custom file merges on top
        m = new Kit.MapSection();
        m.put("entries.lode.tip", "New tip");
        m.put("entries.lode.add-triggers", List.of("place:STONE", "break:*_ORE|ANCIENT_DEBRIS"));
        m.put("entries.alchemy.triggers", List.of("open:ENCHANTING"));
        m.put("entries.mine.title", "My page").put("entries.mine.plugin", "NexusLode").put("entries.mine.triggers", List.of("kill:*"));
        m.put("hidden-entries", List.of("horror", "angler", "ghost-town"));
        Codex e = new Codex();
        e.load(Env.sample(), "builtin");
        e.load(m, "custom");
        e.finish();
        Entry l2 = e.get("lode");
        expect(l2.tip().equals("New tip") && l2.title().equals("Mining") && l2.body().equals("Level up mining."), "a custom page changes only the fields it names");
        expect(l2.triggers().size() == 3 && l2.triggers().get(2).kind().equals("place"), "add-triggers appends (and skips repeats): " + l2.triggers());
        expect(e.get("alchemy").triggers().size() == 1 && e.get("alchemy").triggers().get(0).arg().equals("ENCHANTING"), "triggers replaces the list");
        expect(e.get("mine") != null && e.get("mine").plugin().equals("NexusLode"), "a custom page can be new");
        expect(e.get("horror") == null && e.get("angler") == null, "hidden-entries removes pages");
        expect(warned(e, "'ghost-town', which is not a page"), "hiding a page that does not exist warns");
        expect(e.size() == 9, "10 - 2 hidden + 1 new: " + e.size());
        expect(e.withKind("kill").size() == 1 && e.withKind("api").isEmpty(), "finish() rebuilds the indexes");

        // a page added by a custom file that is only a tweak to nothing
        m = new Kit.MapSection();
        m.put("entries.ghost.tip", "no title anywhere");
        e = new Codex();
        e.load(m, "custom");
        expect(e.size() == 0 && warned(e, "'ghost' has no title"), "tweaking a page that does not exist needs a title");

        // clear
        e.clear();
        expect(e.size() == 0 && e.warnings().isEmpty() && e.withKind("break").isEmpty(), "clear empties everything");
        Kit.finish();
    }
}
