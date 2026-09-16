-- Lab 0 prop — "what the table looks like now"
--
-- The snapshot-persisted Concert maps 1:1 to this row. EVERY column is one of
-- Concert's PRIVATE fields: the database sees the aggregate's entire internals.
-- The table *is* your internal representation — that's the coupling ES removes.
--
-- Illustrative teaching artifact only. NOT wired into the app's real schema.sql.

CREATE TABLE concert (
    id                       UUID          PRIMARY KEY,
    artist                   VARCHAR(255)  NOT NULL,
    ticket_price             INTEGER       NOT NULL,
    show_date_time           TIMESTAMP     NOT NULL,
    doors_time               TIME          NOT NULL,
    capacity                 INTEGER       NOT NULL,
    available_ticket_count   INTEGER       NOT NULL,
    max_tickets_per_purchase INTEGER       NOT NULL,
    tickets_on_sale          BOOLEAN       NOT NULL
);
