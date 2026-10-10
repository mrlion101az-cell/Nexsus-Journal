package com.nexuscraft.nexusjournal;

import java.util.List;

/**
 * One page of the journal. {@code plugin} is the Nexus plugin it is about (blank for a page that belongs to
 * nobody, like a welcome page); {@code tip} is the short action-bar line shown when it is found, {@code body}
 * what the page says afterwards, {@code hint} what the closed page says before.
 */
record Entry(String id, String plugin, String title, String icon, String group, String tip, String body, String hint,
             List<Trigger> triggers) {

    boolean hasPlugin() {
        return plugin != null && !plugin.isBlank();
    }

    /** True when something other than a person typing /journal discover can open it. */
    boolean reachable() {
        for (Trigger t : triggers) {
            if (!t.kind().equals("api")) {
                return true;
            }
        }
        return false;
    }
}
