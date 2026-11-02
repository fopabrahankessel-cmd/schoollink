package com.schoollink.detection.internal;

import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.detection.RuleType;
import com.schoollink.lateness.LatenessRecorded;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BR-17: the rules run after each locked session and each lateness, once the record is committed,
 * so that a detection problem never loses a teacher's or a gate's record. Each student is
 * evaluated in a transaction of their own; a failure is logged and left to the nightly run.
 * <p>
 * The evaluation runs on a small executor of its own, not on the request thread: after the commit
 * the request still holds its connection until the listeners return, and a second connection taken
 * there deadlocks the pool once every connection is held by a request waiting for another
 * (task 3.14, NFR-3: about a hundred phones syncing at once).
 */
@Component
class DetectionListeners {

	private static final Logger log = LoggerFactory.getLogger(DetectionListeners.class);

	private static final Set<RuleType> AFTER_SESSION = EnumSet.of(RuleType.FULL_DAY_ABSENCE,
			RuleType.UNJUSTIFIED_ABSENCES);

	/** A morning's backlog (§11: 2 × 40 classes × 8 periods); beyond it, the nightly run catches up. */
	private static final int QUEUE = 20_000;

	private final Detection detection;

	private final TransactionTemplate newTransaction;

	private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

	/** Evaluations submitted and not yet finished. */
	private final AtomicInteger pending = new AtomicInteger();

	DetectionListeners(Detection detection, PlatformTransactionManager transactions) {
		this.detection = detection;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(QUEUE);
		executor.setThreadNamePrefix("detection-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
	}

	/** D2: a full day is complete when its last period is locked; absences also add to the term's count. */
	@TransactionalEventListener
	void on(AttendanceConfirmed event) {
		Set<UUID> students = event.absentStudentIds();
		if (!students.isEmpty()) {
			submit(event.schoolId(), event.date(), () -> students
				.forEach(student -> run(event.schoolId(), student, event.date(), AFTER_SESSION)));
		}
	}

	/** D3: a lateness may complete a streak. */
	@TransactionalEventListener
	void on(LatenessRecorded event) {
		submit(event.schoolId(), event.date(), () -> run(event.schoolId(), event.studentId(), event.date(),
				EnumSet.of(RuleType.CONSECUTIVE_LATENESS)));
	}

	/** True once every evaluation submitted so far has finished, waiting at most {@code timeout}. */
	boolean awaitIdle(Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (pending.get() > 0) {
			if (System.nanoTime() > deadline) {
				return false;
			}
			Thread.sleep(10);
		}
		return true;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

	private void submit(UUID school, LocalDate date, Runnable evaluation) {
		pending.incrementAndGet();
		try {
			executor.execute(() -> {
				try {
					evaluation.run();
				}
				finally {
					pending.decrementAndGet();
				}
			});
		}
		catch (TaskRejectedException ex) {
			pending.decrementAndGet();
			log.warn("Detection queue full for school {} on {}; left to the nightly run", school, date);
		}
	}

	private void run(UUID school, UUID student, LocalDate date, Set<RuleType> types) {
		try {
			newTransaction.executeWithoutResult(status -> detection.evaluate(school, student, date, types));
		}
		catch (RuntimeException ex) {
			// ids only (NFR-9); the nightly run evaluates the student again
			log.warn("Detection failed for student {} of school {} on {}", student, school, date, ex);
		}
	}

}
