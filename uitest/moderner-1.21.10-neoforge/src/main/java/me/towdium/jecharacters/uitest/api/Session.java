package me.towdium.jecharacters.uitest.api;

/**
 * How much of the client session a scenario needs for itself — the tier a run's restart schedule is
 * built from.
 *
 * <p>A run is expensive to restart and cheap to keep: a client that is already in the right world
 * with the right pinned options can run any number of scenarios that only touch the world's contents
 * or the screen stack, because the harness puts the player where a scenario expects them and every
 * scenario's own teardown clears what it built. Restarting anyway would pay the world load, the JEI
 * load and the chunk load again for no observable difference. So a scenario declares the least it
 * needs, and the runner computes the smallest set of restarts that satisfies every scenario in the
 * selection.
 *
 * <p>The tiers are ordered by what they need, and the runner runs them in that order — the cheapest
 * first — because a restart that satisfies a stronger tier also satisfies a weaker one:
 *
 * @see ScenarioOptions#session()
 */
public enum Session {

    /**
     * The default: this scenario is satisfied by a per-scenario reset of screens, containers and the
     * player's pose, in the world the run already has. Anything a previous scenario left behind that
     * a scenario can see is either cleaned by that scenario's own teardown or is part of the fixture
     * the scenario asserts on — the acceptance tests of this tier are the session proofs in
     * {@code jechtest.session*}.
     */
    shared,

    /**
     * This scenario needs a session that is not the previous one — the client's own per-join state
     * (the level objects, the client-side player, what a join resets) must be new — while the world
     * and its persisted contents must be the same ones. Satisfied by leaving and rejoining the same
     * save: no world creation, no re-generation, and blocks and block entities placed by earlier
     * scenarios are still there.
     */
    reenter,

    /**
     * This scenario needs a world that has never been in any other scenario's hands: it asserts on
     * what a brand-new world looks like, or on the absence of what earlier scenarios placed.
     * Satisfied by creating a fresh save, which is the only tier that pays a world load.
     */
    fresh;

    /** Reads the wire spelling the report carries; an unknown value is {@link #shared}. */
    public static Session fromString(String raw) {
        for (Session tier : values()) {
            if (tier.name().equalsIgnoreCase(raw.trim())) return tier;
        }
        return shared;
    }
}
