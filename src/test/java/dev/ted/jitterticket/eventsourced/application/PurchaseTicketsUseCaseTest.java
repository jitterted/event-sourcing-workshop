package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.*;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerFactory;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerRegistered;
import dev.ted.jitterticket.eventsourced.domain.customer.TicketsPurchased;
import dev.ted.jitterticket.support.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class PurchaseTicketsUseCaseTest {

    @Test
    void purchaseTicketsReducesNumberOfConcertTicketsAvailableAndAddsTicketOrderToCustomer() {
        int originalConcertAvailableTicketCount = 100;
        int ticketPurchaseQuantity = 4;
        TicketOrderId expectedTicketOrderId = new TicketOrderId(UUID.randomUUID());
        Fixture fixture = createForPurchaseTicketsWithCapacityOf(originalConcertAvailableTicketCount);
        PurchaseTicketsUseCase purchaseTicketsUseCase =
                PurchaseTicketsUseCase.createForTest(fixture.concertStore(),
                                                     fixture.customerStore(),
                                                     expectedTicketOrderId);

        Result<TicketOrderId, ConcertRejection> result =
                purchaseTicketsUseCase.purchaseTickets(fixture.concertId(),
                        fixture.customer().getId(), ticketPurchaseQuantity);

        assertThat(result)
                .as("Purchasing 4 of 100 tickets should have succeeded with the expected TicketOrderId")
                .isEqualTo(Result.success(expectedTicketOrderId));


        Concert concertAfter = fixture.concertStore().findById(fixture.concertId()).orElseThrow();
        assertThat(concertAfter.availableTicketCount())
                .isEqualTo(originalConcertAvailableTicketCount - ticketPurchaseQuantity);

        Customer customerAfter = fixture.customerStore().findById(fixture.customer().getId()).orElseThrow();
        assertThat(customerAfter.ticketOrders())
                .as("Customer should have 1 ticket order")
                .hasSize(1)
                .extracting(Customer.TicketOrder::ticketOrderId, Customer.TicketOrder::concertId)
                .first()
                .isEqualTo(tuple(expectedTicketOrderId, fixture.concertId()));
    }

    @Test
    void givenConcertScheduledAndCustomerRegisteredWhenPurchaseTicketsThenTicketsSoldAndTicketsPurchased() {
        // Given
        var concertStore = InMemoryEventStore.forConcerts();
        var customerStore = InMemoryEventStore.forCustomers();
        ConcertId concertId = ConcertId.createRandom();
        int ticketPrice = 35;
        ConcertScheduled concertScheduled = new ConcertScheduled(concertId, null, "Headliner",
                ticketPrice, LocalDateTime.of(2025, 11, 11, 20, 0), LocalTime.of(19, 0), 100, 4);
        concertStore.save(concertId, Stream.of(concertScheduled));
        CustomerId customerId = CustomerId.createRandom();
        CustomerRegistered customerRegistered = new CustomerRegistered(customerId, null, "Customer Name", "customer@example.com");
        customerStore.save(customerId, Stream.of(customerRegistered));

        // When
        TicketOrderId ticketOrderId = new TicketOrderId(UUID.randomUUID());
        PurchaseTicketsUseCase purchaseTicketsUseCase = PurchaseTicketsUseCase.createForTest(concertStore, customerStore, ticketOrderId);
        int quantity = 2;
        Result<TicketOrderId, ConcertRejection> result = purchaseTicketsUseCase.purchaseTickets(concertId, customerId, quantity);

        // Then
        assertThat(result)
                .as("Purchasing 2 of 100 tickets should have succeeded with the expected TicketOrderId")
                .isEqualTo(Result.success(ticketOrderId));
        TicketsSold expectedTicketsSold = new TicketsSold(concertId, 2L, quantity, quantity * ticketPrice);
        assertThat(concertStore.eventsForAggregate(concertId))
                .as("Concert's event stream should end with TicketsSold")
                .endsWith(expectedTicketsSold);
        TicketsPurchased expectedTicketsPurchased = new TicketsPurchased(customerId, 2L, ticketOrderId, concertId, quantity, quantity * ticketPrice);
        assertThat(customerStore.eventsForAggregate(customerId))
                .as("Customer's event stream should end with TicketsPurchased")
                .endsWith(expectedTicketsPurchased);
    }

    @Test
    void ticketOrderIdsAreUniquePerPurchase() {
        Fixture fixture = createForPurchaseTickets();
        PurchaseTicketsUseCase purchaseTicketsUseCase =
                new PurchaseTicketsUseCase(fixture.concertStore,
                                           fixture.customerStore);

        TicketOrderId firstTicketOrderId =
                purchaseTicketsUseCase.purchaseTickets(fixture.concertId, fixture.customer.getId(), 2)
                        .orElseThrow();
        TicketOrderId secondTicketOrderId =
                purchaseTicketsUseCase.purchaseTickets(fixture.concertId, fixture.customer.getId(), 2)
                        .orElseThrow();

        assertThat(firstTicketOrderId)
                .as("Ticket Order IDs should be different for each purchase")
                .isNotEqualTo(secondTicketOrderId);
    }

    @Test
    void purchaseRejectedWhenTicketQuantityIsMoreThanAvailable() {
        Fixture fixture = createForPurchaseTicketsWithCapacityOf(2);
        PurchaseTicketsUseCase purchaseTicketsUseCase = new PurchaseTicketsUseCase(
                fixture.concertStore(), fixture.customerStore());

        Result<TicketOrderId, ConcertRejection> result = purchaseTicketsUseCase.purchaseTickets(
                fixture.concertId, fixture.customer.getId(), 4);

        assertThat(result)
                .as("Not enough tickets to purchase 4 when only 2 available")
                .isEqualTo(Result.rejected(ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE));

        Concert concertAfter = fixture.concertStore().findById(fixture.concertId()).orElseThrow();
        assertThat(concertAfter.availableTicketCount())
                .as("No tickets should have been sold after rejected purchase attempt")
                .isEqualTo(2);

        Customer customerAfter = fixture.customerStore().findById(fixture.customer().getId()).orElseThrow();
        assertThat(customerAfter.ticketOrders())
                .as("No ticket order should exist after rejected purchase attempt")
                .isEmpty();
    }

    @Test
    void purchaseTicketsForInvalidConcertIsRejectedWithNotFound() {
        var concertStore = InMemoryEventStore.forConcerts();
        var customerStore = InMemoryEventStore.forCustomers();
        Customer customer = CustomerFactory.newlyRegistered();
        customerStore.save(customer);
        PurchaseTicketsUseCase purchaseTicketsUseCase =
                new PurchaseTicketsUseCase(concertStore, customerStore);
        ConcertId invalidConcertId = ConcertId.createRandom();

        Result<TicketOrderId, ConcertRejection> result =
                purchaseTicketsUseCase.purchaseTickets(
                        invalidConcertId,
                        customer.getId(), 1);

        assertThat(result)
                .as("Ticket order should have been rejected for a Concert ID that doesn't exist")
                .isEqualTo(Result.rejected(ConcertRejection.CONCERT_NOT_FOUND));
    }

    //region Test Fixture

    private static Fixture createForPurchaseTicketsWithCapacityOf(int originalConcertAvailableTicketCount) {
        Customer customer = CustomerFactory.newlyRegistered();
        Concert concertBefore = ConcertFactory.createWithCapacity(originalConcertAvailableTicketCount);
        var concertStore = InMemoryEventStore.forConcerts();
        concertStore.save(concertBefore);
        ConcertId concertId = concertBefore.getId();
        var customerStore = InMemoryEventStore.forCustomers();
        customerStore.save(customer);
        return new Fixture(customer, concertStore, concertId, customerStore);
    }

    private record Fixture(
            Customer customer,
            EventStore<ConcertId, ConcertEvent, Concert> concertStore,
            ConcertId concertId,
            EventStore<CustomerId, CustomerEvent, Customer> customerStore
    ) {}

    private static Fixture createForPurchaseTickets() {
        return createForPurchaseTicketsWithCapacityOf(42);
    }

    //endregion Test Fixture

}
