package com.schoollink.notification.internal;

import com.schoollink.notification.ActivationSms;
import com.schoollink.notification.internal.SmsProvider.SmsMessage;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** {@link ActivationSms} straight through the {@link SmsProvider}, with no retry: the parent asks again. */
@Component
class DirectActivationSms implements ActivationSms {

	private static final Logger log = LoggerFactory.getLogger(DirectActivationSms.class);

	private final SmsProvider sms;

	private final Templates templates;

	DirectActivationSms(SmsProvider sms, Templates templates) {
		this.sms = sms;
		this.templates = templates;
	}

	@Override
	public boolean send(UUID schoolId, UUID messageId, String phone, String language, String code, Duration validity) {
		try {
			return sms.send(new SmsMessage(messageId, phone, templates.otp(language, code, validity))) instanceof SendResult.Sent;
		}
		catch (RuntimeException ex) {
			// NFR-9: the exception's message may hold the phone; keep its type only
			log.warn("Activation SMS {}: provider failed with {}", messageId, ex.getClass().getName());
			return false;
		}
	}

}
