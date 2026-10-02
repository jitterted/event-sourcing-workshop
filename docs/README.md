# Workshop Docs Index

Planning and facilitation material for the JitterTicket event-sourcing workshop (dev2next). Source of truth for the labs, the learning-objective map, and the prep each lab needs.

## Learning-objective map

- **[knowledge-map.html](knowledge-map.html)** — Backwards knowledge map: the four official learning objectives traced down to atomic facts/skills, each color-coded to where it's taught (Lab 0–3, Foundation, Event Modeling, Testing). Open in a browser; self-contained, no build step.
- **[knowledge-map.md](knowledge-map.md)** — The same map as an editable Markdown outline (renders on GitHub). Edit this; treat the HTML as the rendered view.

## Lab 0 — "Delete the Snapshot" (LO3 demonstrator)

Lab 0 shows two versions of `Concert`: the snapshot-persisted **ORM** version (`orm` package) and the real **ES aggregate** (events + `apply()` fold). A third, non-aggregate "decider" version is deferred to Lab 2 prep.

- **[lab-0-delete-the-snapshot.md](lab-0-delete-the-snapshot.md)** — Facilitator/teaching write-up: the before/after `Concert`, the guided strike-through, scoreboard, the persistence-tax beat (table + migration), debrief questions, and the ES-snapshot caveat. *Run this in the session.*
- **[lab-0-prep-plan.md](lab-0-prep-plan.md)** — *Round 2* prep plan: aligns Lab 0 with the LO3 atoms in the knowledge map, records how commands reject (`Result`), and carries the verification checklist. *Do this before the session.*
- **[archive/lab-0-prep-plan-v1-completed.md](archive/lab-0-prep-plan-v1-completed.md)** — *History, not a to-do list.* Round 1 build plan for the `orm` package (nested `Concert.Snapshot`, `ConcertDbo`, repo-owned mapping). Completed; kept for reference.
- **[lab0/concert-schema.sql](lab0/concert-schema.sql)** — Prop: "what the table looks like now" (columns == `Concert`'s private fields).
- **[lab0/add-on-sale-schedule.migration.sql](lab0/add-on-sale-schedule.migration.sql)** — Prop: the migration traditional persistence forces on you, with the precise ES-vs-table framing. Foreshadows Lab 1B.

## Lab 1 — "Separate On-Sale from Scheduling" (1A–1D)

- **[lab-1-prep-and-facilitation.md](lab-1-prep-and-facilitation.md)** — Combined plan: **Part A** codebase prep after Lab 0 (scaffold the UI, seed data, seams, what to leave for participants) and **Part B** online 4-day facilitation for 1A–1D. Includes a "Decisions to lock" checklist.

## Other working docs

- **[role-playing.md](role-playing.md)** — Role-playing game that teaches read models and processors.
- **[migrate-scheduled-concerts-projector-checklist.md](migrate-scheduled-concerts-projector-checklist.md)**, **[migrate-concert-projectors-checklist.md](migrate-concert-projectors-checklist.md)** — Checklists for moving projectors onto `NewProjectionCoordinator`.

## Suggested reading order

1. `knowledge-map.html` — the shape of the whole workshop.
2. `lab-0-prep-plan.md` (+ the `lab0/` SQL props) → `lab-0-delete-the-snapshot.md` — prep, then run Lab 0.
3. `lab-1-prep-and-facilitation.md` — prep and run Lab 1.

## Also in this folder

- `lab0/` — SQL props for the Lab 0 persistence-tax beat (schema + migration).
- `archive/` — completed plans kept as history.
- `pattern-map.md`, `pattern-map-guidelines.md`, `pattern-map-notes.md` — earlier pattern-mapping notes (pre-existing).
- `images/` — diagram assets referenced by the docs.
