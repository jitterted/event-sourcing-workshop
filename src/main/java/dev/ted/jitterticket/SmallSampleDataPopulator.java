package dev.ted.jitterticket;

import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import dev.ted.jitterticket.eventsourced.domain.customer.TicketsPurchased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

public class SmallSampleDataPopulator implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmallSampleDataPopulator.class);

    static final ConcertId SONIC_WAVES_CONCERT_ID = new ConcertId(UUID.fromString("123e4567-e89b-42d3-a456-556642440001"));
    private final EventStore<CustomerId, CustomerEvent, Customer> customerStore;
    private final EventStore<ConcertId, ConcertEvent, Concert> concertStore;
    private final JdbcTemplate jdbcTemplate;
    private CustomerId firstCustomerId = new CustomerId(UUID.fromString("68f5b2c2-d70d-4992-ad78-c94809ae9a6a"));
    private CustomerId secondCustomerId = new CustomerId(UUID.fromString("123e4567-e89b-42d3-a456-556642440000"));

    public SmallSampleDataPopulator(EventStore<CustomerId, CustomerEvent, Customer> customerStore,
                                    EventStore<ConcertId, ConcertEvent, Concert> concertStore,
                                    JdbcTemplate jdbcTemplate) {
        this.customerStore = customerStore;
        this.concertStore = concertStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info("Populating sample data...truncating tables first");
        jdbcTemplate.execute("TRUNCATE TABLE events, concert_sales, concert_sales_projection RESTART IDENTITY CASCADE");

        LOGGER.info("Creating sample customers...");
        populateCustomerSampleData();
        LOGGER.info("Creating sample concerts...");
        populateConcertSampleData();
        LOGGER.info("Sample data created.");
    }

    private static LocalDateTime daysFromNowAt(long days, int hour, int minute) {
        return LocalDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .plusDays(days)
                .withHour(hour)
                .withMinute(minute);
    }

    void populateConcertSampleData() {
        addSonicWavesConcertToStore();
        addSoldOutConcertToStore();

        // this concert will quickly be made unavailable as it's scheduled
        // for 10 seconds in the future
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Midnight Rebels",
                55,
                LocalDateTime.now().plusSeconds(10),
                LocalTime.of(20, 0),
                150,
                4));

// Example 2: Jazz ensemble with limited capacity
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Blue Note Quartet",
                35,
                daysFromNowAt(83, 19, 30),
                LocalTime.of(18, 30),
                75,
                2));

// Example 3: Popular pop artist with higher capacity
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Stella Nova",
                65,
                daysFromNowAt(5, 20, 0),
                LocalTime.of(18, 30),
                250,
                6));

// Example 4: Indie folk band with afternoon show
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Woodland Echoes",
                30,
                daysFromNowAt(73, 16, 0),
                LocalTime.of(15, 0),
                120,
                4));

// Example 5: Electronic music DJ with a late night show
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Pulse Wave",
                40,
                daysFromNowAt(39, 22, 30),
                LocalTime.of(21, 0),
                180,
                5));

// Example 6: Classical orchestra
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Metropolitan Symphony",
                70,
                daysFromNowAt(81, 19, 0),
                LocalTime.of(18, 0),
                200,
                3));

// Example 7: Alternative rock band
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Cosmic Drift",
                45,
                daysFromNowAt(109, 20, 0),
                LocalTime.of(19, 0),
                130,
                4));

// Example 8: Hip-hop artist
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Lyrical Storm",
                50,
                daysFromNowAt(31, 21, 0),
                LocalTime.of(19, 30),
                175,
                4));

        // h/t sadukie
        concertStore.save(Concert.schedule(
                ConcertId.createRandom(),
                "Jagged Arrays",
                50,
                daysFromNowAt(34, 20, 0),
                LocalTime.of(19, 0),
                150,
                6));
    }

    private void addSonicWavesConcertToStore() {
        Concert sonicWavesConcert = Concert.schedule(
                SONIC_WAVES_CONCERT_ID,
                "The Sonic Waves",
                45,
                daysFromNowAt(26, 20, 0),
                LocalTime.of(19, 0),
                100,
                8);

        // sample data must not be quietly wrong: fail the boot if a command is rejected
        sonicWavesConcert.rescheduleTo(
                daysFromNowAt(33, 21, 0),
                LocalTime.of(20, 0)).orElseThrow();

        sonicWavesConcert.sellTicketsTo(firstCustomerId, 8).orElseThrow();
        sonicWavesConcert.sellTicketsTo(firstCustomerId, 4).orElseThrow();
        sonicWavesConcert.sellTicketsTo(firstCustomerId, 2).orElseThrow();
        sonicWavesConcert.sellTicketsTo(secondCustomerId, 1).orElseThrow();
        sonicWavesConcert.sellTicketsTo(secondCustomerId, 2).orElseThrow();
        sonicWavesConcert.sellTicketsTo(secondCustomerId, 3).orElseThrow();

        concertStore.save(sonicWavesConcert);
    }

    // every ticket sold, so Lab 1D's sold-out state is visible from the first boot
    private void addSoldOutConcertToStore() {
        Concert soldOutConcert = Concert.schedule(
                ConcertId.createRandom(),
                "Last Call Trio",
                60,
                daysFromNowAt(21, 20, 0),
                LocalTime.of(19, 0),
                10,
                4);

        soldOutConcert.sellTicketsTo(firstCustomerId, 4).orElseThrow();
        soldOutConcert.sellTicketsTo(secondCustomerId, 4).orElseThrow();
        soldOutConcert.sellTicketsTo(firstCustomerId, 2).orElseThrow();

        concertStore.save(soldOutConcert);
    }

    private void populateCustomerSampleData() {

        customerStore.save(Customer.register(
                firstCustomerId,
                "First Customer", "first@example.com"));
        customerStore.save(Customer.register(
                secondCustomerId,
                "Second Customer", "second@example.com"
        ));
        customerStore.save(firstCustomerId,
                           Stream.of(new TicketsPurchased(firstCustomerId, 1L, TicketOrderId.createRandom(), SONIC_WAVES_CONCERT_ID, 3, 150)));
    }
}
