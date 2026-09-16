# Event-Sourcing Pattern Map — Working Guidelines

Guidelines for the "Event Sourcing Design Patterns" Navigation Map (tldraw, page *Overview Map*),
for the PLoP paper. These are derived from the map as it actually stands, not imported wholesale
from POSA. Where they disagree with the earlier LLM-generated guidelines, that's intentional — see
[Divergences](#divergences-from-the-earlier-draft). Treat all examples as illustrative, not law.

## 1. What this map is for

The map helps a designer **navigate design decisions**: start at one pattern and discover what
refines it, what competes with it, what it pairs with, and what it buys you. It is *not* a runtime
or data-flow diagram. Success test — from any node a reader can answer:

1. What more specific patterns **refine** it?
2. What **alternatives** exist (and what am I choosing against)?
3. What complementary patterns should I **combine** it with?
4. What capabilities or further patterns does it **enable**?

If an edge doesn't help answer one of those, it probably doesn't belong on this map.

## 2. Two species of node

The current canvas mixes two kinds of node. Keeping them distinct is the single biggest
clarity win available.

- **Structural patterns** — things you *build* or *decide to have*. Concrete, buildable.
  *Event, Immutable Ordered Event Log, Command Log, Command, Decider, Decision Query,
  Event Consumer, Projector, Processor, Read Model / Projection.*
- **Capabilities & principles** — things you *get* or *believe*; consequences of applying patterns.
  *Events Are Source of Truth, History as State, Time Travel, Undo via Command Reversal,
  Automates Command Initiation, Translates for Integration.*

Test: **can you implement it?** → structural pattern. **Is it an outcome you gain?** → capability.
Recommend visually distinguishing the two (e.g. note color), even in draft. A clean reading of the
whole map falls out of this: *structural patterns connect to each other by Refinement/Variant/
Combination; capabilities hang off patterns by Enables.*

Watch for nodes doing double duty:
- `Automate Command Initiation` / `Translate for Integration` are currently drawn as sub-kinds of
  Processor (structural) but *named* as capabilities. Pick one: either name them as patterns
  (e.g. *Stateful Processor / Process Manager* and *Stateless Processor / Translator*) and let the
  capability be an Enables target, or treat them as capabilities and give the two Processor
  sub-patterns their own names.
- `Read model as a Point of View Cache` packs a pattern (Read Model / Projection) + an insight
  (it's a disposable cache) + a metaphor (a point of view) into one label. Keep the node a
  structural pattern (**Projection / Read Model**); carry "custom cache" and "point of view" in the
  pattern's prose or as a linked capability.

## 3. Relationship vocabulary

Four relationship types cover this map. Two are the **backbone** (they give the map its
navigational depth); two are **lateral** (they connect peers). Only Refinement and Enables are
actually drawn today; Variant and Combination are latent and worth adding.

### Backbone

**Refinement** — *A can be refined into the more specific B* (same general problem, narrower
solution). Directed **broad → specific**.
Attested: `Event Consumer → Projector`, `Event Consumer → Processor`,
`Processor → Automate Command Initiation`, `Processor → Translate for Integration`.

**Enables** — *A creates a context in which B (a capability, or a further pattern) becomes possible
or worthwhile.* Directed **pattern → consequence**. This is how capabilities attach.
Attested: `Immutable Ordered Event Log → History as State`,
`Immutable Ordered Event Log → Time Travel`, `Command Log → Undo via Command Reversal`.

### Lateral

**Variant** — *A and B are genuine alternatives to substantially the same problem; choosing one is
choosing against the other.* Undirected. Use sparingly — only for real either/or forks.
Latent candidate: **Command Log ↔ Immutable Ordered Event Log** (command-sourcing vs.
event-sourcing as the source of truth).

**Combination** — *A and B solve complementary problems and are deliberately used together;*
neither refines the other. Undirected.
Latent candidates: **Events Are Source of Truth + Projection** (write-truth paired with read-cache);
**Decider + Decision Query** (a decision needs a decision-shaped view).

### Not a relationship: runtime flow

Behavioral/data-flow edges belong on a *separate* runtime diagram, not here. The tell is a label
that narrates execution ("generates", "records … in a", "so that a"). These four current edges are
the runtime decision loop (`Command → Decider → Event → Log → Decision Query → Decider`) and should
be lifted off the map:

- `Command → Decider` ("expresses intent … so that a")
- `Decision Query → Decider` ("derives … so that")
- `Decider → Event` ("if successful, generates")
- `Decider → Command Log` ("records command and decision in a")

The *nodes* stay; only the runtime edges leave. Reconnect these patterns with backbone/lateral
relationships instead (e.g. Decider **combines with** Decision Query; Command Log is a **variant**
of the Event Log).

## 4. Direction & wording conventions

- **Refinement:** arrow points from broad to specific; reads "…can be refined into…".
- **Enables:** arrow points from the pattern to what it enables; reads "…enables…".
- **Variant / Combination:** undirected; read "…is an alternative to…" / "…is used together with…".
- Encode the **type** in the visual channel (below). Use a short prose label *only* to add
  specificity the type alone doesn't convey — not to carry the type itself.

## 5. Visual grammar

Prefer a small, consistent vocabulary and a legend node on the canvas.

| Relationship | Line | Arrowheads |
|---|---|---|
| Refinement | solid | one, filled (broad → specific) |
| Enables | dashed | one, open (pattern → capability) |
| Variant | solid | both ends |
| Combination | solid | none (a plain link) |

Optionally, color nodes by species (§2). Reserve edge labels for specificity, not type.

## 6. Naming

- **Structural patterns:** concrete, established names — mechanism or role. *Projector, Decider,
  Processor, Event Consumer, Immutable Ordered Event Log.* Matching community vocabulary aids
  navigation (POSA does this: Reactor, Broker, Proactor). "Immutable Ordered Event Log" is a good
  name — *immutable* and *ordered* are the design decisions, not noise.
- **Capabilities & principles:** phrase as the insight or outcome. *Events Are Source of Truth,
  History as State, Time Travel, Undo via Command Reversal.*
- Don't smuggle a benefit into a structural pattern's name (see `Read model as a Point of View
  Cache` in §2).
- Keep one part of speech within a species: structural nouns; capabilities as outcome phrases.

## 7. Multiplicity

Patterns may hold several relationships and appear in more than one — the map is a graph, not a
tree. A worthwhile example already present: **Decision Query** is a Projection used on the
write side, so it is both a **Refinement** of Projection *and* a **Combination** partner of Decider.
Don't force a strict hierarchy.

## Divergences from the earlier draft

1. **POSA's four types aren't gospel.** They happen to fit, so I kept them, but re-derived from this
   map — and Variant/Combination are currently *unused*, not established. Add them where a real
   fork or pairing exists; don't retrofit them everywhere.
2. **Not "insight-name over mechanism-name" across the board.** That over-applies. Structural
   patterns should keep concrete/established names; reserve insight-phrasing for capability and
   principle nodes (§6).
3. **Keep useful edge prose.** The earlier draft said avoid text labels; for a paper the specific
   wording carries meaning. Instead: encode *type* visually, keep prose for *specificity*.
4. **Name the runtime problem explicitly.** The biggest issue with the current canvas isn't naming —
   it's that a large part of it is a runtime flow. Separate concerns first (§3).

## Appendix — current edges classified

| From → To | Current label | Verdict |
|---|---|---|
| Event Consumer → Projector | accrues subset of state via | **Refinement** (keep; relabel) |
| Event Consumer → Processor | triggers other actions through | **Refinement** (keep; relabel) |
| Processor → Automate Command Initiation | may require history to | **Refinement** (see §2 naming) |
| Processor → Translate for Integration | without history can | **Refinement** (see §2 naming) |
| Immutable Ordered Event Log → History as State | enables | **Enables** ✓ |
| Immutable Ordered Event Log → Time Travel | available for querying to | **Enables** (relabel) |
| Command Log → Undo via Command Reversal | enables | **Enables** ✓ |
| Immutable Ordered Event Log → Event Consumer | makes new events known to | recast → **Enables** |
| Immutable Ordered Event Log → Decision Query | available for querying by | recast → **Enables** |
| Event → History as State | accrues over time to form | recast → **Enables** ("History Accrues") |
| Event → Events as Source of Truth | records … so that | recast → **Enables / grounds** |
| Projector → Read Model / Projection | derives | recast → **Enables** (produces the cache) |
| Command → Decider | expresses intent … so that a | **Runtime** → move off map |
| Decision Query → Decider | derives … so that | **Runtime** → move off map |
| Decider → Event | if successful, generates | **Runtime** → move off map |
| Decider → Command Log | records command and decision in a | **Runtime** → move off map |

Latent, not yet drawn: **Command Log ↔ Immutable Ordered Event Log** (Variant);
**Events Are Source of Truth + Projection** and **Decider + Decision Query** (Combination).
