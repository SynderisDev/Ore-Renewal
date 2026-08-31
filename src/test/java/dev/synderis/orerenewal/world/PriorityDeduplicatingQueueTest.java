package dev.synderis.orerenewal.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void retryCannotBePolledAgainBeforeTheNextTick() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("chunk");

        assertEquals("chunk", queue.poll());
        queue.retryNextTick("chunk");

        assertNull(queue.poll());
        assertNull(queue.poll());
        assertEquals(1, queue.size());
    }

    @Test
    void retryBecomesEligibleOnTheNextTick() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("chunk");
        assertEquals("chunk", queue.poll());
        queue.retryNextTick("chunk");

        queue.beginTick();

        assertEquals("chunk", queue.poll());
        assertNull(queue.poll());
    }

    @Test
    void failedEntryMovesBehindAlreadyReadyEntries() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("failed");
        queue.offer("healthy");
        assertEquals("failed", queue.poll());
        queue.retryNextTick("failed");

        queue.beginTick();

        assertEquals("healthy", queue.poll());
        assertEquals("failed", queue.poll());
    }

    @Test
    void priorityReofferCannotBypassTheRetryBarrier() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("chunk");
        assertEquals("chunk", queue.poll());
        queue.retryNextTick("chunk");

        queue.offerPriority("chunk");

        assertNull(queue.poll());
        assertEquals(1, queue.storageSize());
        queue.beginTick();
        assertEquals("chunk", queue.poll());
    }

    @Test
    void deferredEntriesStayPhysicallyDeduplicatedAndClearable() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.retryNextTick("chunk");

        for (int i = 0; i < 100_000; i++) {
            queue.offer("chunk");
            queue.offerPriority("chunk");
            queue.retryNextTick("chunk");
        }

        assertEquals(1, queue.size());
        assertEquals(1, queue.storageSize());
        queue.clear();
        assertEquals(0, queue.storageSize());
        assertNull(queue.poll());
    }

    @Test
    void sustainedPriorityTrafficCannotStarveReadyWork() {
        PriorityDeduplicatingQueue<String> queue = new PriorityDeduplicatingQueue<>();
        queue.offer("ready");

        boolean readyPolled = false;
        for (int i = 0; i < 32; i++) {
            queue.offerPriority("priority-" + i);
            if ("ready".equals(queue.poll())) {
                readyPolled = true;
                break;
            }
        }

        assertTrue(readyPolled);
    }
}
