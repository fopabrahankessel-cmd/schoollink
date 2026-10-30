package com.schoollink.notification.internal;

import com.schoollink.notification.internal.Outbox.EventType;
import com.schoollink.notification.internal.Templates.Digest;
import com.schoollink.notification.internal.Templates.Push;
import com.schoollink.notification.internal.Templates.School;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Snapshot of every notification template in French and English (task 2.5, BR-20, BR-22, NFR-12).
 * A changed text must be changed here on purpose. Generated data only.
 */
class TemplatesTests {

	static final School SCHOOL = new School("Lycée Pilote (fictif)", ZoneId.of("Africa/Douala"));

	/** GSM 03.38 basic character set: anything else makes an SMS Unicode, 70 characters a part. */
	static final String GSM_7 = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡"
			+ "ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";

	final Templates templates = new Templates(messages());

	final JsonMapper json = JsonMapper.builder().build();

	@Test
	void absencePush() { // D2, BR-21
		JsonNode payload = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "periodNumber", 2, "periodStart", "08:25"));

		assertThat(templates.push(EventType.ABSENCE, "FR", payload))
			.isEqualTo(new Push("Absence : Awa", "Absence signalée pour Awa en période 2 (08:25), le 02/11."));
		assertThat(templates.push(EventType.ABSENCE, "EN", payload))
			.isEqualTo(new Push("Absence: Awa", "Awa was marked absent in period 2 (08:25) on 02/11."));
	}

	@Test
	void latenessPush() { // BR-7, BR-21
		JsonNode payload = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "arrivalTime", "07:48"));

		assertThat(templates.push(EventType.LATENESS, "FR", payload))
			.isEqualTo(new Push("Retard : Awa", "Retard signalé pour Awa : arrivée à 07:48, le 02/11."));
		assertThat(templates.push(EventType.LATENESS, "EN", payload))
			.isEqualTo(new Push("Late arrival: Awa", "Awa arrived late at 07:48 on 02/11."));
	}

	@Test
	void correctionPush() { // BR-14, D11
		JsonNode absence = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "kind", "ABSENCE", "periodNumber", 2));
		JsonNode lateness = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "kind", "LATENESS"));

		assertThat(templates.push(EventType.CORRECTION, "FR", absence)).isEqualTo(new Push("Correction : Awa",
				"L'absence signalée pour Awa en période 2, le 02/11, était une erreur : elle est annulée."));
		assertThat(templates.push(EventType.CORRECTION, "EN", absence)).isEqualTo(new Push("Correction: Awa",
				"The absence recorded for Awa in period 2 on 02/11 was a mistake and has been cancelled."));
		assertThat(templates.push(EventType.CORRECTION, "FR", lateness)).isEqualTo(new Push("Correction : Awa",
				"Le retard signalé pour Awa, le 02/11, était une erreur : il est annulé."));
		assertThat(templates.push(EventType.CORRECTION, "EN", lateness)).isEqualTo(new Push("Correction: Awa",
				"The late arrival recorded for Awa on 02/11 was a mistake and has been cancelled."));
	}

	@Test
	void correctionSms() { // BR-14, D11: same channel as the original
		JsonNode absence = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "kind", "ABSENCE", "periodNumber", 2));
		JsonNode lateness = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "kind", "LATENESS"));

		assertThat(templates.sms(EventType.CORRECTION, "FR", absence, SCHOOL)).isEqualTo(
				"SchoolLink : correction. L'absence signalée pour Awa en période 2 le 02/11 était une erreur. Lycée Pilote (fictif)");
		assertThat(templates.sms(EventType.CORRECTION, "EN", absence, SCHOOL)).isEqualTo(
				"SchoolLink: correction. The absence recorded for Awa in period 2 on 02/11 was a mistake. Lycée Pilote (fictif)");
		assertThat(templates.sms(EventType.CORRECTION, "FR", lateness, SCHOOL)).isEqualTo(
				"SchoolLink : correction. Le retard signalé pour Awa le 02/11 était une erreur. Lycée Pilote (fictif)");
		assertThat(templates.sms(EventType.CORRECTION, "EN", lateness, SCHOOL)).isEqualTo(
				"SchoolLink: correction. The late arrival recorded for Awa on 02/11 was a mistake. Lycée Pilote (fictif)");
	}

	@Test
	void summonsPushAndSms() { // BR-23, D12
		JsonNode payload = payload(Map.of("firstName", "Awa", "meetingDate", "2026-11-05", "meetingTime", "10:00"));

		assertThat(templates.push(EventType.SUMMONS, "FR", payload)).isEqualTo(new Push("Convocation : Awa",
				"Le surveillant général vous convoque à l'établissement concernant Awa, le 05/11 à 10:00."));
		assertThat(templates.push(EventType.SUMMONS, "EN", payload)).isEqualTo(new Push("Summons: Awa",
				"The Discipline Master asks you to come to the school about Awa on 05/11 at 10:00."));
		assertThat(templates.sms(EventType.SUMMONS, "FR", payload, SCHOOL)).isEqualTo(
				"SchoolLink : convocation de Lycée Pilote (fictif). Le surveillant général vous attend le 05/11 à 10:00 concernant Awa.");
		assertThat(templates.sms(EventType.SUMMONS, "EN", payload, SCHOOL)).isEqualTo(
				"SchoolLink: summons from Lycée Pilote (fictif). The Discipline Master expects you on 05/11 at 10:00 about Awa.");
	}

	@Test
	void invitationSms() { // D13, UC-P1
		// 23:30 UTC on the 28th is already the 29th in Douala: the date is the school's
		JsonNode payload = payload(Map.of("code", "K7M2-Q9XP", "expiresAt", "2026-11-28T23:30:00Z"));

		assertThat(templates.sms(EventType.INVITATION, "FR", payload, SCHOOL)).isEqualTo(
				"SchoolLink : votre code d'activation pour Lycée Pilote (fictif) est K7M2-Q9XP. Saisissez-le dans l'application SchoolLink avant le 29/11.");
		assertThat(templates.sms(EventType.INVITATION, "EN", payload, SCHOOL)).isEqualTo(
				"SchoolLink: your activation code for Lycée Pilote (fictif) is K7M2-Q9XP. Enter it in the SchoolLink app before 29/11.");
	}

	@Test
	void digestSms() { // BR-22, D6
		LocalDate day = LocalDate.parse("2026-10-28");
		Digest both = new Digest("Paul", day, new TreeSet<>(List.of(1, 2, 3, 5)), "07:48");
		Digest absent = new Digest("Paul", day, new TreeSet<>(List.of(1, 2, 3, 5)), null);
		Digest late = new Digest("Paul", day, new TreeSet<>(), "07:48");

		assertThat(templates.digest("FR", both, SCHOOL))
			.isEqualTo("SchoolLink : Paul - absence P1-P3, P5 et retard (07:48) le 28/10. Lycée Pilote (fictif)");
		assertThat(templates.digest("EN", both, SCHOOL))
			.isEqualTo("SchoolLink: Paul absent P1-P3, P5 and late (07:48) on 28/10. Lycée Pilote (fictif)");
		assertThat(templates.digest("FR", absent, SCHOOL))
			.isEqualTo("SchoolLink : Paul - absence P1-P3, P5 le 28/10. Lycée Pilote (fictif)");
		assertThat(templates.digest("EN", absent, SCHOOL))
			.isEqualTo("SchoolLink: Paul absent P1-P3, P5 on 28/10. Lycée Pilote (fictif)");
		assertThat(templates.digest("FR", late, SCHOOL))
			.isEqualTo("SchoolLink : Paul - retard (07:48) le 28/10. Lycée Pilote (fictif)");
		assertThat(templates.digest("EN", late, SCHOOL))
			.isEqualTo("SchoolLink: Paul late (07:48) on 28/10. Lycée Pilote (fictif)");
	}

	@Test
	void periodRanges() {
		assertThat(Templates.periods(new TreeSet<>(List.of(1)))).isEqualTo("P1");
		assertThat(Templates.periods(new TreeSet<>(List.of(1, 2)))).isEqualTo("P1-P2");
		assertThat(Templates.periods(new TreeSet<>(List.of(1, 3, 5)))).isEqualTo("P1, P3, P5");
		assertThat(Templates.periods(new TreeSet<>(List.of(1, 2, 3, 5, 6, 8)))).isEqualTo("P1-P3, P5-P6, P8");
	}

	@Test
	void everySmsFitsOneGsm7Message() { // BR-25: one SMS counts once against the monthly cap
		School longName = new School("Lycée Bilingue Fictif de la Ville Exemple", ZoneId.of("Africa/Douala"));
		assertThat(longName.name()).hasSizeGreaterThanOrEqualTo(40);
		String firstName = "Marie-Claire";
		JsonNode absence = payload(Map.of("firstName", firstName, "date", "2026-11-02", "kind", "ABSENCE", "periodNumber", 8));
		JsonNode lateness = payload(Map.of("firstName", firstName, "date", "2026-11-02", "kind", "LATENESS"));
		JsonNode summons = payload(Map.of("firstName", firstName, "meetingDate", "2026-11-05", "meetingTime", "10:00"));
		JsonNode invitation = payload(Map.of("code", "K7M2-Q9XP", "expiresAt", "2026-11-28T23:30:00Z"));
		Digest digest = new Digest(firstName, LocalDate.parse("2026-10-28"), new TreeSet<>(List.of(1, 3, 5, 7)), "07:48");

		for (String language : List.of("FR", "EN")) {
			List<String> all = List.of(templates.sms(EventType.CORRECTION, language, absence, longName),
					templates.sms(EventType.CORRECTION, language, lateness, longName),
					templates.sms(EventType.SUMMONS, language, summons, longName),
					templates.sms(EventType.INVITATION, language, invitation, longName),
					templates.digest(language, digest, longName));
			assertThat(all).allSatisfy(sms -> {
				assertThat(sms).as(sms).hasSizeLessThanOrEqualTo(160);
				assertThat(sms.chars().filter(c -> GSM_7.indexOf(c) < 0).mapToObj(Character::toString).toList())
					.as("not GSM-7 in: " + sms)
					.isEmpty();
			});
		}
	}

	@Test
	void absenceAndLatenessGoByDigestOnly() { // BR-22
		JsonNode payload = payload(Map.of("firstName", "Awa", "date", "2026-11-02", "arrivalTime", "07:48"));

		assertThatThrownBy(() -> templates.sms(EventType.LATENESS, "FR", payload, SCHOOL))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> templates.digest("FR", new Digest("Awa", LocalDate.parse("2026-11-02"), new TreeSet<>(), null),
				SCHOOL))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private JsonNode payload(Map<String, Object> fields) {
		return json.valueToTree(fields);
	}

	/** Configured like Spring Boot's message source (application.properties). */
	private static ResourceBundleMessageSource messages() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("messages");
		source.setDefaultEncoding("UTF-8");
		source.setFallbackToSystemLocale(false);
		source.setAlwaysUseMessageFormat(true);
		return source;
	}

}
