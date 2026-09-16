package dev.ted.jitterticket.eventsourced.adapter.out.store.jdbc;

import dev.ted.jitterticket.Gatherers;
import dev.ted.jitterticket.eventsourced.adapter.out.store.EventDto;
import dev.ted.jitterticket.eventsourced.application.BaseEventStore;
import dev.ted.jitterticket.eventsourced.application.Checkpoint;
import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.Event;
import dev.ted.jitterticket.eventsourced.domain.EventSourcedAggregate;
import dev.ted.jitterticket.eventsourced.domain.Id;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Same behavior as {@link JdbcEventStore}, but uses {@link JdbcClient} with a hand-written
 * {@link RowMapper} that goes straight to {@link EventDto}, instead of Spring Data JDBC's
 * reflection-based mapping to {@link EventDbo}.
 */
public class JdbcClientEventStore<ID extends Id, EVENT extends Event, AGGREGATE extends EventSourcedAggregate<EVENT, ID>> extends BaseEventStore<ID, EVENT, AGGREGATE> {

    private static final Logger log = LoggerFactory.getLogger(JdbcClientEventStore.class);

    // Inserting from unnest() makes a save a single (atomic) statement no matter how many events,
    // and WITH ORDINALITY + ORDER BY keeps event_sequence assignment in the same order as the events
    private static final String INSERT_EVENTS_SQL = """
            INSERT INTO events (aggregate_root_id, event_type, json)
            SELECT e.aggregate_root_id, e.event_type, e.json
              FROM unnest(CAST(:aggregateRootIds AS uuid[]),
                          CAST(:eventTypes AS text[]),
                          CAST(:jsons AS text[]))
                   WITH ORDINALITY AS e(aggregate_root_id, event_type, json, ordinal)
             ORDER BY e.ordinal
            RETURNING aggregate_root_id, event_sequence, event_type, json
            """;

    private static final String EVENTS_FOR_AGGREGATE_SQL = """
            SELECT aggregate_root_id, event_sequence, event_type, json
              FROM events
             WHERE aggregate_root_id = :aggregateRootId
             ORDER BY event_sequence
            """;

    private static final String EVENTS_AFTER_SQL = """
            SELECT aggregate_root_id, event_sequence, event_type, json
              FROM events
             WHERE event_sequence > :afterSequence
               AND event_type IN (:eventTypes)
             ORDER BY event_sequence
            """;

    private final JdbcClient jdbcClient;
    private final Class<EVENT> baseEventClass;
    private final List<String> defaultEventTypes;
    private final RowMapper<EventDto<EVENT>> eventDtoRowMapper =
            (rs, _) -> new EventDto<>(rs.getObject("aggregate_root_id", UUID.class),
                                      rs.getLong("event_sequence"),
                                      rs.getString("event_type"),
                                      rs.getString("json"));

    private JdbcClientEventStore(Function<List<EVENT>, AGGREGATE> eventsToAggregate,
                                 JdbcClient jdbcClient,
                                 Class<EVENT> baseEventClass) {
        super(eventsToAggregate);
        this.jdbcClient = jdbcClient;
        this.baseEventClass = baseEventClass;
        this.defaultEventTypes = Event.allConcreteImplementationsOf(baseEventClass).stream().map(Class::getName).toList();
    }


    //region Creation Methods for Specific Aggregate Types
    public static EventStore<ConcertId, ConcertEvent, Concert> forConcerts(JdbcClient jdbcClient) {
        return new JdbcClientEventStore<>(Concert::reconstitute, jdbcClient, ConcertEvent.class);
    }

    public static EventStore<CustomerId, CustomerEvent, Customer> forCustomers(JdbcClient jdbcClient) {
        return new JdbcClientEventStore<>(Customer::reconstitute, jdbcClient, CustomerEvent.class);
    }
    //endregion


    @Override
    protected @Nonnull List<EventDto<EVENT>> eventDtosFor(ID id) {
        return jdbcClient.sql(EVENTS_FOR_AGGREGATE_SQL)
                         .param("aggregateRootId", id.id())
                         .query(eventDtoRowMapper)
                         .list();
    }

    @Override
    public Stream<EVENT> save(ID aggregateId, Stream<EVENT> uncommittedEvents) {
        List<EventDto<EVENT>> dtos = uncommittedEvents
                .map(event -> EventDto.from(aggregateId.id(), null, event))
                .toList();
        if (dtos.isEmpty()) {
            return Stream.empty();
        }
        List<EventDto<EVENT>> savedDtos =
                jdbcClient.sql(INSERT_EVENTS_SQL)
                          .param("aggregateRootIds", dtos.stream().map(EventDto::getAggregateRootId).toArray(UUID[]::new))
                          .param("eventTypes", dtos.stream().map(EventDto::getEventType).toArray(String[]::new))
                          .param("jsons", dtos.stream().map(EventDto::getJson).toArray(String[]::new))
                          .query(eventDtoRowMapper)
                          .list();
        // RETURNING doesn't guarantee row order, so restore event sequence order
        return mapToDomainEvents(savedDtos.stream()
                                          .sorted((a, b) -> Long.compare(a.getEventSequence(), b.getEventSequence())));
    }

    @Override
    public Stream<EVENT> allEventsAfter(Checkpoint checkpoint) {
        return allEventsAfter(checkpoint, Collections.emptySet());
    }

    @Override
    public final Stream<EVENT> allEventsAfter(Checkpoint checkpoint, Set<Class<? extends Event>> onlyEventTypes) {
        List<String> eventTypes;
        if (onlyEventTypes.isEmpty()) {
            eventTypes = defaultEventTypes;
        } else {
            eventTypes = onlyEventTypes.stream()
                                       .map(Class::getName)
                                       .toList();
        }
        log.info("Fetching List<EventDto> via JdbcClient with event types = {}, after event sequence: {}", eventTypes, checkpoint);
        List<EventDto<EVENT>> eventsAfter =
                jdbcClient.sql(EVENTS_AFTER_SQL)
                          .param("afterSequence", checkpoint.value())
                          .param("eventTypes", eventTypes)
                          .query(eventDtoRowMapper)
                          .list();
        log.info("Fetched {} events, mapping to Domain events", eventsAfter.size());
        return mapToDomainEvents(eventsAfter.stream());
    }

    private Stream<EVENT> mapToDomainEvents(Stream<EventDto<EVENT>> eventDtos) {
        return eventDtos
                .map(EventDto::toDomain)
                .gather(Gatherers.filterAndCastTo(baseEventClass));
    }
}
