package kaptainwutax.tungsten.util;

/**
 * MLG bucket trigger policy (audit angle 5, 2026-10-04).
 *
 * <p>Used by {@code MLGBucketFallChain} so that a priority-100 MLG grab only
 * happens for falls that would actually hurt, and by {@code MobDefenseChain} so
 * its "yield to MLG" abstention matches the same rule (before this, defense
 * yielded to an MLG that often was never going to fire — a knockback hop stole
 * the whole combat stack for nothing).
 *
 * <p>Vanilla fall damage is {@code floor(distance - 3)}: the first damaged fall
 * needs 4 blocks. The boundary parameter is deliberately set at 3 (where a fall
 * STARTS to be dangerous), giving one block of safety margin — the policy only
 * ever ABSTAINS on provably harmless falls.
 */
public final class MlgPolicy {

    private MlgPolicy() {
    }

    /**
     * @param fallDistance    the player's accumulated fall distance this fall
     * @param distToGround    projected remaining distance to the first solid block below
     * @param firstDamageFall the fall at which we consider damage possible (vanilla
     *                        first damage is one block deeper)
     */
    public static boolean fallWouldDealDamage(double fallDistance, double distToGround, double firstDamageFall) {
        return fallDistance + distToGround >= firstDamageFall;
    }

    /**
     * Full trigger gate for the MLG chain.
     *
     * @param settingsOn   the user's autoMLGBucket setting
     * @param falling      the physical isFalling() check (velocity below threshold)
     * @param harmful      {@link #fallWouldDealDamage} for the current fall
     * @param onlyHarmful  the mlgBucketOnlyWhenHarmful setting; false restores the
     *                     historical any-fall-triggers behaviour
     */
    public static boolean shouldTrigger(boolean settingsOn, boolean falling, boolean harmful, boolean onlyHarmful) {
        return settingsOn && falling && (!onlyHarmful || harmful);
    }
}
