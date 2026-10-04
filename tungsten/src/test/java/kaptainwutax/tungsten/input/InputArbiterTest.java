package kaptainwutax.tungsten.input;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Central input-ownership registry (audit angle 2, 2026-10-04).
 *
 * <p>The audit found altoclef and tungsten both writing the same KeyBindings with
 * no arbiter. The physical reality on HEAD is phase-disciplined (altoclef writes
 * at MinecraftClient.tick HEAD, tungsten at ClientPlayerEntity.tick HEAD and then
 * wipes+applies from its own declaration), but THREE writer groups still bypass
 * any ownership bookkeeping: tungsten's secondary drivers (ApproachLatch,
 * CombatMoveIntent, ...), altoclef's task-runner presses, and the external/
 * py4j primitives (AgentActionButtons, mouseClick). This registry gives every
 * group a name, records per-tick claims, and reports conflicts when two domains
 * touch the same key in the same tick.
 *
 * <p>Enforcement is a separate switch (TungstenConfig.inputArbiterEnforce, default
 * OFF = shadow mode): the audit's own history (G-0, the six-courses collapse)
 * says an enforced wrong rule is worse than a logged right one, so first we
 * measure, then we enforce.
 */
class InputArbiterTest {

    @AfterEach
    void resetState() {
        InputArbiter.resetForTest();
    }

    @Test
    void firstClaimWinsTheKey() {
        InputArbiter.claim(100, InputArbiter.Domain.ALTOCLEF_TASKS, "key.forward");
        assertTrue(InputArbiter.isOwner(100, InputArbiter.Domain.ALTOCLEF_TASKS, "key.forward"));
        assertFalse(InputArbiter.claim(100, InputArbiter.Domain.TUNGSTEN_MOVEMENT, "key.forward"));
    }

    @Test
    void sameDomainMayReclaim() {
        assertTrue(InputArbiter.claim(7, InputArbiter.Domain.TUNGSTEN_EXECUTOR, "key.jump"));
        assertTrue(InputArbiter.claim(7, InputArbiter.Domain.TUNGSTEN_EXECUTOR, "key.jump"));
    }

    @Test
    void conflictIsRecorded() {
        InputArbiter.claim(5, InputArbiter.Domain.ALTOCLEF_TASKS, "key.attack");
        InputArbiter.claim(5, InputArbiter.Domain.TUNGSTEN_COMBAT, "key.attack");
        assertEquals(1, InputArbiter.conflictCount());
        assertTrue(InputArbiter.lastConflictSummary().contains("key.attack"));
    }

    @Test
    void ticksAreIndependent() {
        InputArbiter.claim(10, InputArbiter.Domain.ALTOCLEF_TASKS, "key.use");
        // next tick: nobody owns it yet
        assertFalse(InputArbiter.isOwner(11, InputArbiter.Domain.ALTOCLEF_TASKS, "key.use"));
        assertTrue(InputArbiter.claim(11, InputArbiter.Domain.TUNGSTEN_MOVEMENT, "key.use"));
        assertEquals(0, InputArbiter.conflictCount());
    }

    @Test
    void unclaimedKeyHasNoOwner() {
        assertFalse(InputArbiter.isOwner(1, InputArbiter.Domain.EXTERNAL, "key.pickItem"));
    }

    @Test
    void shadowModeStillRecordsTheContention() {
        InputArbiter.claim(3, InputArbiter.Domain.ALTOCLEF_TASKS, "key.left");
        // The contending claim is REJECTED as owner; whether the physical write also
        // happens is the caller's decision via TungstenConfig.inputArbiterEnforce.
        boolean allowed = InputArbiter.claim(3, InputArbiter.Domain.TUNGSTEN_COMBAT, "key.left");
        assertFalse(allowed);
        assertEquals(1, InputArbiter.conflictCount());
    }

    @Test
    void runtimeTickAdvanceGivesTheSharedTickId() {
        // altoclef's onTickPre (MinecraftClient.tick HEAD) bumps the shared counter
        // once per game tick; every tungsten driver later in the SAME client tick
        // must read the same id for cross-domain conflicts to be detected.
        long before = InputArbiter.currentTick();
        InputArbiter.advanceTick();
        assertEquals(before + 1, InputArbiter.currentTick());
    }
}
