package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static com.schoollink.testsupport.Actor.DM_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Twilio's signed delivery reports, {@code POST /api/v1/webhooks/sms/delivery} (task 3.7, BR-24,
 * D32, NFR-9). Nobody signs in: the Twilio signature is the only credential, so the forbidden cases
 * are the unsigned and wrongly signed reports. Account, token, SIDs and numbers are fake.
 */
@SpringBootTest(properties = { "schoollink.sms.twilio.account-sid=" + SmsDeliveryTests.ACCOUNT,
		"schoollink.sms.twilio.auth-token=" + SmsDeliveryTests.TOKEN, "schoollink.sms.twilio.from=SchoolLink",
		"schoollink.sms.twilio.status-callback=" + SmsDeliveryTests.CALLBACK,
		"schoollink.sms.twilio.base-url=http://localhost:1" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class SmsDeliveryTests {

	static final String ACCOUNT = "ACfake00000000000000000000000000";

	static final String TOKEN = "fake-auth-token";

	static final String CALLBACK = "https://api.example.org/api/v1/webhooks/sms/delivery";

	static final String PHONE = "+237690000202";

	static final String SID = "SMfake0000000000000000000000000001";

	static final Instant T0 = Instant.parse("2026-11-02T07:40:00Z");

	static final Instant T1 = T0.plusSeconds(20);

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID row;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		UUID guardian = jdbc.queryForObject("""
				INSERT INTO guardian (school_id, name, phone, language) VALUES (?, 'Parent Fictif', ?, 'EN') RETURNING id""",
				UUID.class, world.schoolX, PHONE);
		row = jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				    payload, status, attempts, sent_at, provider_ref)
				VALUES (?, ?, ?, 'SUMMONS', ?, 'SMS', 'EN', '{}'::jsonb, 'SENT', 1, ?, ?) RETURNING id""", UUID.class,
				world.schoolX, guardian, world.studentA, UUID.randomUUID(), Timestamp.from(T0), SID);
		clock.set(T1);
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	@Test
	void aDeliveredReportMarksTheSmsDelivered() throws Exception { // BR-24
		signed(report("delivered")).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "DELIVERED").containsEntry("delivered_at", Timestamp.from(T1))
			.containsEntry("last_error", null);
	}

	@Test
	void anUndeliveredReportFailsTheSmsWithTwiliosCode() throws Exception { // BR-24, NFR-9: the code, no phone
		Map<String, String> report = report("undelivered");
		report.put("ErrorCode", "30003");

		signed(report).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "FAILED").containsEntry("delivered_at", null)
			.containsEntry("last_error", "twilio undelivered error 30003");
	}

	@Test
	void aFailedReportWithoutACodeFailsTheSms() throws Exception { // BR-24
		signed(report("failed")).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "FAILED").containsEntry("last_error", "twilio failed");
	}

	@Test
	void aReportOnTheWayChangesNothing() throws Exception { // BR-24: only the final states count
		signed(report("sent")).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "SENT").containsEntry("delivered_at", null);
	}

	@Test
	void aLateFailureDoesNotUndoADelivery() throws Exception { // BR-24: reports can come out of order
		signed(report("delivered")).andExpect(status().isNoContent());
		signed(report("failed")).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "DELIVERED");
	}

	@Test
	void aReportForAnUnknownSmsIsAcknowledged(CapturedOutput output) throws Exception { // BR-24: Twilio would retry a 4xx
		Map<String, String> report = report("delivered");
		report.put("MessageSid", "SMfake0000000000000000000000000999");

		signed(report).andExpect(status().isNoContent());

		assertThat(state()).containsEntry("status", "SENT");
		assertThat(output).doesNotContain("690000202");
	}

	@Test
	void anOtherChannelsRefIsNotTouched() throws Exception { // BR-24: SMS reports only move SMS
		jdbc.update("UPDATE notification_outbox SET provider_ref = NULL WHERE id = ?", row);
		UUID push = jdbc.queryForObject("""
				INSERT INTO notification_outbox (school_id, guardian_id, student_id, event_type, event_id, channel, language,
				    payload, status, attempts, sent_at, provider_ref)
				SELECT school_id, guardian_id, student_id, 'SUMMONS', gen_random_uuid(), 'PUSH', 'EN', '{}'::jsonb, 'SENT', 1, sent_at, ?
				FROM notification_outbox WHERE id = ? RETURNING id""", UUID.class, SID, row);

		signed(report("delivered")).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT status FROM notification_outbox WHERE id = ?", String.class, push))
			.isEqualTo("SENT");
	}

	@Test
	void anUnsignedReportIsForbidden() throws Exception { // D32
		mockMvc.perform(form(report("delivered"))).andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void aWronglySignedReportIsForbidden() throws Exception { // D32
		Map<String, String> report = report("delivered");
		String signature = TwilioSignature.of("another-token", CALLBACK, values(report));

		mockMvc.perform(form(report).header("X-Twilio-Signature", signature)).andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void aReportSignedForAnotherUrlIsForbidden() throws Exception { // D32: the signature covers the URL
		Map<String, String> report = report("delivered");
		String signature = TwilioSignature.of(TOKEN, "https://evil.example.org/hook", values(report));

		mockMvc.perform(form(report).header("X-Twilio-Signature", signature)).andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void aTamperedReportIsForbidden() throws Exception { // D32: every parameter is signed
		Map<String, String> report = report("failed");
		String signature = TwilioSignature.of(TOKEN, CALLBACK, values(report));
		report.put("MessageStatus", "delivered");

		mockMvc.perform(form(report).header("X-Twilio-Signature", signature)).andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void aReportForAnotherAccountIsForbidden() throws Exception { // D32
		Map<String, String> report = report("delivered");
		report.put("AccountSid", "ACfake00000000000000000000000999");

		signed(report).andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	@Test
	void aSignedInUserCannotReportWithoutTheSignature() throws Exception { // D32, NFR-8: a JWT is no credential here
		mockMvc.perform(form(report("delivered")).header("Authorization", "Bearer " + world.token(DM_1)))
			.andExpect(status().isForbidden());

		assertThat(state()).containsEntry("status", "SENT");
	}

	private Map<String, String> report(String status) {
		Map<String, String> report = new LinkedHashMap<>();
		report.put("AccountSid", ACCOUNT);
		report.put("ApiVersion", "2010-04-01");
		report.put("From", "SchoolLink");
		report.put("MessageSid", SID);
		report.put("MessageStatus", status);
		report.put("SmsSid", SID);
		report.put("SmsStatus", status);
		report.put("To", PHONE);
		return report;
	}

	private ResultActions signed(Map<String, String> report) throws Exception {
		return mockMvc.perform(form(report).header("X-Twilio-Signature", TwilioSignature.of(TOKEN, CALLBACK, values(report))));
	}

	private static MockHttpServletRequestBuilder form(Map<String, String> report) {
		MockHttpServletRequestBuilder request = post("/api/v1/webhooks/sms/delivery")
			.contentType(MediaType.APPLICATION_FORM_URLENCODED);
		report.forEach(request::param);
		return request;
	}

	private static Map<String, String[]> values(Map<String, String> report) {
		Map<String, String[]> values = new LinkedHashMap<>();
		report.forEach((name, value) -> values.put(name, new String[] { value }));
		return values;
	}

	private Map<String, Object> state() {
		return jdbc.queryForMap("SELECT status, delivered_at, last_error FROM notification_outbox WHERE id = ?", row);
	}

}
