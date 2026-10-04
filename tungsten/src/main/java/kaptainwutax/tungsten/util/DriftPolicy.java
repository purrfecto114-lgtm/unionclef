package kaptainwutax.tungsten.util;

/**
 * Frame-time-aware drift allowance for the replay-vs-real comparison in
 * {@code Agent#compare} (audit angle 1, 2026-10-04).
 *
 * <p>Why: the drift bound grows per REPLAYED TICK ({@code threshold + perTick *
 * tick}), but a tick's ground coverage depends on frame rate — at ~10 fps a tick
 * covers roughly twice the ground it was tuned against (TungstenConfig's own
 * note). A fixed per-tick growth therefore punishes well-tracked paths on slow
 * hosts. The policy scales ONLY the growth term by the same frame-factor rule
 * WindMouseRotation already uses (clamp(frameMs / 50ms, 1, 4)); the tick-0
 * guarantee — the threshold itself — is untouched, so the historical tick-1
 * failure (drift 1.723) still aborts.
 *
 * <p>Opt-out: {@code TungstenConfig.driftFrameTimeAdaptive=false} forces factor
 * 1.0, restoring the exact historical bound.
 */
public final class DriftPolicy {

    private DriftPolicy() {
    }

    // ---- frame sampler (fed by the render mixin, read from the client tick) ----

    private static volatile long lastFrameEndMs = 0L;
    private static volatile long lastFrameDurationMs = 0L;

    /** Render mixin calls this once per frame with the current wall clock. */
    public static void noteFrame(long nowMs) {
        long prev = lastFrameEndMs;
        lastFrameEndMs = nowMs;
        if (prev != 0L && nowMs > prev) {
            lastFrameDurationMs = nowMs - prev;
        }
    }

    /**
     * Latest observed frame factor, or 1.0 when no frame has been seen yet (headless
     * contexts, tests, first tick). Clamped by the same rule as WindMouseRotation.
     */
    public static double currentFrameFactor(double refFrameMs, double maxCatchup) {
        return frameFactor(lastFrameDurationMs, refFrameMs, maxCatchup);
    }

    // ---- pure decision ----

    /**
     * Frame factor: how much MORE integration error one replayed tick may carry
     * versus the 50 ms reference frame. Never below 1 (fast frames do not shrink
     * the allowance) and capped at {@code maxCatchup} so a hitch cannot become a
     * teleport licence.
     */
    public static double frameFactor(double frameMs, double refFrameMs, double maxCatchup) {
        if (frameMs <= 0.0 || Double.isNaN(frameMs) || Double.isInfinite(frameMs)) return 1.0;
        return Math.max(1.0, Math.min(maxCatchup, frameMs / refFrameMs));
    }

    /**
     * Total drift allowance in blocks: threshold (absolute, unchanged at tick 0)
     * plus per-tick growth scaled by the frame factor.
     */
    public static double allowedBlocks(double threshold, double perTick, int replayTick, double frameFactor) {
        int tick = Math.max(0, replayTick);
        return threshold + perTick * tick * frameFactor;
    }
}
