package dev.ted.jitterticket.eventviewer.adapter.in.web;

import java.util.Arrays;
import java.util.List;

/**
 * The projected state of an aggregate, in a shape the shared view-events template can render:
 * name/value fields for scalar properties, plus a table for each collection of repeating data
 * (a Customer's ticket orders, for example).
 * <p>
 * Returning structure instead of pre-formatted strings keeps the formatting decisions
 * (alignment, tooltips, empty states) in the template where they belong.
 */
public record ProjectedStateView(List<Field> fields,
                                 List<Table> tables) {

    public static ProjectedStateView ofFields(Field... fields) {
        return new ProjectedStateView(List.of(fields), List.of());
    }

    public ProjectedStateView plus(Table table) {
        return new ProjectedStateView(fields, List.of(table));
    }

    public record Field(String name, String value) {
        public static Field of(String name, Object value) {
            return new Field(name, String.valueOf(value));
        }
    }

    public record Table(String title,
                        List<String> headers,
                        List<Row> rows,
                        String emptyMessage) {
        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    public record Row(List<String> cells) {
        public static Row of(Object... cells) {
            return new Row(Arrays.stream(cells)
                                 .map(String::valueOf)
                                 .toList());
        }
    }
}
