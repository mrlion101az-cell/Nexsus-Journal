package com.nexuscraft.nexusjournal;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** Wildcards and the shape of a trigger. */
public final class TriggerTest {

    public static void main(String[] args) {
        // ---- Glob
        expect(Glob.matchesAny("*", "ANYTHING"), "star matches anything");
        expect(Glob.matchesAny("*", ""), "star matches empty");
        expect(Glob.matchesAny("WHEAT", "wheat"), "exact match ignores case");
        expect(!Glob.matchesAny("WHEAT", "WHEAT_SEEDS"), "exact does not match a longer name");
        expect(!Glob.matchesAny("WHEAT", "WHEA"), "exact does not match a shorter name");
        expect(Glob.matchesAny("*_ORE", "DIAMOND_ORE"), "suffix star");
        expect(!Glob.matchesAny("*_ORE", "DIAMOND_ORE_BLOCK"), "suffix star must end there");
        expect(Glob.matchesAny("DEEPSLATE_*", "DEEPSLATE_GOLD_ORE"), "prefix star");
        expect(!Glob.matchesAny("DEEPSLATE_*", "STONE_DEEPSLATE_X"), "prefix star must start there");
        expect(Glob.matchesAny("*rail*", "POWERED_RAIL"), "star on both sides");
        expect(Glob.matchesAny("*rail*", "RAIL"), "star on both sides matches the bare word");
        expect(!Glob.matchesAny("*rail*", "STONE"), "star on both sides needs the word");
        expect(Glob.matchesAny("A*B*C", "AxxBxxC"), "two stars");
        expect(!Glob.matchesAny("A*B*C", "AxxCxxB"), "two stars keep their order");
        expect(Glob.matchesAny("A**C", "AC"), "a doubled star is one star");
        expect(Glob.matchesAny("BREAD|COOKIE", "cookie"), "either of two");
        expect(Glob.matchesAny("BREAD | COOKIE", "COOKIE"), "spaces around a choice are ignored");
        expect(!Glob.matchesAny("BREAD|COOKIE", "CAKE"), "neither of two");
        expect(!Glob.matchesAny("", "X"), "an empty pattern matches nothing");
        expect(!Glob.matchesAny("|", "X"), "empty choices match nothing");
        expect(!Glob.matchesAny("*", null), "nothing to match against");
        expect(Glob.matchesAny("*nether*|*the_end*", "world_the_end"), "world globs");
        expect(Glob.matchesAny("*nether*|*the_end*", "WORLD_NETHER"), "world globs ignore case");
        expect(!Glob.matchesAny("*nether*|*the_end*", "world"), "the overworld is neither");

        // ---- Trigger.parse
        Trigger t = Trigger.parse("break:*_ORE");
        expect(t != null && t.kind().equals("break") && t.arg().equals("*_ORE"), "kind and arg split at the colon");
        t = Trigger.parse("command");
        expect(t != null && t.kind().equals("command") && t.arg().equals("*"), "a bare kind means any");
        t = Trigger.parse("  Open : BREWING ");
        expect(t != null && t.kind().equals("open") && t.arg().equals("BREWING"), "spaces trimmed, kind lower-cased: " + t);
        t = Trigger.parse("open:");
        expect(t != null && t.arg().equals("*"), "an empty arg means any");
        t = Trigger.parse("place:a:b");
        expect(t != null && t.arg().equals("a:b"), "only the first colon splits");
        expect(Trigger.parse(null) == null, "null is not a trigger");
        expect(Trigger.parse("   ") == null, "blank is not a trigger");
        expect(Trigger.parse(":x") == null, "no kind is not a trigger");
        expect(new Trigger("break", "*").known() && new Trigger("api", "*").known() && new Trigger("join", "*").known(), "known kinds");
        expect(!new Trigger("dance", "*").known(), "unknown kind");
        expect(Trigger.KINDS.size() == 18, "eighteen kinds: " + Trigger.KINDS.size());

        // ---- matches
        Trigger lode = new Trigger("break", "*_ORE|ANCIENT_DEBRIS");
        expect(lode.matches("break", "IRON_ORE"), "matches the kind and the thing");
        expect(lode.matches("break", "ancient_debris"), "second choice, other case");
        expect(!lode.matches("place", "IRON_ORE"), "wrong kind");
        expect(!lode.matches("break", "STONE"), "wrong thing");
        expect(!new Trigger("api", "*").matches("api", "x"), "api matches nothing, not even itself");
        expect(new Trigger("join", "*").matches("join", "join"), "join matches");
        expect(lode.toString().equals("break:*_ORE|ANCIENT_DEBRIS"), "toString round-trips: " + lode);

        // ---- Entry
        Entry open = new Entry("x", "NexusX", "X", "BOOK", "g", "tip", "body", "hint", java.util.List.of(new Trigger("api", "*"), new Trigger("break", "*")));
        Entry shut = new Entry("y", "", "Y", "BOOK", "g", "tip", "body", "hint", java.util.List.of(new Trigger("api", "*")));
        expect(open.reachable(), "a page with a real trigger is reachable");
        expect(!shut.reachable(), "an api-only page is not");
        expect(open.hasPlugin() && !shut.hasPlugin(), "blank plugin means nobody's page");
        expect(!new Entry("z", null, "Z", "BOOK", "g", "t", "b", "h", java.util.List.of()).hasPlugin(), "null plugin means nobody's page");
        expect(!new Entry("z", "  ", "Z", "BOOK", "g", "t", "b", "h", java.util.List.of()).hasPlugin(), "spaces mean nobody's page");
        Kit.finish();
    }
}
