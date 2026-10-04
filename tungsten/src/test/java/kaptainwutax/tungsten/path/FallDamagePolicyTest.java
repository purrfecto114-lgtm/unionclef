package kaptainwutax.tungsten.path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Truth table for the fall-damage guard decision (audit angle 8, 2026-10-04).
 *
 * <p>Extracted from {@code TungstenModDataContainer.searchIgnoresFallDamage()} so
 * the decision is testable without a Minecraft runtime. The historical formula is
 * preserved exactly: {@code relaxed || (ignore && !avoid)}.
 */
class FallDamagePolicyTest {

    @Test
    void defaultFlagsKeepTheGuardACTIVE() {
        // HEADLINE: with the shipped defaults (ignoreFallDamage=true,
        // pathAvoidsFallDamage=true) the search does NOT ignore fall damage —
        // the guard runs. The 2026-10-03 audit claimed the opposite based on a
        // stale comment; this pins the real behaviour.
        assertFalse(FallDamagePolicy.searchIgnoresFallDamage(false, true, true));
    }

    @Test
    void relaxedOverridesEverything() {
        assertTrue(FallDamagePolicy.searchIgnoresFallDamage(true, true, true));
        assertTrue(FallDamagePolicy.searchIgnoresFallDamage(true, false, true));
        assertTrue(FallDamagePolicy.searchIgnoresFallDamage(true, false, false));
    }

    @Test
    void ignoreWithoutAvoidDisablesTheGuard() {
        // The only way (short of the relaxed override) to search without the fall
        // guard: explicitly declare falls acceptable AND opt the pathfinder out.
        assertTrue(FallDamagePolicy.searchIgnoresFallDamage(false, true, false));
    }

    @Test
    void noIgnoreMeansGuardAlwaysRuns() {
        assertFalse(FallDamagePolicy.searchIgnoresFallDamage(false, false, true));
        assertFalse(FallDamagePolicy.searchIgnoresFallDamage(false, false, false));
    }

    @Test
    void fullTruthTableMatchesHistoricalFormula() {
        boolean[] b = {false, true};
        for (boolean relaxed : b)
            for (boolean ignore : b)
                for (boolean avoid : b) {
                    boolean expected = relaxed || (ignore && !avoid);
                    boolean actual = FallDamagePolicy.searchIgnoresFallDamage(relaxed, ignore, avoid);
                    String msg = String.format("relaxed=%b ignore=%b avoid=%b", relaxed, ignore, avoid);
                    assertTrue(expected == actual, msg);
                }
    }
}
