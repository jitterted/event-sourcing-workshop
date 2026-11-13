package dev.ted.jitterticket.eventsourced.application;

/**
 * This is an abstract class instead of an interface, because EventHandler is an abstract class
 */
public abstract class ReadModel<STATE, DELTA extends ReadModelDelta>
        extends EventHandler {

    /**
     * Returns the full, current state of the Read Model
     */
    public abstract Checkpointed<STATE> current();

    protected abstract Checkpoint checkpoint();

    /**
     * Returns any uncommitted changes in the Read Model,
     * clearing it as a result.
     */
    public abstract Checkpointed<DELTA> flush();

}
