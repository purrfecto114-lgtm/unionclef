package kaptainwutax.tungsten.path;

/**
 * Single source of truth for every "how close is close enough" constant in the
 * navigation stack (audit angle 7, 2026-10-04).
 *
 * <p>Before this class the radii lived as bare literals in five different files and
 * used three different dimensionalities (squared distance, distance, per-axis
 * distance), which is how the "1.5 blocks segment gate" myth in the 2026-10-03
 * audit happened: {@code PathExecutor}'s {@code <= 2.25} check LOOKED like an
 * arrival gate but was only a telemetry bucket.
 *
 * <p>Relationship chain (enforced by {@code PathTolerancesTest}):
 * <pre>
 *   emit gate 0.447  &lt;  segment telemetry 1.5  &lt;  arrival sphere 2.0
 *   (search publishes)   (executor bucket)         (goto retry == navigator)
 * </pre>
 *
 * <p>⛔ Do not "unify" these into one value. They sit at different layers on
 * purpose: the search must publish a path strictly tighter than the movement layer
 * accepts it, and the stuck detector must be looser than arrival. Changing a value
 * is a behaviour change and needs its own A/B gate; changing the STRUCTURE should
 * go through {@code docs/BARITONE-PORT.md} finding #331 (unified Goal abstraction).
 */
public final class PathTolerances {

    private PathTolerances() {
    }

    /**
     * Search-side path publish gate ({@code PathFinder#isPathComplete}): SQUARED
     * distance. sqrt ≈ 0.447 blocks. See PathFinder for the "never got close / got
     * to 0.6 and was refused" history behind this number.
     */
    public static final double EMIT_GATE_SQ = 0.2D;

    /**
     * Water and ladder targets get a looser publish gate ({@code PathFinder}):
     * SQUARED distance, sqrt ≈ 0.949 blocks. Swimming bodies settle wider than
     * walking ones.
     */
    public static final double EMIT_GATE_FLUID_SQ = 0.9D;

    /**
     * Tungsten {@code GotoCommand} retry/stop gate: SQUARED distance (2.0 blocks).
     * Below it the goto stops retrying; above it the search re-issues.
     */
    public static final double GOTO_ARRIVAL_SQ = 2.0 * 2.0;

    /**
     * {@code FastNavigator} arrival sphere, in BLOCKS. Kept numerically equal to
     * {@link #GOTO_ARRIVAL_SQ}'s root so the navigator callback and the goto retry
     * loop cannot disagree about "arrived".
     */
    public static final double NAVIGATOR_ARRIVAL = 2.0;

    /**
     * {@code PathExecutor} segment-end classification bucket: SQUARED distance
     * (sqrt = 1.5 blocks). ⚠️ TELEMETRY ONLY — feeds the execArrived/execRanOut
     * counters, never a control-flow decision. Do not wire behaviour to this.
     */
    public static final double SEGMENT_END_TELEMETRY_SQ = 2.25;

    /**
     * {@code UnstuckChain} stuck detection threshold: PER-AXIS distance in blocks
     * (dx &lt; 1.5 &amp;&amp; dy &lt; 1.5 &amp;&amp; dz &lt; 1.5), NOT a euclidean
     * radius. Deliberately looser than the arrival sphere so a bot that just
     * arrived and settled is not flagged as stuck.
     */
    public static final double STUCK_AXIS = 1.5;

    /** Emit gate in blocks (derived). */
    public static double emitGateBlocks() {
        return Math.sqrt(EMIT_GATE_SQ);
    }

    /** Fluid emit gate in blocks (derived). */
    public static double fluidEmitGateBlocks() {
        return Math.sqrt(EMIT_GATE_FLUID_SQ);
    }

    /** Segment telemetry bucket in blocks (derived, diagnostics only). */
    public static double segmentTelemetryBlocks() {
        return Math.sqrt(SEGMENT_END_TELEMETRY_SQ);
    }
}
