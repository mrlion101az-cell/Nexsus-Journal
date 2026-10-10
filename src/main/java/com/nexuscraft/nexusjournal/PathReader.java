package com.nexuscraft.nexusjournal;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Asks "did this player hide that system in NexusPath?" without depending on NexusPath. NexusPath publishes
 * the answer two ways: in the player's persistent data ({@code nexuspath:hidden}, comma separated plugin names)
 * and as the permission {@code nexuspath.hidden.<plugin>}. The data is the default because a wildcard permission
 * ({@code *}) makes the permission look true for every plugin and would hide every page from an admin.
 */
final class PathReader {

    private final NamespacedKey key;
    private final boolean usePermission;

    /** @param key the persistent-data key nexuspath:hidden, or null (then nothing is ever hidden in data mode) */
    PathReader(NamespacedKey key, boolean usePermission) {
        this.key = key;
        this.usePermission = usePermission;
    }

    boolean hidden(Player p, String plugin) {
        String name = plugin.toLowerCase(Locale.ROOT);
        if (usePermission) {
            return p.hasPermission("nexuspath.hidden." + name);
        }
        if (key == null) {
            return false;
        }
        return listed(p.getPersistentDataContainer().get(key, PersistentDataType.STRING), name);
    }

    /** Is {@code name} one of the comma separated entries (any case, spaces ignored)? */
    static boolean listed(String csv, String name) {
        if (csv == null || csv.isEmpty()) {
            return false;
        }
        for (String s : csv.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
