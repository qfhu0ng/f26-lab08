package edu.cmu.cs214.campusreserve.core.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.support.Fixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingModifiedEventTest {

    private static final Instant BOOKED = Instant.parse("2026-10-19T09:00:00Z");
    private static final Instant CHANGED = Instant.parse("2026-10-19T09:30:00Z");

    private final Booking before =
        Booking.confirmed("bk-1", "r-1", "u-1", Fixtures.interval("10:00", "11:00"), 4, BOOKED);
    private final Booking after =
        Booking.confirmed("bk-1", "r-1", "u-1", Fixtures.interval("10:15", "11:15"), 6, BOOKED);

    @Test
    void hasItsOwnWireName() {
        BookingModifiedEvent event = BookingModifiedEvent.of(before, after, CHANGED);

        assertEquals("booking-modified", event.type());
        assertEquals(BookingModifiedEvent.TYPE, event.type());
    }

    @Test
    void carriesTheNewValuesAsTheBookedOnesAndTheOldOnesBesideThem() {
        BookingModifiedEvent event = BookingModifiedEvent.of(before, after, CHANGED);

        assertEquals(CHANGED, event.occurredAt());
        assertEquals("bk-1", event.bookingId());
        assertEquals("r-1", event.roomId());
        assertEquals("u-1", event.userId());
        assertEquals(after.interval(), event.interval());
        assertEquals(6, event.attendees());
        assertEquals(before.interval(), event.previousInterval());
        assertEquals(4, event.previousAttendees());
    }

    @Test
    void refusesToDescribeTwoDifferentBookings() {
        Booking other =
            Booking.confirmed("bk-2", "r-1", "u-1", Fixtures.interval("12:00", "13:00"), 4, BOOKED);

        assertThrows(IllegalArgumentException.class, () -> BookingModifiedEvent.of(before, other, CHANGED));
    }

    @Test
    void refusesAMissingPreviousInterval() {
        assertThrows(NullPointerException.class,
            () -> new BookingModifiedEvent(CHANGED, "bk-1", "r-1", "u-1", after.interval(), 6, null, 4));
    }
}
