package dev.ted.jitterticket.eventsourced.adapter.out.store.jdbc;

import dev.ted.jitterticket.eventsourced.application.Checkpoint;
import dev.ted.jitterticket.eventsourced.application.EventStoreTest;
import dev.ted.jitterticket.eventsourced.application.PurchaseTicketsUseCase;
import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertFactory;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcClientEventStoreTest extends DataJdbcContainerTest {

    private EventStore<ConcertId, ConcertEvent, Concert> concertStore() {
        return JdbcClientEventStore.forConcerts(JdbcClient.create(jdbcTemplate));
    }

    private EventStore<CustomerId, CustomerEvent, Customer> customerStore() {
        return JdbcClientEventStore.forCustomers(JdbcClient.create(jdbcTemplate));
    }

    @Nested
    class EventStoreTests {
        @Test
        void onlyReturnEventsMatchingTypeOfEventStore() {
            var customerStore = customerStore();
            CustomerId onlyCustomerId = new CustomerId(UUID.fromString("68f5b2c2-d70d-4992-ad78-c94809ae9a6a"));
            customerStore.save(Customer.register(onlyCustomerId, "First Customer", "first@example.com"));
            var concertStore = concertStore();
            Concert concert = ConcertFactory.createConcertWithArtist("Concert");
            concertStore.save(concert);
            PurchaseTicketsUseCase purchaseTickets = PurchaseTicketsUseCase.createForTest(concertStore, customerStore, TicketOrderId.createRandom());
            purchaseTickets.purchaseTickets(concert.getId(), onlyCustomerId, 4);

            assertThat(customerStore.allEventsAfter(Checkpoint.INITIAL))
                    .as("Two Customer events should be in the store: registered and purchased tickets")
                    .hasSize(2)
                    .as("Both events belong to the only customer")
                    .map(CustomerEvent::customerId)
                    .containsOnly(onlyCustomerId);
        }

    }

    @Nested
    class ConcertEventStoreTests {

        protected EventStore<ConcertId, ConcertEvent, Concert> concertStore;
        private EventStoreTest.ConcertEventStoreFindById delegatedConcertEventStoreFindById;
        private EventStoreTest.ConcertEventStoreAllEvents delegatedConcertEventStoreAllEvents;
        private EventStoreTest.ConcertEventStoreEventsForAggregate delegatedConcertEventStoreEventsForAggregate;

        @BeforeEach
        void beforeEach() {
            @SuppressWarnings("InstantiationOfUtilityClass")
            EventStoreTest eventStoreTest = new EventStoreTest();
            // yes, the .new is valid Java syntax for instantiating a non-static inner class
            delegatedConcertEventStoreFindById = eventStoreTest.new ConcertEventStoreFindById();
            delegatedConcertEventStoreAllEvents = eventStoreTest.new ConcertEventStoreAllEvents();
            delegatedConcertEventStoreEventsForAggregate = eventStoreTest.new ConcertEventStoreEventsForAggregate();
            concertStore = concertStore();
        }

        @Test
        void findByIdForNonExistingConcertReturnsEmptyOptional() {
            delegatedConcertEventStoreFindById
                    .findByIdForNonExistingConcertReturnsEmptyOptional(concertStore);
        }

        @Test
        void findByIdReturnsSavedConcert() {
            delegatedConcertEventStoreFindById
                    .findByIdReturnsSavedConcert(concertStore);
        }

        @Test
        void findByIdReturnsDifferentInstanceOfConcert() {
            delegatedConcertEventStoreFindById
                    .findByIdReturnsDifferentInstanceOfConcert(concertStore);
        }

        @Test
        void allEventsReturnsAllEventsForAllSavedAggregatesInGlobalEventSequenceOrder() {
            delegatedConcertEventStoreAllEvents
                    .allEventsReturnsAllEventsForAllSavedAggregatesInGlobalEventSequenceOrder(concertStore);
        }

        @Test
        void allEventsAfterReturnsOnlySpecifiedEventTypes() {
            delegatedConcertEventStoreAllEvents
                    .allEventsAfterReturnsOnlySpecifiedEventTypes(concertStore);
        }

        @Test
        void noEventsReturnedForAllEventsAfterWhenEventStoreIsEmpty() {
            delegatedConcertEventStoreAllEvents
                    .noEventsReturnedForAllEventsAfterWhenEventStoreIsEmpty(concertStore);
        }

        @Test
        void oneEventReturnedForOneEventInStoreAskingForEventsAfterZero() {
            delegatedConcertEventStoreAllEvents
                    .oneEventReturnForOneEventInStoreAskingForEventsAfterZero(concertStore);
        }

        @Test
        void oneOfTwoEventsReturnedFromStoreAskingForEventsAfterOne() {
            delegatedConcertEventStoreAllEvents
                    .oneOfTwoEventsReturnedFromStoreAskingForEventsAfterOne(concertStore);
        }

        @Test
        void noEventsReturnedForAllEventsAfterTheMaxEventSequence() {
            delegatedConcertEventStoreAllEvents
                    .noEventsReturnedForAllEventsAfterTheMaxEventSequence(concertStore);
        }

        @Test
        void emptyListReturnedForUnknownAggregateId() {
            delegatedConcertEventStoreEventsForAggregate
                    .emptyListReturnedForUnknownAggregateId(concertStore);
        }

        @Test
        void exactlyAllEventsForSpecifiedConcertAggregateAreReturned() {
            delegatedConcertEventStoreEventsForAggregate
                    .exactlyAllEventsForSpecifiedConcertAggregateAreReturned(concertStore);
        }

        @Test
        void lastGlobalEventSequenceSavedReturnedFromSave() {
            delegatedConcertEventStoreEventsForAggregate
                    .eventSequenceAssignedWhenReturnedFromSave(concertStore);
        }

        @Test
        void lastGlobalEventSequenceSavedReturnedAfterMultipleSaves() {
            delegatedConcertEventStoreEventsForAggregate
                    .lastGlobalEventSequenceSavedReturnedAfterMultipleSaves(concertStore);
        }
    }

    @Nested
    class CustomerEventStoreTests {

        protected EventStoreTest.CustomerEventStoreTest delegatedCustomerEventStoreTest;
        private EventStore<CustomerId, CustomerEvent, Customer> customerStore;

        @BeforeEach
        void beforeEach() {
            @SuppressWarnings("InstantiationOfUtilityClass")
            EventStoreTest eventStoreTest = new EventStoreTest();
            // yes, the .new is valid Java syntax for instantiating a non-static inner class
            delegatedCustomerEventStoreTest = eventStoreTest.new CustomerEventStoreTest();
            customerStore = customerStore();
        }

        @Test
        void eventStoreCanStoreCustomers() {
            delegatedCustomerEventStoreTest
                    .eventStoreCanStoreCustomers(customerStore);
        }

        @Test
        void exactlyAllEventsForSpecifiedCustomerAggregateAreReturned() {
            delegatedCustomerEventStoreTest
                    .exactlyAllEventsForSpecifiedCustomerAggregateAreReturned(customerStore);
        }
    }
}
