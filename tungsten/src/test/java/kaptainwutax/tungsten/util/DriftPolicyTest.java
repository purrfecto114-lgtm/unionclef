package kaptainwutax.tungsten.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Frame-time-aware drift allowance (audit angle 1, 2026-10-04).
 *
 * <p>Historical formula: allowed = threshold + perTick * replayTick. The growth
 * term was tuned per TICK, but at low fps a tick covers twice the ground, so the
 * same live path accumulates more integration error per tick than the bound
 * assumes (TungstenConfig's own ~10 fps note). The policy multiplies ONLY the
 * growth term by the frame factor; the tick-0 guarantee (threshold) is untouched,
 * so the historical tick-1 failure (drift 1.723) still aborts.
 */
class DriftPolicyTest {

    private static final double REF = 50.0;   // WindMouseRotation.REF_FRAME_MS
    private static final double MAX = 4.0;    // WindMouseRotation.MAX_CATCHUP

    @Test
    void historicalFormulaAtReferenceFrameRate() {
        // factor 1.0 must reproduce the pre-change allowance exactly.
        assertEquals(0.8 + 0.05 * 14, DriftPolicy.allowedBlocks(0.8, 0.05, 14, 1.0), 1e-9);
        assertEquals(1.5, DriftPolicy.allowedBlocks(0.8, 0.05, 14, 1.0), 1e-9);
    }

    @Test
    void tickZeroGuaranteeUnchanged() {
        // The tick-1 incident (drift 1.723 > 0.8) that this check exists for must
        // still abort at tick 0 regardless of frame rate.
        assertEquals(0.8, DriftPolicy.allowedBlocks(0.8, 0.05, 0, 4.0), 1e-9);
    }

    @Test
    void slowFramesLoosenOnlyTheGrowthTerm() {
        // 10 fps -> factor 2.0 -> a well-tracked path gets twice the per-tick slack.
        double allowed = DriftPolicy.allowedBlocks(0.8, 0.05, 14, 2.0);
        assertEquals(0.8 + 0.05 * 14 * 2.0, allowed, 1e-9);
        assertTrue(allowed > DriftPolicy.allowedBlocks(0.8, 0.05, 14, 1.0));
    }

    @Test
    void frameFactorClampsToWindMouseBounds() {
        assertEquals(1.0, DriftPolicy.frameFactor(50.0, REF, MAX), 1e-9);   // 20 fps reference
        assertEquals(2.0, DriftPolicy.frameFactor(100.0, REF, MAX), 1e-9);  // 10 fps
        assertEquals(MAX, DriftPolicy.frameFactor(1000.0, REF, MAX), 1e-9); // hitch, capped
        assertEquals(1.0, DriftPolicy.frameFactor(10.0, REF, MAX), 1e-9);   // fast frame: no shrink
    }

    @Test
    void degenerateFramesAreNeutral() {
        assertEquals(1.0, DriftPolicy.frameFactor(0.0, REF, MAX), 1e-9);
        assertEquals(1.0, DriftPolicy.frameFactor(-5.0, REF, MAX), 1e-9);
        assertEquals(1.0, DriftPolicy.frameFactor(Double.NaN, REF, MAX), 1e-9);
    }

    @Test
    void negativeReplayTickTreatedAsZero() {
        assertEquals(0.8, DriftPolicy.allowedBlocks(0.8, 0.05, -3, 2.0), 1e-9);
    }
}
