package kaptainwutax.tungsten.combat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Single decision point for the shield between the TWO raise channels that used
 * to act independently (audit angle 9, 2026-10-04):
 *
 * <ul>
 *   <li>{@code MobDefenseChain.startShielding/stopShielding} — altoclef side,
 *       presses SNEAK + RIGHT_CLICK via InputControls, condition evaluated per
 *       tick from a projectile snapshot;</li>
 *   <li>the tungsten engine arm in {@code CombatController} — holds the use key
 *       directly via {@code ShieldBlocker} while the attack cooldown recharges.</li>
 * </ul>
 *
 * <p>Before the authority, both could act in the SAME tick with no knowledge of
 * each other — the audit's "举盾/收盾在同一秒来回切". The rule: the shield stays
 * up while ANY source wants it up; a source may only stop pressing when it is
 * not the last raiser.
 */
class ShieldAuthorityTest {

    @AfterEach
    void resetState() {
        ShieldAuthority.resetForTest();
    }

    @Test
    void startsDown() {
        assertFalse(ShieldAuthority.isAnyRaising());
    }

    @Test
    void oneRaiserKeepsItUpAgainstTheOthersRelease() {
        ShieldAuthority.raise(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        ShieldAuthority.raise(ShieldAuthority.Source.TUNGSTEN_ENGINE);
        // chain lets go: engine still wants it up
        ShieldAuthority.release(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        assertTrue(ShieldAuthority.isAnyRaising());
        assertTrue(ShieldAuthority.isRaising(ShieldAuthority.Source.TUNGSTEN_ENGINE));
        assertFalse(ShieldAuthority.isRaising(ShieldAuthority.Source.ALTOCLEF_CHAIN));
    }

    @Test
    void downOnlyWhenEverySourceReleased() {
        ShieldAuthority.raise(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        ShieldAuthority.raise(ShieldAuthority.Source.TUNGSTEN_ENGINE);
        ShieldAuthority.release(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        ShieldAuthority.release(ShieldAuthority.Source.TUNGSTEN_ENGINE);
        assertFalse(ShieldAuthority.isAnyRaising());
    }

    @Test
    void releaseWithoutRaiseIsHarmless() {
        ShieldAuthority.release(ShieldAuthority.Source.TUNGSTEN_ENGINE);
        assertFalse(ShieldAuthority.isAnyRaising());
    }

    @Test
    void engineMayDeferToTheChainChannel() {
        // The engine's use-key channel must not fight the chain's SNEAK/RIGHT
        // channel: when the chain is already raising, the engine keeps its own
        // channel down.
        ShieldAuthority.raise(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        assertFalse(ShieldAuthority.engineShouldPress());
        // chain gone -> engine owns the raise
        ShieldAuthority.release(ShieldAuthority.Source.ALTOCLEF_CHAIN);
        ShieldAuthority.raise(ShieldAuthority.Source.TUNGSTEN_ENGINE);
        assertTrue(ShieldAuthority.engineShouldPress());
    }
}
