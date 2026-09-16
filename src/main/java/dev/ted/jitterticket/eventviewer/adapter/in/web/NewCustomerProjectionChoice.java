package dev.ted.jitterticket.eventviewer.adapter.in.web;

import dev.ted.jitterticket.eventsourced.application.AllRegisteredCustomers;
import dev.ted.jitterticket.eventsourced.application.NewProjectionCoordinator;
import dev.ted.jitterticket.eventsourced.application.NewlyRegisteredCustomers;
import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.Event;
import dev.ted.jitterticket.eventsourced.domain.customer.Customer;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerEvent;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;

import java.util.List;
import java.util.UUID;

public class NewCustomerProjectionChoice extends ProjectionChoice {
    private final EventStore<CustomerId, CustomerEvent, Customer> customerStore;
    private final NewProjectionCoordinator<AllRegisteredCustomers, NewlyRegisteredCustomers> registeredCustomersProjection;

    public NewCustomerProjectionChoice(
            EventStore<CustomerId, CustomerEvent, Customer> customerStore,
            NewProjectionCoordinator<AllRegisteredCustomers, NewlyRegisteredCustomers> newRegisteredCustomersProjection
    ) {
        super("Customer", "customers");
        this.customerStore = customerStore;
        this.registeredCustomersProjection = newRegisteredCustomersProjection;
    }

    @Override
    public List<AggregateSummaryView> aggregateSummaryViews() {
        return registeredCustomersProjection.projection().asStream()
                        .map(customerSummary -> new AggregateSummaryView(
                                customerSummary.customerId().id().toString(),
                                customerSummary.name()
                        ))
                        .toList();
    }

    @Override
    public List<? extends Event> eventsFor(UUID uuid) {
        return customerStore.eventsForAggregate(new CustomerId(uuid));
    }

    @Override
    public ProjectedStateView projectedStateFrom(List<? extends Event> events) {
        @SuppressWarnings("unchecked")
        Customer customer = Customer.reconstitute((List<CustomerEvent>) events);
        List<Customer.TicketOrder> ticketOrders = customer.ticketOrders();
        return ProjectedStateView
                .ofFields(
                        ProjectedStateView.Field.of("Customer Name", customer.name()),
                        ProjectedStateView.Field.of("Email", customer.email()),
                        ProjectedStateView.Field.of("Tickets Purchased", totalTicketsPurchased(ticketOrders)),
                        ProjectedStateView.Field.of("Total Paid", asDollars(totalPaid(ticketOrders))))
                .plus(ticketOrdersTable(ticketOrders));
    }

    private ProjectedStateView.Table ticketOrdersTable(List<Customer.TicketOrder> ticketOrders) {
        return new ProjectedStateView.Table(
                "Ticket Orders",
                List.of("Concert", "Qty", "Paid"),
                ticketOrders.stream()
                            .map(NewCustomerProjectionChoice::asRow)
                            .toList(),
                "No ticket orders");
    }

    // the Customer only knows the Concert's ID: the artist name lives in the Concert aggregate
    private static ProjectedStateView.Row asRow(Customer.TicketOrder order) {
        return ProjectedStateView.Row.of(
                abbreviated(order.concertId().id()),
                order.quantity(),
                asDollars(order.amountPaid()));
    }

    // enough of the ID to tell rows apart, without a full UUID dominating the column
    private static String abbreviated(UUID uuid) {
        return uuid.toString().substring(0, 8) + "…";
    }

    private static String asDollars(int amount) {
        return "$" + amount;
    }

    private int totalTicketsPurchased(List<Customer.TicketOrder> ticketOrders) {
        return ticketOrders.stream()
                           .mapToInt(Customer.TicketOrder::quantity)
                           .sum();
    }

    private int totalPaid(List<Customer.TicketOrder> ticketOrders) {
        return ticketOrders.stream()
                           .mapToInt(Customer.TicketOrder::amountPaid)
                           .sum();
    }
}
