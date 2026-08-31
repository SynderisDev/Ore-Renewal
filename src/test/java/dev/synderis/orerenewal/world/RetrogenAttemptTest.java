package dev.synderis.orerenewal.world;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrogenAttemptTest {
    @Test
    void failedFeatureReturnsRetryWithoutCommittingOrBlockingOtherFeatures() {
        AtomicInteger revision = new AtomicInteger(3);
        AtomicInteger attempts = new AtomicInteger();

        RetrogenAttempt.Result result = RetrogenAttempt.runAndCommit(
                List.of("successful-feature", "failing-feature", "later-feature"),
                feature -> {
                    attempts.incrementAndGet();
                    return !feature.equals("failing-feature");
                },
                () -> revision.set(4));

        assertEquals(RetrogenAttempt.Result.RETRY_NEXT_TICK, result);
        assertEquals(3, revision.get());
        assertEquals(3, attempts.get());
    }

    @Test
    void successfulBatchCommitsExactlyOnce() {
        AtomicInteger commits = new AtomicInteger();

        RetrogenAttempt.Result result = RetrogenAttempt.runAndCommit(
                List.of("feature-a", "feature-b"),
                feature -> true,
                commits::incrementAndGet);

        assertEquals(RetrogenAttempt.Result.DONE, result);
        assertEquals(1, commits.get());
    }

    @Test
    void unexpectedAttemptExceptionNeverCommits() {
        AtomicInteger commits = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> RetrogenAttempt.runAndCommit(
                List.of("feature"),
                feature -> {
                    throw new IllegalStateException("fixture failure");
                },
                commits::incrementAndGet));

        assertEquals(0, commits.get());
    }

    @Test
    void emptyBatchStillCommits() {
        AtomicInteger commits = new AtomicInteger();

        RetrogenAttempt.Result result = RetrogenAttempt.runAndCommit(
                List.<String>of(),
                feature -> false,
                commits::incrementAndGet);

        assertEquals(RetrogenAttempt.Result.DONE, result);
        assertEquals(1, commits.get());
    }

    @Test
    void retryReplaysTheWholeUncommittedBatch() {
        AtomicInteger successfulFeatureAttempts = new AtomicInteger();
        AtomicInteger failingFeatureAttempts = new AtomicInteger();
        AtomicInteger commits = new AtomicInteger();
        List<String> batch = List.of("successful-feature", "fails-once");

        RetrogenAttempt.Result first = RetrogenAttempt.runAndCommit(batch, feature -> {
            if (feature.equals("successful-feature")) {
                successfulFeatureAttempts.incrementAndGet();
                return true;
            }
            return failingFeatureAttempts.incrementAndGet() > 1;
        }, commits::incrementAndGet);
        RetrogenAttempt.Result second = RetrogenAttempt.runAndCommit(batch, feature -> {
            if (feature.equals("successful-feature")) {
                successfulFeatureAttempts.incrementAndGet();
                return true;
            }
            return failingFeatureAttempts.incrementAndGet() > 1;
        }, commits::incrementAndGet);

        assertEquals(RetrogenAttempt.Result.RETRY_NEXT_TICK, first);
        assertEquals(RetrogenAttempt.Result.DONE, second);
        assertEquals(2, successfulFeatureAttempts.get());
        assertEquals(2, failingFeatureAttempts.get());
        assertEquals(1, commits.get());
    }
}
