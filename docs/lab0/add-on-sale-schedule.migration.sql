-- Lab 0 prop — the migration traditional persistence forces on you.
--
-- Feature: support a SCHEDULED start of ticket sales (this foreshadows Lab 1B).
-- It's a NEW fact the table never held, so it needs a new column — and once you
-- add a column, every row already in the table has to be dealt with.

ALTER TABLE concert
    ADD COLUMN on_sale_at TIMESTAMP;

-- The DDL is the visible tip. The questions it forces:
--   * The existing rows: leave on_sale_at NULL and make every reader handle
--     "unknown"? Backfill a computed value (from what source of truth)?
--   * The object, Concert.Snapshot, the reconstitution ctor, snapshot(), and the
--     row mapper (both directions) each gain the new field too.
--   * The migration must be written, reviewed, ordered, and RUN against the DB.
--
-- ============================================================================
-- Event-sourced side, SAME feature:
--   * Add a TicketSalesScheduled event + one apply() case.
--   * NO migration, and history is UNTOUCHED — old concert streams simply lack
--     the event, and "no event" is a perfectly good answer for them.
--
-- Precise framing (say it this way in the room):
--   This is a genuinely new fact, so ES is not "zero code" — it still adds an
--   event type and a fold case. The honest win is "no migration, history
--   untouched," not "no code." For the *no-caveats* version, use a field
--   DERIVABLE from events you already have (e.g. tickets-sold-so-far from
--   TicketsSold): there ES adds NOTHING to the write path, while the table
--   still needs a column + backfill.
-- ============================================================================
