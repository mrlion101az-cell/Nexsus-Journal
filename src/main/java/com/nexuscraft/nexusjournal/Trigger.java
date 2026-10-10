package com.nexuscraft.nexusjournal;

import java.util.Locale;
import java.util.Set;

/**
 * One way to meet a system: a kind of thing the player does ({@code break}, {@code open}, ...) and a
 * wildcard for what it was done to ({@code open:BREWING_STAND}, {@code break:*_ORE|ANCIENT_DEBRIS}).
 * Kind {@code command} matches the label of a command owned by the entry's own plugin; kind {@code api}
 * matches nothing: the entry is opened only by a plugin or staff calling /journal discover.
 */
record Trigger(String kind, String arg) {

    static final Set<String> KINDS = Set.of("command", "break", "place", "craft", "use", "interact", "consume",
            "world", "breed", "kill", "death", "enchant", "fish", "sleep", "open", "bucket", "join", "api");

    /** "kind:arg" or just "kind" (any). Returns null when it is not even shaped like one. */
    static Trigger parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = text.trim();
        int colon = s.indexOf(':');
        String kind = (colon < 0 ? s : s.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        String arg = colon < 0 ? "*" : s.substring(colon + 1).trim();
        if (kind.isEmpty()) {
            return null;
        }
        return new Trigger(kind, arg.isEmpty() ? "*" : arg);
    }

    boolean known() {
        return KINDS.contains(kind);
    }

    boolean matches(String happenedKind, String value) {
        return !kind.equals("api") && kind.equals(happenedKind) && Glob.matchesAny(arg, value);
    }

    @Override
    public String toString() {
        return kind + ":" + arg;
    }
}
