package kaptainwutax.tungsten.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MLG bucket trigger policy (audit angle 5, 2026-10-04).
 *
 * <p>Problem being fixed: {@code MLGBucketFallChain} used to return priority 100
 * for ANY fall faster than -0.7 b/t — a combat knockback or a hop down a single
 * block would steal the body from a battle, and {@code MobDefenseChain} yields
 * whenever {@code isFalling()} says so. The gate now abstains unless the fall
 * would actually deal damage.
 *
 * <p>Vanilla math: damage starts at {@code floor(distance - 3)}, i.e. the first
 * real damage needs 4 blocks. The policy triggers at >= 3 (the audit-flagged
 * harm boundary, one block of safety margin before actual damage) — it is
 * deliberately biased towards SAVING the bot, only abstaining on falls that are
 * provably harmless.
 */
class MlgPolicyTest {

    @Test
    void harmlessShortFallAbstains() {
        // A hop onto a 2.5-block lower ledge with no accumulated fall distance:
        // the old code MLG'd this and handed combat control away for nothing.
        assertFalse(MlgPolicy.fallWouldDealDamage(0.0, 2.5, 3.0));
    }

    @Test
    void longFallTriggers() {
        assertTrue(MlgPolicy.fallWouldDealDamage(0.0, 10.0, 3.0));
    }

    @Test
    void accumulatedFallDistanceCounts() {
        // Already fell 2.0 and the ground is 1.5 further down: 3.5 >= 3.
        assertTrue(MlgPolicy.fallWouldDealDamage(2.0, 1.5, 3.0));
    }

    @Test
    void boundaryTriggersConservatively() {
        // Exactly at the boundary: real damage would need one more block, but the
        // policy biases towards catching the fall.
        assertTrue(MlgPolicy.fallWouldDealDamage(0.0, 3.0, 3.0));
    }

    @Test
    void triggerGateCombinesAllConditions() {
        // settings off -> never
        assertFalse(MlgPolicy.shouldTrigger(false, true, true, true));
        // not falling -> never
        assertFalse(MlgPolicy.shouldTrigger(true, false, true, true));
        // settings on + falling + harmless fall + harmful-only mode -> abstain
        assertFalse(MlgPolicy.shouldTrigger(true, true, false, true));
        // harmful fall -> trigger
        assertTrue(MlgPolicy.shouldTrigger(true, true, true, true));
        // harmful-only disabled -> old behaviour (any fall triggers)
        assertTrue(MlgPolicy.shouldTrigger(true, true, false, false));
    }
}
