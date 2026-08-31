package dev.synderis.orerenewal.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PriorityDeduplicatingQueueTest {
    @Test
    void newlyLoadedNeighborhoodCanJumpAheadOfStartupBacklog() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("startup-a");
        queue.offer("startup-b");
        queue.offerPriority("player-neighborhood");

        assertEquals("player-neighborhood", queue.poll());
        assertEquals("startup-a", queue.poll());
        assertEquals("startup-b", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void promotionDoesNotProcessTheSameEntryTwice() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("chunk");
        queue.offerPriority("chunk");

        assertEquals(1, queue.size());
        assertEquals("chunk", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void repeatedPriorityOffersStayPhysicallyDeduplicated() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("chunk");

        for (int i = 0; i < 100_000; i++) {
            queue.offerPriority("chunk");
        }

        assertEquals(1, queue.size());
        assertEquals(1, queue.storageSize());
        assertEquals("chunk", queue.poll());
        assertEquals(0, queue.storageSize());
        assertNull(queue.poll());
    }
}
