package com.schoollink.notification.internal;

import com.schoollink.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR-24: Twilio's delivery reports (its status callback, task 3.7, D32). No user signs in here: the
 * request is trusted only when its {@code X-Twilio-Signature} matches, over the callback URL we gave
 * Twilio, and it names our account; otherwise 403 and nothing changes. A report moves an SMS from
 * SENT to DELIVERED, or to FAILED with Twilio's error code; anything else is acknowledged and
 * ignored. NFR-9: the report carries the phone number, which is never logged or stored.
 */
@RestController
class SmsDeliveryController {

	private static final Logger log = LoggerFactory.getLogger(SmsDeliveryController.class);

	private final TwilioSettings twilio;

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	SmsDeliveryController(TwilioSettings twilio, NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.twilio = twilio;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@PostMapping("/api/v1/webhooks/sms/delivery")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delivery(HttpServletRequest request,
			@RequestHeader(name = "X-Twilio-Signature", required = false) String signature) {
		Map<String, String[]> params = request.getParameterMap();
		if (!TwilioSignature.valid(twilio.authToken(), twilio.statusCallback(), params, signature)
				|| !twilio.accountSid().equals(param(params, "AccountSid"))) {
			throw ApiException.forbidden();
		}
		String sid = param(params, "MessageSid");
		String status = param(params, "MessageStatus");
		if (sid == null || status == null) {
			return;
		}
		MapSqlParameterSource p = new MapSqlParameterSource("ref", sid).addValue("now",
				Timestamp.from(clock.instant()));
		int updated = switch (status) {
			case "delivered" -> jdbc.update("""
					UPDATE notification_outbox SET status = 'DELIVERED', delivered_at = :now
					WHERE channel = 'SMS' AND provider_ref = :ref AND status = 'SENT'""", p);
			case "undelivered", "failed" -> {
				String code = param(params, "ErrorCode");
				yield jdbc.update("""
						UPDATE notification_outbox SET status = 'FAILED', last_error = :error
						WHERE channel = 'SMS' AND provider_ref = :ref AND status = 'SENT'""",
						p.addValue("error", "twilio " + status + (code == null ? "" : " error " + code)));
			}
			// queued, sending, sent: on its way, nothing to record
			default -> -1;
		};
		if (updated == 0) {
			log.debug("SMS delivery report {} for {}: no SMS waiting for it", status, sid);
		}
	}

	private static String param(Map<String, String[]> params, String name) {
		String[] values = params.get(name);
		return values == null || values.length == 0 ? null : values[0];
	}

}
