package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /journal: open it, get a new book, see how far you are; for staff, give, take back and check pages. */
final class JournalCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("open", "book", "stats");
    private static final List<String> ADMIN_SUBS = List.of("discover", "grant", "revoke", "show", "reload", "check");

    private final JournalService service;
    private final JournalBook book;
    private final Runnable reload;

    JournalCommand(JournalService service, JournalBook book, Runnable reload) {
        this.service = service;
        this.book = book;
        this.reload = reload;
    }

    private static boolean admin(CommandSender s) {
        return s.hasPermission("nexusjournal.admin");
    }

    private void say(CommandSender to, String text) {
        to.sendMessage(ColorText.translate(service.cfg().prefix + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "", "open" -> open(sender);
            case "book" -> giveBook(sender);
            case "stats" -> stats(sender);
            case "discover", "grant", "revoke", "show", "reload", "check" -> {
                if (!admin(sender)) {
                    say(sender, "&cOnly staff can do that.");
                } else {
                    adminCommand(sender, sub, args);
                }
            }
            default -> say(sender, "&7Try &f/journal&7, &f/journal book&7 or &f/journal stats&7.");
        }
        return true;
    }

    // ------------------------------------------------------------------ players

    private void open(CommandSender sender) {
        if (sender instanceof Player p) {
            JournalGui.open(service, p, 0, JournalGui.Filter.ALL);
        } else {
            say(sender, "&7Players only.");
        }
    }

    private void giveBook(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            say(sender, "&7Players only.");
        } else if (book.carries(p)) {
            say(p, service.cfg().msgNoBook);
        } else if (book.give(p)) {
            say(p, service.cfg().msgBook);
        } else {
            say(p, "&cYour inventory is full. Make room and try again.");
        }
    }

    private void stats(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            say(sender, "&7Players only.");
            return;
        }
        List<Entry> visible = service.visibleFor(p);
        int found = 0;
        for (Entry e : visible) {
            if (service.has(p.getUniqueId(), e.id())) {
                found++;
            }
        }
        say(p, "&fFound &e" + found + "&f of &e" + visible.size() + "&f pages in your game. &7/journal to read them.");
    }

    // ------------------------------------------------------------------ staff

    private void adminCommand(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "reload" -> {
                reload.run();
                say(sender, "&aReloaded: " + service.codex().size() + " pages.");
            }
            case "check" -> check(sender);
            default -> {
                if (args.length < 2) {
                    say(sender, "&7Usage: /journal " + sub + " <player>" + (sub.equals("show") ? "" : " <page id>"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    say(sender, "&c" + args[1] + " is not online.");
                    return;
                }
                switch (sub) {
                    case "show" -> show(sender, target);
                    case "discover" -> discover(sender, target, args, false);
                    case "grant" -> discover(sender, target, args, true);
                    default -> revoke(sender, target, args);
                }
            }
        }
    }

    private void discover(CommandSender sender, Player target, String[] args, boolean force) {
        if (args.length < 3) {
            say(sender, "&7Usage: /journal " + (force ? "grant" : "discover") + " <player> <page id" + (force ? "|all" : "") + ">");
            return;
        }
        if (force && args[2].equalsIgnoreCase("all")) {
            say(sender, "&aOpened " + service.grantAll(target) + " pages for " + target.getName() + ".");
            return;
        }
        switch (service.discover(target, args[2], force)) {
            case FOUND -> say(sender, "&aOpened '" + args[2] + "' for " + target.getName() + ".");
            case ALREADY -> say(sender, "&7" + target.getName() + " already has '" + args[2] + "'.");
            case NOT_ELIGIBLE -> say(sender, "&7'" + args[2] + "' is not in " + target.getName() + "'s game (the plugin is missing or they hid it). &f/journal grant&7 forces it.");
            default -> say(sender, "&cThere is no page '" + args[2] + "'. &f/journal check&7 lists them.");
        }
    }

    private void revoke(CommandSender sender, Player target, String[] args) {
        if (args.length < 3) {
            say(sender, "&7Usage: /journal revoke <player> <page id|all>");
            return;
        }
        boolean all = args[2].equalsIgnoreCase("all");
        int n = service.revoke(target.getUniqueId(), args[2], all);
        say(sender, n == 0 ? "&7" + target.getName() + " had nothing to take back." : "&aTook back " + n + " page" + (n == 1 ? "" : "s") + " from " + target.getName() + ".");
    }

    private void show(CommandSender sender, Player target) {
        Progress pr = service.store().peek(target.getUniqueId());
        int n = pr == null ? 0 : pr.count();
        say(sender, "&f" + target.getName() + " has &e" + n + "&f pages" + (n == 0 ? "." : ": &7" + String.join(", ", pr.ids())));
    }

    private void check(CommandSender sender) {
        Codex codex = service.codex();
        int missing = 0;
        List<String> api = new ArrayList<>();
        for (Entry e : codex.entries()) {
            if (e.hasPlugin() && !service.isInstalled(e.plugin())) {
                missing++;
            }
            if (!e.reachable()) {
                api.add(e.id());
            }
        }
        say(sender, "&f" + codex.size() + " pages; " + missing + " are about plugins that are not running here.");
        say(sender, api.isEmpty() ? "&aEvery page can open from something a player does." : "&e" + api.size() + " pages open only when a plugin or staff runs &f/journal discover&e: &7" + String.join(", ", api));
        for (String w : codex.warnings()) {
            say(sender, "&e" + w);
        }
    }

    // ------------------------------------------------------------------ tab

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            addMatching(out, PLAYER_SUBS, args[0]);
            if (admin(sender)) {
                addMatching(out, ADMIN_SUBS, args[0]);
            }
        } else if (admin(sender) && args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("check")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                addMatching(out, List.of(p.getName()), args[1]);
            }
        } else if (admin(sender) && args.length == 3 && List.of("discover", "grant", "revoke").contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> ids = new ArrayList<>();
            for (Entry e : service.codex().entries()) {
                ids.add(e.id());
            }
            if (!args[0].equalsIgnoreCase("discover")) {
                ids.add("all");
            }
            addMatching(out, ids, args[2]);
        }
        return out;
    }

    private static void addMatching(List<String> out, List<String> from, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        for (String s : from) {
            if (s.toLowerCase(Locale.ROOT).startsWith(t)) {
                out.add(s);
            }
        }
    }
}
