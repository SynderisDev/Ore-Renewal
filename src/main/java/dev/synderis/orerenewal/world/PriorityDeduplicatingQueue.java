package dev.synderis.orerenewal.world;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class PriorityDeduplicatingQueue<T> {
    private final Set<T> priority = new LinkedHashSet<>();
    private final Set<T> normal = new LinkedHashSet<>();

    synchronized void offer(T value) {
        if (!priority.contains(value)) {
            normal.add(value);
        }
    }

    synchronized void offerPriority(T value) {
        normal.remove(value);
        priority.add(value);
    }

    synchronized T poll() {
        T value = removeFirst(priority);
        return value != null ? value : removeFirst(normal);
    }

    synchronized int size() {
        return priority.size() + normal.size();
    }

    synchronized int storageSize() {
        return priority.size() + normal.size();
    }

    synchronized void clear() {
        priority.clear();
        normal.clear();
    }

    private static <T> T removeFirst(Set<T> values) {
        Iterator<T> iterator = values.iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        T value = iterator.next();
        iterator.remove();
        return value;
    }
}
