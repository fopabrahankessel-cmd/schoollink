package com.schoollink.notification.internal;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * The notification module's jobs, for tests outside it (task 3.13): the SMS digest run and the
 * outbox dispatcher, which the test profile does not schedule, and what the scripted gateways
 * received. Import {@link Config}.
 */
public final class NotificationDriver {

	/** An SMS as the gateway received it. */
	public record Sms(String phone, String text) {
	}

	private final SmsDigest digest;

	private final OutboxDispatcher dispatcher;

	private final OutboxDispatcherTests.ScriptedSms sms;

	private final OutboxDispatcherTests.ScriptedPush push;

	NotificationDriver(SmsDigest digest, OutboxDispatcher dispatcher, OutboxDispatcherTests.ScriptedSms sms,
			OutboxDispatcherTests.ScriptedPush push) {
		this.digest = digest;
		this.dispatcher = dispatcher;
		this.sms = sms;
		this.push = push;
	}

	/** BR-22, D6: the digest runs due at the clock's time; returns the digest SMS created. */
	public int runDigest() {
		return digest.runDue();
	}

	/** Sends what is queued, as the poller would. */
	public int dispatch() {
		return dispatcher.dispatchDue();
	}

	/** Every SMS sent since the last {@link #reset}, in order. */
	public List<Sms> sentSms() {
		return sms.sent.stream().map(m -> new Sms(m.phone(), m.text())).toList();
	}

	public void reset() {
		sms.reset();
		push.reset();
	}

	@TestConfiguration(proxyBeanMethods = false)
	@Import(OutboxDispatcherTests.Providers.class)
	public static class Config {

		@Bean
		NotificationDriver notificationDriver(SmsDigest digest, OutboxDispatcher dispatcher,
				OutboxDispatcherTests.ScriptedSms sms, OutboxDispatcherTests.ScriptedPush push) {
			return new NotificationDriver(digest, dispatcher, sms, push);
		}

	}

}
