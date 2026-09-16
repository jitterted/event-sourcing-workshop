package dev.ted.jitterticket.eventviewer.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.AvailableConcerts;
import dev.ted.jitterticket.eventsourced.application.AvailableConcertsDelta;
import dev.ted.jitterticket.eventsourced.application.ProjectionCoordinator;
import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.Event;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;

import java.util.List;
import java.util.UUID;

public class ConcertProjectionChoice extends ProjectionChoice {

    private final EventStore<ConcertId, ConcertEvent, Concert> concertStore;
    private final ProjectionCoordinator<ConcertEvent, AvailableConcerts, AvailableConcertsDelta> availableConcertsProjection;

    public ConcertProjectionChoice(
            EventStore<ConcertId, ConcertEvent, Concert> concertStore,
            ProjectionCoordinator<ConcertEvent, AvailableConcerts, AvailableConcertsDelta> availableConcertsProjection
    ) {
        super("Concert", "concerts");
        this.concertStore = concertStore;
        this.availableConcertsProjection = availableConcertsProjection;
    }

    @Override
    public List<AggregateSummaryView> aggregateSummaryViews() {
        return availableConcertsProjection
                .projection()
                .availableConcerts()
                .stream()
                .map(concertSummary -> new AggregateSummaryView(
                        concertSummary.concertId().id().toString(),
                        concertSummary.artist()
                ))
                .toList();
    }

    @Override
    public List<? extends Event> eventsFor(UUID uuid) {
        return concertStore.eventsForAggregate(new ConcertId(uuid));
    }

    @Override
    public ProjectedStateView projectedStateFrom(List<? extends Event> events) {
        @SuppressWarnings("unchecked")
        Concert concert = Concert.reconstitute((List<ConcertEvent>) events);
        return ProjectedStateView.ofFields(
                ProjectedStateView.Field.of("Artist", concert.artist()),
                ProjectedStateView.Field.of("Show Time", concert.showDateTime()),
                ProjectedStateView.Field.of("Doors Time", concert.doorsTime()),
                ProjectedStateView.Field.of("Tickets Remaining", concert.availableTicketCount())
        );
    }

}
