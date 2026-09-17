# Lab 0 Prep — Round 2 (align Lab 0 with LO3)

*Event Sourcing Workshop · JitterTicket · prep remaining after the `orm` package was built*

Round 1 is **done and archived**: [archive/lab-0-prep-plan-v1-completed.md](archive/lab-0-prep-plan-v1-completed.md). It produced the working "before" — the snapshot-persisted [`dev.ted.jitterticket.orm`](../src/main/java/dev/ted/jitterticket/orm/) package, green round-trip tests, and the two SQL props in `docs/lab0/`.

This round closes the gap between what Lab 0 actually shows and the **LO3 atoms** in [knowledge-map.md](knowledge-map.md), and adds the third version of `Concert`.

## The shape Lab 0 now takes

Three versions of `Concert`, shown in two separate sittings:

| # | Version | Where | When |
|---|---|---|---|
| 1 | **ORM** — snapshot round-trip, back doors | `dev.ted.jitterticket.orm` | Lab 0 opener (15–20 min) |
| 2 | **ES aggregate** — events + `apply()` fold | `eventsourced.domain.concert.Concert` (real code, untouched) | Lab 0 opener |
| 3 | **Pure ES, non-aggregate** — a `decide` function, no object holding state | new isolated package (see B1) | Lead-in to **Lab 2**, not the opener |

Splitting 3 off keeps the opener tight and gives Lab 2's DCB material a running start.

**The behavior all three implement: `sellTicketsTo(customerId, quantity)`,** with one rule — *quantity must not exceed the tickets available*. It's the cleanest illustration of the distinction that drives this round:

> **A query method that serves the domain or the UI is not a back door.** `artist()` and `showDateTime()` exist because a screen needs them. `availableTicketCount` is different: it's internal state that a *decision* reads, and nothing outside needs to see it. Event sourcing lets it stay completely private. ORM persistence drags it out through `snapshot()` whether you expose a query method or not.

That sentence is the LO3 "back-door" clause stated precisely, and it replaces the map's current "zero public accessors" atom (see C).

---

# A. Code prep — the `orm` package (the "before")

### A1. Add `sellTicketsTo` to `orm.Concert` [BUILD]
- `public void sellTicketsTo(CustomerId customerId, int quantity)` — mutates `availableTicketCount` directly; no event.
- Rule: if `quantity > availableTicketCount`, reject (throw). One rule only — the per-customer limit belongs to Lab 2 and must not be pre-empted here.
- Needs a local `CustomerId` record in `orm`, since the package imports nothing from `…eventsourced`.
- The new state still round-trips through `Concert.Snapshot`, which already carries `availableTicketCount`. **That's the point of the beat:** the field is private, no query method exposes it, and the database gets it anyway.

### A2. Remove the `availableTicketCount()` query method from `orm.Concert` [BUILD]
Right now it's public, which blunts A1. Once it's gone:
- Nothing in the domain or a screen reads it.
- `snapshot()` still hands it to the repository — the hole punch is visible with nothing else to blame it on.
- `ConcertRepositoryTest` asserts on it today, so the assertion moves to observable behavior instead (sell 4 of 100, then a 97-ticket purchase is rejected while a 96-ticket one succeeds).

Keep `artist()`, `showDateTime()`, `doorsTime()` and `ticketsOnSale()` — those are the legitimate query methods, and keeping them makes the distinction teachable rather than absolute.

### A3. Give `orm.Concert.rescheduleTo` the same guard as the real aggregate [BUILD]
The real `Concert.rescheduleTo` only reschedules when the new date is later **and** no tickets have been sold. `orm.Concert` has no guard. Side by side, that difference reads as if event sourcing caused it. Add the same guard so **the only differences between versions 1 and 2 are persistence**.

### A4. Sell-path test for `orm` [BUILD]
Add to `ConcertRepositoryTest` (or a sibling test): sell tickets, save, reload, sell again, and show the availability carried across the round-trip. This is the "working before" evidence for the new behavior.

---

# B. Code prep — the pure-ES, non-aggregate version (Lab 2 lead-in)

### B1. New isolated package [BUILD]
Proposed name: **`dev.ted.jitterticket.decider`** — *confirm or rename before I build it.* Same isolation rule as `orm`: imports nothing from `…eventsourced`, nothing from `…orm`.

Contents:
- **Local event types** — `ConcertEvent` (sealed), `ConcertScheduled`, `TicketsSold`. Minimal: no metadata/`null` second field, no `Id` plumbing. They are copies, deliberately, so the decider reads clean.
- **`ConcertDecider`** — one static function, no fields, no instance:
  ```java
  static List<ConcertEvent> sellTickets(List<ConcertEvent> history, CustomerId customerId, int quantity)
  ```
  It folds `history` into **local variables** (capacity minus tickets sold), applies the one rule, and returns the resulting events (or an empty list / a rejection — decide in B2).
- **A minimal event store** — append + query over a `Map<ConcertId, List<ConcertEvent>>`. Roughly 15 lines. This is where "append + query" is literally true, unlike the real `EventStore`.
- **`ConcertDeciderTest`** — Given events → When command → Then events. This also demonstrates the LO4 testing pattern with nothing to mock and no object to construct.

### B2. Decisions to lock before I write it
1. **Rejection style:** throw, or return an empty list of events? (The map's LO4 atom says *"a rejected command may emit NO event — events are facts, not errors."* An empty list matches the atom; a throw matches the rest of this codebase.)
2. **Does the decider take the whole history, or a folded decision model?** You chose the decider function, so: history in, events out, folding inline. Confirm you don't want the intermediate `record DecisionModel(...)` — that's the Lab 2 shape and might be better saved for Lab 2 itself.
3. **Tags / query predicate:** leave them out entirely here (pure Lab 2 material), or gesture at them in a comment?

---

# C. Knowledge-map corrections (LO3 branch)

Three L00 atoms don't match reality. Edit both [knowledge-map.md](knowledge-map.md) and `knowledge-map.html` — the HTML is generated but hand-editable; each atom appears once as a literal string.

| Current atom | Problem | Proposed replacement |
|---|---|---|
| `Delete ConcertDbo / asConcert(); append + query replace save + findById` | `asConcert()` never existed (the repository owns the mapping). The real `EventStore` **has** `save(aggregate)` and `findById(id)` — the repository shape survives event sourcing. | `Delete ConcertDbo + the snapshot mapping; save/findById survive — only the back doors go` |
| `The aggregate can now expose ZERO public state accessors` | False for the real `Concert` (8 public query methods), and false in spirit: query methods for domain/UI are legitimate. | `Query methods for domain/UI are fine; state that only the DB demanded (availableTicketCount) stays private` |
| `Adding a field folds from existing events — no persistence code changes` | True only for a **derivable** field. The current SQL prop uses scheduled-on-sale, a genuinely new fact that does cost an event + an `apply` case. | `A derivable field (tickets sold so far) folds from existing events — no migration, no persistence code` |

**Also consider (your call):**
- Retag `In DCB there is NO aggregate to load — you query and fold` as `[L00 · L2]`, since version 3 now introduces it.
- The `[F]`-tagged atoms *"a repository hides a store behind a collection illusion"* and *"saving forces the aggregate to expose its whole internal state"* are marked Foundation, but Lab 0 is where they're actually demonstrated. Retag `[F · L00]`.
- The LO3 note at the bottom of the map still says Lab 0 strikes out "the DBO/mapper"; update it for the three-version shape.

---

# D. Doc work

### D1. Rewrite `lab-0-delete-the-snapshot.md` [BUILD — not started, on hold at your request]
The review found the Java in it matches neither `orm.Concert` nor the real aggregate. It uses `ShowTime`, `onSale`, `startTicketSales()`, `TicketsOnSale`, `Concert.from(history)` and commands returning `List<ConcertEvent>` — several of which are things participants build in **Lab 1A**, so they don't exist yet at Lab 0 time. Planned fixes:
- Replace the inline sketches with links to the real files (as this plan does), or rewrite them with the real names: `showDateTime`/`doorsTime`, `ticketsOnSale`, `stopTicketSales()`/`TicketSalesStopped`, `reconstitute(...)`, `void` commands calling `enqueue(...)`.
- Fix `apply()`: it is `protected` (an override), not private. Debrief Q3's argument has to change accordingly.
- Fix the "no repository to delete" claim and the `append`/`query` flow to match C's first atom.
- Rewrite debrief Q2 around the accessors-vs-back-door distinction.
- Rebuild the scoreboard around `sellTicketsTo` and `availableTicketCount`.
- Number the beats consistently (the doc says "three beats" but only labels Beat 3).
- Add the ES-snapshot caveat's local example: this codebase already uses "snapshot" for **projections** (`ProjectionCoordinator.loadSnapshot()`), which is exactly the opt-in checkpoint kind.

### D2. New doc: `docs/lab-2-lead-in-decider.md` [BUILD]
Short facilitator piece for version 3: the decider function, "there is no aggregate to load", append + query for real, and the Given/When/Then test with nothing to construct. Cross-link from the Lab 0 doc and from the Lab 2 material when it's written.

### D3. `docs/README.md` [BUILD]
Add the archive folder, the new lead-in doc, and the new package; refresh the Lab 0 line for the three-version shape.

---

# E. Verification checklist

- [ ] `orm.Concert.sellTicketsTo` exists, enforces the availability rule, and has **no** `availableTicketCount()` query method.
- [ ] `orm.Concert.rescheduleTo` guards exactly like the real aggregate.
- [ ] `orm` tests green, including the sell-path round-trip.
- [ ] The decider package exists, imports nothing from `…eventsourced` or `…orm`, and its Given/When/Then test is green.
- [ ] The real `eventsourced` code is still untouched (`git diff` shows nothing under `src/main/java/dev/ted/jitterticket/eventsourced`).
- [ ] The three LO3 atoms are corrected in **both** `knowledge-map.md` and `knowledge-map.html`.
- [ ] `lab-0-delete-the-snapshot.md` names only members that exist **before** Lab 1A.
- [ ] `docs/README.md` points at every current doc, and the archived plan is clearly marked as history.
