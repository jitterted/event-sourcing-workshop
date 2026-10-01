package dev.ted.jitterticket.eventsourced.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.InMemoryEventStore;
import dev.ted.jitterticket.eventsourced.application.PurchaseTicketsUseCase;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRejection;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PurchaseTicketsControllerTest {

    @Test
    void purchaseTicketsViewPutsConcertAndTicketOrderIntoModel() {
        var concertStore = InMemoryEventStore.forConcerts();
        ConcertId concertId = ConcertId.createRandom();
        int ticketPrice = 55;
        int maxCapacity = 150;
        int maxTicketsPerPurchase = 4;
        concertStore.save(Concert.schedule(concertId, "Midnight Rebels", ticketPrice,
                LocalDateTime.of(2025, 9, 15, 21, 0), LocalTime.of(20, 0),
                maxCapacity, maxTicketsPerPurchase));
        var customerStore = InMemoryEventStore.forCustomers();
        customerStore.save(Customer.register(CustomerId.createRandom(), "Purchaser", "tix@example.com"));
        PurchaseTicketsController purchaseTicketsController =
                new PurchaseTicketsController(concertStore, customerStore,
                        new PurchaseTicketsUseCase(concertStore, customerStore));
        String concertIdString = concertId.id().toString();

        Model model = new ConcurrentModel();
        String viewName = purchaseTicketsController.purchaseTicketsView(model, concertIdString);

        assertThat(viewName)
                .isEqualTo("purchase-tickets");
        ConcertView concertView = (ConcertView) model.getAttribute("concert");
        assertThat(concertView)
                .isEqualTo(new ConcertView(
                        concertIdString,
                        "Midnight Rebels",
                        "$55",
                        "September 15, 2025", "9:00\u202FPM"
                ));
        TicketOrderForm ticketOrderForm = (TicketOrderForm) model.getAttribute("ticketOrderForm");
        assertThat(ticketOrderForm.customerId())
                .isNotBlank();
        assertThat(ticketOrderForm.quantity())
                .isEqualTo(2);
    }

    @Test
    void successfulTicketOrderRedirectsToOrderConfirmationPage() {
        var concertStore = InMemoryEventStore.forConcerts();
        var customerStore = InMemoryEventStore.forCustomers();
        TicketOrderId ticketOrderId = TicketOrderId.createRandom();
        PurchaseTicketsUseCase purchaseTicketsUseCase = PurchaseTicketsUseCase.createForTest(
                concertStore, customerStore, ticketOrderId);
        PurchaseTicketsController purchaseTicketsController =
                new PurchaseTicketsController(concertStore, customerStore, purchaseTicketsUseCase);
        ConcertId concertId = ConcertId.createRandom();
        int initialCapacity = 100;
        concertStore.save(Concert.schedule(concertId, "Pulse Wave", 40, LocalDateTime.of(2025, 11, 8, 22, 30), LocalTime.of(21, 0), initialCapacity, 4));
        CustomerId customerId = CustomerId.createRandom();
        customerStore.save(Customer.register(customerId, "Customer Name", "customer@example.com"));

        int numberOfTicketsToPurchase = 4;
        String customerIdAsString = customerId.id().toString();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        TicketOrderForm ticketOrderForm = new TicketOrderForm(customerIdAsString, numberOfTicketsToPurchase);
        String redirectString = purchaseTicketsController
                .purchaseTickets(concertId.id().toString(), ticketOrderForm, redirectAttributes);

        assertThat(redirectString)
                .isEqualTo("redirect:/customers/" + customerIdAsString
                           + "/confirmations/" + ticketOrderId.id().toString());

        Concert concert = concertStore.findById(concertId).orElseThrow();
        assertThat(concert.availableTicketCount())
                .isEqualTo(initialCapacity - numberOfTicketsToPurchase);
    }


    @Test
    void rejectedTicketOrderRedirectsBackToPurchaseViewWithRejectionMessage() {
        var concertStore = InMemoryEventStore.forConcerts();
        var customerStore = InMemoryEventStore.forCustomers();
        PurchaseTicketsUseCase purchaseTicketsUseCase =
                new PurchaseTicketsUseCase(concertStore, customerStore);
        PurchaseTicketsController purchaseTicketsController =
                new PurchaseTicketsController(concertStore, customerStore, purchaseTicketsUseCase);
        ConcertId concertId = ConcertId.createRandom();
        int initialCapacity = 2;
        concertStore.save(Concert.schedule(concertId, "Pulse Wave", 40, LocalDateTime.of(2025, 11, 8, 22, 30), LocalTime.of(21, 0), initialCapacity, 4));
        CustomerId customerId = CustomerId.createRandom();
        customerStore.save(Customer.register(customerId, "Customer Name", "customer@example.com"));

        int numberOfTicketsToPurchase = 4;
        String concertIdAsString = concertId.id().toString();
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        String redirectString = purchaseTicketsController
                .purchaseTickets(concertIdAsString,
                        new TicketOrderForm(customerId.id().toString(),
                                numberOfTicketsToPurchase),
                        redirectAttributes);

        assertThat(redirectString)
                .as("Rejected purchase should go back to the purchase view for the same concert")
                .isEqualTo("redirect:/concerts/" + concertIdAsString);
        Map<String, ?> flashAttributes = redirectAttributes.getFlashAttributes();
        assertThat(flashAttributes)
                .as("Rejection reason should be flashed for the purchase view to display")
                .containsKey("rejectionMessage");
        assertThat(flashAttributes.get("rejectionMessage"))
                .as("Flashed message should be the reason the purchase was rejected")
                .isEqualTo(ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE.message());

        Concert concert = concertStore.findById(concertId).orElseThrow();
        assertThat(concert.availableTicketCount())
                .as("No tickets should have been sold for a rejected purchase")
                .isEqualTo(initialCapacity);
    }

    @Test
    void ticketOrderForUnknownConcertIsNotFound() {
        var concertStore = InMemoryEventStore.forConcerts();
        var customerStore = InMemoryEventStore.forCustomers();
        PurchaseTicketsUseCase purchaseTicketsUseCase =
                new PurchaseTicketsUseCase(concertStore, customerStore);
        PurchaseTicketsController purchaseTicketsController =
                new PurchaseTicketsController(concertStore, customerStore, purchaseTicketsUseCase);
        CustomerId customerId = CustomerId.createRandom();
        customerStore.save(Customer.register(customerId, "Customer Name", "customer@example.com"));
        String unknownConcertId = ConcertId.createRandom().id().toString();
        TicketOrderForm ticketOrderForm = new TicketOrderForm(customerId.id().toString(), 2);
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        ResponseStatusException exception = catchThrowableOfType(
                ResponseStatusException.class,
                () -> purchaseTicketsController.purchaseTickets(unknownConcertId,
                        ticketOrderForm,
                        redirectAttributes));

        assertThat(exception)
                .as("Purchasing for an unknown Concert ID should throw")
                .isNotNull();
        assertThat(exception.getStatusCode())
                .as("Unknown Concert should be reported as not found")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

}