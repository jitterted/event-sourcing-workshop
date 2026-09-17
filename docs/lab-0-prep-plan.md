# Lab 0 Prep — Build the "Before" (Snapshot-Persisted Concert)

*Event Sourcing Workshop · JitterTicket · prep for the "Delete the Snapshot" guided exercise (LO3)*

## Goal

Lab 0 is a guided reading/strike-through demo of **LO3 — Event Sourcing simplifies DDD implementation**, targeting the **"back-door state access"** clause: persistence forcing the aggregate to expose its whole internal state (`snapshot()` out, `new Concert(snapshot)` in) purely so the database can save it.

The live codebase is *already* event-sourced, so there is nothing to delete. This prep creates a **working "before"** — a snapshot/DBO-persisted `Concert` with **no event sourcing in it** — to show side-by-side against the real (ES) `Concert`, which stays untouched as the "after."

## Strategy: an isolated package, not an in-place edit

Add a self-contained package `dev.ted.jitterticket.orm`. **Do not** convert the real `domain/concert/Concert.java` — the whole app (event store generics, projectors, processors, `PurchaseTicketsUseCase`, controllers) depends on the ES `Concert`, so stripping ES out of it in place would break compilation and you'd no longer have a *working* before.

- **After** (the reveal): `dev.ted.jitterticket.eventsourced.domain.concert.Concert` — leave exactly as-is.
- **Before** (this prep): `dev.ted.jitterticket.orm.*` — compiles and runs on its own, imports nothing from `…eventsourced`.

Demoed behavior: `rescheduleTo(...)` (mutates two fields) plus `stopTicketSales()` (flips a flag) — both exist on the real `Concert`, so the later ES conversion maps 1:1.

**Design conventions used below** (matching the real codebase's style):
- `Concert.Snapshot` is a **nested record** inside `Concert` (like `Customer.TicketOrder`) — it's Concert's own state shape, and nesting concentrates all the snapshot plumbing in one file so the strike-through is more visceral.
- `ConcertDbo` is a **dumb bag of data** (a `record`). It knows nothing about how to build or extract a `Concert`.
- The **repository owns both directions of the mapping** — extraction (`snapshot()`) in `save`, reconstruction (`new Concert(snapshot)`) in `findById` — so both back-door call sites sit together at the persistence boundary.

---

## What to ADD

New files under `src/main/java/dev/ted/jitterticket/orm/` (+ one test).

### `ConcertId.java`
Local id so the package imports nothing from `…eventsourced`.

Source: [ConcertId.java](../src/main/java/dev/ted/jitterticket/orm/ConcertId.java)

### `Concert.java` — mutable aggregate (the teaching subject)
Note the two **back-door** members: the reconstitution constructor (state **in**) and `snapshot()` (state **out**), plus the nested `Snapshot` record. Everything else is ordinary domain behavior that mutates private fields directly — **no events**.

Source: [Concert.java](../src/main/java/dev/ted/jitterticket/orm/Concert.java)

### `ConcertDbo.java` — a dumb persistence row
No behavior. The repository, not the DBO, owns the mapping. `id` is duplicated as the key column, exactly like a real JDBC row (a primary key + a serialized state blob) — keep it; it makes the DBO feel authentically persistence-y.

Source: [ConcertDbo.java](../src/main/java/dev/ted/jitterticket/orm/ConcertDbo.java)

### `ConcertRepository.java` + `InMemoryConcertRepository.java`
The repository is where **both** back doors are called.

Source: [ConcertRepository.java](../src/main/java/dev/ted/jitterticket/orm/ConcertRepository.java)

Source: [InMemoryConcertRepository.java](../src/main/java/dev/ted/jitterticket/orm/InMemoryConcertRepository.java)

### `ConcertRepositoryTest.java` (test source root) — proves it's a *working* before

Source: [ConcertRepositoryTest.java](../src/test/java/dev/ted/jitterticket/orm/ConcertRepositoryTest.java)

---

## What to deliberately OMIT (this is what "no after" means)

The `orm` package must contain **none** of the following (all present in the real `Concert`):

- **No `extends EventSourcedAggregate`**, and none of `enqueue / apply / applyAll / uncommittedEvents / clearUncommittedEvents`.
- **No event types** (`ConcertScheduled / ConcertRescheduled / TicketsSold / TicketSalesStopped / ConcertEvent / Event`). Behavior mutates fields instead of enqueuing events.
- **No `reconstitute(List<events>)`** — reconstruction is `new Concert(snapshot)` only.
- **No `EventStore / CommandExecutorFactory / projectors / processors`** imports.

The members that exist **only here** — `snapshot()`, `new Concert(Snapshot)`, the nested `Concert.Snapshot`, `ConcertDbo`, and the repository's mapping (both directions) — are exactly what the reveal deletes, replaced by events + `apply()` + the event store.

---

## The round-trip (what the debrief strikes through)

The two hole-punch calls live in the repository:

```java
// save(concert):
Concert.Snapshot snapshot = concert.snapshot(); // <-- back door (out)
store.put(concert.getId(), new ConcertDbo(concert.getId(), snapshot));

// findById(id):
return Optional.ofNullable(store.get(id))
               .map(dbo -> new Concert(dbo.snapshot())); // <-- back door (in)
```

`snapshot()` and `new Concert(snapshot)` exist only because the store needs the aggregate's guts. Nothing else in `Concert` calls them.

---

## Recommended prop — the persistence tax made physical (the table & the migration)

Two static SQL artifacts turn the abstract "traditional persistence has a tax" into something the room can see. They're **reading props**, not wired into the app — you don't need a live database.

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

- [ ] `orm` package compiles.
- [ ] `ConcertRepositoryTest` is green (the round-trip actually persists state).
- [ ] `ConcertDbo` is a `record` with **no** behavior; `Concert.Snapshot` is nested; the mapping lives in the repository (both directions).
- [ ] A search of `orm/` shows **zero** imports from `dev.ted.jitterticket.eventsourced`.
- [ ] The real `domain/concert/Concert.java` is unchanged (the untouched "after").
- [ ] The two SQL props are on hand (`docs/lab0/`).
- [ ] You can open `orm.Concert` and `eventsourced.domain.concert.Concert` side-by-side for the reveal.

## In the session

Show `orm.Concert` (before) → run the strike-through: point at every member that exists only for the database, delete it, then reveal the SQL table + migration (the persistence tax), and finally `eventsourced.domain.concert.Concert` (after), where those members are gone and behavior emits events folded by `apply()`. Land: **the aggregate should never need a method whose only caller is the database.**
