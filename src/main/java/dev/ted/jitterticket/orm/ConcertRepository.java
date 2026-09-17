package dev.ted.jitterticket.orm;

import java.util.Optional;

public interface ConcertRepository {
    void save(Concert concert);
    Optional<Concert> findById(ConcertId id);
}