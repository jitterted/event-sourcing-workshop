package dev.ted.jitterticket.eventsourced.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.Checkpoint;
import dev.ted.jitterticket.eventsourced.application.PurchaseTicketsUseCase;
import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.TicketOrderId;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRejection;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerRegistered;
import dev.ted.jitterticket.support.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Set;
import java.util.UUID;

@Controller
public class PurchaseTicketsController {

    private final EventStore<ConcertId, ConcertEvent, Concert> concertStore;
    private final EventStore<CustomerId, CustomerEvent, Customer> customerStore;
    private final PurchaseTicketsUseCase purchaseTicketsUseCase;

    @Autowired
    public PurchaseTicketsController(EventStore<ConcertId, ConcertEvent, Concert> concertStore,
                                     EventStore<CustomerId, CustomerEvent, Customer> customerStore,
                                     PurchaseTicketsUseCase purchaseTicketsUseCase) {
        this.concertStore = concertStore;
        this.customerStore = customerStore;
        this.purchaseTicketsUseCase = purchaseTicketsUseCase;
    }

    @GetMapping("/concerts/{concertId}")
    public String purchaseTicketsView(Model model,
                                      @PathVariable("concertId") String concertId) {
        model.addAttribute("concert", ConcertView.concertViewFor(concertStore, concertId));
        CustomerEvent customerRegistered = customerStore.allEventsAfter(Checkpoint.INITIAL, Set.of(CustomerRegistered.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No CustomerRegistered events found"));
        String customerUuidString = customerRegistered.customerId().id().toString();
        int defaultQuantity = 2;
        model.addAttribute("ticketOrderForm", new TicketOrderForm(customerUuidString, defaultQuantity));
        return "purchase-tickets";
    }

    @PostMapping("/concerts/{concertId}")
    public String purchaseTickets(@PathVariable("concertId") String concertId,
                                  TicketOrderForm ticketOrderForm) {
        Result<TicketOrderId, ConcertRejection> result = purchaseTicketsUseCase
                .purchaseTickets(
                        new ConcertId(UUID.fromString(concertId)),
                        new CustomerId(UUID.fromString(ticketOrderForm.customerId())),
                        ticketOrderForm.quantity());
        return switch (result) {
            case Result.Success<TicketOrderId, ConcertRejection>(TicketOrderId id) ->
                    "redirect:/customers/" + ticketOrderForm.customerId() + "/confirmations/" + id.id().toString();
            case Result.Failure<TicketOrderId, ConcertRejection>(ConcertRejection rejection) -> {
//                redirectAttributes.addFlashAttribute("rejectionMessage", rejection.message());
                yield "redirect:/concerts/" + concertId;
            }
        };

    }

}

record TicketOrderForm(String customerId, int quantity) {}
