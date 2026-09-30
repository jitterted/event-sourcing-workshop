package dev.ted.jitterticket.orm;

import dev.ted.jitterticket.support.Result;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class Concert {
    private ConcertId id;
    private String artist;
    private int ticketPrice;
    private LocalDateTime showDateTime;
    private LocalTime doorsTime;
    private int capacity;
    private int availableTicketCount;
    private int maxTicketsPerPurchase;
    private boolean ticketsOnSale = true;

    // Creation
    public static Concert schedule(ConcertId id, String artist, int ticketPrice,
                                   LocalDateTime showDateTime, LocalTime doorsTime,
                                   int capacity, int maxTicketsPerPurchase) {
        Concert concert = new Concert();
        concert.id = id;
        concert.artist = artist;
        concert.ticketPrice = ticketPrice;
        concert.showDateTime = showDateTime;
        concert.doorsTime = doorsTime;
        concert.capacity = capacity;
        concert.availableTicketCount = capacity;
        concert.maxTicketsPerPurchase = maxTicketsPerPurchase;
        return concert;
    }

    private Concert() { }

    // BACK DOOR (in): "reconstitute" the object from the full state "snapshot"
    Concert(Snapshot snapshot) {
        this.id = snapshot.id();
        this.artist = snapshot.artist();
        this.ticketPrice = snapshot.ticketPrice();
        this.showDateTime = snapshot.showDateTime();
        this.doorsTime = snapshot.doorsTime();
        this.capacity = snapshot.capacity();
        this.availableTicketCount = snapshot.availableTicketCount();
        this.maxTicketsPerPurchase = snapshot.maxTicketsPerPurchase();
        this.ticketsOnSale = snapshot.ticketsOnSale();
    }

    // BACK DOOR (out): externalize the entire internal state, handed (only) to the repository
    public Snapshot snapshot() {
        return new Snapshot(id, artist, ticketPrice, showDateTime, doorsTime,
                            capacity, availableTicketCount, maxTicketsPerPurchase, ticketsOnSale);
    }

    // region: Commands (behavior): changes fields directly

    public Result<Void, ConcertRejection> rescheduleTo(LocalDateTime newShowDateTime, LocalTime newDoorsTime) {
        if (!newShowDateTime.isAfter(showDateTime)) {
            return Result.rejected(ConcertRejection.NEW_SHOW_DATE_TIME_IS_NOT_LATER);
        }
        if (capacity - availableTicketCount != 0) {
            return Result.rejected(ConcertRejection.TICKETS_HAVE_ALREADY_BEEN_SOLD);
        }
        this.showDateTime = newShowDateTime;
        this.doorsTime = newDoorsTime;
        return Result.succeeded();
    }

    // availableTicketCount is private state that only this decision reads:
    // event sourcing can keep it that way, ORM persistence can not
    public Result<Void, ConcertRejection> sellTicketsTo(CustomerId customerId, int quantity) {
        if (quantity > availableTicketCount) {
            return Result.rejected(ConcertRejection.NOT_ENOUGH_TICKETS_AVAILABLE);
        }
        this.availableTicketCount -= quantity;
        return Result.succeeded();
    }

    public Result<Void, ConcertRejection> stopTicketSales() {
        this.ticketsOnSale = false;
        return Result.succeeded();
    }

    // end region Commands


    // region: Queries: return field values

    public ConcertId getId() { return id; }
    public LocalDateTime showDateTime() { return showDateTime; }
    public LocalTime doorsTime() { return doorsTime; }
    public boolean ticketsOnSale() { return ticketsOnSale; }
    public String artist() { return artist; }
    // Note that there's no `availableTicketCount()` query method
    // nothing in the domain or the UI needs it, but snapshot() has to provide it to the database
    // --> that's the back door/break in encapsulation required by ORMs

    // end region Queries

    // Concert's internal state representation
    // exists ONLY so the database can round-trip the aggregate
    // breaks encapsulation by directly exposing internal storage of fields
    public record Snapshot(ConcertId id, String artist, int ticketPrice,
                           LocalDateTime showDateTime, LocalTime doorsTime,
                           int capacity, int availableTicketCount,
                           int maxTicketsPerPurchase, boolean ticketsOnSale) { }
}
