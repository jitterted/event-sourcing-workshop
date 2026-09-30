package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRejection;
import dev.ted.jitterticket.support.Result;

@FunctionalInterface
public interface CommandWithParams<AGGREGATE, PARAMS> {
    Result<Void, ConcertRejection> execute(AGGREGATE aggregate, PARAMS params);
}
