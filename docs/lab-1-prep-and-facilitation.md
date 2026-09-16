# Lab 1 — Codebase Prep + Facilitation Plan

*Event Sourcing Workshop · JitterTicket · combined plan for the prep you do after Lab 0, and how to run Lab 1.*

**Decisions baked into this plan** (from planning): delivery is **online, 4 days**; you **scaffold the UI, participants code the domain**; **all of 1A–1D** are in scope; **no branches** (repo isn't initialized — you manage versions however you like).

Grounded in the actual source under `dev.ted.jitterticket`. Tags below:
**[BUILD]** = you pre-build · **[STUB]** = compile-safe placeholder you add · **[CONFIRM]** = verify an existing seam · **[PARTICIPANTS]** = leave unbuilt (the exercise).

---

# Part A — Codebase prep to do once Lab 0 is complete

Goal: get the repo to a **Lab 1 starting state** where the app compiles and runs, the screens render, and every domain hook participants need is either present or a clearly-marked TODO — without giving away the exercise.

## A1. Baseline sanity [CONFIRM]
- App boots; the **I/O-Free Tests** run config is green.
- Lab 0's `dev.ted.jitterticket.lab0` package is present and its round-trip test passes (from the Lab 0 prep doc). It's isolated, so it won't interfere.

## A2. Seed data [BUILD]
- **Activate a populator.** `SmallSampleDataPopulator` is currently `//@Component` (inactive). Uncomment `@Component` on it (or `LargeSampleDataPopulator`) so screens have content. It `TRUNCATE`s `events, concert_sales, concert_sales_projection` on boot — fine for a workshop.
- **Tune the spread** for the demos:
  - Keep several future concerts (already present: Blue Note Quartet, Stella Nova, etc.).
  - The **"Midnight Rebels"** concert is scheduled `now()+10s` and auto-stops via `ConcertStartedProcessor` — good for showing `TicketSalesStopped` live, but remove it if the flicker is distracting.
  - Add **one near-capacity concert** (schedule with a small `capacity`, then several `sellTicketsTo(...)` calls summing near capacity) so **1D's sold-out** state is visible on day one.
- Note: pre-Lab-1, every `Concert.schedule(...)` is **on sale by default** (`ticketsOnSale = true`). The *variety* of on-sale vs not-on-sale states only appears once participants add start/stop in 1A/1C — so don't over-engineer seed states now.

## A3. Scaffold the management UI [BUILD] + [STUB]
Mirror `ScheduleConcertController` + `schedule-concert.html`.

- **[BUILD] `ConcertManagementController`** (in `adapter/in/web`):
  - `GET /manage/concerts` → renders a table of concerts with a **Start Sales** button (and, for 1C, a **Stop Sales** button), switched by an `onSale` flag: `th:if="${concert.onSale}"` shows Stop, else Start.
  - `POST /manage/concerts/{id}/start-sales` and `.../stop-sales` → execute the command → `redirect:/manage/concerts`.
  - Follow the `ScheduleConcertController` shape (constructor-injected command bean, a `createForTest()` fixture record).
- **[BUILD] `manage-concerts.html`** — a simple table like `concerts.html` (not the heavy inline-CSS form). Rows via `th:each`, a tiny `<form method="post">` per action button.
- **[BUILD] Data-source seam.** Inject a narrow query the controller reads (e.g., `ConcertsForManagementQuery`). Back it **now** with the existing **`AllConcertsProjector`** (all scheduled concerts) so the screen renders immediately. Leave a `// TODO 1A/1C: swap to the eligible / on-sale read models` marker. This is the clean hexagonal seam participants slot their read models into.
- **[STUB] Command wiring** (in `Commands` + `CommandAndQueryConfiguration`):
  - **Stop** can be real today — `Concert::stopTicketSales` already exists:
    `commandExecutorFactory.wrap(Concert::stopTicketSales)`.
  - **Start** must be a stub until participants implement the aggregate method, so the app compiles and the button is visibly "not yet wired":
    ```java
    commandExecutorFactory.wrap(concert -> {
        throw new UnsupportedOperationException("TODO 1A: implement Concert.startTicketSales()");
    });
    ```

## A4. Buyer-view plumbing for 1D [BUILD]
- Add a `boolean soldOut` (and, if handy, `onSale`) field to **`ConcertView`** (record + `create`/`from`), defaulting `soldOut=false` in the mapper for now.
- In **`concerts.html`**, wrap the "Buy Tickets" link in `th:if="${!concert.soldOut}"` and add a `th:if="${concert.soldOut}"` **"SOLD OUT"** badge.
- Leave the **derivation** (folding `TicketsSold` vs `capacity`) to participants in 1D — you're only laying the view rails.

## A5. Seams to confirm, not build [CONFIRM]
- **New read model pattern:** `ProjectionConfiguration` bean + `AvailableConcertsProjector` + `MemoryAvailableConcertsProjectionPersistence` is the copy-paste template.
- **Time:** `Clock` is injectable; `ClockFactory.fixedClockAt(y,m,d)` exists for tests (1B).
- **Actuator:** `ConcertStartedProcessor` arms a `ScheduledFuture` at a time → fires a command; registered in `ProcessorConfiguration`. Template for 1B.
- **GWT harness:** `Concert.reconstitute(List<events>)` / `concert.uncommittedEvents()` / `MakeEvents` / `ConcertFactory` — ready.

## A6. Leave unbuilt — the exercise [PARTICIPANTS]
Do **not** pre-build these; they're what participants write:
- `TicketsOnSale` event (add to the `ConcertEvent permits` list), `Concert.startTicketSales()`, flip `ticketsOnSale` default to `false`, add the `apply` case.
- The **eligible-to-start-sales** read model (1A) and the **on-sale** read model (1C).
- The **scheduled-on-sale actuator** + wherever the on-sale timestamp lives (1B).
- The **sold-out derivation** in the buyer read model (1D), and the optional `sellTicketsTo` → auto-`TicketSalesStopped` on sell-out.

## A7. Pre-flight check
App boots · `/concerts` renders · `/manage/concerts` renders with Start buttons (clicking Start throws the TODO — expected) · I/O-Free Tests green.

## Decisions to lock before delivery (so you're unblocked offline)
1. **Where the on-sale timestamp lives (1B)** — a field on `ScheduleParams`/`ConcertScheduled`, or a separate `ScheduleTicketSales` command + event? (Recommend a separate command/event so scheduling and on-sale stay independent — reinforces the LO.)
2. **1D sell-out behavior** — display-only `soldOut` flag, or also emit `TicketSalesStopped` when `availableTicketCount` hits 0? (Recommend flag-only first; auto-stop as a stretch.)
3. **Management route name** — `/manage/concerts` vs something in your house style.

---

# Part B — Facilitating Lab 1 (online, 4 days)

## B0. Placement & tools
- **Suggested arc** (adjustable): **Session 1** = Lab 0 reveal + **1A**; **Session 2** = **1B**; **Session 3** = **1C**; **Session 4** = **1D** (+ slack for Lab 2 setup). Lab 1 is the backbone of the on-sale story; Labs 2–3 are separate.
- **Tools:** a shared **Miro** board for event modeling; **screen-share** for the scaffold demo; **breakout rooms** for pairs/triads; a chat convention for "**done = GWT green**."

## B1. The repeating sub-part cycle (~60–75 min)
1. **Frame** the business change (5m).
2. **Event-model together** on Miro: Screen → Command → Event → Read Model, left-to-right (10–15m).
3. **Agree the GWT rules** as a group (5–10m) — this is the definition of done.
4. **Show the scaffold** and the exact seam/TODO where their code plugs in (5m).
5. **Breakouts** implement the domain until GWT is green (25–40m); you hop rooms.
6. **Reconvene**: one pair demos; debrief; tie back to the **knowledge-map L1 atoms** (10m).

## B2. Sub-part specifics

### 1A — Manual "Start Ticket Sales"
- **Participant tasks:** add `TicketsOnSale`; `Concert.startTicketSales()` (guard: only if not already on sale); flip `ticketsOnSale` default → `false`; `apply` case; the **eligible** read model (scheduled AND not yet on sale); shift the buyer `AvailableConcerts` to insert on **`TicketsOnSale`** (not `ConcertScheduled`); replace the Start command **[STUB]** with `concert.startTicketSales()`; point the management query at the eligible read model.
- **GWT to land:**
  - Given `[ConcertScheduled]` · When `startTicketSales` · Then `[TicketsOnSale]`
  - Given `[ConcertScheduled, TicketsOnSale]` · When `startTicketSales` · Then `[]` (already on sale)
- **Debrief atom:** the buyer list now means *on sale*, not *scheduled*; "future/eligible is derived state," `now()` is a read-time filter.
- **Pitfalls:** forgetting to flip the default; buyer read model still keyed on `ConcertScheduled`; surprise that a freshly scheduled concert no longer appears for purchase (that's the point).

### 1B — Scheduled start (the actuator)
- **Participant tasks:** decide where the on-sale datetime lives (see Decision #1); a `ScheduledOnSaleProcessor` copying `ConcertStartedProcessor` that arms an alarm at the on-sale time → fires `startTicketSales`; inject `Clock`; register in `ProcessorConfiguration`.
- **GWT to land:** with `ClockFactory.fixedClockAt(...)`, advancing to the on-sale time fires `TicketsOnSale`; before it, nothing.
- **Debrief atom:** "an Actuator/Processor turns the passage of time into a Command."
- **Pitfalls:** on-sale time not captured in an **event** (can't re-arm the alarm on restart/replay); real clock leaking into tests; alarm not re-armed on reschedule.

### 1C — Stop sales
- **Note:** the domain already exists (`stopTicketSales` / `TicketSalesStopped`). So this sub-part is mostly **read model + wiring**.
- **Participant tasks:** an **on-sale** read model (for the management Stop list); wire the real Stop command + button (already possible); confirm the buyer list removes on `TicketSalesStopped` (it does).
- **GWT to land:**
  - Given `[ConcertScheduled, TicketsOnSale]` · When `stopTicketSales` · Then `[TicketSalesStopped]`
  - Given `[…, TicketSalesStopped]` · When `stopTicketSales` · Then `[]` (already stopped)
- **Debrief atom:** a rejected command emits **no event** — and plant the **idempotency seed** (what if the Stop request is retried?), the hook into Lab 2/3.
- **Pitfalls:** management screen still on the placeholder query; Start and Stop lists derive from the same events but **opposite predicates**.

### 1D — Sold-out display
- **Participant tasks:** fold `TicketsSold` vs `capacity` in the buyer read model; expose `soldOut`; **keep** sold-out concerts in the list (don't remove them); the template already hides Buy + shows the badge **[BUILD]**. Optional stretch: `sellTicketsTo` emits `TicketSalesStopped` at zero available.
- **GWT to land:** read-model test — scheduled(capacity=N) + `TicketsSold(N)` ⇒ concert present with `soldOut=true`; Buy hidden.
- **Debrief atom:** projection-time vs read-time filtering; **sold-out is derived state** (from events), not a stored flag.
- **Pitfalls:** removing sold-out from the list instead of flagging it; trying to derive `soldOut` from `now()` (it's from events); double-counting `TicketsSold`.

## B3. Online logistics
- Breakouts of **2–3**, rotate a driver; keep the scaffold running on **your** machine to re-demo each screen.
- **Between sessions (async):** post the agreed GWT tests as the target; participants push/share; you review async and open the next session with a 5-min recap.
- **Parking lot** for DCB / consistency-boundary tangents → explicitly defer to Lab 2.
- **Stuck-room fallback** (no solution branch): keep a private reference of each aggregate method handy and share just the ~10 lines, never the read model.

## B4. Facilitator pre-flight (every session)
App boots · `/concerts` and `/manage/concerts` render · seed data present · Miro board pre-templated with empty slice lanes · I/O-Free Tests green · knowledge map open for the debrief callbacks.

## B5. Definition of Done (per sub-part)
- [ ] Group event-model exists on Miro.
- [ ] Agreed GWT tests are written **and green**.
- [ ] The scaffolded screen exercises the new behavior end-to-end.
- [ ] Debrief connected the work to its L1 knowledge-map atom.
