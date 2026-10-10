package com.nexuscraft.nexusjournal;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.nexuscraft.nexusjournal.Kit.expect;

/** One tip per player per gap; the rest wait. */
public final class TipperTest {

    public static void main(String[] args) {
        List<String> shown = new ArrayList<>();
        boolean[] online = {true};
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Tipper t = new Tipper((id, line) -> {
            if (!online[0]) {
                return false;
            }
            shown.add((id.equals(a) ? "A:" : "B:") + line);
            return true;
        }, 4000);

        t.offer(a, "one", 0);
        expect(shown.equals(List.of("A:one")), "the first tip shows at once: " + shown);
        t.offer(a, "two", 1000);
        t.offer(a, "three", 2000);
        expect(shown.size() == 1 && t.waiting(a) == 2, "later tips wait for the gap: " + shown + " waiting " + t.waiting(a));
        t.pump(3999);
        expect(shown.size() == 1, "not before the gap is over");
        t.pump(4000);
        expect(shown.equals(List.of("A:one", "A:two")), "exactly at the gap the next shows: " + shown);
        t.pump(5000);
        expect(shown.size() == 2, "one per gap");
        t.pump(8000);
        expect(shown.equals(List.of("A:one", "A:two", "A:three")) && t.waiting(a) == 0, "in the order offered: " + shown);
        t.pump(12000);
        expect(shown.size() == 3, "nothing left, nothing shown");

        // players are independent
        t.offer(b, "bee", 8500);
        expect(shown.get(shown.size() - 1).equals("B:bee"), "another player's tip is not held up by Ann's gap");

        // after a long quiet spell the next tip is immediate
        t.offer(a, "late", 100_000);
        expect(shown.get(shown.size() - 1).equals("A:late"), "an offer after the gap shows at once");

        // the queue is capped
        UUID c = UUID.randomUUID();
        t.offer(c, "first", 200_000);
        for (int i = 0; i < 30; i++) {
            t.offer(c, "n" + i, 200_000);
        }
        expect(t.waiting(c) == Tipper.MAX_QUEUE, "a flood is capped at " + Tipper.MAX_QUEUE + ": " + t.waiting(c));

        // a player who left
        UUID d = UUID.randomUUID();
        t.offer(d, "d1", 300_000);
        t.offer(d, "d2", 300_000);
        t.offer(d, "d3", 300_000);
        expect(t.waiting(d) == 2, "two wait");
        online[0] = false;
        t.pump(310_000);
        expect(t.waiting(d) == 0, "an offline player's queue is dropped");
        online[0] = true;
        int before = shown.size();
        t.pump(320_000);
        expect(shown.size() == before, "and nothing shows for them later");

        // forget
        UUID e = UUID.randomUUID();
        t.offer(e, "e1", 400_000);
        t.offer(e, "e2", 400_000);
        t.forget(e);
        expect(t.waiting(e) == 0, "forget empties the queue");
        int n = shown.size();
        t.offer(e, "e3", 400_001);
        expect(shown.size() == n + 1, "and the gap is forgotten too: a new tip is immediate");

        // gap can change
        t.setGap(1000);
        UUID f = UUID.randomUUID();
        t.offer(f, "f1", 500_000);
        t.offer(f, "f2", 500_500);
        expect(t.waiting(f) == 1, "short gap still holds back");
        t.pump(501_000);
        expect(t.waiting(f) == 0, "and releases at the new gap");
        Kit.finish();
    }
}
