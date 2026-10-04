package adris.altoclef.commands;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.commandsystem.ArgParser;
import adris.altoclef.commandsystem.Command;
import adris.altoclef.commandsystem.args.FloatArg;
import adris.altoclef.commandsystem.exception.CommandException;

/**
 * Exposes the ALREADY-EXISTING runtime knob for how hard internal self-preservation
 * chains may hit the user task chain (audit angle 5, 2026-10-04).
 *
 * <p>{@code BotBehaviour.setUserTaskChainPriority} has always been settable at
 * runtime (MurderMysteryTask does it); this just gives chat/external-agent users
 * the same lever without a task having to do it for them.
 *
 * <p>Priority ladder as of this fix (TaskRunner takes the max every tick):
 * <pre>
 *   100  WorldSurvival (lava/fire/air) · MLG bucket (harmful falls only now)
 *    90  GameMenu · WorldSurvival (water bucket)
 *    80  MobDefense "can't deal with it" flee
 *   65-70  MobDefense regular arms
 *    55  Food · Unstuck
 *    50  UserTaskChain (default — this command changes it)
 * </pre>
 * An external agent that must not be interrupted mid-command can raise this above
 * the arms it cares about; self-preservation at 100 (lava/fire/air, harmful-fall
 * MLG) is deliberately still above any reachable value for obvious reasons.
 */
public class PriorityCommand extends Command {

    private static final float MIN = 0f;
    /** Above 100 nothing external should ever sit — self-preservation owns 100. */
    private static final float MAX = 99f;

    public PriorityCommand() throws CommandException {
        super("priority", "Set the priority of YOUR tasks vs the bot's self-preservation chains (0-99, default 50)",
                new FloatArg("priority", 50f)
        );
    }

    @Override
    protected void call(AltoClef mod, ArgParser parser) throws CommandException {
        float priority = parser.get(Float.class);
        float clamped = Math.max(MIN, Math.min(MAX, priority));
        if (clamped != priority) {
            Debug.logMessage("Priority clamped to " + clamped + " (allowed range " + MIN + ".." + MAX + ")");
        }
        mod.getBehaviour().setUserTaskChainPriority(clamped);
        Debug.logMessage("User task chain priority set to " + clamped
                + " (self-preservation lava/fire/air stays at 100).");
    }
}
