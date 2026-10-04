package kaptainwutax.tungsten.util;

/**
 * Lazy look rotation + route-shape hop gate (user request, 2026-10-04):
 *
 * <ul>
 *   <li><b>Lazy view rotation</b> — the navigation camera glides toward its facing
 *       with an ease-out curve instead of the aggressive fast nav turn. The ready-made
 *       algorithm this ports is the "legit camera movement" turn of
 *       {@code 23william90/baritone-26.3} ({@code LookBehavior.handleLegitPlayerUpdate},
 *       GitHub, retrieved 2026-10-04): a human-like glide with a per-step dynamic speed
 *       <pre>speed    = min(maxSpeed, max(minSpeed, remaining * smoothing))
 * step     = min(speed, remaining)</pre>
 *       large turns converge fast (capped), small corrections trail off (ease-out tail),
 *       and no step ever overshoots the target. Adapted here to WindMouseRotation's
 *       per-frame integration (the caller multiplies by the frame-rate factor and
 *       quantises through the vanilla mouse pipeline, exactly like every other mode).</li>
 *   <li><b>No flat-ground hop on short or twisty routes</b> — a sprint-jump is a speed
 *       optimisation; on a short leg or one that keeps changing direction it buys almost
 *       nothing and reads as botty bunny-hopping. The gate classifies the REMAINING
 *       route: below {@code shortBlocks} of walking left, or at least
 *       {@code twistyTurns} direction changes still ahead, the walker walks instead of
 *       hopping. Necessary jumps (climbing a step, ladders) are decided elsewhere and
 *       are never suppressed by this gate.</li>
 * </ul>
 *
 * <p>Pure math on purpose: no Minecraft imports, so it is unit-testable the same way
 * {@code MlgPolicy} / {@code DriftPolicy} are.
 */
public final class LazyLookPolicy {

    private LazyLookPolicy() {
    }

    /** Within this many degrees of the target the turn is considered done. */
    public static final double DONE_DEG = 0.25;

    /**
     * One lazy rotation step, in degrees, toward a target {@code remainingDeg} away.
     * Ported from baritone-26.3's legit camera: {@code dynamicSpeed =
     * min(maxSpeed, max(minSpeed, totalDistance * smoothing))}, then
     * {@code ratio = min(1, dynamicSpeed / totalDistance)} — i.e. the step is the
     * dynamic speed, clamped so it can never overshoot.
     *
     * <p>For {@code remainingDeg <= DONE_DEG} the whole remainder is the step (snap).
     *
     * @param remainingDeg angular distance still to turn, degrees (>= 0)
     * @param maxSpeedDeg  hard cap per step (the glide's sprint-flick ceiling)
     * @param minSpeedDeg  floor per step (keeps the tail converging at all)
     * @param smoothing    ease-out factor applied to the remaining angle
     */
    public static double stepDeg(double remainingDeg, double maxSpeedDeg,
                                 double minSpeedDeg, double smoothing) {
        // NaN must be neutral (finish-in-one-step = 0), not propagate — Math.max(0, NaN)
        // is NaN in Java, which would poison the pixel accumulator.
        if (remainingDeg != remainingDeg   // NaN
                || remainingDeg <= DONE_DEG) {
            return Math.max(0.0, remainingDeg != remainingDeg ? 0.0 : remainingDeg);
        }
        double dynamic = Math.min(maxSpeedDeg, Math.max(minSpeedDeg, remainingDeg * smoothing));
        double ratio = Math.min(1.0, dynamic / remainingDeg);
        return remainingDeg * ratio;   // == min(dynamic, remainingDeg): never overshoots
    }

    // ---------------------------------------------------------------------------------------
    // Route shape: turns in the remaining waypoint leg
    // ---------------------------------------------------------------------------------------

    /** Shortest signed angular difference in (-180,180], duplicated from
     *  {@link WindMouseRotation#wrapDelta} so this class stays Minecraft-free. */
    public static double wrapDelta(double delta) {
        delta = delta % 360.0;
        if (delta > 180.0)   delta -= 360.0;
        if (delta <= -180.0) delta += 360.0;
        return delta;
    }

    /**
     * Count direction changes among consecutive route segments. A change counts when the
     * angle between two consecutive segment bearings is at least {@code turnAngleDeg}.
     * Diagonal grid routes produce 45° bends per step, L-shaped orthogonal routes one
     * 90° bend per corner, so the default threshold of 45 counts every real corner.
     *
     * @param segmentBearingsDeg bearing of each remaining segment, in degrees
     *                           (any absolute convention — only differences matter)
     * @param turnAngleDeg       minimum angle between consecutive segments that counts
     */
    public static int countTurns(double[] segmentBearingsDeg, double turnAngleDeg) {
        if (segmentBearingsDeg == null || segmentBearingsDeg.length < 2) return 0;
        int turns = 0;
        for (int i = 1; i < segmentBearingsDeg.length; i++) {
            double d = Math.abs(wrapDelta(segmentBearingsDeg[i] - segmentBearingsDeg[i - 1]));
            if (d >= turnAngleDeg) turns++;
        }
        return turns;
    }

    /**
     * The hop gate itself. {@code true} means the walker should WALK this route —
     * suppress the flat-ground speed hop. Both disjuncts are individually optional so
     * either half of the request can be tuned off.
     *
     * @param shortEnabled  master switch for the "path is short" half
     * @param remainingDist walking distance still ahead, blocks
     * @param shortBlocks   routes shorter than this are walked, not hopped
     * @param twistyEnabled master switch for the "many bends" half
     * @param turns         direction changes still ahead on the route
     * @param twistyTurns   routes with at least this many remaining turns are walked
     */
    public static boolean suppressFlatHop(boolean shortEnabled, double remainingDist, double shortBlocks,
                                          boolean twistyEnabled, int turns, int twistyTurns) {
        if (shortEnabled && remainingDist < shortBlocks) return true;
        return twistyEnabled && turns >= twistyTurns;
    }
}
