package dev.ted.jitterticket.orm;

import java.util.UUID;

public record ConcertId(UUID id) {
    public static ConcertId createRandom() {
        return new ConcertId(UUID.randomUUID());
    }
}