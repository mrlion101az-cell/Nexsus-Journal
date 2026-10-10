package com.nexuscraft.nexusjournal;

import org.bukkit.configuration.ConfigurationSection;

/** Settings from config.yml, each held to something sensible. */
final class JournalConfig {

    boolean bookOnJoin = true;
    int bookDelaySeconds = 10;
    String tipFormat = "&6✦ &e{title} &7- {tip}";
    int tipGapSeconds = 4;
    boolean tipChat = false;
    boolean respectPath = true;
    String pathSource = "data";
    boolean showUnreachable = false;

    String prefix = "&6&l[Journal] &r";
    String msgFound = "&6New page in your field journal: &e{title}&6. &7/journal to read it.";
    String msgBook = "&7You have a field journal now. Right-click it, or type &f/journal&7.";
    String msgNoBook = "&7You already have your journal with you.";

    void load(ConfigurationSection c) {
        bookOnJoin = c.getBoolean("book.give-on-join", bookOnJoin);
        bookDelaySeconds = clamp(c.getInt("book.delay-seconds", bookDelaySeconds), 0, 600);
        tipFormat = c.getString("tip.format", tipFormat);
        tipGapSeconds = clamp(c.getInt("tip.gap-seconds", tipGapSeconds), 1, 60);
        tipChat = c.getBoolean("tip.also-chat", tipChat);
        respectPath = c.getBoolean("nexuspath.respect-hidden", respectPath);
        pathSource = "permission".equalsIgnoreCase(c.getString("nexuspath.source", pathSource)) ? "permission" : "data";
        showUnreachable = c.getBoolean("menu.show-unreachable", showUnreachable);
        prefix = c.getString("messages.prefix", prefix);
        msgFound = c.getString("messages.found", msgFound);
        msgBook = c.getString("messages.book", msgBook);
        msgNoBook = c.getString("messages.no-book", msgNoBook);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
