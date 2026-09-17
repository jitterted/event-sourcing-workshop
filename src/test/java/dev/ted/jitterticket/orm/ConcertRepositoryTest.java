package dev.ted.jitterticket.orm;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConcertRepositoryTest {
    @Test
    void reschedulePersistsThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId id = ConcertId.createRandom();
        Concert concert = Concert.schedule(
                id, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        // loads snapshot, reconstitutes it
        Concert loadedConcert = repo.findById(id).orElseThrow();
        assertThat(loadedConcert)
                .extracting(Concert::getId, Concert::artist, Concert::availableTicketCount)
                .containsExactly(id, "Headliner", 100);

        // change state via command execution
        LocalDateTime newShowDateTime = LocalDateTime.of(2026, 11, 12, 20, 0);
        loadedConcert.rescheduleTo(newShowDateTime, LocalTime.of(19, 0));

        // extracts the snapshot (the "back door") and stores it
        repo.save(loadedConcert);

        // verify the state changed was persisted
        assertThat(repo.findById(id))
                .as("No Concert with id was found: " + id)
                .isPresent()
                .map(Concert::showDateTime)
                .as("Should have the new show date-time")
                .hasValue(newShowDateTime);

    }

    @Test
    void stopTicketSalesPersistsThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId id = ConcertId.createRandom();
        Concert concert = Concert.schedule(
                id, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0),
                100, 4);
        repo.save(concert);

        // loads snapshot, reconstitutes it
        Concert loadedConcert = repo.findById(id).orElseThrow();
        assertThat(loadedConcert.ticketsOnSale())
                .as("Tickets should be on-sale (true)")
                .isTrue();

        // change state via command execution
        loadedConcert.stopTicketSales();

        // extracts the snapshot (the "back door") and stores it
        repo.save(loadedConcert);

        // verify the state changed was persisted
        assertThat(repo.findById(id))
                .as("No Concert with id was found: " + id)
                .isPresent()
                .map(Concert::ticketsOnSale)
                .as("Tickets should NOT be on-sale")
                .hasValue(false);

    }

}