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

**Design conventions used below** (matching the real codebase's style):
- `Concert.Snapshot` is a **nested record** inside `Concert` (like `Customer.TicketOrder`) — it's Concert's own state shape, and nesting concentrates all the snapshot plumbing in one file so the strike-through is more visceral.
- `ConcertDbo` is a **dumb bag of data** (a `record`, like the real `EventDbo`). It knows nothing about how to build or extract a `Concert`.
- The **repository owns both directions of the mapping** — extraction (`snapshot()`) in `save`, reconstruction (`new Concert(snapshot)`) in `findById` — so both back-door call sites sit together at the persistence boundary.

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
Note the two **back-door** members: the reconstitution constructor (state **in**) and `snapshot()` (state **out**), plus the nested `Snapshot` record. Everything else is ordinary domain behavior that mutates private fields directly — **no events**.

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
    Concert(Snapshot s) {
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

    // BACK DOOR (out): the whole internal state, handed to the repository.
    public Snapshot snapshot() {
        return new Snapshot(id, artist, ticketPrice, showDateTime, doorsTime,
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

    // Concert's own state shape — nested because it IS Concert's internals, and
    // it exists ONLY so the database can round-trip the aggregate. Vanishes in ES.
    public record Snapshot(ConcertId id, String artist, int ticketPrice,
                           LocalDateTime showDateTime, LocalTime doorsTime,
                           int capacity, int availableTicketCount,
                           int maxTicketsPerPurchase, boolean ticketsOnSale) { }
}
```

### `ConcertDbo.java` — a dumb persistence row
No behavior. The repository, not the DBO, owns the mapping. `id` is duplicated as the key column, exactly like a real JDBC row (a primary key + a serialized state blob) — keep it; it makes the DBO feel authentically persistence-y.

```java
package dev.ted.jitterticket.lab0;

public record ConcertDbo(ConcertId id, Concert.Snapshot snapshot) { }
```

### `ConcertRepository.java` + `InMemoryConcertRepository.java`
The repository is where **both** back doors are called.

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
        Concert.Snapshot snapshot = concert.snapshot();                 // BACK DOOR (out)
        store.put(concert.getId(), new ConcertDbo(concert.getId(), snapshot));
    }

    @Override
    public Optional<Concert> findById(ConcertId id) {
        return Optional.ofNullable(store.get(id))
                       .map(dbo -> new Concert(dbo.snapshot()));         // BACK DOOR (in)
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

        Concert concert = repo.findById(id).orElseThrow();   // load row -> rebuild (back door in)
        LocalDateTime newShow = LocalDateTime.of(2026, 11, 12, 20, 0);
        concert.rescheduleTo(newShow, LocalTime.of(19, 0));  // behavior mutates state
        repo.save(concert);                                   // extract snapshot -> store (back door out)

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

The members that exist **only here** — `snapshot()`, `new Concert(Snapshot)`, the nested `Concert.Snapshot`, `ConcertDbo`, and the repository's mapping (both directions) — are exactly what the reveal deletes, replaced by events + `apply()` + the event store.

---

## The round-trip (what the debrief strikes through)

The two hole-punch calls live in the repository:

```
// save(concert):
Concert.Snapshot s = concert.snapshot();        // EXTRACT full state   <-- back door (out)
store.put(id, new ConcertDbo(id, s));            // store the row

// findById(id):
ConcertDbo dbo = store.get(id);                  // load the row
Concert concert = new Concert(dbo.snapshot());   // REBUILD from the dump <-- back door (in)
```

`snapshot()` and `new Concert(snapshot)` exist only because the store needs the aggregate's guts. Nothing else in `Concert` calls them.

---

## Recommended prop — the persistence tax made physical (the table & the migration)

Two static SQL artifacts turn the abstract "traditional persistence has a tax" into something the room can see. They're **reading props**, not wired into the app — you don't need a live database. (If you want the live "`SELECT *` shows my private fields" moment, back the repo with embedded **H2** + a 3-line `JdbcTemplate` — no Docker, instant — and treat it as an optional day-of stretch, kept isolated to `lab0`.)

Files: `docs/lab0/concert-schema.sql` and `docs/lab0/add-on-sale-schedule.migration.sql`.

**Beat 2 — the table IS your internal representation.** Show `concert-schema.sql`: every column is one of `Concert`'s private fields. The database can see the whole aggregate; that's the coupling, made physical.

```sql
CREATE TABLE concert (
    id                       UUID PRIMARY KEY,
    artist                   VARCHAR(255) NOT NULL,
    ticket_price             INTEGER NOT NULL,
    show_date_time           TIMESTAMP NOT NULL,
    doors_time               TIME NOT NULL,
    capacity                 INTEGER NOT NULL,
    available_ticket_count   INTEGER NOT NULL,
    max_tickets_per_purchase INTEGER NOT NULL,
    tickets_on_sale          BOOLEAN NOT NULL
);
```

**Beat 3 — change costs a migration.** Show `add-on-sale-schedule.migration.sql`: adding a scheduled on-sale time means a new column, and every existing row must be dealt with.

```sql
ALTER TABLE concert ADD COLUMN on_sale_at TIMESTAMP;   -- + backfill the existing rows?
```

**Say the framing precisely.** Scheduled on-sale is a genuinely *new fact*, so ES is **not** "zero code" — it adds a `TicketSalesScheduled` event + one `apply()` case. The honest win is **"no migration, history untouched"** (old streams simply lack the event) — not "no code." For the *no-caveats* version, use a field **derivable from existing events** (e.g. tickets-sold-so-far from `TicketsSold`): there ES adds nothing to the write path while the table still needs a column + backfill. (This beat also foreshadows Lab 1B.)

---

## Verification checklist

- [ ] `lab0` package compiles.
- [ ] `ConcertRoundTripTest` is green (the round-trip actually persists state).
- [ ] `ConcertDbo` is a `record` with **no** behavior; `Concert.Snapshot` is nested; the mapping lives in the repository (both directions).
- [ ] A search of `lab0/` shows **zero** imports from `dev.ted.jitterticket.eventsourced`.
- [ ] The real `domain/concert/Concert.java` is unchanged (the untouched "after").
- [ ] The two SQL props are on hand (`docs/lab0/`).
- [ ] You can open `lab0.Concert` and `eventsourced.domain.concert.Concert` side-by-side for the reveal.

## In the session

Show `lab0.Concert` (before) → run the strike-through: point at every member that exists only for the database, delete it, then reveal the SQL table + migration (the persistence tax), and finally `eventsourced.domain.concert.Concert` (after), where those members are gone and behavior emits events folded by `apply()`. Land: **the aggregate should never need a method whose only caller is the database.**
