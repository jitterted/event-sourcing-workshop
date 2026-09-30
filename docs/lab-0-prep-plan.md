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

# A0. How commands reject — `Result` [DONE, applied to `orm` + the real aggregate]

*Decided and implemented. The decider (B1) will use the same style when it's built.*

Today the codebase has three different answers, which is one too many for a side-by-side reveal:

| Command | Today | Problem |
|---|---|---|
| real `Concert.rescheduleTo` | guard fails → **silently does nothing** | The caller can't tell. Indefensible in front of a room, and it's in production code, not just the teaching copy. |
| `orm.Concert.sellTicketsTo` | throws `IllegalStateException` | Matches `reconstitute`'s style, but treats a rejection as an error. |
| real `Concert.sellTicketsTo` | no guard at all | Nothing to reject with. |

**Decision: a `Result` return on commands** — `rescheduleTo` and `sellTicketsTo` can reject; `stopTicketSales` returns a `Result` too (always success — see "What shipped" below for why it couldn't stay `void`).

- **Not `boolean`.** It carries no reason, so a controller has nothing to show the user and a test can only assert "something failed" — which fights LO4's *"assertions phrased in domain terms."*
- **Not a throw.** LO4's atom says *"a rejected command may emit NO event — events are facts, not errors."* A rejection is an ordinary outcome, so it belongs in the return value. Keeping it there also stops anyone inventing a `TicketSaleRejected` event.
- **Failure type:** a domain-named `Rejection` (record or enum) in the Ubiquitous Language, not a `String`.
- **In the decider (B1) it's the natural shape:** `Result<List<ConcertEvent>, Rejection>` — "either these facts happened, or none did, and here's why."
- **Write a fresh `Result`, don't copy** the `tdd-game-event-sourced` `jeslib.Result` verbatim: that one is a sealed abstract class whose `successValue()` returns `null` on failure, so a careless caller gets an NPE instead of a compile error. This codebase is on Java 26 (see `case TicketSalesStopped _ ->`), so a sealed interface with `Success` / `Failure` records plus exhaustive `switch` gives the same thing with no null holes.

## What shipped

- **`dev.ted.jitterticket.support.Result`** — sealed interface, `Success` / `Failure` records, `success(v)` / `succeeded()` / `rejected(r)`, `isSuccess()` / `isRejected()`, plus `orElseThrow()` for callers that treat a rejection as their own bug (sample data). Shared by all three versions.
- **`ConcertRejection`** — an enum per package (`eventsourced.domain.concert` and `orm`), each value carrying a message: `NEW_SHOW_DATE_TIME_IS_NOT_LATER`, `TICKETS_HAVE_ALREADY_BEEN_SOLD`, `NOT_ENOUGH_TICKETS_AVAILABLE`.
- **`Command` / `CommandWithParams`** return `Result`, and **`CommandExecutorFactory` only saves on success** — a rejected command writes nothing to the event store.
- **`RescheduleConcertController`** sets a flash attribute; `reschedule-concert.html` renders the rejection above the form.
- **Populators** call `.orElseThrow()`, so bad seed data fails the boot instead of quietly under-selling.
- Full suite green: **267 tests, 1 skipped**.

## Four consequences worth carrying forward

1. **`stopTicketSales` returns `Result` too**, against this section's original plan. `ConcertStartedProcessor` passes `Concert::stopTicketSales` as a `Command<Concert>`, and a `void` method no longer fits that interface. Every aggregate command now has one shape; the ones that can't reject always return success.
2. **The `Runnable` / `Callable` overload trap.** In `ConcertStartedProcessor`, `schedule(() -> ...execute(concertId), delay, unit)` was a `Runnable`. Once `execute` returned a value, the expression lambda silently bound to the **`Callable`** overload, so no alarms were scheduled and 5 processor tests failed. Fixed with a block lambda + comment. **Lab 1B has participants write actuator code exactly like this** — worth calling out in the lab, or leaving as a deliberate trap to debrief.
3. **`PurchaseTicketsUseCase` still returns `Optional<TicketOrderId>`.** A rejected sale is now indistinguishable from "concert not found", and an oversell surfaces as `orElseThrow()` in `PurchaseTicketsController` → a 500 error, not a message. A TODO marks it. **Open item: change it to `Result<TicketOrderId, …>` (see D4).**
4. **The real `sellTicketsTo` now enforces availability**, which overlaps Lab 1D's sold-out work. The sold-out *event* (`TicketSalesStopped` at zero available) is still unwritten, so 1D still has content — but **re-scope 1D in the Lab 1 doc** so it doesn't ask participants to add a rule that now exists.

**Blast radius (what was actually touched)** (recommended — mixed styles across the reveal is exactly the noise that derails a room):
- `application/Commands.java` (the command executor), `application/PurchaseTicketsUseCase.java`
- `SmallSampleDataPopulator` (7 call sites), `LargeSampleDataPopulator`
- 7 test files, `ConcertTest` among them

**Timing:** Lab 1A has participants add `startTicketSales()` with a guard ("only if not already on sale") — a rejection. They will copy whatever style is in place, which is now `Result`. Scaffold Lab 1 on top of it.

---

# A. Code prep — the `orm` package (the "before")

### A1. Add `sellTicketsTo` to `orm.Concert` [DONE]
- `public void sellTicketsTo(CustomerId customerId, int quantity)` — mutates `availableTicketCount` directly; no event.
- Rule: if `quantity > availableTicketCount`, reject with `NOT_ENOUGH_TICKETS_AVAILABLE` (A0). One rule only — the per-customer limit belongs to Lab 2 and must not be pre-empted here.
- Needs a local `CustomerId` record in `orm`, since the package imports nothing from `…eventsourced`.
- The new state still round-trips through `Concert.Snapshot`, which already carries `availableTicketCount`. **That's the point of the beat:** the field is private, no query method exposes it, and the database gets it anyway.

### A2. Remove the `availableTicketCount()` query method from `orm.Concert` [DONE]
Right now it's public, which blunts A1. Once it's gone:
- Nothing in the domain or a screen reads it.
- `snapshot()` still hands it to the repository — the hole punch is visible with nothing else to blame it on.
- `ConcertRepositoryTest` asserts on it today, so the assertion moves to observable behavior instead (sell 4 of 100, then a 97-ticket purchase is rejected while a 96-ticket one succeeds).

Keep `artist()`, `showDateTime()`, `doorsTime()` and `ticketsOnSale()` — those are the legitimate query methods, and keeping them makes the distinction teachable rather than absolute.

### A3. Give `orm.Concert.rescheduleTo` the same guard as the real aggregate [DONE]
The real `Concert.rescheduleTo` only reschedules when the new date is later **and** no tickets have been sold. `orm.Concert` has no guard. Side by side, that difference reads as if event sourcing caused it. Add the same guard so **the only differences between versions 1 and 2 are persistence**. Both versions now split that guard into two rejections (A0), so neither fails silently.

### A4. Sell-path test for `orm` [DONE]
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
1. **Rejection style:** follows from **A0** — recommended `Result<List<ConcertEvent>, Rejection>`.
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

### D4. `PurchaseTicketsUseCase` → `Result` [BUILD]
Follow-up from A0, item 3. It needs a failure type that covers both "no such concert" and a `ConcertRejection`, and `PurchaseTicketsController` then shows the reason instead of throwing. Decide whether this is prep work or Lab 2 material.

---

# E. Verification checklist

- [x] **A0 applied** to `orm` and the real aggregate — no silent no-ops left; the decider follows when built.
- [ ] `PurchaseTicketsUseCase` returns a `Result` instead of a bare `Optional` (D4).
- [ ] Lab 1D re-scoped for the availability rule that now lives in the real aggregate (A0, item 4).
- [x] `orm.Concert.sellTicketsTo` exists, enforces the availability rule, and has **no** `availableTicketCount()` query method.
- [x] `orm.Concert.rescheduleTo` guards exactly like the real aggregate.
- [x] `orm` tests green, including the sell-path round-trip (5 tests).
- [ ] The decider package exists, imports nothing from `…eventsourced` or `…orm`, and its Given/When/Then test is green.
- [ ] ~~The real `eventsourced` code is still untouched~~ — no longer true, and deliberately so: A0 changed the aggregate, the command plumbing, the reschedule controller and the populators. The *shape* of the ES `Concert` (events + `apply`) is unchanged, which is what the reveal depends on.
- [ ] The three LO3 atoms are corrected in **both** `knowledge-map.md` and `knowledge-map.html`.
- [ ] `lab-0-delete-the-snapshot.md` names only members that exist **before** Lab 1A.
- [ ] `docs/README.md` points at every current doc, and the archived plan is clearly marked as history.
