# Checklist: move AvailableConcertsProjector and AllConcertsProjector to NewProjectionCoordinator

Replace in place, same as `ScheduledConcertsProjector`
(see `migrate-scheduled-concerts-projector-checklist.md`).

These two are the remaining startup hogs: each loads all 1,000,100 concert events today and throws away
the 1,000,000 `TicketsSold` it doesn't handle.

## Read this before starting: they are coupled

`ConcertProjectionChoice` takes a `ProjectionCoordinator<ConcertEvent, AvailableConcerts,
AvailableConcertsDelta>`, and **both** projectors are passed into it:

- production (`ProjectionConfiguration.projectionChoices`) passes an `AllConcertsProjector` coordinator
- `EventViewerControllerTest` passes an `AvailableConcertsProjector` coordinator

Both projectors also share the state and delta types (`AvailableConcerts` / `AvailableConcertsDelta`), so
changing that parameter type forces both migrations into the same commit.

- [ ] **Decide how to decouple them, or accept one big commit.** Options:
      1. Migrate both together in one commit (simplest to reason about, biggest diff)
      2. Narrow `ConcertProjectionChoice` to what it actually uses — it only calls
         `.projection().availableConcerts()`, so a `Supplier<AvailableConcerts>` parameter makes it
         independent of which coordinator supplies the state, and the two migrations become separate
         commits. My recommendation
      3. Temporarily overload the `ConcertProjectionChoice` constructor to accept either coordinator, then
         delete the old one after both moves

If you take option 2, do it first as its own no-behavior-change commit.

---

## Part A — AvailableConcertsProjector

Handles `ConcertScheduled`, `ConcertRescheduled`, `TicketSalesStopped`.

### A1. The projector

`src/main/java/dev/ted/jitterticket/eventsourced/application/AvailableConcertsProjector.java`

- [ ] Extend `NewDomainProjector<AvailableConcerts, AvailableConcertsDelta>` instead of implementing
      `DomainProjector<…>`
- [ ] Add a constructor taking `Checkpointed<AvailableConcerts>`: seed `availableConcertsMap` from
      `snapshot.state()` (the existing `loadMapFrom` does this), checkpoint from `snapshot.checkpoint()`
- [ ] Promote all four locals to fields: `availableConcertsMap` (full state), `insertedConcerts`,
      `updatedConcerts`, `removedConcertIds` (delta accumulators)
- [ ] Split the `switch` into `public void handle(ConcertScheduled)`,
      `public void handle(ConcertRescheduled)`, `public void handle(TicketSalesStopped)` — all **public**
      and **declared on this class**, because `EventHandler` uses `getDeclaredMethods()` + `unreflect`
- [ ] **Set the checkpoint in every handle method** (`checkpoint = Checkpoint.of(event.eventSequence())`)
- [ ] Keep `shouldMarkAsRemoved` and the `insertedConcerts.remove` / `updatedConcerts.remove` calls exactly
      as they are — see A4 for why
- [ ] Implement `projection()`: build a fresh `AvailableConcerts` from the map values, wrapped in
      `Checkpointed`
- [ ] Implement `flush()`: merge `insertedConcerts` + `updatedConcerts` into the upserted list, pair it
      with `removedConcertIds`, **clear all three**, return with the current checkpoint
- [ ] Implement `protected Checkpoint checkpoint()`
- [ ] Drop the `default -> {}` case

### A2. The persistence

`MemoryAvailableConcertsProjectionPersistence.java`

- [ ] Implement `loadProjector()` to return an `AvailableConcertsProjector` seeded from `loadSnapshot()`
- [ ] `saveDelta` and `loadSnapshot` need no changes
- [ ] Leave the class serving `AllConcertsProjector` too for now — the old coordinator only calls
      `loadSnapshot()` and `saveDelta()`, never `loadProjector()`, so nothing breaks in the interim

### A3. Wiring

- [ ] `ProjectionConfiguration.availableConcertsProjectionCoordinator` (lines 24–29): return type becomes
      `NewProjectionCoordinator<AvailableConcerts, AvailableConcertsDelta>`; constructor takes only the
      persistence and the store (3 type parameters drop to 2 — `EVENT` disappears)
- [ ] `ConcertsController` (field line 19, constructor line 22): change the type. The `@Autowired`
      by-generic-type injection still resolves, since this stays the only bean of that type. The
      `projection().availableConcerts()` call is unchanged

### A4. Tests

`AvailableConcertsProjectorTest` — 6 tests, all calling `project(state, stream)`. They become
construct-from-snapshot, `handle(Stream.of(…))`, assert `projection().state()` and `flush().state()`.

- [ ] **`projectIgnoresConcertScheduledAndTicketSalesStoppedInOneStream` (line 113) uses
      `MakeEvents.withNullEventSequences()`.** That will NPE once every handle method calls
      `Checkpoint.of(event.eventSequence())` — unboxing null to `long`. Switch it to `MakeEvents.with()`,
      which supplies 1, 2, 3, …
- [ ] Two tests pin the batch-scoped delta semantics and must keep passing:
      `projectIgnoresConcertScheduledAndTicketSalesStoppedInOneStream` (scheduled then stopped in one
      batch is a no-op: absent from upserted *and* removed) and
      `projectShouldStillRemoveConcertIdAfterConcertRescheduledAndTicketSalesStopped` (rescheduled then
      stopped still reports a removal). The batch boundary is now "everything the coordinator passes to
      `handle(Stream)` before calling `flush()`", which is the same boundary as one `project()` call today
- [ ] Remember `flush()` clears the delta — a second call returns empty
- [ ] `ConcertsControllerTest` lines 31–36
- [ ] `EventViewerControllerTest` lines 71–74 (`createConcertProjectionCoordinator`) — only if you did
      *not* take option 2 above

---

## Part B — AllConcertsProjector

Handles `ConcertScheduled` only. Simpler projector, but it needs its own persistence class.

### B1. The projector

`src/main/java/dev/ted/jitterticket/eventsourced/application/AllConcertsProjector.java`

- [ ] Extend `NewDomainProjector<AvailableConcerts, AvailableConcertsDelta>`
- [ ] Constructor taking `Checkpointed<AvailableConcerts>`, seeding the map and checkpoint
- [ ] Promote `availableConcertsMap` and `insertedConcerts` to fields
- [ ] Replace the `switch` with a single `public void handle(ConcertScheduled)`, setting the checkpoint
- [ ] `projection()`, `flush()` (upserts only, removed list always empty), `checkpoint()`
- [ ] Drop the `default -> {}` case

### B2. New persistence class

- [ ] Add `MemoryAllConcertsProjectionPersistence` — a copy of the Available one whose `loadProjector()`
      returns an `AllConcertsProjector`. Both implement
      `ProjectionPersistencePort<AvailableConcerts, AvailableConcertsDelta>`
- [ ] *Why the copy:* `loadProjector()` binds a persistence implementation to exactly one projector, while
      `loadSnapshot()` doesn't. The alternative is passing a
      `Function<Checkpointed<AvailableConcerts>, NewDomainProjector<…>>` factory into the existing class's
      constructor — less duplication, more indirection. Pick whichever reads better for the workshop
- [ ] Both are constructed with `new`, not injected, so no Spring ambiguity from the identical generics

### B3. Wiring

- [ ] `ProjectionConfiguration.projectionChoices` (lines 64–68): the `allConcertsProjection` local becomes
      a `NewProjectionCoordinator<>` over the new persistence
- [ ] `ConcertProjectionChoice` (field line 18, constructor line 22): change the type — unless option 2
      already replaced it with a `Supplier<AvailableConcerts>`

### B4. Tests

- [ ] There is **no `AllConcertsProjectorTest`** today. Worth adding one while you're in here — it's the
      projector with the least behavior, so it's a small test and it documents the "keeps concerts even
      after ticket sales stop" difference from `AvailableConcertsProjector`
- [ ] `EventViewerControllerTest` lines 35, 67, 71–74

---

## Verify (after both parts)

- [ ] Startup log shows `Fetched 100 events` for all three concert projections instead of `1000100`
- [ ] Startup against the large dataset should drop to roughly a second — it was 15.6s with
      `event.store.type=jdbc`, 8.2s with `jdbc_client`, of which ~7s is these two projections plus
      `ScheduledConcerts`
- [ ] `/concerts` page still lists concerts, sorted by show time (`ConcertsController`)
- [ ] Event viewer's "concerts" choice still lists aggregates (`ConcertProjectionChoice`)
- [ ] A concert with `TicketSalesStopped` disappears from `/concerts` but still appears in the event
      viewer's list — that's the behavioral difference between the two projectors, and the thing most
      likely to be broken by a mistake in `flush()`

## Note on checkpoints

Same caveat as the other checklist: these projectors will record the sequence of the last *handled*
event, not the global position. Harmless for correctness and for in-memory persistence — see the note at
the end of `migrate-scheduled-concerts-projector-checklist.md` for why it still matters if these ever
become database-backed.
