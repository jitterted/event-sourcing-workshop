# Event-Sourcing Pattern Map — Working Notes

Companion to `pattern-map-guidelines.md`. Captures decisions made during the node-by-node
review, **prose details deliberately kept out of the diagram** (to write up later), and
**deferred edges/relationships**. Canvas: tldraw doc *Event Sourcing Design Patterns*, page
*Overview Map*.

## Species model (THREE species — a refinement of the guidelines)
The guidelines (§2) name **two** species (structural patterns vs. "capabilities & principles"
lumped together). The map now uses **three**, splitting the non-pattern species:

| Species | Test | Color | Examples |
|---|---|---|---|
| **Pattern** | Can you *build/implement* it? | 🟡 yellow | Event, Decider, Projector, Actuator |
| **Capability** | Is it an *outcome you gain*? | 🔵 light-blue | History as State, Time Travel, Undo via Command Reversal |
| **Principle** | Is it a *stance/axiom you commit to*? | 🟢 light-green | Events as Source of Truth |

- The Principle/Capability split matters: `Events as Source of Truth` is not a *thing you can now do*,
  it's the *axiom* ("events are authoritative; all else is derived & disposable") the capabilities rest on.
- A capability is more than an *unexercised* feature when it's a **design precondition** (see Reversible
  Command): you must build the command's inverse in for the capability to exist at all.

## Vocabulary (house style)
- Use **"replay"** (temporal / rebuild sense) and **"accrue"** (accumulation-over-time sense).
- **Retire "fold"** (FP jargon). Plain gloss: *"replay the events, applying each in order, to rebuild state."*
- Avoid **"aggregate"** as a synonym for fold (it's the DDD building block). Avoid **"reduce"** (same register as fold).
- Avoid **"real-time"** for the Decision Query — it connotes speed/streaming; the property meant is a *consistency/freshness guarantee*.

## Visual conventions (Legend)
- **Color = species:** 🟡 yellow = Pattern · 🔵 light-blue = Capability · 🟢 light-green = Principle.
- **Bold outline ring = Primary pattern** (the root patterns everything orbits). Ring is grouped with its note.
- **_Italic_ label = abstract pattern** (interface / abstract base class).
- Three orthogonal channels: color (species), ring (primary), italic (abstract).

## Write-side symmetry (framing for the paper)
- **Event mirrors Command:** Event = what *happened*; Command = what someone *wants to happen*.
- **Event Log parallels Command Log:** Event (as a Recorded Event) "stored in" Event Log; Command
  (+ decision) "stored in" Command Log. The two logs **coexist** (they are not variants of each other).
- The write path (currently drawn as runtime): `Command → Decider → Event → Log`, with the Decider reading
  a `Strongly-Consistent Decision Query` to decide.

## Deferred edges / relationships (per user: "leave out different edge types for now")
- **Runtime edges kept for now** (mixing runtime into the map is OK for now): `Command→Decider`
  ("expresses intent…"), `Decider→Event` ("generates"), `Decider→Command Log` ("records command and
  decision in a"), `Strongly-Consistent Decision Query→Decider` ("derives up-to-date slice…").
  Possible future: separate onto a runtime diagram.
- **Combination (deferred):** `Decider + Strongly-Consistent Decision Query` (a decision needs a
  decision-shaped view); `Events as Source of Truth + Eventually-Consistent Read Model`;
  `Actuator + Read Model` (the "to-do list" the Actuator works off).
- **Refinement (deferred):** `Projection → Strongly-Consistent Decision Query` (Decision Query is a
  Projection used on the write side); likewise Read Model is a Projection.
- **Variant — REJECTED:** `Command Log ↔ Event Log` is **not** a variant in this model; the two logs
  **coexist** (command+decision log vs. event log), complementary, not either/or.

## Per-node prose notes & decisions

### Event — Pattern, **Primary**
- The atom every benefit traces back to (Source of Truth, History as State, Time Travel).
- Sits on a boundary: a buildable record, *and* the thing the whole benefit-cluster hangs off of.
- The **bare domain payload** ("what happened"); the log-situated form is the Recorded Event (below).
  `Event → Recorded Event` edge: **"carried inside."**

### Recorded Event — Pattern (added; the domain Event *situated in the log*)
- Name chosen over **"Event Envelope"** (established but generic) and **"Log Entry"/"Sequenced Event"**:
  *recorded* names the insight — the act of **recording** is what confers position, time, and identity.
  Reference impl: JitterTravel's `StoredEvent` (sequence, type, eventId, timestamp, payload, commandId).
- **Composition, not refinement:** an envelope isn't a *kind of* Event; it *wraps* the Event payload plus
  the metadata the log confers. Two metadata origins are the key insight:
  - assigned by the **Log on append** — `sequence` (→ the "Ordered" property; also optimistic-concurrency
    append), `timestamp` (→ point-in-time selection);
  - assigned by the **writer/Decider** — `tags` (→ the selection/consistency boundary, DCB-style),
    `commandId` (→ causation, links back to the originating command), `eventId` (identity / idempotency).
- **Reroutes the old `Event → Log "stored in"` edge:** now `Event → Recorded Event` ("carried inside")
  and `Recorded Event → Log` (**"is an ordered element of"** — a composition/part-of, not "stored in":
  the log is literally a *sequence of Recorded Events*, and the wording carries the ordering, which is
  where the sequence number lives). Discussed folding Recorded Event into the Log; kept separate because
  it has relationships and variation the container lacks (tags → Decision Query, timestamp → Time Travel,
  commandId → Command Log; the DCB tag schema evolves independently of the log's storage mechanics).
- Edges out:
  - `Recorded Event → Strongly-Consistent Decision Query` **"allows precise selection for"** — the tags
    are the selection key / consistency boundary (DCB). Complements `Log → Decision Query` "available for
    querying for" (the Log is the substrate; the tags are the filter). *Possible consolidation later.*
  - `Recorded Event → Time Travel` **"provides position information for"** — the timestamp/sequence is
    the *coordinate*; History as State supplies the *state* ("as of a point in time"). The two labels are
    now distinct (coordinate vs. state), resolving the earlier near-synonym concern.
  - `Recorded Event → Command Log` **"associates with command in"** — the `commandId` causation trace.

### Append-Only, Ordered Event Log — Pattern
- Renamed from "Immutable Ordered Event Log." **Append-only** chosen over **immutable**:
  - append-only entails immutability of existing entries *and* a tail-ordering;
  - **"immutable" overclaims** — the past *does* get touched (event upcasting/versioning, tombstones,
    archiving / crypto-shredding for GDPR). Those exceptions are themselves *appends* (a tombstone is a
    new record, an upcast writes a new versioned event), so **append-only is the honest invariant** that
    survives them; immutability rides implicitly.
- **"Ordered" kept deliberately** (redundant with append-only but load-bearing: Time Travel, replay,
  Decision Query all depend on sequence). It's a design decision, not noise.
- **Replayability is a benefit in its own right:** because the log can be replayed, a Projector can
  **catch up during recovery or recalculation** (rebuild a read model from scratch). Current edge:
  `Log → Projector` "enables catch-up during recovery or recalculation."

### Events as Source of Truth — **Principle** (axiom, not a capability)
- The stance: events are authoritative; everything else (current state, read models) is derived & disposable.
- This principle is *why* read models are disposable/rebuildable caches (tie-in for the Read Model prose).

### History as State — Capability
- Replay/accrue the events to rebuild state. Sole enabler is the Log (a single Event isn't history).

### Time Travel — Capability, **refinement of History as State**
- Edge label "as of a point in time." NOTE FOR PAPER: Refinement can chain **capability→capability**,
  not only pattern→pattern (a slight extension of guidelines §3).

### Command — Pattern, **Primary**
- Mirror of Event (see Write-side symmetry). Anchors no benefit of its own; its payoff arrives downstream
  via Reversible Command + Command Log → Undo.

### Reversible Command — Pattern (refinement of Command, "with a compensating action")
- Reversibility is a **design precondition**, not an unexercised capability: the command must define an
  inverse/**compensating action**. Not all commands qualify (`SendEmail` has no un-send).
- **Undo via Command Reversal** needs BOTH: a Reversible Command *and* the Command Log (both "enable" it).
  (Being in the log is not enough — the command itself must support reversal.)

### Decider — Pattern, **Primary**
- Write-side decision function/aggregate: Command + decision state → Event(s) or rejection.
- Currently only runtime edges touch it (kept for now).
- Decides *whether*; contrast the Actuator, which just *acts* (see Actuator).

### Command Log — Pattern
- Append-only store of commands + their decision outcomes. Makes `Undo via Command Reversal` actionable.
- Coexists with the Event Log (see rejected Variant above).

### Strongly-Consistent Decision Query — Pattern
- A Projection aimed at the **write** side: assembles exactly the state the Decider needs.
- **Must be strongly consistent** — a decision guarding an invariant cannot run on stale data.
- Name deliberately pairs against "Eventually-Consistent Read Model" (the sharpest write-vs-read lesson).

### Undo via Command Reversal — Capability
- Roll the system back by replaying inverse/compensating commands.

### Event Consumer — Pattern, **abstract** (italic)
- Subscribes to the Event Log and reacts. Abstract parent (interface / ABC in current implementations).
- **Taxonomy by output** — the three concrete consumers each emit a different thing:
  - **Projector → a Read Model** (queryable state)
  - **Actuator → Commands** (write-side triggers)
  - **Translator → Events/messages** (across a boundary)
  (Structurally: Event Consumer → {Projector, Processor}; Processor → {Actuator, Translator}.)
- Current edge `Event Consumer → Processor`: "triggers activity via." `Event Consumer → Projector`:
  "accrues subset of state via."

### Projector — Pattern (concrete, refinement of Event Consumer)
- Replays events to build/update a read model; **produces** (enables) the Read Model.

### Processor — Pattern, **abstract** (italic)
- Event Consumer that reacts by triggering actions/side-effects (vs. Projector, which builds read state).
- Splits by **output**, NOT by state (see below): its children are Actuator and Translator.

### Actuator — Pattern (concrete, refinement of Processor)
- Renamed from "Automate Command Initiation." Alias: **Automation** (Event Modeling term, paired with a
  **"To-Do List"** read model). Edge label: "turns events into commands."
- **Turns events → commands.** Sibling of Translator (events → events/messages). Its **output edge is now
  explicit:** `Actuator → Command` **"emits"** — which closes a **write-side feedback loop** (events drive
  an Actuator that emits new Commands back into the Decider).
- State is **incidental, not defining** (this is why "Stateful Processor" was rejected). Spans:
  - pure stateless reaction — `TicketsSold → AssignTicketToCustomer`, *no decision, just do it*;
  - works off an **external** read-model "to-do list";
  - works off an **internal** one — then it fuses *Event Consumer → Read Model* + command-triggering in one class;
  - holds other internal state (alarms/timers for future action = a time-shaped to-do list).
- **Best paired with a Read Model** (the to-do list) — a real `Actuator ↔ Read Model` Combination (deferred).
- Contrast with **Decider**: the Actuator does **not decide**, it **triggers**; if a genuine decision is
  needed, that's the Decider's job on the command the Actuator fires.
- Name rationale: control-systems metaphor (sense → decide → act); pairs with Decider (decides ↔ acts).
- Names rejected: "Command Initiator" (not active enough), "Process Manager" (overloaded/generic),
  "Policy" (too passive), "Stateful Processor" (state is incidental).

### Translator — Pattern (concrete, refinement of Processor)
- Renamed from "Translate for Integration." Edge label: "turns events into other events."
- **Turns events → other events/messages** across a bounded-context / integration boundary. Does **not**
  issue commands. (Optionally could be named "Event Translator.")
- Its **output edge is now explicit:** `Translator → Published Event` **"translates to."**

### Published Event — Pattern (added; the Translator's output)
- The event/message a Translator emits **across a boundary** (bounded context / integration). Distinct from
  the internal domain `Event`: it's the *outward-facing* published form, shaped for consumers outside this
  context. Sibling-in-spirit to Recorded Event (both are boundary forms of the core Event fact — one for the
  log, one for the outside world), though not drawn as related.

### Eventually-Consistent Read Model — Pattern
- Renamed from "Read model as a Point of View Cache." Stripped two ideas OUT of the node name (keep in prose):
  1. **"Point of view"** — each read model is one *tailored view* built for a specific query/purpose.
  2. **"Disposable / rebuildable cache"** — safe to drop and rebuild from events *because events are the
     source of truth*. DECIDED: **prose only** (no node) — keeps the map lean. In the write-up, tie it back
     to `Events as Source of Truth` (that principle is *why* read models are cheap to drop and rebuild).
- **Eventually consistent** — deliberately allowed to lag; the read-side counterpart to the
  Strongly-Consistent Decision Query. (User: not fond of "eventual consistency" but it's the common term.)

## Review status
- All 16 original nodes reviewed (Nodes 1–16). New nodes added: Reversible Command, Recorded Event,
  Published Event. New output edges: `Actuator → Command` "emits", `Translator → Published Event`
  "translates to".
- Still open for a later pass: the deferred edges above (runtime separation, Combinations, Refinements),
  and writing the full prose descriptions from these notes.
