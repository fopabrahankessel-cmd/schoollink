package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Outbox.EventType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Push and SMS texts in the guardian's language (BR-20, NFR-12), from the {@code notification.*}
 * keys of {@code messages_fr/en.properties} and an outbox row's payload. Dates are written dd/MM
 * in both languages, times HH:mm, as stored.
 *
 * <p>Payload fields, by event type:
 * <ul>
 * <li>ABSENCE: firstName, date, periodNumber, periodStart</li>
 * <li>LATENESS: firstName, date, arrivalTime</li>
 * <li>CORRECTION: firstName, date, kind (ABSENCE or LATENESS), periodNumber for an absence</li>
 * <li>SUMMONS: firstName, meetingDate, meetingTime</li>
 * <li>INVITATION: code, expiresAt (an instant)</li>
 * </ul>
 * Absence and lateness never go by SMS one by one: guardians without the app get the daily
 * {@link #digest} (BR-22, D6).
 */
@Component
class Templates {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

	/** What an SMS says about the school: its name, and its zone for dates. */
	record School(String name, ZoneId zone) {
	}

	record Push(String title, String body) {
	}

	/**
	 * One SMS digest for one student and day (BR-22, D6): the absent periods, the arrival time if
	 * late, or both. {@code absentPeriods} may be empty when the student was only late.
	 */
	record Digest(String firstName, LocalDate date, SortedSet<Integer> absentPeriods, String arrivalTime) {
	}

	private final MessageSource messages;

	Templates(MessageSource messages) {
		this.messages = messages;
	}

	Push push(EventType type, String language, JsonNode payload) {
		String firstName = text(payload, "firstName");
		return switch (type) {
			case ABSENCE -> push(language, "absence", firstName, text(payload, "periodNumber"),
					text(payload, "periodStart"), day(payload, "date"));
			case LATENESS -> push(language, "lateness", firstName, text(payload, "arrivalTime"), day(payload, "date"));
			case CORRECTION -> new Push(message(language, "notification.correction.push.title", firstName),
					message(language, correctionKey(payload, "push.body"), firstName, periodOf(payload),
							day(payload, "date")));
			case SUMMONS -> push(language, "summons", firstName, day(payload, "meetingDate"),
					text(payload, "meetingTime"));
			default -> throw new IllegalArgumentException("No push template for " + type);
		};
	}

	/** The SMS sent at once: corrections (D11), summons (BR-23, D12) and invitations (D13). */
	String sms(EventType type, String language, JsonNode payload, School school) {
		return switch (type) {
			case CORRECTION -> message(language, correctionKey(payload, "sms"), text(payload, "firstName"),
					periodOf(payload), day(payload, "date"), school.name());
			case SUMMONS -> message(language, "notification.summons.sms", text(payload, "firstName"),
					day(payload, "meetingDate"), text(payload, "meetingTime"), school.name());
			case INVITATION -> message(language, "notification.invitation.sms", text(payload, "code"), school.name(),
					Instant.parse(text(payload, "expiresAt")).atZone(school.zone()).format(DAY));
			default -> throw new IllegalArgumentException("No immediate SMS for " + type + ": it goes in the digest");
		};
	}

	/** BR-22: "SchoolLink: Paul absent P1-P3 and late (07:48) on 28/10. Collège X." */
	String digest(String language, Digest digest, School school) {
		boolean absent = !digest.absentPeriods().isEmpty();
		boolean late = digest.arrivalTime() != null;
		if (!absent && !late) {
			throw new IllegalArgumentException("D6: no digest without an absence or a lateness");
		}
		String key = absent && late ? "both" : absent ? "absent" : "late";
		return message(language, "notification.digest." + key + ".sms", digest.firstName(),
				periods(digest.absentPeriods()), digest.date().format(DAY), school.name(), digest.arrivalTime());
	}

	/** Consecutive periods as a range: 1, 2, 3, 5 gives "P1-P3, P5". */
	static String periods(SortedSet<Integer> numbers) {
		List<String> parts = new ArrayList<>();
		Integer start = null;
		Integer previous = null;
		for (Integer n : numbers) {
			if (start != null && n == previous + 1) {
				previous = n;
				continue;
			}
			if (start != null) {
				parts.add(range(start, previous));
			}
			start = n;
			previous = n;
		}
		if (start != null) {
			parts.add(range(start, previous));
		}
		return String.join(", ", parts);
	}

	private static String range(int start, int end) {
		return start == end ? "P" + start : "P" + start + "-P" + end;
	}

	private Push push(String language, String event, Object... args) {
		return new Push(message(language, "notification." + event + ".push.title", args),
				message(language, "notification." + event + ".push.body", args));
	}

	private static String correctionKey(JsonNode payload, String suffix) {
		String kind = text(payload, "kind");
		return switch (kind) {
			case "ABSENCE" -> "notification.correction.absence." + suffix;
			case "LATENESS" -> "notification.correction.lateness." + suffix;
			default -> throw new IllegalArgumentException("Unknown correction kind " + kind);
		};
	}

	/** A lateness correction has no period; its template does not use the argument. */
	private static String periodOf(JsonNode payload) {
		return payload.has("periodNumber") ? text(payload, "periodNumber") : "";
	}

	private String message(String language, String key, Object... args) {
		return messages.getMessage(key, args, "EN".equals(language) ? Locale.ENGLISH : Locale.FRENCH);
	}

	private static String day(JsonNode payload, String field) {
		return LocalDate.parse(text(payload, field)).format(DAY);
	}

	private static String text(JsonNode payload, String field) {
		JsonNode value = payload.get(field);
		if (value == null || value.isNull()) {
			throw new IllegalArgumentException("Missing payload field " + field);
		}
		return value.asString();
	}

}
