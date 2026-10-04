package kaptainwutax.tungsten.input;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central input-ownership registry — the arbiter the audit asked for (angle 2).
 *
 * <p><b>What it is:</b> one place that knows WHICH subsystem owns WHICH key in
 * WHICH tick. Every writer group names itself when it presses; the arbiter says
 * whether the claim is uncontended and records every conflict.
 *
 * <p><b>What it is NOT (yet):</b> a gatekeeper over all ~230 tungsten
 * {@code setPressed} sites. The physical order on this client is deterministic —
 * altoclef's task runner writes at {@code MinecraftClient.tick} HEAD, tungsten's
 * drivers write at {@code ClientPlayerEntity.tick} HEAD (later in the same tick),
 * and {@code Movement.applyInputs} re-applies tungsten's declaration after wiping
 * the keys — so wholesale interception would mostly re-implement the existing
 * phase discipline with new ways to be wrong (see G-0: making {@code Nav.pause}
 * do something cost six courses). What the phase order does NOT cover is the
 * secondary writers: ApproachLatch, CombatMoveIntent, Movement's applyInputs,
 * altoclef's InputControls presses, and the external py4j primitives
 * (AgentActionButtons, mouseClick). Those register here. Still unregistered
 * (planned, not claimed): ProjectileDodge, VoidGuard, TriggerBot's sprint
 * release, PathExecutor's inline presses — they stay under the phase discipline
 * until their claim points land.
 *
 * <p><b>Mode:</b> shadow by default ({@code TungstenConfig.inputArbiterEnforce}).
 * In shadow mode a contended claim still records a conflict and the caller may
 * write physically; in enforce mode the caller MUST skip the write when
 * {@link #claim} returns false. Watch the conflict log for a while, then flip.
 *
 * <p>Thread model: claims happen on the client thread. The tick id comes from the
 * caller (altoclef's ClientTickEvent counter or tungsten's driver tick); keys are
 * identified by their translation-key-ish name so no Minecraft classes are needed
 * and the registry stays unit-testable.
 */
public final class InputArbiter {

    /** Writer groups that may own keys for a tick. */
    public enum Domain {
        ALTOCLEF_TASKS("altoclef task runner (InputControls)"),
        TUNGSTEN_MOVEMENT("tungsten MovementQueue"),
        TUNGSTEN_EXECUTOR("tungsten PathExecutor"),
        TUNGSTEN_COMBAT("tungsten combat (latch/intent/dodge)"),
        EXTERNAL("external py4j / agent UI primitives");

        public final String label;

        Domain(String label) {
            this.label = label;
        }
    }

    private static final Map<String, Domain> OWNED = new HashMap<>();
    private static long ownedTick = Long.MIN_VALUE;

    // Shared game-tick id. Bumped ONCE per client tick by altoclef's onTickPre
    // (MinecraftClient.tick HEAD), read by every driver later in the same tick.
    private static volatile long tick = 0L;

    private static final AtomicLong CONFLICTS = new AtomicLong();
    private static volatile String lastConflictSummary = "";

    private InputArbiter() {
    }

    /** Bump the shared game-tick id. Called from altoclef's onTickPre only. */
    public static void advanceTick() {
        tick++;
    }

    /** The shared game-tick id for this client tick's claims. */
    public static long currentTick() {
        return tick;
    }

    /**
     * Claim a key for this tick.
     *
     * @return true when the claim is uncontended (or already ours). false means
     * another domain owns the key this tick — in enforce mode the caller must
     * skip the physical write.
     */
    public static synchronized boolean claim(long tick, Domain domain, String key) {
        if (tick != ownedTick) {
            OWNED.clear();
            ownedTick = tick;
        }
        Domain existing = OWNED.get(key);
        if (existing == domain) return true;
        if (existing != null) {
            long n = CONFLICTS.incrementAndGet();
            lastConflictSummary = "tick " + tick + ": " + domain.label + " wants '" + key
                    + "' but " + existing.label + " already owns it (conflict #" + n + ")";
            if (n % 50 == 1) {
                System.out.println("[InputArbiter] " + lastConflictSummary);
            }
            return false;
        }
        OWNED.put(key, domain);
        return true;
    }

    /** Who owns the key this tick, or null. */
    public static synchronized Domain owner(long tick, String key) {
        if (tick != ownedTick) return null;
        return OWNED.get(key);
    }

    /** Convenience over owner(): is this domain the owner? */
    public static synchronized boolean isOwner(long tick, Domain domain, String key) {
        return owner(tick, key) == domain;
    }

    /** Total conflicts since boot (for the py4j telemetry surface). */
    public static long conflictCount() {
        return CONFLICTS.get();
    }

    /** Human-readable last conflict, for logs/telemetry. */
    public static String lastConflictSummary() {
        return lastConflictSummary;
    }

    /** Test-only wipe. */
    static void resetForTest() {
        synchronized (InputArbiter.class) {
            OWNED.clear();
            ownedTick = Long.MIN_VALUE;
            lastConflictSummary = "";
            CONFLICTS.set(0);
        }
    }
}
