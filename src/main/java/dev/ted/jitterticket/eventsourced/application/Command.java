package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRejection;
import dev.ted.jitterticket.support.Result;

@FunctionalInterface
public interface Command<T> {
    Result<Void, ConcertRejection> execute(T t);
}
