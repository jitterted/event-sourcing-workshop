# Lab 0 Prep — Build the "Before" (Snapshot-Persisted Concert)

*Event Sourcing Workshop · JitterTicket · prep for the "Delete the Snapshot" guided exercise (LO3)*

## Goal

Lab 0 is a guided reading/strike-through demo of **LO3 — Event Sourcing simplifies DDD implementation**, targeting the **"back-door state access"** clause: persistence forcing the aggregate to expose its whole internal state (`snapshot()` out, `new Concert(snapshot)` in) purely so the database can save it.

The live codebase is *already* event-sourced, so there is nothing to delete. This prep creates a **working "before"** — a snapshot/DBO-persisted `Concert` with **no event sourcing in it** — to show side-by-side against the real (ES) `Concert`, which stays untouched as the "after."

## Strategy: an isolated package, not an in-place edit

Add a self-contained package `dev.ted.jitterticket.lab0`. **Do not** convert the real `domain/concert/Concert.java` — the whole app (event store generics, projectors, processors, `PurchaseTicketsUseCase`, controllers) depends on the ES `Concert`, so stripping ES out of it in place would break compilation and you'd no longer have a *working* before.

- **After** (the reveal): `dev.ted.jitterticket.eventsourced.domain.concert.Concert` — leave exactly as-is.
- **Before** (this prep): `dev.ted.jitterticket.lab0.*` — compiles and runs on its own, imports nothing from `…eventsourced`.

Demoed behavior: `rescheduleTo(...)` (mutates two fields) plus `stopTicketSales()` (flips a flag) — both exist on the real `Concert`, so the later ES conversion maps 1:1.

---

## What to ADD

New files under `src/main/java/dev/ted/jitterticket/lab0/` (+ one test).

### `ConcertId.java`
Local id so the package imports nothing from `…eventsourced`.

```java
package dev.ted.jitterticket.lab0;

import java.util.UUID;

public record ConcertId(UUID id) {
    public static ConcertId createRandom() {
        return new ConcertId(UUID.randomUUID());
    }
}
```

### `Concert.java` — mutable aggregate (the teaching subject)
Note the two **back-door** members: the reconstitution constructor (state **in**) and `snapshot()` (state **out**). Everything else is ordinary domain behavior that mutates private fields directly — **no events**.

```java
package dev.ted.jitterticket.lab0;

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
        Concert c = new Concert();
        c.id = id;
        c.artist = artist;
        c.ticketPrice = ticketPrice;
        c.showDateTime = showDateTime;
        c.doorsTime = doorsTime;
        c.capacity = capacity;
        c.availableTicketCount = capacity;
        c.maxTicketsPerPurchase = maxTicketsPerPurchase;
        return c;
    }

    private Concert() { }

    // BACK DOOR (in): the whole internal state, injected from outside.
    Concert(ConcertSnapshot s) {
        this.id = s.id();
        this.artist = s.artist();
        this.ticketPrice = s.ticketPrice();
        this.showDateTime = s.showDateTime();
        this.doorsTime = s.doorsTime();
        this.capacity = s.capacity();
        this.availableTicketCount = s.availableTicketCount();
        this.maxTicketsPerPurchase = s.maxTicketsPerPurchase();
        this.ticketsOnSale = s.ticketsOnSale();
    }

    // BACK DOOR (out): the whole internal state, handed to the mapper.
    public ConcertSnapshot snapshot() {
        return new ConcertSnapshot(id, artist, ticketPrice, showDateTime, doorsTime,
                                   capacity, availableTicketCount, maxTicketsPerPurchase, ticketsOnSale);
    }

    // Behavior — mutates fields directly (no events)
    public void rescheduleTo(LocalDateTime newShowDateTime, LocalTime newDoorsTime) {
        this.showDateTime = newShowDateTime;
        this.doorsTime = newDoorsTime;
    }

    public void stopTicketSales() {
        this.ticketsOnSale = false;
    }

    // Queries
    public ConcertId getId() { return id; }
    public LocalDateTime showDateTime() { return showDateTime; }
    public LocalTime doorsTime() { return doorsTime; }
    public boolean ticketsOnSale() { return ticketsOnSale; }
    public String artist() { return artist; }
    public int availableTicketCount() { return availableTicketCount; }
}
```

### `ConcertSnapshot.java` — the full state carrier

```java
package dev.ted.jitterticket.lab0;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record ConcertSnapshot(ConcertId id, String artist, int ticketPrice,
                              LocalDateTime showDateTime, LocalTime doorsTime,
                              int capacity, int availableTicketCount,
                              int maxTicketsPerPurchase, boolean ticketsOnSale) { }
```

### `ConcertDbo.java` — wraps the snapshot for the store; owns the mapping

```java
package dev.ted.jitterticket.lab0;

public class ConcertDbo {
    private final ConcertId id;
    private final ConcertSnapshot snapshot;

    public ConcertDbo(ConcertId id, ConcertSnapshot snapshot) {
        this.id = id;
        this.snapshot = snapshot;
    }

    public static ConcertDbo from(Concert concert) {
        return new ConcertDbo(concert.getId(), concert.snapshot());
    }

    public Concert asConcert() {
        return new Concert(snapshot);
    }

    public ConcertId id() { return id; }
}
```

### `ConcertRepository.java` + `InMemoryConcertRepository.java`

```java
package dev.ted.jitterticket.lab0;

import java.util.Optional;

public interface ConcertRepository {
    void save(Concert concert);
    Optional<Concert> findById(ConcertId id);
}
```

```java
package dev.ted.jitterticket.lab0;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryConcertRepository implements ConcertRepository {
    private final Map<ConcertId, ConcertDbo> store = new HashMap<>();

    @Override
    public void save(Concert concert) {
        store.put(concert.getId(), ConcertDbo.from(concert));  // EXTRACT full state
    }

    @Override
    public Optional<Concert> findById(ConcertId id) {
        return Optional.ofNullable(store.get(id))
                       .map(ConcertDbo::asConcert);             // REBUILD from the dump
    }
}
```

### `ConcertRoundTripTest.java` (test source root) — proves it's a *working* before

```java
package dev.ted.jitterticket.lab0;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.LocalTime;
import static org.assertj.core.api.Assertions.assertThat;

class ConcertRoundTripTest {
    @Test
    void reschedulePersistsThroughSnapshotRoundTrip() {
        ConcertRepository repo = new InMemoryConcertRepository();
        ConcertId id = ConcertId.createRandom();
        repo.save(Concert.schedule(id, "Headliner", 35,
                LocalDateTime.of(2026, 11, 11, 20, 0), LocalTime.of(19, 0), 100, 4));

        Concert concert = repo.findById(id).orElseThrow();   // load DBO -> asConcert
        LocalDateTime newShow = LocalDateTime.of(2026, 11, 12, 20, 0);
        concert.rescheduleTo(newShow, LocalTime.of(19, 0));  // behavior mutates state
        repo.save(concert);                                   // snapshot() -> store

        assertThat(repo.findById(id).orElseThrow().showDateTime()).isEqualTo(newShow);
    }
}
```

---

## What to deliberately OMIT (this is what "no after" means)

The `lab0` package must contain **none** of the following (all present in the real `Concert`):

- **No `extends EventSourcedAggregate`**, and none of `enqueue / apply / applyAll / uncommittedEvents / clearUncommittedEvents`.
- **No event types** (`ConcertScheduled / ConcertRescheduled / TicketsSold / TicketSalesStopped / ConcertEvent / Event`). Behavior mutates fields instead of enqueuing events.
- **No `reconstitute(List<events>)`** — reconstruction is `new Concert(snapshot)` only.
- **No `EventStore / CommandExecutorFactory / projectors / processors`** imports.

The members that exist **only here** — `snapshot()`, `new Concert(ConcertSnapshot)`, `ConcertSnapshot`, `ConcertDbo` / `asConcert()`, and the repository's DBO mapping — are exactly what the reveal deletes, replaced by events + `apply()` + the event store.

---

## The 5-step round-trip (what the debrief strikes through)

```
ConcertDbo dbo     = repo.findById(id);       // 1. load DBO (a snapshot)
Concert    concert = dbo.asConcert();          // 2. map DBO -> entity   <-- back door (in)
concert.rescheduleTo(...);                     // 3. behavior mutates state
ConcertSnapshot s  = concert.snapshot();       // 4. EXTRACT full state   <-- back door (out)
repo.save(new ConcertDbo(id, s));              // 5. store
```

Steps 2 and 4 exist only because the store needs the aggregate's guts.

---

## Verification checklist

- [ ] `lab0` package compiles.
- [ ] `ConcertRoundTripTest` is green (the round-trip actually persists state).
- [ ] A search of `lab0/` shows **zero** imports from `dev.ted.jitterticket.eventsourced`.
- [ ] The real `domain/concert/Concert.java` is unchanged (the untouched "after").
- [ ] You can open `lab0.Concert` and `eventsourced.domain.concert.Concert` side-by-side for the reveal.

## In the session

Show `lab0.Concert` (before) → run the strike-through: point at every member that exists only for the database, delete it, and reveal `eventsourced.domain.concert.Concert` (after), where those members are gone and behavior emits events folded by `apply()`. Land: **the aggregate should never need a method whose only caller is the database.**
