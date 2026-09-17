package dev.ted.jitterticket.orm;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryConcertRepository implements ConcertRepository {
    private final Map<ConcertId, ConcertDbo> store = new HashMap<>();

    @Override
    public void save(Concert concert) {
        Concert.Snapshot snapshot = concert.snapshot(); // grab internal data for persistence
        store.put(concert.getId(), new ConcertDbo(concert.getId(), snapshot));
    }

    @Override
    public Optional<Concert> findById(ConcertId id) {
        return Optional.ofNullable(store.get(id))   // empty optional if not in the store (map)
                       .map(dbo -> new Concert(dbo.snapshot())); // create from persisted snapshot
    }
}
