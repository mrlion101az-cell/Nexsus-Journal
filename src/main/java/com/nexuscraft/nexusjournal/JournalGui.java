package com.nexuscraft.nexusjournal;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The journal menu: a page of tiles, one per entry. Found pages show what they say; the rest show only the
 * group they belong to and a hint. Plain chest menu, so it works the same for Java and Bedrock players.
 */
final class JournalGui implements InventoryHolder {

    static final int SIZE = 54;
    static final int PER_PAGE = 45;
    static final int PREV = 45;
    static final int FILTER = 47;
    static final int INFO = 49;
    static final int CLOSE = 51;
    static final int NEXT = 53;
    /** Visible characters per lore line before it is wrapped. */
    static final int WRAP = 44;

    enum Filter {
        ALL("All pages"), FOUND("Found"), MISSING("Not found yet");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    final int page;
    final Filter filter;
    final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;

    private JournalGui(int page, Filter filter) {
        this.page = page;
        this.filter = filter;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    static void open(JournalService svc, Player p, int page, Filter filter) {
        List<Entry> visible = svc.visibleFor(p);
        int total = visible.size();
        int found = 0;
        List<Entry> shown = new ArrayList<>();
        for (Entry e : visible) {
            boolean has = svc.has(p.getUniqueId(), e.id());
            if (has) {
                found++;
            }
            if (filter == Filter.ALL || (filter == Filter.FOUND) == has) {
                shown.add(e);
            }
        }
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        JournalGui gui = new JournalGui(pg, filter);
        gui.inventory = Bukkit.createInventory(gui, SIZE, ColorText.translate("&6&lField Journal"));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= shown.size()) {
                break;
            }
            Entry e = shown.get(idx);
            gui.inventory.setItem(i, svc.has(p.getUniqueId(), e.id()) ? foundTile(e) : missingTile(e));
        }
        gui.put(INFO, button(Material.BOOK, "&6&lField Journal", List.of(
                "&fFound " + found + " of " + total + " pages.",
                "&7Pages open by themselves as you play. No need to hunt for them.",
                filter == Filter.ALL && total == 0 ? "&7Nothing to find in your game yet." : "&7Showing: " + filter.label)), null);
        gui.put(FILTER, button(Material.COMPASS, "&e&lShow: " + filter.label, List.of("&7Click to switch between all,", "&7found and not found yet.")), "filter");
        gui.put(CLOSE, button(Material.BARRIER, "&c&lClose", List.of()), "close");
        if (pg > 0) {
            gui.put(PREV, button(Material.ARROW, "&f&lPrevious page", List.of("&7Page " + pg + " of " + pages)), "prev");
        }
        if (pg + 1 < pages) {
            gui.put(NEXT, button(Material.ARROW, "&f&lNext page", List.of("&7Page " + (pg + 2) + " of " + pages)), "next");
        }
        p.openInventory(gui.inventory);
    }

    private static ItemStack foundTile(Entry e) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + (e.body().isBlank() ? e.tip() : e.body()));
        lore.add("");
        lore.add("&8" + e.group() + (e.hasPlugin() ? " - " + e.plugin() : ""));
        return button(material(e.icon(), Material.BOOK), "&a&l✔ " + e.title(), lore);
    }

    private static ItemStack missingTile(Entry e) {
        List<String> lore = new ArrayList<>();
        lore.add("&7" + (e.hint().isBlank() ? "Keep playing and you will come across it." : e.hint()));
        return button(Material.GRAY_DYE, "&8Not found yet - " + e.group(), lore);
    }

    private void put(int slot, ItemStack item, String action) {
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        }
    }

    /** A click on slot {@code slot} of this menu. */
    void click(JournalService svc, Player p, int slot) {
        String action = actions.get(slot);
        if (action == null) {
            return;
        }
        switch (action) {
            case "close" -> p.closeInventory();
            case "prev" -> open(svc, p, page - 1, filter);
            case "next" -> open(svc, p, page + 1, filter);
            case "filter" -> open(svc, p, 0, filter.next());
            default -> { }
        }
    }

    private static ItemStack button(Material m, String name, List<String> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(name));
        List<String> out = new ArrayList<>();
        for (String s : lore) {
            for (String piece : wrap(s, WRAP)) {
                out.add(ColorText.translate(piece));
            }
        }
        meta.setLore(out);
        item.setItemMeta(meta);
        return item;
    }

    /** Splits a long lore line at spaces; every piece keeps the line's leading colour codes. */
    static List<String> wrap(String line, int width) {
        int start = 0;
        while (start + 1 < line.length() && line.charAt(start) == '&') {
            start += 2;
        }
        String codes = line.substring(0, start);
        String text = line.substring(start);
        List<String> out = new ArrayList<>();
        if (text.length() <= width) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(codes + cur);
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        if (cur.length() > 0) {
            out.add(codes + cur);
        }
        return out;
    }

    static Material material(String name, Material fallback) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null || m == Material.AIR ? fallback : m;
    }
}
