package com.nexuscraft.nexusjournal;

import org.bukkit.entity.Player;

/**
 * For other plugins that know something the server's events cannot say ("the player just forged their first
 * blade"). Get it with {@code Bukkit.getServicesManager().load(JournalApi.class)}, or, with no dependency at
 * all, run the console command {@code journal discover <player> <page id>}.
 */
public interface JournalApi {

    /**
     * Opens a journal page for the player. Returns false when there is no such page, the player already has it,
     * or the page is about a system the player hid with NexusPath.
     */
    boolean discover(Player player, String pageId);

    /** Whether the player has this page. */
    boolean found(Player player, String pageId);
}
