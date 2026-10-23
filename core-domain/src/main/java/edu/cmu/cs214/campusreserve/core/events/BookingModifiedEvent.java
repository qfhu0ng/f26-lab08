package edu.cmu.cs214.campusreserve.core.events;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import java.time.Instant;
import java.util.Objects;

/**
 * A booking changed its interval, its attendee count, or both, and kept its
 * room. {@link #interval()} and {@link #attendees()} are the new values, like
 * every other event's. The event also carries what they were before, so a reader
 * can name both.
 */
public record BookingModifiedEvent(
    Instant occurredAt,
    String bookingId,
    String roomId,
    String userId,
    TimeInterval interval,
    int attendees,
    TimeInterval previousInterval,
    int previousAttendees) implements DomainEvent {

    /** The wire name of this event. */
    public static final String TYPE = "booking-modified";

    public BookingModifiedEvent {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(previousInterval, "previousInterval");
    }

    /** Builds the event from the booking as it was and as it is now. */
    public static BookingModifiedEvent of(Booking previous, Booking current, Instant occurredAt) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (!previous.id().equals(current.id())) {
            throw new IllegalArgumentException(
                "A modification is about one booking, got " + previous.id() + " and " + current.id());
        }
        return new BookingModifiedEvent(
            occurredAt,
            current.id(),
            current.roomId(),
            current.userId(),
            current.interval(),
            current.attendees(),
            previous.interval(),
            previous.attendees());
    }

    @Override
    public String type() {
        return TYPE;
    }
}
