package kaptainwutax.tungsten.combat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single decision point for WHO WANTS THE SHIELD UP (audit angle 9, 2026-10-04).
 *
 * <p>Two independent channels raise the shield and, before this class, neither
 * could see the other — same tick double-raise and cross-channel release was the
 * audit's "举盾/收盾在同一秒来回切":
 * <ul>
 *   <li>{@code MobDefenseChain} (altoclef): projectile snapshot →
 *       startShielding/stopShielding → InputControls SNEAK + RIGHT_CLICK;</li>
 *   <li>{@code CombatController} engine arm: cooldown gap →
 *       {@code ShieldBlocker.hold(3)} → direct use key.</li>
 * </ul>
 *
 * <p>Contract, enforced by {@code ShieldAuthorityTest}:
 * <ul>
 *   <li>{@link #isAnyRaising()} stays true while ANY source wants the shield;</li>
 *   <li>the engine's use-key channel ({@link #engineShouldPress()}) defers to the
 *       chain's channel — pressing use while the chain already presses is the
 *       same-tick fight this class exists to end;</li>
 *   <li>{@link #release(Source)} is idempotent and never turns the shield off
 *       under another source.</li>
 * </ul>
 *
 * <p>Thread model: raise/release arrive from the client thread (chain tick) and
 * the combat tick inside the player tick — all client-thread; atomics anyway so
 * a stray render-thread read is still consistent.
 */
public final class ShieldAuthority {

    public enum Source { ALTOCLEF_CHAIN, TUNGSTEN_ENGINE }

    private static final AtomicBoolean CHAIN = new AtomicBoolean(false);
    private static final AtomicBoolean ENGINE = new AtomicBoolean(false);

    private ShieldAuthority() {
    }

    public static void raise(Source source) {
        (source == Source.ALTOCLEF_CHAIN ? CHAIN : ENGINE).set(true);
    }

    public static void release(Source source) {
        (source == Source.ALTOCLEF_CHAIN ? CHAIN : ENGINE).set(false);
    }

    public static boolean isRaising(Source source) {
        return (source == Source.ALTOCLEF_CHAIN ? CHAIN : ENGINE).get();
    }

    /** The shield must stay up: at least one source wants it. */
    public static boolean isAnyRaising() {
        return CHAIN.get() || ENGINE.get();
    }

    /**
     * May the engine's own use-key channel press? False while the altoclef chain
     * channel is already holding the shield up, so the two channels never press
     * the same key from two code paths in the same tick.
     */
    public static boolean engineShouldPress() {
        return !CHAIN.get() && ENGINE.get();
    }

    /** Test-only: wipe all state between unit tests. */
    static void resetForTest() {
        CHAIN.set(false);
        ENGINE.set(false);
    }
}
