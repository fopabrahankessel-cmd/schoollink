package com.schoollink.notification.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the dispatcher every few seconds (NFR-5: 95% of pushes sent within a minute of the sync).
 * Off in tests ({@code schoollink.outbox.dispatch-enabled=false}), which call the dispatcher
 * themselves.
 */
@Component
@ConditionalOnProperty(name = "schoollink.outbox.dispatch-enabled", havingValue = "true", matchIfMissing = true)
class OutboxPoller {

	private final OutboxDispatcher dispatcher;

	OutboxPoller(OutboxDispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}

	@Scheduled(fixedDelayString = "${schoollink.outbox.poll-interval:5s}")
	void poll() {
		// drain: a full batch means more may be waiting
		while (dispatcher.dispatchDue() >= OutboxDispatcher.BATCH) {
			// next batch
		}
	}

}
