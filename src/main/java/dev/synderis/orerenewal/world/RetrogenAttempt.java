package dev.synderis.orerenewal.world;

import java.util.function.Predicate;

final class RetrogenAttempt {
    enum Result {
        DONE,
        RETRY_NEXT_TICK
    }

    private RetrogenAttempt() {
    }

    static <T> Result runAndCommit(
            Iterable<T> pending,
            Predicate<T> attempt,
            Runnable commit
    ) {
        boolean allSuccessful = true;
        for (T item : pending) {
            if (!attempt.test(item)) {
                allSuccessful = false;
            }
        }
        if (!allSuccessful) {
            return Result.RETRY_NEXT_TICK;
        }
        commit.run();
        return Result.DONE;
    }
}
