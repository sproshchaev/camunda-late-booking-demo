package booking;

import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.process.test.api.CamundaProcessTest;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.grpc.Status;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@CamundaProcessTest
class BookingProcessTest {

    private final Supplier supplier = new Supplier();
    private CamundaClient client;
    private CamundaProcessTestContext context;

    @BeforeEach
    void deployProcess() {
        client.newDeployResourceCommand().addResourceFromClasspath("booking.bpmn").send().join();
    }

    @Test
    void confirmationBeforeTimer() {
        var attempt = startBooking();
        supplier.confirm(attempt.processKey());
        completeReservation(attempt);

        assertThat(attempt.process()).isCompleted().hasCompletedElements("booked");
        assertEquals(SupplierState.CONFIRMED, supplier.state(attempt.processKey()));
        printResult("before-timer", "booked", attempt, "OK");
    }

    @Test
    void lateConfirmationLeavesBookingAtSupplier() {
        var attempt = startBooking();
        waitForTimeout(attempt);
        supplier.confirm(attempt.processKey());

        var rejected = assertThrows(ClientStatusException.class, () -> completeReservation(attempt));
        assertEquals(Status.Code.NOT_FOUND, rejected.getStatusCode());
        assertThat(attempt.process()).isCompleted().hasCompletedElements("timedOut");
        assertEquals(SupplierState.CONFIRMED, supplier.state(attempt.processKey()));
        printResult("late-baseline", "timedOut", attempt, rejected.getStatusCode().name());
    }

    @Test
    void knownTimeoutRoutesLateConfirmationToSupplierCancellation() {
        var attempt = startBooking();
        waitForTimeout(attempt);

        var recordedTimeouts = Set.of(attempt.processKey());
        supplier.confirm(attempt.processKey());
        assertEquals(SupplierState.CONFIRMED, supplier.state(attempt.processKey()));
        handleLateConfirmation(attempt.processKey(), recordedTimeouts);
        handleLateConfirmation(attempt.processKey(), recordedTimeouts);

        assertThat(attempt.process()).isCompleted().hasCompletedElements("timedOut");
        assertEquals(SupplierState.CANCELED, supplier.state(attempt.processKey()));
        assertEquals(1, supplier.cancellations);
        printResult("late-with-cancel", "timedOut", attempt, "SKIPPED");
    }

    private BookingAttempt startBooking() {
        var process = client.newCreateInstanceCommand()
                .bpmnProcessId("booking").latestVersion().send().join();
        assertThat(process).hasActiveElements("reserve");

        var jobs = client.newActivateJobsCommand().jobType("reserve")
                .maxJobsToActivate(1).timeout(Duration.ofMinutes(15)).send().join().getJobs();
        assertEquals(1, jobs.size());
        var job = jobs.getFirst();
        assertEquals(process.getProcessInstanceKey(), job.getProcessInstanceKey());
        supplier.request(process.getProcessInstanceKey());
        return new BookingAttempt(process, job.getKey());
    }

    private void waitForTimeout(BookingAttempt attempt) {
        context.increaseTime(Duration.ofMinutes(6));
        assertThat(attempt.process()).isCompleted()
                .hasTerminatedElements("reserve")
                .hasCompletedElements("timedOut");
        assertEquals(SupplierState.PENDING, supplier.state(attempt.processKey()));
    }

    private void completeReservation(BookingAttempt attempt) {
        client.newCompleteJobCommand(attempt.jobKey()).useGrpc().send().join();
    }

    private void handleLateConfirmation(long bookingId, Set<Long> recordedTimeouts) {
        if (recordedTimeouts.contains(bookingId)) {
            supplier.cancel(bookingId);
        }
    }

    private void printResult(String scenario, String endEvent, BookingAttempt attempt, String completeJob) {
        System.out.printf("RESULT %s\t%s\t%s\t%s\t%d%n",
                scenario, endEvent, supplier.state(attempt.processKey()), completeJob, supplier.cancellations);
    }

    private record BookingAttempt(ProcessInstanceEvent process, long jobKey) {
        private long processKey() {
            return process.getProcessInstanceKey();
        }
    }

    private enum SupplierState {
        PENDING, CONFIRMED, CANCELED
    }

    private static final class Supplier {
        private final Map<Long, SupplierState> bookings = new HashMap<>();
        private int cancellations;

        private void request(long bookingId) {
            if (bookings.putIfAbsent(bookingId, SupplierState.PENDING) != null) {
                throw new IllegalStateException("Booking already requested: " + bookingId);
            }
        }

        private void confirm(long bookingId) {
            if (!bookings.replace(bookingId, SupplierState.PENDING, SupplierState.CONFIRMED)) {
                throw new IllegalStateException("No pending booking: " + bookingId);
            }
        }

        private void cancel(long bookingId) {
            if (bookings.replace(bookingId, SupplierState.CONFIRMED, SupplierState.CANCELED)) {
                cancellations++;
            } else if (state(bookingId) != SupplierState.CANCELED) {
                throw new IllegalStateException("No confirmed booking: " + bookingId);
            }
        }

        private SupplierState state(long bookingId) {
            return bookings.get(bookingId);
        }
    }
}
