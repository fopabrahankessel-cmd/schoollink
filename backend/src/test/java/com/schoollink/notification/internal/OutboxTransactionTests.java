package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.attendance.AttendanceConfirmed;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NFR-6: outbox rows are only ever written inside the transaction of the event that causes them.
 * No test transaction here, and nothing is written.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxTransactionTests {

	@Autowired
	ApplicationEventPublisher events;

	@Test
	void anEventPublishedOutsideATransactionIsRefused() {
		AttendanceConfirmed event = new AttendanceConfirmed(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				LocalDate.parse("2026-11-02"), UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-11-02T07:20:00Z"),
				List.of());

		assertThatThrownBy(() -> events.publishEvent(event)).isInstanceOf(IllegalTransactionStateException.class);
	}

}
