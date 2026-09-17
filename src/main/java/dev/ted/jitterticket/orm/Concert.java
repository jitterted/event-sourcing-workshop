package dev.ted.jitterticket.orm;

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
    public void rescheduleTo(LocalDateTime newShowDateTime, LocalTime newDoorsTime) {
        this.showDateTime = newShowDateTime;
        this.doorsTime = newDoorsTime;
    }

    public void stopTicketSales() {
        this.ticketsOnSale = false;
    }
    // end region Commands

    // region: Queries: return field values
    public ConcertId getId() { return id; }
    public LocalDateTime showDateTime() { return showDateTime; }
    public LocalTime doorsTime() { return doorsTime; }
    public boolean ticketsOnSale() { return ticketsOnSale; }
    public String artist() { return artist; }
    public int availableTicketCount() { return availableTicketCount; }
    // end region Queries

    // Concert's internal state representation
    // exists ONLY so the database can round-trip the aggregate
    // breaks encapsulation by directly exposing internal storage of fields
    public record Snapshot(ConcertId id, String artist, int ticketPrice,
                           LocalDateTime showDateTime, LocalTime doorsTime,
                           int capacity, int availableTicketCount,
                           int maxTicketsPerPurchase, boolean ticketsOnSale) { }
}
