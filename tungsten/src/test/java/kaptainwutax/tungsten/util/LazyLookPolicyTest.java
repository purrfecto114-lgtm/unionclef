package kaptainwutax.tungsten.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lazy look rotation (ported from 23william90/baritone-26.3's legit camera ease-out)
 * and the short/twisty flat-hop gate (user request, 2026-10-04).
 */
class LazyLookPolicyTest {

    private static final double MAX = 16.0;
    private static final double MIN = 1.6;
    private static final double SMOOTH = 0.30;

    // ── stepDeg: the ported formula ──────────────────────────────────────────

    @Test
    void largeTurnIsCappedAtMaxSpeed() {
        // 90° left: min(16, max(1.6, 90*0.3=27)) = 16 — the cap phase of the glide.
        assertEquals(MAX, LazyLookPolicy.stepDeg(90, MAX, MIN, SMOOTH), 1e-9);
        assertEquals(MAX, LazyLookPolicy.stepDeg(120, MAX, MIN, SMOOTH), 1e-9);
    }

    @Test
    void smallTurnFollowsTheEaseOutFactor() {
        // 10°: min(16, max(1.6, 3.0)) = 3.0 — a fraction of the remainder, not a snap.
        assertEquals(3.0, LazyLookPolicy.stepDeg(10, MAX, MIN, SMOOTH), 1e-9);
        // 5°: max(1.6, 1.5) = 1.6 — the floor keeps the tail moving.
        assertEquals(MIN, LazyLookPolicy.stepDeg(5, MAX, MIN, SMOOTH), 1e-9);
    }

    @Test
    void stepNeverOvershootsTheTarget() {
        // Below the floor the step would exceed the remainder; it must clamp to it.
        assertEquals(1.0, LazyLookPolicy.stepDeg(1.0, MAX, MIN, SMOOTH), 1e-9);
        assertEquals(0.3, LazyLookPolicy.stepDeg(0.3, MAX, MIN, SMOOTH), 1e-9);
    }

    @Test
    void doneThresholdSnapsTheRemainder() {
        assertEquals(0.25, LazyLookPolicy.stepDeg(LazyLookPolicy.DONE_DEG, MAX, MIN, SMOOTH), 1e-9);
        assertEquals(0.0, LazyLookPolicy.stepDeg(0.0, MAX, MIN, SMOOTH), 1e-9);
    }

    @Test
    void ninetyDegreeTurnConvergesQuicklyWithoutOvershoot() {
        // Simulate a full glide: the remaining angle must shrink monotonically and
        // finish in a couple dozen frames (lazy, not frozen), never growing.
        double remaining = 90.0;
        int frames = 0;
        double prev = remaining;
        while (remaining > LazyLookPolicy.DONE_DEG && frames < 200) {
            remaining -= LazyLookPolicy.stepDeg(remaining, MAX, MIN, SMOOTH);
            assertTrue(remaining <= prev + 1e-12, "step overshot the target");
            assertTrue(remaining >= 0.0, "step went past zero");
            prev = remaining;
            frames++;
        }
        assertTrue(frames < 60, "90deg glide took " + frames + " frames — frozen, not lazy");
        assertTrue(remaining <= LazyLookPolicy.DONE_DEG);
    }

    @Test
    void tinyTurnStillConvergesOnTheFloor() {
        double remaining = 3.0;
        int frames = 0;
        while (remaining > LazyLookPolicy.DONE_DEG && frames < 200) {
            remaining -= LazyLookPolicy.stepDeg(remaining, MAX, MIN, SMOOTH);
            frames++;
        }
        assertTrue(frames < 60, "3deg tail took " + frames + " frames");
    }

    @Test
    void degenerateInputsAreNeutral() {
        assertEquals(0.0, LazyLookPolicy.stepDeg(0.0, MAX, MIN, SMOOTH), 1e-9);
        assertEquals(0.0, LazyLookPolicy.stepDeg(-4.0, MAX, MIN, SMOOTH), 1e-9);
        assertEquals(0.0, LazyLookPolicy.stepDeg(Double.NaN, MAX, MIN, SMOOTH), 1e-9);
    }

    // ── route shape: turns ───────────────────────────────────────────────────

    @Test
    void straightRouteHasZeroTurns() {
        double[] seg = {0, 0, 0, 0};
        assertEquals(0, LazyLookPolicy.countTurns(seg, 45.0));
        assertEquals(0, LazyLookPolicy.countTurns(new double[0], 45.0));
        assertEquals(0, LazyLookPolicy.countTurns(new double[]{10}, 45.0));
        assertEquals(0, LazyLookPolicy.countTurns(null, 45.0));
    }

    @Test
    void lShapedRouteHasOneTurn() {
        double[] seg = {0, 0, 90, 90};     // two cells north, two cells east
        assertEquals(1, LazyLookPolicy.countTurns(seg, 45.0));
    }

    @Test
    void staircaseCountsEveryDiagonalBend() {
        double[] seg = {45, 135, 45, 135, 45};  // zig-zag diagonals
        assertEquals(4, LazyLookPolicy.countTurns(seg, 45.0));
    }

    @Test
    void wrapAroundTurnCounted() {
        double[] seg = {170, -170};        // 20° across the ±180 seam
        assertEquals(0, LazyLookPolicy.countTurns(seg, 45.0));
        double[] seam = {0, 180};          // full reversal
        assertEquals(1, LazyLookPolicy.countTurns(seam, 45.0));
    }

    @Test
    void subThresholdBendsDoNotCount() {
        double[] seg = {0, 20, 40};        // gentle drift, never a "bend"
        assertEquals(0, LazyLookPolicy.countTurns(seg, 45.0));
    }

    // ── hop gate ─────────────────────────────────────────────────────────────

    @Test
    void shortRouteSuppressesTheHop() {
        assertTrue(LazyLookPolicy.suppressFlatHop(true, 5.0, 12.0, true, 0, 3));
    }

    @Test
    void longStraightRouteKeepsTheHop() {
        assertFalse(LazyLookPolicy.suppressFlatHop(true, 40.0, 12.0, true, 0, 3));
    }

    @Test
    void twistyRouteSuppressesTheHopEvenWhenLong() {
        assertFalse(LazyLookPolicy.suppressFlatHop(true, 40.0, 12.0, true, 2, 3),
                "two bends is not many");
        assertTrue(LazyLookPolicy.suppressFlatHop(true, 40.0, 12.0, true, 3, 3),
                "three bends is many");
    }

    @Test
    void switchesDisableTheirHalf() {
        assertFalse(LazyLookPolicy.suppressFlatHop(false, 5.0, 12.0, false, 9, 3),
                "both halves off must never suppress");
        // Short half off: the twisty half alone decides — 9 turns >= 3 still suppresses.
        assertTrue(LazyLookPolicy.suppressFlatHop(false, 5.0, 12.0, true, 9, 3),
                "short half off leaves only the twisty half");
        // Twisty half off: the short half alone decides — 5 < 12 still suppresses.
        assertTrue(LazyLookPolicy.suppressFlatHop(true, 5.0, 12.0, false, 9, 3),
                "twisty half off leaves only the short half");
        // Each half on but its condition unmet: no suppression.
        assertFalse(LazyLookPolicy.suppressFlatHop(false, 5.0, 12.0, true, 2, 3));
        assertFalse(LazyLookPolicy.suppressFlatHop(true, 40.0, 12.0, false, 9, 3));
    }

    @Test
    void exactlyAtShortBoundaryIsNotShort() {
        // "< shortBlocks" is strict: a route of exactly the threshold keeps the hop.
        assertFalse(LazyLookPolicy.suppressFlatHop(true, 12.0, 12.0, false, 0, 3));
        assertTrue(LazyLookPolicy.suppressFlatHop(true, 11.999, 12.0, false, 0, 3));
    }
}
