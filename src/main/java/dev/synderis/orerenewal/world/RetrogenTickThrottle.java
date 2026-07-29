package dev.synderis.orerenewal.world;

final class RetrogenTickThrottle {
    private final int maxDeferralTicks;
    private int deferredTicks;

    RetrogenTickThrottle(int maxDeferralTicks) {
        if (maxDeferralTicks < 1) {
            throw new IllegalArgumentException("maxDeferralTicks must be positive");
        }
        this.maxDeferralTicks = maxDeferralTicks;
    }

    int budget(boolean hasTime, boolean onlyWhenTickHasTime, int configuredBudget) {
        if (!onlyWhenTickHasTime || hasTime) {
            deferredTicks = 0;
            return configuredBudget;
        }

        deferredTicks++;
        if (deferredTicks < maxDeferralTicks) {
            return 0;
        }
        deferredTicks = 0;
        return 1;
    }

    void reset() {
        deferredTicks = 0;
    }
}
