package com.schoollink.detection.internal;

import com.schoollink.attendance.AttendanceConfirmed;
import com.schoollink.detection.RuleType;
import com.schoollink.lateness.LatenessRecorded;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BR-17: the rules run after each locked session and each lateness, once the record is committed,
 * so that a detection problem never loses a teacher's or a gate's record. Each student is
 * evaluated in a transaction of their own; a failure is logged and left to the nightly run.
 */
@Component
class DetectionListeners {

	private static final Logger log = LoggerFactory.getLogger(DetectionListeners.class);

	private static final Set<RuleType> AFTER_SESSION = EnumSet.of(RuleType.FULL_DAY_ABSENCE,
			RuleType.UNJUSTIFIED_ABSENCES);

	private final Detection detection;

	private final TransactionTemplate newTransaction;

	DetectionListeners(Detection detection, PlatformTransactionManager transactions) {
		this.detection = detection;
		this.newTransaction = new TransactionTemplate(transactions);
		this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** D2: a full day is complete when its last period is locked; absences also add to the term's count. */
	@TransactionalEventListener
	void on(AttendanceConfirmed event) {
		for (UUID student : event.absentStudentIds()) {
			run(event.schoolId(), student, event.date(), AFTER_SESSION);
		}
	}

	/** D3: a lateness may complete a streak. */
	@TransactionalEventListener
	void on(LatenessRecorded event) {
		run(event.schoolId(), event.studentId(), event.date(), EnumSet.of(RuleType.CONSECUTIVE_LATENESS));
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
