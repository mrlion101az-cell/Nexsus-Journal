package com.nexuscraft.nexusjournal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** What one player has found: page id -> when (epoch millis), in the order found. */
final class Progress {

    private final Map<String, Long> found = new LinkedHashMap<>();
    boolean bookGiven;

    boolean has(String id) {
        return found.containsKey(id);
    }

    /** True the first time only. */
    boolean add(String id, long at) {
        return found.putIfAbsent(id, at) == null;
    }

    boolean remove(String id) {
        return found.remove(id) != null;
    }

    int clear() {
        int n = found.size();
        found.clear();
        return n;
    }

    int count() {
        return found.size();
    }

    Long when(String id) {
        return found.get(id);
    }

    Set<String> ids() {
        return Collections.unmodifiableSet(found.keySet());
    }

    Map<String, Long> all() {
        return Collections.unmodifiableMap(found);
    }

    boolean isBlank() {
        return found.isEmpty() && !bookGiven;
    }
}
