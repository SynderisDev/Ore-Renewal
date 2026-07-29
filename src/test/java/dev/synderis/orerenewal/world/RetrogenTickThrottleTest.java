package dev.synderis.orerenewal.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetrogenTickThrottleTest {
    @Test
    void busyTicksCannotStarveTheQueueForever() {
        RetrogenTickThrottle throttle = new RetrogenTickThrottle(20);

        for (int tick = 1; tick < 20; tick++) {
            assertEquals(0, throttle.budget(false, true, 8));
        }
        assertEquals(1, throttle.budget(false, true, 8));
        assertEquals(0, throttle.budget(false, true, 8));
    }

    @Test
    void spareTimeAndDisabledGateUseConfiguredBudget() {
        RetrogenTickThrottle throttle = new RetrogenTickThrottle(20);
        for (int tick = 0; tick < 10; tick++) {
            throttle.budget(false, true, 8);
        }

        assertEquals(8, throttle.budget(true, true, 8));
        assertEquals(8, throttle.budget(false, false, 8));
        assertEquals(0, throttle.budget(false, true, 8));
    }
}
