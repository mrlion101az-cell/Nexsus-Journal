package com.nexuscraft.nexusjournal;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Queues the one-line tips so two pages found in the same second do not overwrite each other on the action
 * bar: at most one line per player every {@code gapMillis}, the rest wait their turn.
 */
final class Tipper {

    static final int MAX_QUEUE = 12;

    private final BiPredicate<UUID, String> sink;
    private final Map<UUID, ArrayDeque<String>> queues = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();
    private long gapMillis;

    /** @param sink shows a line to the player; false means the player is gone (the line is dropped) */
    Tipper(BiPredicate<UUID, String> sink, long gapMillis) {
        this.sink = sink;
        this.gapMillis = gapMillis;
    }

    void setGap(long gapMillis) {
        this.gapMillis = gapMillis;
    }

    void offer(UUID id, String line, long now) {
        ArrayDeque<String> q = queues.computeIfAbsent(id, k -> new ArrayDeque<>());
        if (q.size() < MAX_QUEUE) {
            q.add(line);
        }
        pump(now);
    }

    /** Shows what is due. Called after every offer and once a second. */
    void pump(long now) {
        Iterator<Map.Entry<UUID, ArrayDeque<String>>> it = queues.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ArrayDeque<String>> e = it.next();
            ArrayDeque<String> q = e.getValue();
            Long prev = last.get(e.getKey());
            if (!q.isEmpty() && (prev == null || now - prev >= gapMillis)) {
                String line = q.poll();
                if (sink.test(e.getKey(), line)) {
                    last.put(e.getKey(), now);
                } else {
                    q.clear();
                }
            }
            if (q.isEmpty()) {
                it.remove();
            }
        }
    }

    /** How many players have a queue (or a gap timer) right now; empty queues are dropped. */
    int queued() {
        return queues.size();
    }

    int waiting(UUID id) {
        ArrayDeque<String> q = queues.get(id);
        return q == null ? 0 : q.size();
    }

    void forget(UUID id) {
        queues.remove(id);
        last.remove(id);
    }
}
