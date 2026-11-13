package dev.ted.jitterticket.eventsourced.application;

public interface ReadModelPersistencePort<STATE, DELTA extends ReadModelDelta> {

    Checkpointed<STATE> loadSnapshot();

    ReadModel<STATE, DELTA> loadReadModel();

    void saveDelta(Checkpointed<DELTA> checkpointed);
}
