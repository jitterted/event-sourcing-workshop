package dev.ted.jitterticket;

import dev.ted.jitterticket.eventsourced.adapter.out.store.EventDto;
import dev.ted.jitterticket.eventsourced.domain.Event;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertScheduled;
import dev.ted.jitterticket.eventsourced.domain.concert.TicketsSold;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerRegistered;
import dev.ted.jitterticket.eventsourced.domain.customer.TicketsPurchased;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Creates the same shape of data as {@link LargeSampleDataPopulator}, but bypasses
 * aggregates and the EventStore: events are created directly and written using
 * batched JDBC inserts, so no event consumers (projectors, processors) are notified,
 * and memory use stays flat regardless of the number of customers and concerts.
 */
//@Component
public class BatchedLargeSampleDataPopulator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchedLargeSampleDataPopulator.class);
    private static final int CUSTOMERS_TO_CREATE = 10_000; // 100_000
    private static final int CONCERTS_TO_CREATE = 100; // 200
    private static final int BATCH_SIZE = 5_000;
    private static final String INSERT_EVENT_SQL =
            "INSERT INTO events (aggregate_root_id, event_type, json) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new Random();
    private final List<EventDto<? extends Event>> pendingEvents = new ArrayList<>(BATCH_SIZE);

    private static final String[] ARTISTS = {
            "The Electric Vibrations", "Neon Dreams", "Solar Flare", "Midnight Pulse", "Acoustic Echo",
            "Quantum Harmony", "The Velvet Rhythms", "Lunar Shadows", "Cyber Soul", "Infinite Loop",
            "Starlight Symphony", "Bass Drop Theory", "The Echo Chamber", "Golden Hour", "Silver Lining",
            "Desert Wind", "Ocean Breeze", "Mountain Peak", "Urban Jungle", "Forest Whisper"
    };

    private static final String[] FIRST_NAMES = {
            "Alice", "Bob", "Charlie", "Diana", "Edward", "Fiona", "George", "Hannah", "Ian", "Julia",
            "Kevin", "Laura", "Michael", "Nina", "Oscar", "Paula", "Quinn", "Rose", "Steven", "Tanya"
    };

    private static final String[] LAST_NAMES = {
            "Adams", "Baker", "Clark", "Davis", "Evans", "Frank", "Ghosh", "Hills", "Irwin", "Jones",
            "Klein", "Lopez", "Mason", "Nolan", "Owen", "Perez", "Quinn", "Ross", "Smith", "Tyler"
    };

    // only what's needed to generate ticket sales, instead of a full Concert aggregate
    private record ConcertInfo(ConcertId concertId, String artist, int ticketPrice) {}

    public BatchedLargeSampleDataPopulator(JdbcTemplate jdbcTemplate,
                                           TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("TRUNCATE TABLE events, concert_sales_projection RESTART IDENTITY CASCADE");
        log.info("Cleared existing data, starting batched population of large sample dataset");
        long start = System.currentTimeMillis();

        List<ConcertInfo> concerts = transactionTemplate.execute(_ -> scheduleConcerts(CONCERTS_TO_CREATE));
        log.info("Scheduled {} concerts", concerts.size());
        List<CustomerId> customerIds = transactionTemplate.execute(_ -> registerCustomers(CUSTOMERS_TO_CREATE));
        log.info("Registered {} customers", customerIds.size());

        log.info("Starting ticket sales for {} customers and {} concerts", customerIds.size(), concerts.size());
        long salesStart = System.currentTimeMillis();
        for (ConcertInfo concert : concerts) {
            transactionTemplate.executeWithoutResult(_ -> sellTicketsForConcert(concert, customerIds));
            log.info("Sold tickets for concert: {}", concert.artist());
        }
        log.info("Completed ticket sales processing after {}ms", System.currentTimeMillis() - salesStart);

        log.info("Successfully populated database with {} concerts and {} customers in {}ms",
                 concerts.size(), customerIds.size(), System.currentTimeMillis() - start);
    }

    private List<ConcertInfo> scheduleConcerts(int count) {
        List<ConcertInfo> concerts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ConcertId concertId = ConcertId.createRandom();
            String artist = ARTISTS[random.nextInt(ARTISTS.length)] + " " + i;
            int price = 20 + random.nextInt(180);
            int capacity = (CUSTOMERS_TO_CREATE * 4) + random.nextInt(5000);
            append(concertId.id(), new ConcertScheduled(
                    concertId,
                    null,
                    artist,
                    price,
                    daysFromNowAt(10 + random.nextInt(CONCERTS_TO_CREATE), 19, 0),
                    LocalTime.of(18, 0),
                    capacity,
                    10));
            concerts.add(new ConcertInfo(concertId, artist, price));
        }
        flush();
        return concerts;
    }

    private List<CustomerId> registerCustomers(int count) {
        List<CustomerId> customerIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CustomerId customerId = CustomerId.createRandom();
            String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
            String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
            String name = firstName + " " + lastName + " " + i;
            String email = firstName.toLowerCase() + "." + lastName.toLowerCase() + i + "@example.com";
            append(customerId.id(), new CustomerRegistered(customerId, null, name, email));
            customerIds.add(customerId);
        }
        flush();
        return customerIds;
    }

    private void sellTicketsForConcert(ConcertInfo concert, List<CustomerId> customerIds) {
        for (CustomerId customerId : customerIds) {
            int quantity = random.nextBoolean() ? 2 : 4;
            int amountPaid = quantity * concert.ticketPrice();
            append(concert.concertId().id(),
                   new TicketsSold(concert.concertId(), null, quantity, amountPaid));
            append(customerId.id(),
                   new TicketsPurchased(customerId, null, TicketOrderId.createRandom(),
                                        concert.concertId(), quantity, amountPaid));
        }
        flush();
    }

    private void append(UUID aggregateRootId, Event event) {
        pendingEvents.add(EventDto.from(aggregateRootId, null, event));
        if (pendingEvents.size() >= BATCH_SIZE) {
            flush();
        }
    }

    private void flush() {
        if (pendingEvents.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_EVENT_SQL, pendingEvents, pendingEvents.size(),
                                 (ps, dto) -> {
                                     ps.setObject(1, dto.getAggregateRootId());
                                     ps.setString(2, dto.getEventType());
                                     ps.setString(3, dto.getJson());
                                 });
        pendingEvents.clear();
    }

    private static LocalDateTime daysFromNowAt(long days, int hour, int minute) {
        return LocalDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .plusDays(days)
                .withHour(hour)
                .withMinute(minute);
    }
}
