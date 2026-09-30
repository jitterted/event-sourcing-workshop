package dev.ted.jitterticket.orm;

import dev.ted.jitterticket.support.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertRepositoryTest {
    @Test
    void reschedulePersistsThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId concertId = ConcertId.createRandom();
        Concert concert = Concert.schedule(
                concertId, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        // loads snapshot, reconstitutes it
        Concert loadedConcert = repo.findById(concertId).orElseThrow();
        assertThat(loadedConcert)
                .extracting(Concert::getId, Concert::artist)
                .containsExactly(concertId, "Headliner");

        // change state via command execution
        LocalDateTime newShowDateTime = LocalDateTime.of(2026, 11, 12, 20, 0);
        loadedConcert.rescheduleTo(newShowDateTime, LocalTime.of(19, 0));

        // extracts the snapshot (the "back door") and stores it
        repo.save(loadedConcert);

        // verify the state changed was persisted
        assertThat(repo.findById(concertId))
                .as("Should have found Concert with ID: " + concertId)
                .isPresent()
                .map(Concert::showDateTime)
                .as("Should have the new show date-time")
                .hasValue(newShowDateTime);

    }

    @Test
    void rescheduleToEarlierDateTimeIsIgnored() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId concertId = ConcertId.createRandom();
        LocalDateTime originalShowDateTime = LocalDateTime.of(2026, 11, 11, 20, 0);
        Concert concert = Concert.schedule(
                concertId, "Headliner", 35,
                originalShowDateTime, LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        Concert loadedConcert = repo.findById(concertId).orElseThrow();
        LocalDateTime newShowDateTimeOneDayBeforeOriginal = originalShowDateTime.minusDays(1);
        loadedConcert.rescheduleTo(newShowDateTimeOneDayBeforeOriginal, LocalTime.of(19, 0));
        repo.save(loadedConcert);

        assertThat(repo.findById(concertId))
                .as("Should have found Concert with ID: " + concertId)
                .isPresent()
                .map(Concert::showDateTime)
                .as("Should still have the original show date-time")
                .hasValue(originalShowDateTime);
    }

    @Test
    void soldTicketsReduceAvailabilityThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId concertId = ConcertId.createRandom();
        Concert concert = Concert.schedule(
                concertId, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        // loads snapshot, reconstitutes it
        Concert loadedConcert = repo.findById(concertId).orElseThrow();
        loadedConcert.sellTicketsTo(CustomerId.createRandom(), 98);

        // extracts the snapshot (the "back door") and stores it
        repo.save(loadedConcert);

        // availableTicketCount is never exposed: the reduced availability is
        // only observable through the behavior that depends on it
        Concert reloadedConcert = repo.findById(concertId).orElseThrow();
        assertThat(reloadedConcert.sellTicketsTo(CustomerId.createRandom(), 3))
                .as("Only 2 of the 100 tickets remain, so 3 can not be sold")
                .isEqualTo(Result.rejected(ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE));
        assertThat(reloadedConcert.sellTicketsTo(CustomerId.createRandom(), 2))
                .as("The last 2 tickets can be sold")
                .isEqualTo(Result.succeeded());
    }

    @Test
    void concertWithSoldTicketsIsNotRescheduled() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId concertId = ConcertId.createRandom();
        LocalDateTime originalShowDateTime = LocalDateTime.of(2026, 11, 11, 20, 0);
        Concert concert = Concert.schedule(
                concertId, "Headliner", 35,
                originalShowDateTime, LocalTime.of(19, 0),
                100, 4);
        concert.sellTicketsTo(CustomerId.createRandom(), 1);
        repo.save(concert);

        Concert loadedConcert = repo.findById(concertId).orElseThrow();
        loadedConcert.rescheduleTo(originalShowDateTime.plusDays(1), LocalTime.of(19, 0));
        repo.save(loadedConcert);

        assertThat(repo.findById(concertId))
                .as("Should have found Concert with ID: " + concertId)
                .isPresent()
                .map(Concert::showDateTime)
                .as("Should still have the original show date-time")
                .hasValue(originalShowDateTime);
    }

    @Test
    void stopTicketSalesPersistsThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId concertId = ConcertId.createRandom();
        Concert concert = Concert.schedule(
                concertId, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        // loads snapshot, reconstitutes it
        Concert loadedConcert = repo.findById(concertId).orElseThrow();
        assertThat(loadedConcert.ticketsOnSale())
                .as("Tickets should be on-sale (true)")
                .isTrue();

        // change state via command execution
        loadedConcert.stopTicketSales();

        // extracts the snapshot (the "back door") and stores it
        repo.save(loadedConcert);

        // verify the state changed was persisted
        assertThat(repo.findById(concertId))
                .as("No Concert with id was found: " + concertId)
                .isPresent()
                .map(Concert::ticketsOnSale)
                .as("Tickets should NOT be on-sale")
                .hasValue(false);

    }

}