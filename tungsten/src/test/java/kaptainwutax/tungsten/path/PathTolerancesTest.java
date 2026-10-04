package kaptainwutax.tungsten.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single source of truth for the arrival/stuck tolerances that used to be scattered
 * as bare literals across PathFinder, PathExecutor, tungsten GotoCommand,
 * FastNavigator and UnstuckChain. Values are the historical ones — this change is
 * deliberately behaviour-preserving; it only makes the relationships explicit and
 * testable (audit angle 7).
 */
class PathTolerancesTest {

    @Test
    void emitGateIs0_447Blocks() {
        assertEquals(0.4472, PathTolerances.emitGateBlocks(), 0.001);
        assertEquals(0.2D, PathTolerances.EMIT_GATE_SQ, 1e-9);
    }

    @Test
    void fluidEmitGateIs0_949Blocks() {
        assertEquals(0.9487, PathTolerances.fluidEmitGateBlocks(), 0.001);
        assertEquals(0.9D, PathTolerances.EMIT_GATE_FLUID_SQ, 1e-9);
    }

    @Test
    void fluidGateIsLooserThanSolidGate() {
        assertTrue(PathTolerances.fluidEmitGateBlocks() > PathTolerances.emitGateBlocks(),
                "water/ladder needs a looser publish gate than solid ground");
    }

    @Test
    void gotoRetryGateMatchesNavigatorArrivalSphere() {
        // Both were historically 2.0 blocks. If they ever drift apart the goto retry
        // loop and the navigator arrival callback disagree about "arrived".
        assertEquals(PathTolerances.NAVIGATOR_ARRIVAL * PathTolerances.NAVIGATOR_ARRIVAL,
                PathTolerances.GOTO_ARRIVAL_SQ, 1e-9);
        assertEquals(4.0D, PathTolerances.GOTO_ARRIVAL_SQ, 1e-9);
    }

    @Test
    void searchPublishesTighterThanMovementAccepts() {
        // The search-side emit gate must be tighter than the executor/navigator arrival
        // gates, otherwise a path is published already counting as "arrived" and the
        // executor immediately runs out of work (or, worse, never publishes).
        assertTrue(PathTolerances.emitGateBlocks() < Math.sqrt(PathTolerances.SEGMENT_END_TELEMETRY_SQ));
        assertTrue(Math.sqrt(PathTolerances.SEGMENT_END_TELEMETRY_SQ) < PathTolerances.NAVIGATOR_ARRIVAL);
    }

    @Test
    void stuckAxisIsLooserThanArrival() {
        // Unstuck must not fire on a bot that just legitimately arrived: the per-axis
        // stuck threshold (1.5) has to exceed the emit gate.
        assertTrue(PathTolerances.STUCK_AXIS > PathTolerances.emitGateBlocks());
        assertEquals(1.5D, PathTolerances.STUCK_AXIS, 1e-9);
    }

    @Test
    void segmentEndTelemetryIs2_25Squared() {
        // PathExecutor's segment-end bucket (execArrived/execRanOut) is TELEMETRY ONLY —
        // it must never be used as a control-flow gate (that was the audit's confusion).
        assertEquals(2.25D, PathTolerances.SEGMENT_END_TELEMETRY_SQ, 1e-9);
    }
}
