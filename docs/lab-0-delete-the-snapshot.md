# Lab 0 — Delete the Snapshot

*A guided, whole-room demonstration of Learning Objective 3.*

> **LO3 — Experience how Event Sourcing simplifies DDD implementation:** Eliminate traditional repository abstractions, object-relational mapping, and "back-door" state access that traditional persistence requires, while keeping the value of Bounded Contexts and Ubiquitous Language.

## Why this exercise exists

LO3 is the objective that's normally *felt* rather than drilled — and in an already-event-sourced codebase the saving is invisible, because there's no repository to delete and no mapper to skip. You can't feel an absence.

This exercise manufactures the contrast. We start from the persistence style many teams actually use — a **pure domain model** (no ORM annotations) round-tripped through a **snapshot** — and delete the parts of it that exist *only* so the database can see inside the aggregate. The payload is visible: participants watch real members get struck through, and watch the aggregate's encapsulation improve at the same time.

The precise target is the **"back-door state access"** clause. It does **not** mean business logic reading getters. It means: **persistence forces the aggregate to expose its entire internal state** — once on the way out (to save it) and once on the way in (to rebuild it). Those exposures are a hole punched in the aggregate purely to serve the store.

- **Format:** facilitator-led, whole room. No implementation risk.
- **Time:** 15–20 minutes (three beats; the third — the SQL table & migration — adds ~5).
- **On screen:** the two `Concert` sketches below side by side, the running scoreboard, and the two SQL props (`docs/lab0/`).
- **Placement:** works best as an opener, *before* Lab 1 — it establishes the ES value proposition the feature labs then lean on.

---

## The "before" — pure domain, persisted via a snapshot round-trip

Deliberately the *sophisticated* version: the domain model is clean Java with no JPA annotations. The infrastructure keeps its distance — and pays for it with a snapshot round-trip. `Snapshot` is nested inside `Concert` (it's Concert's own state shape); `ConcertDbo` is a dumb data row; the **repository owns the mapping both ways**.

```java
// Pure domain — but the DB still has to get its state in and out.
public class Concert {
    private ConcertId id;
    private ShowTime  showTime;
    private boolean   onSale;
    // ... more private state ...

    // BACK DOOR (in): the whole internal state, injected from outside.
    Concert(Snapshot s) {
        this.id = s.id(); this.showTime = s.showTime(); this.onSale = s.onSale();
    }

    public void startTicketSales() {
        if (onSale) throw new IllegalStateException("already on sale");
        this.onSale = true;
    }

    // BACK DOOR (out): the whole internal state, handed to the repository.
    public Snapshot snapshot() {
        return new Snapshot(id, showTime, onSale);
    }

    // Concert's own state shape — exists ONLY so the DB can round-trip it.
    public record Snapshot(ConcertId id, ShowTime showTime, boolean onSale) { }
}

// A dumb persistence row. The repository — not the DBO — owns the mapping.
public record ConcertDbo(ConcertId id, Concert.Snapshot snapshot) { }
```

The mapping — both hole-punch calls — lives in the repository:

```java
// InMemoryConcertRepository
void save(Concert concert) {
    Concert.Snapshot s = concert.snapshot();              // EXTRACT full state  <-- hole punch (out)
    rows.put(concert.getId(), new ConcertDbo(concert.getId(), s));
}
Optional<Concert> findById(ConcertId id) {
    return Optional.ofNullable(rows.get(id))
                   .map(dbo -> new Concert(dbo.snapshot()));   // REBUILD  <-- hole punch (in)
}
```

…and the caller never sees any of it:

```java
Concert concert = repository.findById(id).orElseThrow();  // load row -> rebuild
concert.startTicketSales();                               // behavior mutates state
repository.save(concert);                                 // extract -> store
```

`snapshot()` and `new Concert(Snapshot)` exist only because the store needs the aggregate's guts. The aggregate is *made* to expose them.

---

## The "after" — event-sourced, state never leaves the aggregate

```java
public class Concert {
    private ConcertId id;
    private ShowTime  showTime;
    private boolean   onSale;          // still fully private — nothing extracts it

    // Reconstitution: fold domain facts, not inject a state dump.
    static Concert from(List<ConcertEvent> history) {
        Concert c = new Concert();
        history.forEach(c::apply);
        return c;
    }

    public List<ConcertEvent> startTicketSales() {
        if (onSale) throw new IllegalStateException("already on sale");
        return List.of(new TicketsOnSale(id));       // behavior EMITS a fact
    }

    private void apply(ConcertEvent event) {          // private fold
        switch (event) {
            case ConcertScheduled e -> { this.id = e.id(); this.showTime = e.showTime(); }
            case TicketsOnSale     e -> this.onSale = true;
            default -> { }
        }
    }
    // no snapshot(), no Snapshot, no ConcertDbo, no mapper
}
```

The application flow shrinks to three steps, and none of them reads state out of the aggregate:

```java
List<ConcertEvent> history = eventStore.query(id);   // load facts
Concert concert            = Concert.from(history);  // fold
List<ConcertEvent> events  = concert.startTicketSales(); // behavior emits facts
eventStore.append(id, events);                       // store the facts
```

---

## The guided strike-through (facilitator script)

Do this live on the "before" files so deletion is visceral — actually comment out or delete each line as the room calls it, and keep a running tally.

1. **"Point to every member that exists only so the database can save or load this aggregate."** The room should land on: the nested `Concert.Snapshot`, `snapshot()`, `Concert(Snapshot)`, `ConcertDbo`, and the repository's `snapshot()`/`new Concert(...)` mapping. Strike each one.
2. **"Which of those does the *domain* need?"** None. They are all persistence plumbing. Leave them struck through.
3. **"What did the behavior produce in the ES version that we can persist instead?"** An event — `TicketsOnSale` — a fact the behavior was already emitting for domain reasons. Add it to the "kept" column.
4. **"What replaced the two back doors?"** One private `apply()` fold. Add it — and note it's *smaller* than the members it replaced, and it's private.
5. **Read the scoreboard back.** Count deletions vs. additions out loud.

---

## The scoreboard

| Member (before) | Why it exists | Survives ES? |
|---|---|---|
| `Concert.Snapshot` (nested record) | carries the whole internal state for the DB | ❌ deleted |
| `snapshot()` — state **out** | hands full internal state to the repository | ❌ deleted — *the hole punch* |
| `new Concert(Snapshot)` — state **in** | rebuilds state from the dump | ❌ replaced by `from(history)` fold |
| `ConcertDbo` (dumb row) | the persistence shape of the aggregate | ❌ deleted |
| repository's snapshot mapping (both ways) | persist / rehydrate raw state | ❌ replaced by `append` / `query` |
| events (`TicketsOnSale`, …) | **domain facts** | ✅ kept — they were the point |
| `apply()` / fold | rebuild state from facts (private) | ✅ added — smaller than the back doors it replaces |

The shape of the win: **the members that disappear are exactly the ones with no domain meaning.** What remains is behavior and facts.

---

## Beat 3 — the persistence tax made physical (the table & the migration)

Reveal the two SQL props (`docs/lab0/`) to turn "traditional persistence has a tax" into something visible.

**The table IS your internal representation.** Every column in `concert-schema.sql` is one of `Concert`'s private fields — the database sees the whole aggregate:

```sql
CREATE TABLE concert (
    id UUID PRIMARY KEY, artist VARCHAR(255) NOT NULL, ticket_price INTEGER NOT NULL,
    show_date_time TIMESTAMP NOT NULL, doors_time TIME NOT NULL, capacity INTEGER NOT NULL,
    available_ticket_count INTEGER NOT NULL, max_tickets_per_purchase INTEGER NOT NULL,
    tickets_on_sale BOOLEAN NOT NULL
);
```

**Change costs a migration.** Adding a scheduled on-sale time means a new column — and every existing row has to be dealt with:

```sql
ALTER TABLE concert ADD COLUMN on_sale_at TIMESTAMP;   -- + backfill the existing rows?
```

**Say the framing precisely.** Scheduled on-sale is a genuinely *new fact*, so the ES side is **not** "zero code" — it adds a `TicketSalesScheduled` event + one `apply()` case. The honest win is **"no migration, and history is untouched"** — old concert streams simply lack the event, and "no event" is a fine answer for them. (For the *no-caveats* version, use a field **derivable from existing events** — e.g. tickets-sold-so-far from `TicketsSold` — where ES adds nothing to the write path while the table still needs a column + backfill.) This beat also foreshadows **Lab 1B**, where you build exactly this scheduled-on-sale feature the ES way.

---

## Debrief questions

1. **Which deleted members served the domain, and which served only the database?** (All of the deleted ones served only the database.)
2. **Can `Concert` now expose zero public state accessors? Try to name one the domain still genuinely needs** — as opposed to one persistence used to demand.
3. **We deleted `snapshot()` but added `apply()`. Why isn't `apply()` just another back door?** (It *consumes* facts the aggregate itself emitted, and it's private — nothing outside ever reads state back out. State goes in as domain events, never comes out as a dump.)
4. **When you refactor `Concert`'s internal fields tomorrow, what persisted artifact must change?** (Before: the `concert` table's shape — an `ALTER TABLE` migration plus every existing row. After: nothing — the events are unchanged. The persisted shape is *facts*, decoupled from the internal representation.)

---

## Caveat — the ES "snapshot" is a different animal

Someone will ask, because event sourcing also has a thing called a snapshot. Name the difference before it derails you:

- The **ORM snapshot** here is **mandatory and authoritative** — it *is* your saved state, and the aggregate is forced to expose it.
- An **ES snapshot** is an **opt-in performance checkpoint** — a cache of folded state you can throw away and rebuild from the events at any time. It never becomes the source of truth, and it never requires a public "dump my state" method on the aggregate.

Same word, opposite relationship to encapsulation.

---

## Facilitation notes

- Keep both `Concert` files visible simultaneously; the contrast does the teaching.
- Resist implementing anything — this is a *reading and deleting* exercise. Save the typing for Labs 1–3.
- If the room is skeptical that "nobody writes the snapshot pattern," point out that this *is* the disciplined version (pure domain, no annotations). The naive JPA-entity version leaks even more.
- The single sentence to land: **the aggregate should never need a method whose only caller is the database.**
