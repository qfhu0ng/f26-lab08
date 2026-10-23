package edu.cmu.cs214.campusreserve.notifications.templates;

import edu.cmu.cs214.campusreserve.notifications.NotificationKind;

/**
 * The message a person gets when they change a booking of theirs in place. It
 * names the booking as it was and as it is now.
 *
 * <p>Wants {@link Placeholders#NAME}, {@link Placeholders#ROOM_ID},
 * {@link Placeholders#WHEN}, {@link Placeholders#ATTENDEES},
 * {@link Placeholders#PREVIOUS_WHEN}, {@link Placeholders#PREVIOUS_ATTENDEES},
 * and {@link Placeholders#BOOKING_ID}.
 */
public final class BookingModifiedTemplate extends TextMessageTemplate {

    private static final String SUBJECT = "Booking for room {roomId} is now {when}";

    private static final String BODY = """
        Hi {name},

        Your booking {bookingId} for room {roomId} has changed.
        It was {previousWhen}, for {previousAttendees} people.
        It is now {when}, for {attendees} people.

        If you no longer need the room, cancel the booking so someone else can take it.
        """;

    /** Builds the template. */
    public BookingModifiedTemplate() {
        super(NotificationKind.BOOKING_MODIFIED, SUBJECT, BODY);
    }
}
