package dev.ted.jitterticket.eventsourced.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.concert.*;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerFactory;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PurchaseTicketsController.class)
class PurchaseTicketsControllerMvcTest extends BaseMvcTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    EventStore<ConcertId, ConcertEvent, Concert> concertStore;

    @Autowired
    EventStore<CustomerId, CustomerEvent, Customer> customerStore;

    @Test
    void getToPurchaseTicketViewEndpointReturns200Ok() {
        ConcertId concertId = ConcertFactory.Store.saveScheduledConcertIn(concertStore);

        mvc.get()
           .uri("/concerts/" + concertId.id().toString())
           .assertThat()
           .hasStatus2xxSuccessful();
    }

    @Test
    void postToPurchaseTicketEndpointRedirects() {
        Customer customer = CustomerFactory.newlyRegistered();
        customerStore.save(customer);
        ConcertId concertId = ConcertFactory.Store.saveScheduledConcertIn(concertStore);
        mvc.post()
           .formField("customerId", customer.getId().id().toString())
           .formField("quantity", "2")
           .uri("/concerts/" + concertId.id().toString())
           .assertThat()
           .hasStatus3xxRedirection();
    }

    @Test
    void postRejectedPurchaseRedirectsBackToPurchaseViewWithRejectionFlashed() {
        Customer customer = CustomerFactory.newlyRegistered();
        customerStore.save(customer);
        Concert concert = ConcertFactory.createWithCapacity(2);
        concertStore.save(concert);
        String concertId = concert.getId().id().toString();

        mvc.post()
                .formField("customerId", customer.getId().id().toString())
                .formField("quantity", "4")
                .uri("/concerts/" + concertId)
                .assertThat()
                .hasStatus3xxRedirection()
                .hasRedirectedUrl("/concerts/" + concertId)
                .flash()
                .containsEntry("rejectionMessage",
                        ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE.message());
    }

    @Test
    void postToUnknownConcertReturns404NotFound() {
        Customer customer = CustomerFactory.newlyRegistered();
        customerStore.save(customer);
        String unknownConcertId = ConcertId.createRandom().id().toString();

        mvc.post()
                .formField("customerId", customer.getId().id().toString())
                .formField("quantity", "2")
                .uri("/concerts/" + unknownConcertId)
                .assertThat()
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPurchaseViewDisplaysFlashedRejectionMessage() {
        customerStore.save(CustomerFactory.newlyRegistered());
        ConcertId concertId = ConcertFactory.Store.saveScheduledConcertIn(concertStore);

        mvc.get()
                .uri("/concerts/" + concertId.id().toString())
                .flashAttr("rejectionMessage", "Not enough tickets are available")
                .assertThat()
                .hasStatus2xxSuccessful()
                .bodyText()
                .contains("Not enough tickets are available");
    }

}