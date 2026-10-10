package com.nexuscraft.nexusjournal;

import org.bukkit.entity.Player;

import java.util.List;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** The rules: who can open what, what opens it, staff powers and the plugin-facing API. */
public final class ServiceTest {

    static List<String> ids(List<Entry> list) {
        return list.stream().map(Entry::id).toList();
    }

    public static void main(String[] args) {
        Env e = new Env();
        Kit.TestPlayer ann = e.player("Ann");
        Kit.TestPlayer bob = e.player("Bob");
        JournalService s = e.service;

        // ---- eligibility
        Entry lode = e.codex.get("lode");
        expect(s.eligible(ann, lode), "a running plugin's page is open to a player");
        expect(!s.eligible(ann, e.codex.get("ghost")), "a plugin that is not running has no page for anyone");
        expect(s.eligible(ann, e.codex.get("welcome")), "a page about nobody is always open");
        e.hidden.add("Ann:nexuslode");
        expect(!s.eligible(ann, lode), "a system the player hid in NexusPath has no page");
        expect(s.eligible(bob, lode), "and it is only hidden for that player");
        e.hidden.clear();
        expect(s.isInstalled("nexuslode") && s.isInstalled("NEXUSLODE") && !s.isInstalled("NexusGhost") && !s.isInstalled(null), "isInstalled ignores case");
        e.installed.add("NexusGhost");
        expect(!s.isInstalled("NexusGhost"), "the installed set is a snapshot until refreshed");
        s.refreshInstalled();
        expect(s.isInstalled("NexusGhost") && s.eligible(ann, e.codex.get("ghost")), "refreshInstalled sees a plugin that arrived");
        e.installed.remove("NexusGhost");
        s.refreshInstalled();
        expect(!s.isInstalled("NexusGhost"), "and one that left");

        // ---- discover
        expect(s.discover(ann, "lode", false) == JournalService.Result.FOUND, "first time: found");
        expect(e.opened.equals(List.of("Ann:lode")), "the tip callback ran once: " + e.opened);
        expect(s.discover(ann, "LODE", false) == JournalService.Result.ALREADY, "second time (any case): already");
        expect(e.opened.size() == 1, "no second callback");
        expect(s.discover(ann, "nope", false) == JournalService.Result.UNKNOWN, "no such page");
        expect(s.discover(ann, "ghost", false) == JournalService.Result.NOT_ELIGIBLE, "plugin not running");
        expect(!e.has(ann, "ghost"), "and nothing was recorded");
        expect(s.discover(ann, "ghost", true) == JournalService.Result.FOUND, "staff can force it");
        expect(e.has(ann, "ghost") && e.opened.contains("Ann:ghost"), "forced page is recorded and announced");
        e.hidden.add("Bob:nexusalchemy");
        expect(s.discover(bob, "alchemy", false) == JournalService.Result.NOT_ELIGIBLE, "hidden by the player");
        expect(s.discover(bob, "alchemy", true) == JournalService.Result.FOUND, "but staff can still force it");
        e.hidden.clear();
        expect(s.discover(bob, "nope", true) == JournalService.Result.UNKNOWN, "forcing a page that does not exist is still unknown");
        expect(e.store.dirty(), "finding something marks the store for saving");
        Long when = e.store.peek(ann.getUniqueId()).when("lode");
        expect(when != null && when == 1_000_000L, "the time found is the clock's: " + when);

        // ---- happened (events)
        Env f = new Env();
        Kit.TestPlayer cy = f.player("Cy");
        expect(f.service.happened(cy, "break", "STONE") == 0, "nothing matches stone");
        expect(f.opened.isEmpty(), "so nothing opened");
        expect(f.service.happened(cy, "break", "DIAMOND_ORE") == 1 && f.has(cy, "lode"), "mining ore opens the mining page");
        expect(f.service.happened(cy, "break", "IRON_ORE") == 0, "a second ore opens nothing new");
        expect(f.service.happened(cy, "dance", "x") == 0, "a kind no page uses is cheap and empty");
        expect(f.service.happened(cy, "break", "WHEAT") == 1 && f.has(cy, "farming"), "wheat opens farming");
        expect(f.service.happened(cy, "use", "GOLDEN_HOE") == 0, "farming is already open; its other trigger adds nothing");
        expect(f.service.happened(cy, "open", "FURNACE") == 1 && f.has(cy, "smelting"), "a furnace opens smelting");
        expect(f.service.happened(cy, "open", "BREWING") == 1 && f.has(cy, "alchemy"), "a brewing stand opens alchemy");
        expect(f.service.happened(cy, "place", "POWERED_RAIL") == 1 && f.has(cy, "rails"), "any rail opens rails");
        expect(f.service.happened(cy, "join", "join") == 1 && f.has(cy, "welcome"), "joining opens the welcome page");
        expect(f.service.happened(cy, "api", "x") == 0 && !f.has(cy, "horror"), "an api page is never opened by an event");

        // the player hid a system: its page stays shut
        Kit.TestPlayer di = f.player("Di");
        f.hidden.add("Di:nexusfarming");
        expect(f.service.happened(di, "break", "WHEAT") == 0 && !f.has(di, "farming"), "events do not open pages of a hidden system");
        f.hidden.clear();
        expect(f.service.happened(di, "break", "WHEAT") == 1, "once unhidden the same thing opens it");

        // one thing can open several pages
        Env g = new Env();
        Kit.TestPlayer ed = g.player("Ed");
        Kit.MapSection extra = Env.sample();
        Env.page(extra, "second", "NexusLode", "Second", "BOOK", "Make", "also", "b", "h", "break:DIAMOND_ORE");
        Codex two = new Codex();
        two.load(extra, "t");
        two.finish();
        JournalService multi = new JournalService(g.cfg, two, g.store, () -> g.installed, (p, pl) -> false, (p, en) -> g.opened.add(en.id()), g.clock::get);
        expect(multi.happened(ed, "break", "DIAMOND_ORE") == 2 && g.opened.equals(List.of("lode", "second")), "one thing can open two pages, in codex order: " + g.opened);

        // ---- commands
        Env h = new Env();
        Kit.TestPlayer flo = h.player("Flo");
        expect(h.service.commandTyped(flo, "rime", null) == 0, "a command nobody owns opens nothing");
        expect(h.service.commandTyped(flo, "rime", "NexusFarming") == 0 && !h.has(flo, "rime"), "only the owning plugin's pages open");
        expect(h.service.commandTyped(flo, "rime", "nexusrime") == 1 && h.has(flo, "rime"), "the owner (any case) opens its page");
        expect(h.service.commandTyped(flo, "rime", "NexusRime") == 0, "once");
        expect(h.service.commandTyped(flo, "lode", "NexusLode") == 1 && h.has(flo, "lode"), "a plugin with command and event triggers opens on a command too");
        expect(h.service.commandTyped(flo, "anything", "NexusAlchemy") == 0 && !h.has(flo, "alchemy"), "a page without a command trigger ignores commands");
        expect(h.service.commandTyped(flo, "anything", "NexusHorror") == 0, "api pages ignore commands");
        Kit.MapSection one = new Kit.MapSection();
        Env.page(one, "cmd", "NexusRime", "One", "BOOK", "g", "t", "b", "h", "command:rime|winter*");
        Codex cc = new Codex();
        cc.load(one, "t");
        cc.finish();
        JournalService picky = new JournalService(h.cfg, cc, new JournalStore(null, w -> { }), () -> h.installed, (p, pl) -> false, (p, en) -> { }, h.clock::get);
        expect(picky.commandTyped(flo, "frost", "NexusRime") == 0, "a command trigger can name labels: wrong label");
        expect(picky.commandTyped(flo, "winterfell", "NexusRime") == 1, "a command trigger can name labels: wildcard label");
        h.hidden.add("Flo:nexusangler");
        expect(h.service.commandTyped(flo, "angler", "NexusAngler") == 0, "no command trigger on angler; nothing");
        Env hh = new Env();
        Kit.TestPlayer gus = hh.player("Gus");
        hh.hidden.add("Gus:nexusrime");
        expect(hh.service.commandTyped(gus, "rime", "NexusRime") == 0 && !hh.has(gus, "rime"), "typing a hidden system's command opens nothing");

        // ---- what the menu lists
        Env v = new Env();
        Kit.TestPlayer ivy = v.player("Ivy");
        expect(ids(v.service.visibleFor(ivy)).equals(List.of("welcome", "lode", "alchemy", "rime", "farming", "angler", "smelting", "rails")),
                "unfound pages she could earn, in codex order, without api-only or missing-plugin pages: " + ids(v.service.visibleFor(ivy)));
        v.hidden.add("Ivy:nexusrime");
        expect(!ids(v.service.visibleFor(ivy)).contains("rime"), "a hidden system's unfound page is not listed");
        v.hidden.clear();
        v.service.discover(ivy, "horror", false);
        expect(ids(v.service.visibleFor(ivy)).contains("horror"), "an api page appears once found");
        v.service.discover(ivy, "rime", false);
        v.hidden.add("Ivy:nexusrime");
        v.installed.remove("NexusLode");
        v.service.refreshInstalled();
        expect(ids(v.service.visibleFor(ivy)).contains("rime"), "a found page stays even when the system is hidden later");
        v.service.discover(ivy, "lode", true);
        expect(ids(v.service.visibleFor(ivy)).contains("lode"), "and when the plugin is gone");
        expect(ids(v.service.visibleFor(ivy)).contains("alchemy"), "other unfound pages are unaffected");
        Kit.TestPlayer zed = v.player("Zed");
        expect(!ids(v.service.visibleFor(zed)).contains("horror"), "an api-only page stays out of sight by default");
        v.cfg.showUnreachable = true;
        expect(ids(v.service.visibleFor(zed)).contains("horror"), "show-unreachable lists the closed tile of an unreachable page");
        expect(!ids(v.service.visibleFor(zed)).contains("ghost"), "but still not a page about a plugin that is not running");
        v.cfg.showUnreachable = false;
        expect(v.service.foundCount(ivy.getUniqueId()) == 3, "foundCount: " + v.service.foundCount(ivy.getUniqueId()));
        expect(v.service.foundCount(java.util.UUID.randomUUID()) == 0, "foundCount for a stranger is 0");

        // ---- staff
        Env w = new Env();
        Kit.TestPlayer jay = w.player("Jay");
        w.service.discover(jay, "lode", false);
        w.service.discover(jay, "rime", false);
        w.service.discover(jay, "farming", false);
        expect(w.service.revoke(jay.getUniqueId(), "RIME", false) == 1 && !w.has(jay, "rime"), "revoke one page (any case)");
        expect(w.service.revoke(jay.getUniqueId(), "rime", false) == 0, "revoking it again does nothing");
        expect(w.service.revoke(java.util.UUID.randomUUID(), "x", false) == 0 && w.service.revoke(java.util.UUID.randomUUID(), "x", true) == 0, "revoking from a stranger does nothing");
        expect(w.service.discover(jay, "rime", false) == JournalService.Result.FOUND, "a revoked page can be found again");
        expect(w.service.revoke(jay.getUniqueId(), "ignored", true) == 3 && w.service.foundCount(jay.getUniqueId()) == 0, "revoke all");
        w.store.flush();
        expect(w.service.grantAll(jay) == 10, "grantAll opens every page: " + w.service.foundCount(jay.getUniqueId()));
        expect(w.service.grantAll(jay) == 0, "and only once");
        expect(w.opened.size() >= 10, "each announced");

        // ---- the API other plugins use
        Env x = new Env();
        Kit.TestPlayer kit = x.player("Kit");
        JournalApi api = x.service;
        expect(!api.found(kit, "lode"), "api: not found yet");
        expect(api.discover(kit, "lode"), "api: opens a page");
        expect(api.found(kit, "lode"), "api: found now");
        expect(!api.discover(kit, "lode"), "api: false when already open");
        expect(!api.discover(kit, "nope"), "api: false for an unknown page");
        x.hidden.add("Kit:nexusrime");
        expect(!api.discover(kit, "rime"), "api: false for a hidden system");
        expect(api.discover(kit, "horror"), "api: opens an api-only page");
        expect(!api.discover(null, "lode") && !api.found(null, "lode"), "api: null player is harmless");
        Kit.finish();
    }
}
