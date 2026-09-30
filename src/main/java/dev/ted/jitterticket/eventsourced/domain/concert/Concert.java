package dev.ted.jitterticket.eventsourced.domain.concert;

import dev.ted.jitterticket.eventsourced.domain.EventSourcedAggregate;
import dev.ted.jitterticket.eventsourced.domain.customer.CustomerId;
import dev.ted.jitterticket.support.Result;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.StringJoiner;

public class Concert extends EventSourcedAggregate<ConcertEvent, ConcertId> {

    private String artist;
    private int ticketPrice;
    private LocalDateTime showDateTime;
    private LocalTime doorsTime;
    private int capacity;
    private int maxTicketsPerPurchase;
    private int availableTicketCount;
    private boolean ticketsOnSale = true;

    //region Creation Command
    public static Concert schedule(ConcertId concertId,
                                   String artist,
                                   int ticketPrice,
                                   LocalDateTime showDateTime,
                                   LocalTime doorsTime,
                                   int capacity,
                                   int maxTicketsPerPurchase) {
        return new Concert(concertId, artist, ticketPrice, showDateTime, doorsTime, capacity, maxTicketsPerPurchase);
    }
    //endregion Creation Command

    private Concert(ConcertId concertId,
                    String artist,
                    int ticketPrice,
                    LocalDateTime showDateTime,
                    LocalTime doorsTime,
                    int capacity,
                    int maxTicketsPerPurchase) {
        ConcertScheduled concertScheduled = new ConcertScheduled(
                concertId, null, artist, ticketPrice,
                showDateTime, doorsTime, capacity, maxTicketsPerPurchase
        );
        enqueue(concertScheduled);
    }

    // only invoked by EventStore (and tests)
    public static Concert reconstitute(List<ConcertEvent> concertEvents) {
        // concertEvents must not be empty!
        if (concertEvents.isEmpty()) {
            throw new IllegalArgumentException("Can not reconstitute from an empty list of ConcertEvents.");
        }
        return new Concert(concertEvents);
    }

    //region Event Application

    // this is a Projection!
    private Concert(List<ConcertEvent> concertEvents) {
        applyAll(concertEvents);
    }

    @Override
    protected void apply(ConcertEvent concertEvent) {
        switch (concertEvent) {
            case ConcertScheduled scheduled -> {
                this.setId(scheduled.concertId());
                this.artist = scheduled.artist();
                this.ticketPrice = scheduled.ticketPrice();
                this.showDateTime = scheduled.showDateTime();
                this.doorsTime = scheduled.doorsTime();
                this.capacity = scheduled.capacity();
                this.availableTicketCount = scheduled.capacity();
                this.maxTicketsPerPurchase = scheduled.maxTicketsPerPurchase();
            }
            case ConcertRescheduled rescheduled -> {
                this.showDateTime = rescheduled.newShowDateTime();
                this.doorsTime = rescheduled.newDoorsTime();
            }
            case TicketsSold sold -> availableTicketCount -= sold.quantity();
            case TicketSalesStopped _ -> ticketsOnSale = false;
        }
    }

    //endregion Event Application

    //region Queries

    public int ticketPrice() {
        return ticketPrice;
    }

    public LocalDateTime showDateTime() {
        return showDateTime;
    }

    public LocalTime doorsTime() {
        return doorsTime;
    }

    public int capacity() {
        return capacity;
    }

    public int maxTicketsPerPurchase() {
        return maxTicketsPerPurchase;
    }

    public String artist() {
        return artist;
    }

    public int availableTicketCount() {
        return availableTicketCount;
    }

    public boolean ticketsOnSale() {
        return ticketsOnSale;
    }

    //endregion Queries

    //region Commands

    // other potential validations for this decision:
    // * New date/time must be X amount of time in the future
    // * Must not conflict with existing Concert
    public Result<Void, ConcertRejection> rescheduleTo(LocalDateTime newShowDateTime, LocalTime newDoorsTime) {
        if (!newShowDateTime.isAfter(showDateTime)) {
            return Result.rejected(ConcertRejection.NEW_SHOW_DATE_TIME_IS_NOT_LATER);
        }
        if (capacity - availableTicketCount != 0) {
            return Result.rejected(ConcertRejection.TICKETS_HAVE_ALREADY_BEEN_SOLD);
        }
        ConcertRescheduled concertRescheduled =
                new ConcertRescheduled(getId(), null, newShowDateTime, newDoorsTime);
        enqueue(concertRescheduled);
        return Result.succeeded();
    }

    public Result<Void, ConcertRejection> sellTicketsTo(CustomerId customerId, int quantity) {
        if (quantity > availableTicketCount) {
            return Result.rejected(ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE);
        }
        TicketsSold ticketsSold = new TicketsSold(getId(),
                                                  null,
                                                  quantity,
                                                  quantity * ticketPrice);
        enqueue(ticketsSold);
        // TODO: have we sold out? enqueue(TicketSalesStopped)
        return Result.succeeded();
    }

    public Result<Void, ConcertRejection> stopTicketSales() {
        enqueue(new TicketSalesStopped(getId(), null));
        return Result.succeeded();
    }

    //endregion Commands

    @Override
    public String toString() {
        return new StringJoiner(", ", Concert.class.getSimpleName() + "[", "]")
                .add("(id='" + getId() + "')")
                .add("artist='" + artist + "'")
                .add("ticketPrice=" + ticketPrice)
                .add("showDateTime=" + showDateTime)
                .add("doorsTime=" + doorsTime)
                .add("capacity=" + capacity)
                .add("maxTicketsPerPurchase=" + maxTicketsPerPurchase)
                .add("availableTicketCount=" + availableTicketCount)
                .toString();
    }
}
