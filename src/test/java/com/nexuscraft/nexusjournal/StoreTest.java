package com.nexuscraft.nexusjournal;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** What a player has found, kept on disk. */
public final class StoreTest {

    public static void main(String[] args) throws Exception {
        // ---- Progress
        Progress p = new Progress();
        expect(p.isBlank() && p.count() == 0 && !p.has("a") && p.when("a") == null, "a new record is blank");
        expect(p.add("a", 5L) && !p.add("a", 9L), "add is true once");
        expect(p.when("a") == 5L, "the first time is kept");
        p.add("b", 6L);
        expect(p.count() == 2 && p.ids().iterator().next().equals("a"), "found order is kept");
        expect(!p.isBlank(), "not blank with pages");
        expect(p.remove("a") && !p.remove("a") && !p.has("a"), "remove");
        expect(p.clear() == 1 && p.count() == 0 && p.isBlank(), "clear returns how many");
        p.bookGiven = true;
        expect(!p.isBlank(), "having been given the book is also something to keep");
        try {
            p.ids().add("x");
            expect(false, "ids() is read-only");
        } catch (UnsupportedOperationException ok) {
            expect(true, "ids() is read-only");
        }
        try {
            p.all().put("x", 1L);
            expect(false, "all() is read-only");
        } catch (UnsupportedOperationException ok) {
            expect(true, "all() is read-only");
        }

        // ---- the store in memory
        JournalStore mem = new JournalStore(null, w -> { });
        UUID u = UUID.randomUUID();
        expect(mem.peek(u) == null && mem.size() == 0, "peek does not create");
        Progress made = mem.of(u);
        expect(mem.peek(u) == made && mem.of(u) == made && mem.size() == 1, "of() creates once");
        expect(!mem.dirty(), "making a record is not a change to save");
        mem.markDirty();
        expect(mem.dirty(), "markDirty");
        mem.flush();
        expect(mem.dirty(), "a store with no file cannot flush and stays dirty");
        mem.load();
        expect(mem.size() == 0 && !mem.dirty(), "load() with no file empties it");

        // ---- on disk
        File dir = Files.createTempDirectory("nexusjournal-store").toFile();
        File file = new File(dir, "sub/players.yml");
        List<String> warnings = new ArrayList<>();
        JournalStore s = new JournalStore(file, warnings::add);
        UUID ann = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID idle = UUID.randomUUID();
        s.of(ann).add("lode", 111L);
        s.of(ann).add("alchemy", 222L);
        s.of(ann).bookGiven = true;
        s.of(bob).bookGiven = true;
        s.of(idle);
        s.flush();
        expect(!file.exists(), "nothing is written while nothing is dirty");
        s.markDirty();
        s.flush();
        expect(file.exists() && !s.dirty(), "flush writes (making the folder) and clears the flag");
        JournalStore back = new JournalStore(file, warnings::add);
        back.load();
        expect(back.size() == 2, "blank records are not saved: " + back.size());
        Progress ba = back.peek(ann);
        expect(ba != null && ba.bookGiven && ba.count() == 2 && ba.when("lode") == 111L && ba.when("alchemy") == 222L, "Ann's pages and times survive");
        expect(ba.ids().iterator().next().equals("lode"), "and their order");
        expect(back.peek(bob) != null && back.peek(bob).bookGiven && back.peek(bob).count() == 0, "Bob's book flag survives");
        expect(back.peek(idle) == null, "Idle was never saved");
        expect(!back.dirty() && warnings.isEmpty(), "loading is not a change: " + warnings);

        // a real epoch time (it does not fit in an int)
        File big = new File(dir, "big.yml");
        JournalStore bs = new JournalStore(big, warnings::add);
        bs.of(ann).add("lode", 1_790_123_456_789L);
        bs.markDirty();
        bs.flush();
        JournalStore bb = new JournalStore(big, warnings::add);
        bb.load();
        expect(bb.peek(ann).when("lode") == 1_790_123_456_789L, "epoch milliseconds survive a save: " + bb.peek(ann).when("lode"));

        // a hand-edited file
        Files.writeString(file.toPath(), String.join("\n",
                "players.not-a-uuid.book\tb\ttrue",
                "players." + ann + ".found.LODE\tl\t5",
                "players." + ann + ".found.rime\ti\t7",
                "players." + bob + ".book\tb\tfalse") + "\n");
        JournalStore odd = new JournalStore(file, warnings::add);
        odd.load();
        expect(warnings.size() == 1 && warnings.get(0).contains("not-a-uuid"), "a bad player id is skipped with a warning: " + warnings);
        expect(odd.peek(ann) != null && odd.peek(ann).has("lode"), "page ids are lower-cased when loaded");
        Files.writeString(file.toPath(), "other.thing\ts\tx\n");
        odd.load();
        expect(odd.size() == 0, "a file with no players section loads as empty");

        // saving when the folder cannot be made
        warnings.clear();
        File blocker = new File(dir, "blocker");
        Files.writeString(blocker.toPath(), "x");
        JournalStore bad = new JournalStore(new File(blocker, "players.yml"), warnings::add);
        bad.of(ann).bookGiven = true;
        bad.markDirty();
        bad.flush();
        expect(bad.dirty() && warnings.size() == 1 && warnings.get(0).contains("Could not save"), "a failed save warns and stays dirty: " + warnings);
        Kit.finish();
    }
}
