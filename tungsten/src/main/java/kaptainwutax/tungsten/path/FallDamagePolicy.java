package kaptainwutax.tungsten.path;

/**
 * The one decision point for "does THIS search ignore fall damage?" (audit
 * angle 8, 2026-10-04).
 *
 * <p>Historical mess, untangled but behaviour-preserving:
 * <ul>
 *   <li>{@code pathAvoidsFallDamage} (TungstenConfig, shipped 2026-08-23 with its
 *       own A/B gate) — the pathfinder's fall guard is ON by default.</li>
 *   <li>{@code ignoreFallDamage} (TungstenModDataContainer, upstream tungsten's
 *       {@code ;settings} flag) — MEANING "the user declares falls acceptable",
 *       NOT "the guard is off". It only disables the guard when the pathfinder is
 *       ALSO opted out of avoidance.</li>
 *   <li>{@code fallGuardRelaxed} — an engine-internal, temporary override used by
 *       the openSet-exhausted retry, never a user setting.</li>
 * </ul>
 *
 * <p>The 2026-10-03 audit read {@code TungstenConfig}'s stale comment ("SECOND
 * line is if (ignoreFallDamage) return false") and concluded the guard is dead by
 * default. It is not: with defaults the early return is false and every guard
 * site (PathFinder:809, Node.fallGuardActive, BlockNode, RunToNode, WalkToNode,
 * SprintJumpMove) runs. The comment has been corrected in place; this class pins
 * the real semantics with a test.
 */
public final class FallDamagePolicy {

    private FallDamagePolicy() {
    }

    /**
     * @param relaxed engine-internal temporary relaxation (openSet-exhausted retry)
     * @param ignore  user-facing "falls are acceptable" declaration (upstream
     *                tungsten's ignoreFallDamage)
     * @param avoid   pathfinder's pathAvoidsFallDamage guard switch
     * @return true when the search should SKIP fall-damage checks
     */
    public static boolean searchIgnoresFallDamage(boolean relaxed, boolean ignore, boolean avoid) {
        return relaxed || (ignore && !avoid);
    }
}
