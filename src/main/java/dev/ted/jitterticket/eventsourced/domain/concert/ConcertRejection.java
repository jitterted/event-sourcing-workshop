package dev.ted.jitterticket.eventsourced.domain.concert;

/**
 * Why a Concert command was rejected. Named in the Ubiquitous Language, so tests and
 * screens can say what the domain rule was, not what the code did.
 */
public enum ConcertRejection {
    NEW_SHOW_DATE_TIME_IS_NOT_LATER("The new show date and time must be later than the current one"),
    TICKETS_HAVE_ALREADY_BEEN_SOLD("Tickets have already been sold, the concert can not be rescheduled"),
    NOT_ENOUGH_TICKETS_AVAILABLE("There are not enough tickets available for that purchase"),
    CONCERT_NOT_FOUND("No Concert was found with that Concert ID");

    private final String message;

    ConcertRejection(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
