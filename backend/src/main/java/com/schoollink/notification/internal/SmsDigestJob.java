package com.schoollink.notification.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BR-22, NFR-5: looks every minute for a school whose SMS digest time has come, so a digest is
 * queued within a minute of it. Off in tests ({@code schoollink.digest.enabled=false}), which call
 * {@link SmsDigest#runDue} themselves.
 */
@Component
@ConditionalOnProperty(name = "schoollink.digest.enabled", havingValue = "true", matchIfMissing = true)
class SmsDigestJob {

	private final SmsDigest digest;

	SmsDigestJob(SmsDigest digest) {
		this.digest = digest;
	}

	@Scheduled(cron = "0 * * * * *")
	void run() {
		digest.runDue();
	}

}
