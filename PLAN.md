# PLAN.md

One section per milestone. Fill them in order, with the plan before you run
anything, the guardrail before you run a task, and the run after.

Keep it short and specific. Point at modules, gates, and file paths.

---

## Milestone 1: The plan

Written on 2026-10-22, before any task was run.

**Decisions the tasks rest on.**

- History lives on the `Booking` record: an immutable list of changes
  (previous and new interval and attendees, and when), stored by the same
  `BookingStore.save` as the booking. `Booking.confirmed(...)` keeps its shape
  and starts with no history, and `cancelled()` keeps it. No new store port and
  no new `BookingService` constructor parameter, because
  `cli/.../CliContext.java:59` and shipped tests construct `BookingService`.
- Check order in `BookingService.modifyBooking`: the booking exists, the caller
  owns it, it still holds the room, it has not started (clause 1). If nothing
  changes, succeed and record nothing (clause 5). Then
  `BookingRules.checkRequest` for the same room, new interval, and new attendees
  (clause 3). Then the room calendar and the booker's calendar, each with this
  booking removed by id before `ConflictChecker` is asked, and no cap check
  (clause 2).
- Rejections reuse `RejectionReason`, and `ErrorMapping` does not change. A new
  `ModificationResult` (`Modified`, `Unchanged`, `Rejected`) mirrors
  `CancellationResult`.
- `PATCH /bookings/{bookingId}` takes any of `start`, `end`, `minutes`, and
  `attendees`. An omitted field keeps its current value, and `start` alone keeps
  the length. `end` together with `minutes`, an empty request, or a `roomId`
  other than the booking's is a 400.
- The `booking-modified` line keeps the shared fields, with `start`, `end`, and
  `attendees` holding the new values, and adds `previousStart`, `previousEnd`,
  and `previousAttendees`. The ledger folds it into the booking's existing record
  and keeps `created_at` and `from_waitlist`. A modification for a booking the log
  never opened is collected, like a cancellation without a booking.
- I edit `AGENTS.md` and `ARCHITECTURE.md` by hand, as a planned takeover, in
  the task that makes each statement false.

**The tasks.**

### T1. The owner is told what changed

- **Delivers:** a `booking-modified` domain event carrying the previous and the
  new interval and attendee count. Publishing it queues a message to the owner
  that names both the old and the new details (clause 6).
- **Touches:** `core-domain` (new `core/events/BookingModifiedEvent.java`, the
  `permits` list in `DomainEvent`) and `notifications` (`NotificationKind`,
  `NotificationService.valuesFor`, `templates/Placeholders`, a new template,
  `TemplateRegistry.standard()`), with new tests in both.
- **Depends on:** nothing. The event and the message are one task because
  `DomainEvent` is sealed and `NotificationKind.forEvent` and
  `NotificationService.valuesFor` switch over it exhaustively, so the event
  cannot land alone and still compile.
- **Checkpoint:** `mvn -B -pl notifications -am test` ends in `BUILD SUCCESS`,
  with the core-domain block above 167 tests and the notifications block above
  41. A new core-domain test pins the event's type and fields. A new
  notifications test publishes an event whose old and new intervals differ and
  whose attendee counts differ, and asserts that the queued body contains
  `IntervalText.formatUtc` of both intervals and both counts. A second case
  changes attendees only. Then `mvn -B clean test` is green, which is what
  shows no other module broke on the sealed hierarchy.
- **Green but wrong:** the template fills both the old and the new time from
  `event.interval()`, so the message shows the new time twice. A test that only
  asserts the new time, or that only uses an attendee-only change where both
  times are equal, still passes.

### T2. A booker can modify a booking in the domain

- **Delivers:** `BookingService.modifyBooking`, covering clauses 1 to 5 at the
  service level. Owner only, upcoming only. Every `checkRequest` rule applies.
  The room and own-calendar re-checks ignore the booking itself, and the cap is
  waived. A rejection stores, publishes, and promotes nothing. A success stores
  the booking with one new history entry, publishes `booking-modified`, and runs
  the existing `promoteWaitlist` pass when the interval changed. A no-op stores,
  publishes, and promotes nothing.
- **Touches:** `core-domain` only (`service/BookingService.java`,
  `domain/Booking.java`, a new change record, a new
  `domain/ModificationResult.java`), with a new `BookingServiceModifyTest`.
- **Depends on:** T1, for the event it publishes.
- **Checkpoint:** `mvn -B -pl core-domain -am test` ends in `BUILD SUCCESS` with
  more than 167 tests, and the new tests include these cases. A fifteen-minute
  nudge succeeds. A move into a taken slot is `ROOM_UNAVAILABLE`, and the stored
  booking, the published events, and the waitlist are unchanged. An overlap
  with the booker's booking in another room is `USER_DOUBLE_BOOKED`. A booker at
  `maxActiveBookingsPerUser` can still modify. Started, cancelled, and other
  people's bookings are refused. Each `checkRequest` rule fires on the new
  interval or count. Shortening promotes the first waiter that fits, in arrival
  order, and lengthening promotes nobody. An attendee-only change publishes
  `booking-modified` and leaves a waitlist entry whose slot has passed in place,
  which shows no pass ran. A no-op records nothing. Cancelling after a
  modification keeps the history. The rule 3 and 4 audit below runs with this
  gate. Then `mvn -B clean test`.
- **Green but wrong:** the agent skips the booking itself by comparing starts and
  ends by hand instead of removing it by id before `ConflictChecker`. Every
  behavior test passes, and rule 3 is broken.

### T3. The event log and the utilization report learn `booking-modified`

- **Delivers:** each modification writes one `booking-modified` line. Analytics
  parses it and folds it into the booking's existing record, so utilization
  counts the booking once, at its current length, on the day it now starts
  (clause 8). The event-type lists in `AGENTS.md` rule 6 and in the
  `ARCHITECTURE.md` section on the event log name four types (clause 9).
- **Touches:** `storage` (`EventLogFormat.toLine`, `EventLogFormat.eventTypes`)
  and `analytics` (`campusreserve_analytics/events.py` only), with new tests on
  both sides, plus the two docs, edited by me. `utilization.py` and `revenue.py`
  are not edited.
- **Depends on:** T1, for the event type.
- **Checkpoint:** `mvn -B -pl storage -am test` ends in `BUILD SUCCESS` with the
  storage block above 63, including a test that pins the exact line written for
  a modification. From `analytics/`, `../.venv/bin/pytest` reports more than 137
  passed, with tests for these cases: created then modified is counted once at
  the new length; moved to another day is counted on the new day; promoted then
  modified still counts as promoted; a modification without a booking is
  collected, not counted. The pytest fixture line is copied verbatim from the
  Java test's expected line.
- **Green but wrong:** Java writes one key spelling and Python reads another
  from its own hand-written fixture. Both gates pass, and the first real log
  breaks the reports. The verbatim copy is the check.

### T4. PATCH on the booking resource, with history on the read surface

- **Delivers:** `PATCH /bookings/{bookingId}`, owner only (clause 7).
  `GET /bookings/{bookingId}` and `GET /bookings` show the history (clause 5).
  The HTTP table in `ARCHITECTURE.md` has the new row (clause 9).
- **Touches:** `http-api` (`BookingsHandler`, `Router`, `JsonViews`), with a new
  endpoint test class that sends PATCH through `ApiTestServer.method`, plus
  `ARCHITECTURE.md`, edited by me. `ErrorMapping` is not edited.
- **Depends on:** T2.
- **Checkpoint:** `mvn -B -pl http-api -am test` ends in `BUILD SUCCESS` with the
  http-api block above 78, and the new tests cover these cases. The owner moves
  a booking and gets a 200, and a later GET shows the new times and one history
  entry. An attendee-only PATCH is a 200. A taken slot is a 409, and a later GET
  is unchanged. An administrator and another member both get a 403. A started
  booking is a 409. An empty request, `end` with `minutes`, and a different
  `roomId` are 400s. A no-op is a 200 with no history entry. Then
  `mvn -B clean test`.
- **Green but wrong:** the handler copies `show` and authorizes with
  `requireActingFor`, which lets an administrator through. Tests that only try
  the owner and another member stay green, which is why the administrator case
  is in the checkpoint.

**AGENTS.md rules 3 and 4.** T2's checkpoint catches both, through the audit
that runs with its gate rather than through its tests, since a correct second
overlap predicate passes every test. I read every line that
`git add -N . && git diff -U0 -- '*/src/main/*' | grep -E '^\+.*\.(isBefore|isAfter|compareTo)\('`
prints, and none may compare one interval's start or end with another's. And
`grep -rnE 'waitlist(\(\))?\.(save|remove)\(' --include='*.java' */src/main`
must still list only `BookingService.java`, while
`grep -rl WaitlistStore --include='*.java' */src/main` must still list only the
four files it lists today (`BookingService`, the `WaitlistStore` port,
`StoreRegistry`, `InMemoryWaitlistStore`), which catches a write through a
differently named variable.

**Out of scope, with reasons.**

- `billing`: "The price does not change." and "Billing is not part of this
  change request." Nothing in it reads a modification.
- `cli`: "Nothing in the CLI." This is also why `BookingService`'s constructor
  stays as it is (`CliContext.java:59` calls it).
- `accounts`: "Nothing in account management. Modification uses the token and
  ownership checks that already exist."
- `analytics/campusreserve_analytics/revenue.py`: "The revenue report is not
  part of this change." It keeps reading the same ledger, with no filter and no
  second fold.
- Every shipped test, in every module: `AGENTS.md` rule 7. New tests go in new
  files.

---

## Milestone 2: The guardrail

Pick one recipe from `guardrails/README.md`. Configure it, then prove it binds.

**The recipe.** Which one you picked, the exact paths or trees it covers, and
one line on what it cannot catch.

**The captured block.** Paste the excerpt of the agent's own turn showing what
you asked it to do, its tool call, and the block the guardrail returned. A bare
terminal paste with no agent turn around it does not show the boundary binding.

---

## Milestone 3: The run

Run exactly ONE task from your plan with the agent, under the guardrail, through
its gate. Any task whose dependencies are done qualifies, including the
smallest one.

**The takeover point.** Write this before you start the run. A concrete,
conditional statement of when you would stop the agent and write the code
yourself. "If something looks wrong" does not count. Name the trigger and name
what you would do.

**The evidence.** Name the task from Milestone 1. Paste the result line of its
gate command (run it without `-q` so the line is in the output) and the result
line of one `mvn -B clean test` run after the task was finished. The module
gate cannot see a cross-module compile break on a warm tree. A cold build can,
and so can CI.

**The diff.** The files the agent changed, and how each one maps to that task's
declared scope (plain `git diff` leaves out new files, but
`git add -N . && git diff --stat` includes them). Note anything it touched that
you did not expect, including any shipped test. Then quote the one line you
read hardest and say what you were checking for.

**The refuter.** What you gave it and what you asked it to do (paste the
brief). Then the one finding you settled, and how (the new failing test with
its red run, or the evidence that dismissed it). If nothing it reported held
up, say how you checked its strongest claim.
