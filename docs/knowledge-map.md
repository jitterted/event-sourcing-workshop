# Event Sourcing Workshop — Backwards Knowledge Map

*dev2next 2026 · JitterTicket Concert Ticketing*

Built **backwards**: from the four official Learning Objectives down to the **atomic pieces of knowledge** each requires. Every atom is tagged with **where** it is taught, so you can spot knowledge no lab exercises (a gap) or knowledge crammed into one lab (a bottleneck).

## How to read it

Three tiers: **Objective** → **Enabling idea** → **Atomic knowledge** (tagged by the lab that teaches it).

| Tag | Where it's taught / practiced |
|-----|-------------------------------|
| **F** | Foundation — baseline codebase / taught up front |
| **EM** | Event Modeling / group discussion (precedes each lab) |
| **L00** | Lab 0 — Delete the Snapshot (LO3 demonstrator, opener) |
| **L1** | Lab 1 — Separate On-Sale from Scheduling (1A–1D) |
| **L2** | Lab 2 — Limit Purchase Quantity per Customer + Concert |
| **L3** | Lab 3 — Refund Tickets |
| **T** | Testing thread — GWT, runs across every lab |

## Overview — shared foundations

The objectives lean on a common set of foundations and on one central idea, the **command decision loop (DL)**:

- **F1** — Event = immutable past-tense fact
- **F2** — Command = intent that may be rejected
- **F3** — Read Model = state derived by folding events
- **F4** — Aggregate reconstituted by folding its stream
- **F5** — Ubiquitous Language: names come from the domain
- **F6** — Bounded Contexts: Ticket Sales vs Scheduling
- **F7** — Event log's cheap filters: aggregate-id, type, sequence
- **DL** — Decision loop: Command → query decision context → apply rules with I/O → append event → update read models

- **LO1** leans on: DL, F1, F2, F3
- **LO2** leans on: DL, F3, F4, F7
- **LO3** leans on: F1, F4, F5, F6, F7
- **LO4** leans on: DL, F1, F5

## LO1 — Design “Slices” using Event Modeling

> Work together to map the flow of commands, events, and state views within a bounded context using Event Modeling as a visual design technique.

- **Enabling: Slice = one vertical path: UI → Command → Event → Read Model**
  - A Screen issues Commands and displays Read Models `[EM]`
  - A slice is the unit of design AND the unit of implementation `[EM]`
  - Identify which Read Model feeds each screen `[L1]`
  - Sold-out is derived state → hide the ‘Buy’ affordance from the read model `[L1]`
- **Enabling: Event Modeling: sticky-note design, implementation-focused**
  - Sticky vocabulary: UI / Command / Event / Read Model, connected left-to-right `[EM]`
  - Event Modeling vs Event Storming: focused on implementing features `[EM]`
- **Enabling: The command decision loop**
  - now() and external inputs enter at decision time = I/O `[L1]`
  - ‘future / eligible’ is derived state, not a property of one event `[L1]`
  - Read model stores showDateTime; query applies showDateTime > now() `[L1]`
  - An Actuator / Processor turns the passage of time into a Command `[L1]`

*Reading the tags:* Event-Modeling (EM) atoms are practiced in the group modeling exercise that opens every lab. The rest are first concretely met in Lab 1 (1A manual trigger, 1B actuator/time, 1D sold-out display).

## LO2 — Implement domain behavior with per-command consistency boundaries

> Replace a fixed Aggregate boundary with Command-specific boundaries, allowing independent behaviors (like rescheduling a concert and changing its lineup) to evolve without affecting each other.

- **Enabling: Aggregate = fixed consistency boundary**
  - An Aggregate is a fixed transactional consistency boundary `[F]`
  - Optimistic concurrency via aggregate version `[F]`
- **Enabling: DCB = boundary defined by the query predicate**
  - A Tag e.g. concert:X scopes which events a query returns `[L2]`
  - QueryPredicate = event types + tags; returns events matching ALL tags `[L2]`
  - Decision model built just-in-time by folding the query result `[L2]`
  - The consistency boundary IS the query predicate — it is dynamic `[L2]`
  - Append is conditional: ‘no new events matching this query since seq N’ `[L2]`
  - Failure mode: two concurrent commands both pass rules and both append `[L2]`
  - Tags are append-time facts, NOT derived state `[L2]`
- **Enabling: Command-specific boundaries → independent behaviors co-evolve**
  - Reschedule vs. lineup change evolve under separate command boundaries `[EM]`
- **Enabling: A boundary may need to span two aggregates**
  - ‘4 tickets per customer per concert’ spans Customer AND Concert `[L2]`
  - Concert doesn’t cheaply know a customer’s total; Customer doesn’t own inventory `[L2]`
  - Three designs: enlarge aggregate / projection-check (race) / reservation `[L2]`
  - Eventual consistency can be a business decision, not a technical failure `[L2]`
  - Cross-aggregate decision = one query, multiple tags {cust:C, concert:X} `[L2 · L3]`

*Reading the tags:* The meatiest objective, and it carries the cross-aggregate invariant work from Lab 2. A1–A2 are Foundations participants arrive with; the reschedule-vs-lineup atom is an Event-Modeling / discussion point; the DCB mechanics and cross-aggregate invariant are driven by Lab 2. “Cross-aggregate decision = one query, multiple tags” is reused in Lab 3.

## LO3 — Experience how Event Sourcing simplifies DDD implementation

> Eliminate traditional repository abstractions, object-relational mapping, and “back-door” state access that traditional persistence requires, while keeping the value of Bounded Contexts and Ubiquitous Language.

- **Enabling: What traditional persistence forces on you**
  - A repository hides a store behind a collection illusion `[F]`
  - Saving forces the aggregate to expose its whole internal state — the ‘back door’ `[F]`
- **Enabling: Persistence no longer punches an encapsulation hole  —  Lab 0**
  - snapshot() out + new Concert(snapshot) in exist ONLY for the database `[L00]`
  - ES persists domain facts (events), not a full state dump `[L00]`
  - Delete ConcertDbo / asConcert(); append + query replace save + findById `[L00]`
  - The aggregate can now expose ZERO public state accessors `[L00]`
  - Persisted shape = facts, decoupled from internal representation `[L00]`
  - Adding a field folds from existing events — no persistence code changes `[L00]`
- **Enabling: ES replaces load-state with query-and-fold**
  - In DCB there is NO aggregate to load — you query and fold `[L2]`
  - Event log’s cheap filter dimensions: aggregate-id, event type, sequence `[F]`
- **Enabling: CQRS: querying derived state is the read model’s job**
  - Folding the whole log at query time is the anti-pattern to avoid `[L1]`
  - A maintained + checkpointed projection serves list queries `[L1]`
  - CQRS separation is by design, not a defect of event sourcing `[L1]`
- **Enabling: DDD value is retained, machinery is removed**
  - Bounded Contexts still bound the model: Ticket Sales vs Concert Scheduling `[F]`
  - Ubiquitous Language still names everything: Concert, Customer, Purchase `[F]`

*Reading the tags:* LO3 used to be purely experiential — felt across the other labs, drilled by none. Lab 0 — Delete the Snapshot (the L00 branch) fixes that: it directly exercises the “back-door state access” clause by striking out snapshot(), the reconstitution constructor, and the DBO/mapper, and watching encapsulation improve. The rest of LO3 stays experiential across Labs 1–2, so pair Lab 0 with an explicit “notice what you didn’t have to write” debrief.

## LO4 — Apply a consistent testing pattern across all domain behavior

> Use “Given Events, When Command executes, Then Events result” to write clear, consistent tests that express domain rules in the Ubiquitous Language.

- **Enabling: The GWT structure: Given Events → When Command → Then Events**
  - Given = seed prior events as the history `[T]`
  - When = execute exactly one command `[T]`
  - Then = assert the resulting event(s) — or a rejection `[T]`
  - Domain tests are I/O-free (the ‘I/O-Free Tests’ run config) `[T]`
  - now() is injected as a test input, not read from the wall clock `[T]`
- **Enabling: Tests express rules in the Ubiquitous Language**
  - Assertions phrased in domain terms, not storage / framework terms `[T]`
- **Enabling: Coverage: happy path + each rejection + idempotent retry**
  - A rejected command may emit NO event — events are facts, not errors `[T]`
  - Test each domain rule failing separately `[T]`
  - Idempotent retry: duplicate commandId must not double-emit `[T]`

*Reading the tags:* The testing thread (T) runs across every lab — GWT is the medium in which each lab’s rules get expressed. ‘Rejected command = no event’ first bites in Lab 1; idempotent retry becomes essential in Lab 3’s refund/cancellation fan-out; the injected now() pairs with the Lab 1 foundation.

## Coverage view — which lab carries which knowledge

| Enabling area | Foundation (F/EM) | Lab 0 (L00) | Lab 1 | Lab 2 | Lab 3 | Testing (T) |
|---|---|---|---|---|---|---|
| **Slice / Event Modeling (LO1)** | Screen↔command↔read-model, sticky vocabulary, slice-as-unit | — | now()=I/O, ‘future’ is derived, read-model-per-screen, actuator, sold-out display | — | — | — |
| **Consistency boundaries (LO2)** | Aggregate = fixed boundary, optimistic version, reschedule-vs-lineup (discussion) | — | tags scope queries (setup) | QueryPredicate, JIT decision model, boundary = query, conditional append, race failure, tags = append-time | one-query-multi-tag (reused) | — |
| **Cross-aggregate invariant (under LO2)** | — | — | — | invariant spans Customer+Concert, 3 design trade-offs, eventual-consistency-as-business-call | DCB {cust,concert} query (reused) | — |
| **ES simplifies DDD (LO3)** | repository illusion, back-door = state extraction to persist, bounded contexts, ubiquitous language | delete snapshot() / reconstitution ctor / DBO / mapper, facts-not-state-dump, zero public accessors, decoupled persisted shape | fold-whole-log anti-pattern, maintained projection, CQRS-by-design | no aggregate to load | — | — |
| **GWT testing (LO4)** | I/O-free tests | — | rejected-command-no-event, now() injected | — | idempotent retry / commandId | Given/When/Then, one command, rule-by-rule, domain-language assertions |

**Two gaps this surfaces, for your prep:**

1. LO3 (ES simplifies DDD) now has a dedicated demonstrator — Lab 0, “Delete the Snapshot” (L00) — which drills the back-door clause head-on (strike out snapshot(), the reconstitution ctor, and the DBO/mapper; watch encapsulation improve). The rest of LO3 stays experiential across Labs 1–2, so still plan the “notice what you didn’t have to write” debrief — Lab 0 seeds it rather than replacing it.
2. Lab 3 (Refunds) is marked “discuss, and if time is available, implement” — yet it’s the only place two atoms are practiced: idempotent-retry / commandId and the cross-aggregate {cust, concert} query in a refund context. If Lab 3 gets cut for time, those two atoms lose their only home — consider seeding idempotency into Lab 2 as insurance.
