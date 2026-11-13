package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.stream.Stream;

public class DurableReadModel<STATE, DELTA extends ReadModelDelta>
        implements EventStreamConsumer {

    private static final Logger log = LoggerFactory.getLogger(DurableReadModel.class);
    private final ReadModel<STATE, DELTA> readModel;
    private final ReadModelPersistencePort<STATE, DELTA> readModelPersistencePort;
    private Checkpoint lastWrittenCheckpoint;

    public DurableReadModel(
            ReadModelPersistencePort<STATE, DELTA> readModelPersistencePort,
            EventStore<?, ? extends Event, ?> eventStore) {
        this.readModelPersistencePort = readModelPersistencePort;

        this.readModel = readModelPersistencePort.loadReadModel();
        this.lastWrittenCheckpoint = this.readModel.checkpoint();

        Set<Class<? extends Event>> handledEventTypes = this.readModel.handledEventTypes();

        log.info("Catching up on events for event types: {}", handledEventTypes);

        Stream<? extends Event> eventStream = eventStore.allEventsAfter(lastWrittenCheckpoint, handledEventTypes);
        log.info("Fetched events, now handling them...");
        handle(eventStream);

        log.info("Subscribing to event store for event types: {}", handledEventTypes);
        eventStore.subscribe(this, handledEventTypes);
    }

    @Override
    public void handle(Stream<? extends Event> eventStream) {
        readModel.handle(eventStream);
        Checkpointed<DELTA> checkpointedDelta = readModel.flush();

        if (checkpointedDelta.checkpoint().newerThan(lastWrittenCheckpoint)) {
            lastWrittenCheckpoint = checkpointedDelta.checkpoint();
            readModelPersistencePort.saveDelta(checkpointedDelta);
        }
    }

    public STATE projection() {
        return readModel.current().state();
    }
}
