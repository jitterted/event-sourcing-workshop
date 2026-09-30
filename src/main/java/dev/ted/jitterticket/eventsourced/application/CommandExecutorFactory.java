package dev.ted.jitterticket.eventsourced.application;

import dev.ted.jitterticket.eventsourced.application.port.EventStore;
import dev.ted.jitterticket.eventsourced.domain.concert.Concert;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertEvent;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertId;
import dev.ted.jitterticket.eventsourced.domain.concert.ConcertRejection;
import dev.ted.jitterticket.support.Result;

public class CommandExecutorFactory {
    private final EventStore<ConcertId, ConcertEvent, Concert> concertEventStore;
    private final ConcertQuery concertQuery;

    private CommandExecutorFactory(EventStore<ConcertId, ConcertEvent, Concert> concertEventStore) {
        this.concertEventStore = concertEventStore;
        concertQuery = new ConcertQuery(concertEventStore);
    }

    public static CommandExecutorFactory create(EventStore<ConcertId, ConcertEvent, Concert> concertEventStore) {
        return new CommandExecutorFactory(concertEventStore);
    }

    public static CommandExecutorFactory createForTest() {
        return create(InMemoryEventStore.forConcerts());
    }

    public Command<ConcertId> wrap(Command<Concert> concertCommand) {
        return concertId -> {
            Concert concert = concertQuery.find(concertId);
            Result<Void, ConcertRejection> result = concertCommand.execute(concert);
            // a rejected command changed nothing, so there is nothing to save
            if (result.isSuccess()) {
                concertEventStore.save(concert);
            }
            return result;
        };
    }

    public CommandWithParams<ConcertId, RescheduleParams> wrapWithParams(
            CommandWithParams<Concert, RescheduleParams> command) {
        return (concertId, reschedule) -> {
            Concert concert = concertQuery.find(concertId);
            Result<Void, ConcertRejection> result = command.execute(concert, reschedule);
            // a rejected command changed nothing, so there is nothing to save
            if (result.isSuccess()) {
                concertEventStore.save(concert);
            }
            return result;
        };
    }

    public CreateWithParams<ConcertId, ScheduleParams> wrapForCreation(
            CreateWithParams<Concert, ScheduleParams> command) {
        return scheduleParams -> {
            Concert concert = command.execute(scheduleParams);
            concertEventStore.save(concert);
            return concert.getId();
        };
    }

}

