package dev.synderis.orerenewal.world;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

final class PriorityDeduplicatingQueue<T> {
    private static final int MAX_PRIORITY_BURST = 8;
    private final Set<T> priority = new LinkedHashSet<>();
    private final Set<T> normal = new LinkedHashSet<>();
    private final Set<T> deferred = new LinkedHashSet<>();
    private int priorityBurst;

    synchronized void offer(T value) {
        if (!priority.contains(value) && !deferred.contains(value)) {
            normal.add(value);
        }
    }

    synchronized void offerPriority(T value) {
        if (deferred.contains(value)) {
            return;
        }
        normal.remove(value);
        priority.add(value);
    }

    synchronized void retryNextTick(T value) {
        priority.remove(value);
        normal.remove(value);
        deferred.add(value);
    }

    synchronized void beginTick() {
        for (T value : deferred) {
            if (!priority.contains(value)) {
                normal.add(value);
            }
        }
        deferred.clear();
    }

    synchronized T poll() {
        if (!priority.isEmpty() && (normal.isEmpty() || priorityBurst < MAX_PRIORITY_BURST)) {
            priorityBurst++;
            return removeFirst(priority);
        }

        T value = removeFirst(normal);
        if (value != null) {
            priorityBurst = 0;
            return value;
        }

        priorityBurst = 0;
        return removeFirst(priority);
    }

    synchronized int size() {
        return priority.size() + normal.size() + deferred.size();
    }

    synchronized int storageSize() {
        return priority.size() + normal.size() + deferred.size();
    }

    synchronized void clear() {
        priority.clear();
        normal.clear();
        deferred.clear();
        priorityBurst = 0;
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
