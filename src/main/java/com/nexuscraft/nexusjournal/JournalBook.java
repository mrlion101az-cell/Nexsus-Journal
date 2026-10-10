package com.nexuscraft.nexusjournal;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The carryable journal: a book tagged so right-clicking it opens the menu. */
final class JournalBook {

    private final NamespacedKey key;

    JournalBook(NamespacedKey key) {
        this.key = key;
    }

    ItemStack create() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate("&6&lField Journal"));
        List<String> lore = new ArrayList<>();
        lore.add(ColorText.translate("&7Right-click to read it."));
        lore.add(ColorText.translate("&7Pages fill in as you play."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "1");
        item.setItemMeta(meta);
        return item;
    }

    boolean isBook(ItemStack item) {
        if (item == null || item.getType() != Material.BOOK) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /** Whether the player is carrying one already (anywhere in the inventory). */
    boolean carries(Player p) {
        for (ItemStack s : p.getInventory().getContents()) {
            if (isBook(s)) {
                return true;
            }
        }
        return false;
    }

    /** Puts a journal in the player's inventory. False when there was no room. */
    boolean give(Player p) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(create());
        return left.isEmpty();
    }
}
