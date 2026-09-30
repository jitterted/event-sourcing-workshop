package dev.ted.jitterticket.orm;

import java.util.UUID;

public record CustomerId(UUID id) {
    public static CustomerId createRandom() {
        return new CustomerId(UUID.randomUUID());
    }
}
