package dev.synderis.orerenewal.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class PriorityDeduplicatingQueue<T> {
    private final ConcurrentLinkedQueue<T> priority = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<T> normal = new ConcurrentLinkedQueue<>();
    private final Set<T> scheduled = ConcurrentHashMap.newKeySet();

    void offer(T value) {
        if (scheduled.add(value)) {
            normal.add(value);
        }
    }

    void offerPriority(T value) {
        scheduled.add(value);
        priority.add(value);
    }

    T poll() {
        T value;
        while ((value = priority.poll()) != null) {
            if (scheduled.remove(value)) {
                return value;
            }
        }
        while ((value = normal.poll()) != null) {
            if (scheduled.remove(value)) {
                return value;
            }
        }
        return null;
    }

    int size() {
        return scheduled.size();
    }

    void clear() {
        priority.clear();
        normal.clear();
        scheduled.clear();
    }
}
