package com.nexuscraft.nexusjournal;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** Reading what NexusPath says a player hid. */
public final class PathReaderTest {

    public static void main(String[] args) {
        // ---- the list
        expect(PathReader.listed("nexuslode,nexusrime", "nexuslode"), "first of a list");
        expect(PathReader.listed("nexuslode,nexusrime", "nexusrime"), "last of a list");
        expect(PathReader.listed("NexusLode , NexusRime", "nexusrime"), "any case, spaces ignored");
        expect(!PathReader.listed("nexuslodex", "nexuslode"), "a longer name is not the same plugin");
        expect(!PathReader.listed("nexus", "nexuslode"), "a shorter name is not the same plugin");
        expect(!PathReader.listed("", "nexuslode") && !PathReader.listed(null, "nexuslode"), "nothing listed");
        expect(PathReader.listed("nexuslode", "NEXUSLODE"), "the name asked for is compared in any case too");

        // ---- data mode
        NamespacedKey key = NamespacedKey.fromString("nexuspath:hidden");
        expect(key != null && key.toString().equals("nexuspath:hidden"), "the key is nexuspath:hidden");
        PathReader data = new PathReader(key, false);
        Kit.TestPlayer ann = new Kit.TestPlayer("Ann", 0, 0);
        expect(!data.hidden(ann, "NexusLode"), "no data: nothing hidden");
        ann.getPersistentDataContainer().set(key, PersistentDataType.STRING, "nexuslode,nexusrime");
        expect(data.hidden(ann, "NexusLode") && data.hidden(ann, "NEXUSRIME"), "listed plugins are hidden, any case");
        expect(!data.hidden(ann, "NexusFarming"), "unlisted plugins are not");
        ann.getPersistentDataContainer().set(key, PersistentDataType.STRING, "");
        expect(!data.hidden(ann, "NexusLode"), "an empty list hides nothing");
        Kit.TestPlayer root = new Kit.TestPlayer("Root", 0, 0);
        root.perms.add("nexuspath.hidden.nexuslode");
        expect(!data.hidden(root, "NexusLode"), "in data mode a permission means nothing (wildcard admins)");
        expect(!new PathReader(null, false).hidden(ann, "NexusLode"), "no key (a malformed one) hides nothing");

        // ---- permission mode
        PathReader perm = new PathReader(key, true);
        expect(perm.hidden(root, "NexusLode") && perm.hidden(root, "nexuslode"), "in permission mode the node decides, in any case");
        expect(!perm.hidden(root, "NexusRime"), "other plugins are not hidden");
        ann.getPersistentDataContainer().set(key, PersistentDataType.STRING, "nexuslode");
        expect(!perm.hidden(ann, "NexusLode"), "and the data means nothing there");
        expect(NamespacedKey.fromString("nocolon") == null && NamespacedKey.fromString(null) == null && NamespacedKey.fromString(":x") == null, "stub: fromString rejects malformed text");
        Kit.finish();
    }
}
