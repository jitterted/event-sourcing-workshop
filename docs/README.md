# Workshop Docs Index

Planning and facilitation material for the JitterTicket event-sourcing workshop (dev2next). Source of truth for the labs, the learning-objective map, and the prep each lab needs.

## Learning-objective map

- **[knowledge-map.html](knowledge-map.html)** — Backwards knowledge map: the four official learning objectives traced down to atomic facts/skills, each color-coded to where it's taught (Lab 0–3, Foundation, Event Modeling, Testing). Open in a browser; self-contained, no build step.
- **[knowledge-map.md](knowledge-map.md)** — The same map as an editable Markdown outline (renders on GitHub). Edit this; treat the HTML as the rendered view.

## Lab 0 — "Delete the Snapshot" (LO3 demonstrator)

- **[lab-0-delete-the-snapshot.md](lab-0-delete-the-snapshot.md)** — Facilitator/teaching write-up: the before/after `Concert`, the guided strike-through, scoreboard, the persistence-tax beat (table + migration), debrief questions, and the ES-snapshot caveat. *Run this in the session.*
- **[lab-0-prep-plan.md](lab-0-prep-plan.md)** — Build plan for the "before": the isolated, ES-free `lab0` package (full Java skeletons — nested `Concert.Snapshot`, dumb-bag `ConcertDbo` record, repo-owned mapping), what to omit, the round-trip demo, and a verification checklist. *Do this before the session.*
- **[lab0/concert-schema.sql](lab0/concert-schema.sql)** — Prop: "what the table looks like now" (columns == `Concert`'s private fields).
- **[lab0/add-on-sale-schedule.migration.sql](lab0/add-on-sale-schedule.migration.sql)** — Prop: the migration traditional persistence forces on you, with the precise ES-vs-table framing. Foreshadows Lab 1B.

## Lab 1 — "Separate On-Sale from Scheduling" (1A–1D)

- **[lab-1-prep-and-facilitation.md](lab-1-prep-and-facilitation.md)** — Combined plan: **Part A** codebase prep after Lab 0 (scaffold the UI, seed data, seams, what to leave for participants) and **Part B** online 4-day facilitation for 1A–1D. Includes a "Decisions to lock" checklist.

## Suggested reading order

1. `knowledge-map.html` — the shape of the whole workshop.
2. `lab-0-prep-plan.md` (+ the `lab0/` SQL props) → `lab-0-delete-the-snapshot.md` — prep, then run Lab 0.
3. `lab-1-prep-and-facilitation.md` — prep and run Lab 1.

## Also in this folder

- `lab0/` — SQL props for the Lab 0 persistence-tax beat (schema + migration).
- `pattern-map.md`, `pattern-map-guidelines.md`, `pattern-map-notes.md` — earlier pattern-mapping notes (pre-existing).
- `images/` — diagram assets referenced by the docs.
