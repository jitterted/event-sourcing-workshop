# Checklist: move ScheduledConcertsProjector to NewProjectionCoordinator

Replace in place — no `New…` copy alongside. (The `NewRegisteredCustomersProjector` precedent left the
old `RegisteredCustomersProjector` pair as dead code still referenced only by tests; don't grow a
second orphan.)

**Why:** `DurableReadModel` filters the catch-up query by `domainProjector.handledEventTypes()`,
which `EventHandler` derives from the `handle` methods. This projection then loads ~100 events at
startup instead of 1,000,100.

## 1. The projector

`src/main/java/dev/ted/jitterticket/eventsourced/application/ScheduledConcertsProjector.java`

- [ ] Extend `NewDomainProjector<ScheduledConcerts, ScheduledConcertsDelta>` instead of implementing
      `DomainProjector<…>`
- [ ] Add a constructor taking `Checkpointed<ScheduledConcerts>`: seed the map from `snapshot.state()`,
      the checkpoint from `snapshot.checkpoint()`
- [ ] Promote `ProjectorMaps`'s two maps to fields: `scheduledConcertsMap` (full state) and
      `upsertedConcertsMap` (delta). The nested class can stay or be inlined
- [ ] Replace the `switch` with `public void handle(ConcertScheduled)` and
      `public void handle(ConcertRescheduled)`. Both must be **public** and **declared on this class** —
      `EventHandler` uses `getDeclaredMethods()` plus `unreflect`
- [ ] **Set the checkpoint in every handle method** (`checkpoint = Checkpoint.of(event.eventSequence())`).
      Miss this and the delta never saves, so the projection replays from scratch on every startup
- [ ] Implement `projection()`: build a fresh `ScheduledConcerts` from the map values, wrapped in
      `Checkpointed`
- [ ] Implement `flush()`: build the delta from `upsertedConcertsMap` plus the always-empty removed list,
      clear the map, return it with the current checkpoint
- [ ] Implement `protected Checkpoint checkpoint()`
- [ ] Drop the `default -> {}` case. Unhandled types now throw `UnwantedEventException`, which the
      coordinator's filtering prevents

## 2. The persistence

`MemoryScheduledConcertsProjectionPersistence.java`

- [ ] Implement `loadProjector()` (currently throws `UnsupportedOperationException`): return the projector
      seeded from `loadSnapshot()`
- [ ] `saveDelta` and `loadSnapshot` need no changes

## 3. Wiring (these must change together)

- [ ] `ProjectionConfiguration.scheduledConcertsProjectionCoordinator` (~line 41): return type becomes
      `NewProjectionCoordinator<ScheduledConcerts, ScheduledConcertsDelta>`, constructor takes only the
      persistence and the store. The type parameter count drops from 3 to 2 — `EVENT` disappears
- [ ] `Commands` (field line 14, constructor line 17): change the type. The
      `projection().conflictsWith(…)` call at line 58 is unchanged — both coordinators return `STATE`
      from `projection()`
- [ ] `CommandAndQueryConfiguration.commands` (line 38): change the parameter type
- [ ] `ScheduleConcertController.createForTest` (~line 28): update the fixture wiring

## 4. Tests

- [ ] `ScheduledConcertsProjectorTest`: all 3 tests call `project(state, stream)`. They become
      construct-from-snapshot, `handle(Stream.of(…))`, then assert on `projection().state()` and
      `flush().state()`. The third test seeds a non-empty `ScheduledConcerts`, which now goes in through
      the `Checkpointed` snapshot — give it a non-`INITIAL` checkpoint to be realistic
- [ ] Remember `flush()` clears the delta, so a test calling it twice gets an empty second result
- [ ] `RescheduleConcertControllerTest` lines 58–61
- [ ] `ConcertTest` lines 187–192

## 5. Verify

- [ ] Start the app against the large dataset and confirm the log reads `Fetched 100 events` for this
      projection instead of `1000100`
- [ ] Check scheduling-conflict detection end to end — `Commands` is the only real consumer of this
      projection

## Related work from the same session

- `BatchedLargeSampleDataPopulator` — creates events directly and writes them with batched JDBC inserts
  (~25s for 1,000,100 events)
- `JdbcClientEventStore` (`event.store.type=jdbc_client`) — hand-written `RowMapper` straight to
  `EventDto`; startup 15.6s → 8.2s versus Spring Data JDBC's `EventDboRepository`
- Still loading 1M events each: `AvailableConcertsProjector` and `AllConcertsProjector`.
  `AllConcertsProjector` also needs its own persistence class, since
  `MemoryAvailableConcertsReadModelPersistence` serves two projectors and `loadProjector()` can only
  return one

## Note: a checkpoint should record the global sequence, not the last handled event

The old coordinator advanced its checkpoint to the last event it saw **of any type**. The new one
advances only to the last **handled** type, so a projection's checkpoint sits far behind the global
sequence — for `ScheduledConcerts` against the large dataset, ~100 versus ~1,000,100.

This is not a correctness problem. The catch-up query is
`event_sequence > checkpoint AND event_type IN (handled types)`, so every handled event above the
checkpoint is unseen and every one below it has been applied. Nothing is skipped or double-applied. The
query cost is also bounded by handled-type rows above the checkpoint, not by the global gap — measured
at 2.9ms (checkpoint 500) versus 1.4ms (checkpoint at the global head), the difference being that a
lagging checkpoint picks `idx_events_event_type` over `idx_events_event_type_and_sequence`.

The reason to record the global sequence anyway is what it lets you build later. Once the stored number
means "up to here, for my types only", these all break:

- **Lag monitoring.** `max(event_sequence) - checkpoint` shows a fully caught-up projection as ~1M
  behind, so any staleness alert built on it fires constantly.
- **Read-your-writes.** "Write an event, then wait until the projection's checkpoint reaches that
  event's sequence" never completes when the written event isn't one of the projection's handled types.
- **Comparing progress across projections.** Each checkpoint is measured against a different subset of
  the log, so the numbers aren't comparable.

None of these exist in the code today; they're the things that quietly become impossible if the
handled-type sequence is what gets stored. Keeping the global meaning needs the store to report how far
the filtered query actually scanned — e.g. capture `max(event_sequence)` in the same transaction as the
catch-up query, and have the subscription carry the global position alongside the filtered events —
rather than deriving the checkpoint from the events the projector happened to handle.

(Unrelated: if a projector's handled-event set ever changes, that projection resets to checkpoint 0 and
replays regardless of which scheme is used.)
