package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertScheduled;
import dev.ted.jitterticket.eventsourced.domain.concert.TicketsSold;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ConcertSalesReadModelTest {

    private static final int MAX_CAPACITY = 100;
    private static final int MAX_TICKETS_PER_PURCHASE = 8;
    private static final Stream<ConcertEvent> EMPTY_CONCERT_EVENT_STREAM = Stream.empty();

    @Test
    void noScheduledConcertsReturnsNoSalesSummaries() {
        ConcertSalesReadModel projector = new ConcertSalesReadModel();

        ConcertSalesReadModel.ReadModelResult readModelResult =
                projector.project(Collections.emptyMap(),
                                  EMPTY_CONCERT_EVENT_STREAM, 0L);

        assertThat(readModelResult.lastEventSequenceSeen())
                .isZero();
        assertThat(readModelResult.salesSummaries())
                .as("Expected the SET of Concert Sales to be empty")
                .isEmpty();
    }

    @Test
    void singleConcertScheduledOnly_ProjectsSingleRow() {
        ConcertSalesReadModel projector = new ConcertSalesReadModel();
        ConcertId concertId = new ConcertId(UUID.fromString("123e4567-e89b-42d3-a456-556642440000"));
        LocalDateTime showDateTime = LocalDateTime.of(2026, 1, 1, 20, 0);
        long eventSequence = 23L;
        Stream<ConcertEvent> concertEvents = Stream.of(
                new ConcertScheduled(concertId,
                                     eventSequence,
                                     "The Dessy Bells",
                                     42,
                                     showDateTime,
                                     LocalTime.now(),
                                     MAX_CAPACITY,
                                     MAX_TICKETS_PER_PURCHASE)
        );

        ConcertSalesReadModel.ReadModelResult readModelResult =
                projector.project(Collections.emptyMap(), concertEvents, 0L);

        assertThat(readModelResult.lastEventSequenceSeen())
                .as("Last event sequence seen should be the event sequence of the single event processed")
                .isEqualTo(eventSequence);
        assertThat(readModelResult.salesSummaries())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new ConcertSalesReadModel.ConcertSalesSummary(
                        concertId,
                        "The Dessy Bells",
                        showDateTime,
                        0, 0));
    }

    @Test
    void singleConcertScheduled_SingleTicketPurchase_ProjectsSingleRowWithTicketSales() {
        ConcertSalesReadModel projector = new ConcertSalesReadModel();
        ConcertId concertId = new ConcertId(UUID.fromString("123e4567-e89b-42d3-a456-556642440000"));
        LocalDateTime showDateTime = LocalDateTime.of(2026, 1, 1, 20, 0);
        long firstEventSequence = 27L;
        long secondEventSequence = 29L;
        Stream<ConcertEvent> concertEvents = Stream.of(
                new ConcertScheduled(concertId,
                                     firstEventSequence,
                                     "The Dessy Bells",
                                     75,
                                     showDateTime,
                                     LocalTime.now(),
                                     MAX_CAPACITY,
                                     MAX_TICKETS_PER_PURCHASE),
                new TicketsSold(concertId, secondEventSequence, 4, 4 * 75)
        );

        ConcertSalesReadModel.ReadModelResult readModelResult =
                projector.project(Collections.emptyMap(), concertEvents, 0L);

        assertThat(readModelResult.lastEventSequenceSeen())
                .as("Last event sequence seen should be the event sequence of the last event processed")
                .isEqualTo(secondEventSequence);
        assertThat(readModelResult.salesSummaries())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(new ConcertSalesReadModel.ConcertSalesSummary(
                        concertId,
                        "The Dessy Bells",
                        showDateTime,
                        4,
                        4 * 75));
    }

    @Test
    void multipleConcerts_MultipleTicketsSoldPerConcert_ProjectsRowPerConcert() {
        ConcertId firstConcertId = ConcertId.createRandom();
        ConcertId secondConcertId = ConcertId.createRandom();
        Stream<ConcertEvent> concertEventStream = MakeEvents.with()
                          .concertScheduled(firstConcertId, (concert) -> concert
                                  .ticketPrice(75).ticketsSold(4))
                          .concertScheduled(secondConcertId, (concert) -> concert
                                  .ticketPrice(50).ticketsSold(2).ticketsSold(5))
                .stream();

        ConcertSalesReadModel readModel = new ConcertSalesReadModel();
        ConcertSalesReadModel.ReadModelResult readModelResult =
                readModel.project(Collections.emptyMap(), concertEventStream, 0L);

        assertThat(readModelResult.salesSummaries())
                .extracting(ConcertSalesReadModel.ConcertSalesSummary::concertId,
                            ConcertSalesReadModel.ConcertSalesSummary::totalQuantity,
                            ConcertSalesReadModel.ConcertSalesSummary::totalSales)
                .containsExactlyInAnyOrder(
                        tuple(firstConcertId, 4, 75 * 4),
                        tuple(secondConcertId, 2 + 5, 50 * (2 + 5)));
    }

    @Test
    void singleConcertRescheduled_ProjectsUpdatedDate() {
        ConcertId concertId = ConcertId.createRandom();
        LocalDateTime newShowDateTime = LocalDateTime.of(2026, 1, 1, 19, 30);
        LocalTime newDoorsTime = LocalTime.of(18, 30);
        Stream<ConcertEvent> concertEventStream =
                MakeEvents.with()
                          .concertScheduled(concertId, Function.identity())
                          .reschedule(concertId, newShowDateTime, newDoorsTime)
                          .stream();

        ConcertSalesReadModel concertSalesReadModel = new ConcertSalesReadModel();
        ConcertSalesReadModel.ReadModelResult readModelResult =
                concertSalesReadModel.project(Collections.emptyMap(),
                                              concertEventStream, 0L);

        assertThat(readModelResult.salesSummaries())
                .extracting(ConcertSalesReadModel.ConcertSalesSummary::concertId,
                            ConcertSalesReadModel.ConcertSalesSummary::showDateTime)
                .containsExactly(
                        tuple(concertId, newShowDateTime)
                );
    }

    @Test
    void updatesPreviouslyComputedProjectionWithNewEvents() {
        ConcertId concertId = ConcertId.createRandom();
        LocalDateTime showDateTime = LocalDateTime.of(2026, 12, 21, 20, 0);
        List<ConcertEvent> concertScheduledWithTicketsSoldEvents =
                MakeEvents.with()
                          .concertScheduled(concertId, (concert) -> concert
                                  .artistNamed("Artist Name")
                                  .showDateTime(showDateTime)
                                  .ticketPrice(35)
                                  .ticketsSold(6))
                          .list();
        ConcertEvent concertScheduledEvent = concertScheduledWithTicketsSoldEvents.getFirst();
        ConcertEvent ticketsSoldEvent = concertScheduledWithTicketsSoldEvents.getLast();

        ConcertSalesReadModel projector = new ConcertSalesReadModel();
        ConcertSalesReadModel.ReadModelResult initialProjection =
                projector.project(Collections.emptyMap(), Stream.of(concertScheduledEvent), 0L);

        Map<ConcertId, ConcertSalesReadModel.ConcertSalesSummary> secondSalesSummaryMap =
                mapFromProjection(initialProjection);
        ConcertSalesReadModel.ReadModelResult finalProjection =
                projector.project(secondSalesSummaryMap, Stream.of(ticketsSoldEvent), 0L);

        assertThat(finalProjection.lastEventSequenceSeen())
                .isEqualTo(ticketsSoldEvent.eventSequence());
        assertThat(finalProjection.salesSummaries())
                .containsExactly(new ConcertSalesReadModel.ConcertSalesSummary(
                        concertId,
                        "Artist Name",
                        showDateTime,
                        6, 6 * 35
                ));
    }

    private static Map<ConcertId, ConcertSalesReadModel.ConcertSalesSummary> mapFromProjection(ConcertSalesReadModel.ReadModelResult initialProjection) {
        return initialProjection.salesSummaries()
                                .stream()
                                .collect(Collectors.toMap(ConcertSalesReadModel.ConcertSalesSummary::concertId, Function.identity()));
    }

    @Test
    void lastEventSequenceSeenUnchangedWhenProcessingEmptyEventStream() {
        Stream<ConcertEvent> concertEvents = MakeEvents.with()
                .concertScheduled()
                .stream();
        ConcertSalesReadModel projector = new ConcertSalesReadModel();
        ConcertSalesReadModel.ReadModelResult initialReadModelResult =
                projector.project(Collections.emptyMap(), concertEvents, 0L);

        ConcertSalesReadModel.ReadModelResult finalReadModelResult =
                projector.project(mapFromProjection(initialReadModelResult),
                                  Stream.empty(), initialReadModelResult.lastEventSequenceSeen());

        assertThat(finalReadModelResult.lastEventSequenceSeen())
                .as("The last event sequence seen should remain unchanged when processing an empty event stream")
                .isEqualTo(initialReadModelResult.lastEventSequenceSeen());
    }

    @Test
    void ticketsSoldWhenConcertScheduledEventNotSeenIsIgnoredThenProjectionIsEmpty() {
        ConcertId concertId = ConcertId.createRandom();
        TicketsSold ticketsSold = new TicketsSold(concertId, 1L, 3, 75);

        ConcertSalesReadModel concertSalesReadModel = new ConcertSalesReadModel();
        ConcertSalesReadModel.ReadModelResult readModelResult =
                concertSalesReadModel.project(Collections.emptyMap(),
                                              Stream.of(ticketsSold), 0L);

        assertThat(readModelResult.salesSummaries())
                .isEmpty();
    }


    // TODO: test against the ConcertSalesSummary record "withers" directly,
    //       i.e., the plusTicketsSold and the reschedule

}

