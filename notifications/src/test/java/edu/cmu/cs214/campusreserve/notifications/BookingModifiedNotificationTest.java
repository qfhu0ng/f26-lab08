package edu.cmu.cs214.campusreserve.notifications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.cmu.cs214.campusreserve.core.domain.Booking;
import edu.cmu.cs214.campusreserve.core.domain.TimeInterval;
import edu.cmu.cs214.campusreserve.core.events.BookingModifiedEvent;
import edu.cmu.cs214.campusreserve.notifications.support.Fixtures;
import edu.cmu.cs214.campusreserve.notifications.support.TestClock;
import edu.cmu.cs214.campusreserve.notifications.templates.TemplateRegistry;
import org.junit.jupiter.api.Test;

/**
 * The owner's message after a modification has to name the booking as it was and
 * as it is now. Each assertion pins a value to its own sentence, so a template
 * that filled both from the new values would fail here.
 */
class BookingModifiedNotificationTest {

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final TestClock clock = TestClock.at("2026-10-19T09:00:00Z");
    private final NotificationService service =
        new NotificationService(Fixtures.directory(), outbox, clock);

    @Test
    void theOwnerHearsTheOldAndTheNewTimeAndPartySize() {
        TimeInterval before = Fixtures.interval();
        TimeInterval after = TimeInterval.parse("2026-10-20T14:00:00Z", "2026-10-20T15:30:00Z");
        assertNotEquals(IntervalText.formatUtc(before), IntervalText.formatUtc(after));

        service.publish(BookingModifiedEvent.of(booking(before, 4), booking(after, 7), Fixtures.NOW));

        Notification notification = outbox.peek().orElseThrow();
        String body = notification.body();
        assertEquals(NotificationKind.BOOKING_MODIFIED, notification.kind());
        assertEquals(Fixtures.USER_ID, notification.recipientUserId());
        assertEquals("Booking for room r-401 is now 2026-10-20 14:00 to 15:30 UTC", notification.subject());
        assertTrue(body.contains("It was " + IntervalText.formatUtc(before) + ", for 4 people."), body);
        assertTrue(body.contains("It is now " + IntervalText.formatUtc(after) + ", for 7 people."), body);
        assertEquals(1L, service.queuedCount());
        assertEquals(0, service.skippedCount());
    }

    @Test
    void anAttendeeOnlyChangeStillNamesBothCounts() {
        TimeInterval same = Fixtures.interval();

        service.publish(BookingModifiedEvent.of(booking(same, 4), booking(same, 2), Fixtures.NOW));

        String body = outbox.peek().orElseThrow().body();
        assertTrue(body.contains("It was " + IntervalText.formatUtc(same) + ", for 4 people."), body);
        assertTrue(body.contains("It is now " + IntervalText.formatUtc(same) + ", for 2 people."), body);
    }

    @Test
    void theKindMatchesTheEventWireNameAndHasShippedWording() {
        assertEquals(BookingModifiedEvent.TYPE, NotificationKind.BOOKING_MODIFIED.code());
        assertEquals(NotificationKind.BOOKING_MODIFIED, NotificationKind.fromCode("booking-modified"));
        assertTrue(TemplateRegistry.standard().find(NotificationKind.BOOKING_MODIFIED).isPresent());
    }

    private static Booking booking(TimeInterval interval, int attendees) {
        return Booking.confirmed(
            Fixtures.BOOKING_ID, Fixtures.ROOM_ID, Fixtures.USER_ID, interval, attendees, Fixtures.NOW);
    }
}
