package com.nexuscraft.nexusjournal;

import java.util.Locale;

/** Tiny case-insensitive wildcard match: {@code *} stands for any run of characters; {@code a|b} means either. */
final class Glob {

    private Glob() {
    }

    static boolean matchesAny(String patterns, String text) {
        if (text == null) {
            return false;
        }
        String t = text.toLowerCase(Locale.ROOT);
        for (String p : patterns.split("\\|")) {
            String q = p.trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && match(q, 0, t, 0)) {
                return true;
            }
        }
        return false;
    }

    private static boolean match(String p, int pi, String t, int ti) {
        while (pi < p.length()) {
            char c = p.charAt(pi);
            if (c == '*') {
                while (pi < p.length() && p.charAt(pi) == '*') {
                    pi++;
                }
                if (pi == p.length()) {
                    return true;
                }
                for (int k = ti; k <= t.length(); k++) {
                    if (match(p, pi, t, k)) {
                        return true;
                    }
                }
                return false;
            }
            if (ti >= t.length() || t.charAt(ti) != c) {
                return false;
            }
            pi++;
            ti++;
        }
        return ti == t.length();
    }
}
