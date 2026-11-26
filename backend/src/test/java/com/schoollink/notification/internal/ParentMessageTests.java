package com.schoollink.notification.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestClock;
import com.schoollink.testsupport.TestWorld;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static com.schoollink.testsupport.Actor.ADMIN_X;
import static com.schoollink.testsupport.Actor.ADMIN_Y;
import static com.schoollink.testsupport.Actor.ANONYMOUS;
import static com.schoollink.testsupport.Actor.ASSISTANT_DM_1;
import static com.schoollink.testsupport.Actor.COUNSELLOR_X;
import static com.schoollink.testsupport.Actor.DM_1;
import static com.schoollink.testsupport.Actor.DM_2;
import static com.schoollink.testsupport.Actor.PARENT_A;
import static com.schoollink.testsupport.Actor.RESEARCHER;
import static com.schoollink.testsupport.Actor.TEACHER_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-D4, UC-A5, D38: a DM writes to the guardians of a student or a class of their blocks, an
 * administrator to the whole school or a class. The preview gives the counts and the SMS cost
 * before sending; sending writes the message, one outbox row per guardian and channel, and an audit
 * row in one transaction (BR-20, NFR-6, NFR-11). Generated data only.
 *
 * <pre>
 * Class A (block 1, DM_1): SA (PARENT_A, app), A2 and A3 (G2, app, parent of both),
 *                          A2 also G3 (no app), A3 also G4 (no app)
 * Class B (block 2, DM_2): SB (G5, no app)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestClock.Config.class })
@Transactional
class ParentMessageTests {

	static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

	static final String TEXT = "Apportez la tenue de sport demain.";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	@Autowired
	JsonMapper json;

	@Autowired
	TestClock clock;

	TestWorld world;

	UUID studentA2;

	UUID studentA3;

	UUID studentB;

	@BeforeEach
	void createWorld() {
		clock.set(NOW);
		world = TestWorld.create(jdbc, encoder);
		jdbc.update("UPDATE school SET sms_monthly_cap = 10 WHERE id = ?", world.schoolX);
		studentA2 = student(world.classA, "TW-0002", "Awa");
		studentA3 = student(world.classA, "TW-0003", "Bruno");
		studentB = student(world.classB, "TW-0004", "Chloé");
		UUID g2 = guardian("Tuteur Deux", "+237690000002", true);
		link(studentA2, g2);
		link(studentA3, g2);
		link(studentA2, guardian("Tuteur Trois", "+237690000003", false));
		link(studentA3, guardian("Tuteur Quatre", "+237690000004", false));
		link(studentB, guardian("Tuteur Cinq", "+237690000005", false));
	}

	@AfterEach
	void resetClock() {
		clock.reset();
	}

	// Permissions (NFR-8)

	@TestFactory
	Stream<DynamicTest> messagePreviewIsForTheDmOnly() {
		return PermissionMatrix.row("POST /messages/preview", w -> dmPreview(Map.of("classId", w.classA)))
			.expect(200, DM_1)
			.expect(404, DM_2)
			.expect(403, ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> messageSendIsForTheDmOnly() {
		return PermissionMatrix.row("POST /messages", w -> dmSend(Map.of("classId", w.classA)))
			.expect(201, DM_1)
			.expect(404, DM_2)
			.expect(403, ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> announcementPreviewIsForTheAdminOnly() {
		return PermissionMatrix.row("POST /announcements/preview", w -> adminPreview(Map.of()))
			.expect(200, ADMIN_X, ADMIN_Y)
			.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> announcementSendIsForTheAdminOnly() {
		return PermissionMatrix.row("POST /announcements", w -> adminSend(Map.of("classId", w.classA)))
			.expect(201, ADMIN_X)
			.expect(404, ADMIN_Y)
			.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> classesAreForTheAuthors() {
		return PermissionMatrix.row("GET /messages/classes", w -> get("/api/v1/messages/classes"))
			.expect(200, DM_1, DM_2, ADMIN_X, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@Test
	void theParentsOfAStudentWhoLeftGetNoNewMessage() throws Exception { // D43
		jdbc.update("UPDATE student SET left_on = '2026-10-30' WHERE id = ?", world.studentA);

		perform(DM_1, dmSend(Map.of("studentId", world.studentA))).andExpect(status().isNotFound());
		perform(DM_1, dmPreview(Map.of("studentId", world.studentA))).andExpect(status().isNotFound());
	}

	@Test
	void outOfScopeTargetsAreNotFound() throws Exception {
		perform(DM_1, dmPreview(Map.of("classId", world.classB))).andExpect(status().isNotFound());
		perform(DM_1, dmPreview(Map.of("studentId", studentB))).andExpect(status().isNotFound());
		perform(DM_1, dmSend(Map.of("classId", world.classB))).andExpect(status().isNotFound());
		perform(DM_1, dmSend(Map.of("studentId", studentB))).andExpect(status().isNotFound());
		perform(ADMIN_X, adminSend(Map.of("classId", world.classC))).andExpect(status().isNotFound());
		perform(ADMIN_X, adminPreview(Map.of("classId", world.classC))).andExpect(status().isNotFound());
		assertNothingWritten();
	}

	// Preview

	@Test
	void dmPreviewOfAClassCountsEachGuardianOnce() throws Exception {
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", true)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.recipients").value(4))
			.andExpect(jsonPath("$.byApp").value(2))
			// D49: the SMS goes to every guardian, app users too
			.andExpect(jsonPath("$.bySms").value(4))
			.andExpect(jsonPath("$.unreached").value(0))
			.andExpect(jsonPath("$.smsLeft").value(10))
			.andExpect(jsonPath("$.overCap").value(false));
	}

	@Test
	void withoutSmsTheGuardiansWithoutAppAreUnreached() throws Exception {
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", false))).andExpect(status().isOk())
			.andExpect(jsonPath("$.bySms").value(0))
			.andExpect(jsonPath("$.unreached").value(2))
			.andExpect(jsonPath("$.estimatedCost").value(0))
			.andExpect(jsonPath("$.smsSample").isEmpty());
	}

	@Test
	void previewGivesUnitsCostAndSample() throws Exception {
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", true)))
			.andExpect(jsonPath("$.smsUnits").value(1))
			.andExpect(jsonPath("$.smsUnicode").value(false))
			.andExpect(jsonPath("$.estimatedCost").value(4 * 181))
			.andExpect(jsonPath("$.smsSample").value("SchoolLink - SG Lycée Fictif X : " + TEXT));
	}

	@Test
	void specialLettersMakeTheSmsUnicodeUnlessPlain() throws Exception {
		String text = "Fête de l'école : apportez un gâteau.";
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", true, "body", text)))
			.andExpect(jsonPath("$.smsUnicode").value(true));
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", true, "body", text, "plainSms", true)))
			.andExpect(jsonPath("$.smsUnicode").value(false))
			.andExpect(jsonPath("$.smsSample").value("SchoolLink - SG Lycee Fictif X : Fete de l'ecole : apportez un gateau."));
	}

	// Sending

	@Test
	void sendWritesMessageOutboxRowsAndAuditInOneGo() throws Exception {
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("classId", world.classA, "sms", true))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(id.toString()));

		assertThat(jdbc.queryForMap("SELECT kind, audience, class_id, body, sms FROM parent_message WHERE id = ?", id))
			.containsEntry("kind", "DM_MESSAGE")
			.containsEntry("audience", "CLASS")
			.containsEntry("class_id", world.classA)
			.containsEntry("body", TEXT)
			.containsEntry("sms", true);
		assertThat(jdbc.queryForList("""
				SELECT channel || ' ' || status FROM notification_outbox
				WHERE event_type = 'DM_MESSAGE' AND event_id = ? AND student_id IS NULL ORDER BY 1""", String.class, id))
			.containsExactly("PUSH QUEUED", "PUSH QUEUED", "SMS QUEUED", "SMS QUEUED", "SMS QUEUED", "SMS QUEUED");
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM audit_log WHERE entity = 'parent_message' AND entity_id = ? AND action = 'CREATE'""",
				Integer.class, id.toString())).isOne();
	}

	@Test
	void aStudentMessageReachesThatStudentsGuardiansOnly() throws Exception {
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("studentId", studentA2, "sms", true))).andExpect(status().isCreated());

		assertThat(jdbc.queryForList("""
				SELECT channel FROM notification_outbox WHERE event_id = ? AND student_id = ? ORDER BY 1""", String.class,
				id, studentA2)).containsExactly("PUSH", "SMS", "SMS");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_id = ?", Integer.class, id))
			.isEqualTo(3);
	}

	@Test
	void sameIdTwiceSendsOnce() throws Exception {
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("classId", world.classA))).andExpect(status().isCreated());
		perform(DM_1, dmSend(id, Map.of("classId", world.classA))).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id.toString()));

		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_message", Integer.class)).isOne();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE event_id = ?", Integer.class, id))
			.isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity = 'parent_message'", Integer.class))
			.isOne();
	}

	@Test
	void sameIdWithOtherTextIsRefused() throws Exception {
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("classId", world.classA))).andExpect(status().isCreated());
		perform(DM_1, dmSend(id, Map.of("classId", world.classA, "body", "Autre texte")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("messages.id_reused"));
	}

	@Test
	void smsLeftCountsQueuedSms() throws Exception {
		jdbc.update("UPDATE school SET sms_monthly_cap = 5 WHERE id = ?", world.schoolX);
		perform(DM_1, dmSend(Map.of("classId", world.classA, "sms", true))).andExpect(status().isCreated());

		// the four SMS of the first message are queued, not sent yet: one left, the class needs four
		perform(DM_1, dmPreview(Map.of("classId", world.classA, "sms", true)))
			.andExpect(jsonPath("$.smsLeft").value(1))
			.andExpect(jsonPath("$.overCap").value(true));
		perform(DM_1, dmSend(Map.of("classId", world.classA, "sms", true))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("messages.sms_over_cap"));
		perform(DM_1, dmSend(Map.of("classId", world.classA, "sms", false))).andExpect(status().isCreated());
	}

	@Test
	void blankOrLongBodyIsRefused() throws Exception {
		perform(DM_1, dmSend(Map.of("classId", world.classA, "body", "   "))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("messages.body_required"));
		perform(DM_1, dmSend(Map.of("classId", world.classA, "body", "a".repeat(481))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("messages.body_too_long"));
		assertNothingWritten();
	}

	@Test
	void dmNeedsExactlyOneTarget() throws Exception {
		perform(DM_1, dmSend(Map.of())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("messages.target_required"));
		perform(DM_1, dmSend(Map.of("classId", world.classA, "studentId", studentA2)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("messages.target_required"));
	}

	@Test
	void anIdIsRequired() throws Exception {
		Map<String, Object> body = new HashMap<>(Map.of("classId", world.classA, "body", TEXT, "sms", false));
		perform(DM_1, post("/api/v1/messages").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("messages.id_required"));
	}

	@Test
	void noReachableGuardianIsRefused() throws Exception {
		perform(DM_2, dmSend(Map.of("classId", world.classB, "sms", false))).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("messages.no_recipient"));
		assertNothingWritten();
	}

	@Test
	void adminAnnouncesToTheWholeSchool() throws Exception {
		UUID id = UUID.randomUUID();
		perform(ADMIN_X, adminSend(id, Map.of("sms", true))).andExpect(status().isCreated());

		assertThat(jdbc.queryForMap("SELECT kind, audience FROM parent_message WHERE id = ?", id))
			.containsEntry("kind", "ANNOUNCEMENT")
			.containsEntry("audience", "SCHOOL");
		// PARENT_A, G2 by push; all five by SMS (D49): each guardian once per channel
		assertThat(jdbc.queryForList("""
				SELECT channel FROM notification_outbox WHERE event_type = 'ANNOUNCEMENT' AND event_id = ? ORDER BY 1""",
				String.class, id)).containsExactly("PUSH", "PUSH", "SMS", "SMS", "SMS", "SMS", "SMS");
	}

	@Test
	void classesListsTheAuthorsScope() throws Exception {
		perform(DM_1, get("/api/v1/messages/classes")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].classId").value(world.classA.toString()))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			.andExpect(jsonPath("$[0].students").value(3));
		perform(ADMIN_X, get("/api/v1/messages/classes")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].className").value(startsWith("5e B")));
	}

	// Delivery and the sent lists (Task 2)

	@Test
	void theInboxShowsADmMessageWithItsFullText() throws Exception { // UC-P4
		String text = "Réunion des parents vendredi à 15h. " + "Merci de venir. ".repeat(20);
		perform(DM_1, dmSend(Map.of("studentId", world.studentA, "body", text))).andExpect(status().isCreated());

		perform(PARENT_A, get("/api/v1/me/notifications")).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].eventType").value("DM_MESSAGE"))
			.andExpect(jsonPath("$[0].studentId").value(world.studentA.toString()))
			.andExpect(jsonPath("$[0].title").value("Message du surveillant général"))
			.andExpect(jsonPath("$[0].body").value(text.strip()));
	}

	@Test
	void theDmSeesTheirSentMessagesWithDelivery() throws Exception {
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("classId", world.classA, "sms", true))).andExpect(status().isCreated());
		perform(DM_1, get("/api/v1/messages")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(id.toString()))
			.andExpect(jsonPath("$[0].kind").value("DM_MESSAGE"))
			.andExpect(jsonPath("$[0].audience").value("CLASS"))
			.andExpect(jsonPath("$[0].className").value("6e A"))
			.andExpect(jsonPath("$[0].body").value(TEXT))
			.andExpect(jsonPath("$[0].sms").value(true))
			.andExpect(jsonPath("$[0].authorName").value("Test DM_1"))
			.andExpect(jsonPath("$[0].delivery.recipients").value(4))
			.andExpect(jsonPath("$[0].delivery.pending").value(4))
			.andExpect(jsonPath("$[0].delivery.opened").value(0));

		UUID push = jdbc.queryForObject(
				"SELECT id FROM notification_outbox WHERE event_id = ? AND channel = 'PUSH' LIMIT 1", UUID.class, id);
		UUID sms = jdbc.queryForObject("""
				SELECT o.id FROM notification_outbox o JOIN guardian g ON g.id = o.guardian_id
				WHERE o.event_id = ? AND o.channel = 'SMS' AND g.user_id IS NULL LIMIT 1""", UUID.class, id);
		jdbc.update("UPDATE notification_outbox SET status = 'OPENED', sent_at = now(), opened_at = now() WHERE id = ?",
				push);
		jdbc.update("UPDATE notification_outbox SET status = 'FAILED' WHERE id = ?", sms);
		// D49: one count per guardian, by the best of their push and SMS
		perform(DM_1, get("/api/v1/messages")).andExpect(jsonPath("$[0].delivery.recipients").value(4))
			.andExpect(jsonPath("$[0].delivery.opened").value(1))
			.andExpect(jsonPath("$[0].delivery.failed").value(1))
			.andExpect(jsonPath("$[0].delivery.pending").value(2));
	}

	@Test
	void aFailedPushIsNotAFailureWhileTheSmsCanStillArrive() throws Exception { // D49
		UUID id = UUID.randomUUID();
		perform(DM_1, dmSend(id, Map.of("studentId", studentA2, "sms", true))).andExpect(status().isCreated());
		jdbc.update("UPDATE notification_outbox SET status = 'FAILED' WHERE event_id = ? AND channel = 'PUSH'", id);
		jdbc.update("UPDATE notification_outbox SET status = 'SENT', sent_at = now() WHERE event_id = ? AND channel = 'SMS'",
				id);

		perform(DM_1, get("/api/v1/messages")).andExpect(jsonPath("$[0].delivery.recipients").value(2))
			.andExpect(jsonPath("$[0].delivery.sent").value(2))
			.andExpect(jsonPath("$[0].delivery.failed").value(0));
	}

	@Test
	void aStudentMessageNamesTheStudent() throws Exception {
		perform(DM_1, dmSend(Map.of("studentId", studentA2))).andExpect(status().isCreated());
		perform(DM_1, get("/api/v1/messages")).andExpect(jsonPath("$[0].audience").value("STUDENT"))
			.andExpect(jsonPath("$[0].studentId").value(studentA2.toString()))
			.andExpect(jsonPath("$[0].studentName").value("Fictif Awa"))
			.andExpect(jsonPath("$[0].className").value("6e A"));
	}

	@Test
	void aStudentsMessagesAreFilteredByStudent() throws Exception {
		// D41: the student page's Messages tab
		perform(DM_1, dmSend(Map.of("studentId", studentA2))).andExpect(status().isCreated());
		perform(DM_1, dmSend(Map.of("classId", world.classA))).andExpect(status().isCreated());
		perform(DM_1, get("/api/v1/messages").param("studentId", studentA2.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].audience").value("STUDENT"))
			.andExpect(jsonPath("$[0].studentId").value(studentA2.toString()));
	}

	@Test
	void anotherDmsStudentListsNothing() throws Exception {
		// NFR-8: the filter only narrows the caller's own messages
		perform(DM_1, dmSend(Map.of("studentId", studentA2))).andExpect(status().isCreated());
		perform(DM_2, get("/api/v1/messages").param("studentId", studentA2.toString())).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aMalformedStudentIdIsRefused() throws Exception {
		perform(DM_1, get("/api/v1/messages").param("studentId", "abc")).andExpect(status().isBadRequest());
	}

	@Test
	void aDmDoesNotSeeAnotherDmsMessages() throws Exception {
		perform(DM_1, dmSend(Map.of("classId", world.classA))).andExpect(status().isCreated());
		perform(DM_2, get("/api/v1/messages")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void theAdminSeesTheSchoolsAnnouncements() throws Exception {
		perform(ADMIN_X, adminSend(Map.of())).andExpect(status().isCreated());
		perform(ADMIN_X, get("/api/v1/announcements")).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].audience").value("SCHOOL"))
			.andExpect(jsonPath("$[0].delivery.recipients").value(2));
		perform(ADMIN_Y, get("/api/v1/announcements")).andExpect(jsonPath("$.length()").value(0));
	}

	@TestFactory
	Stream<DynamicTest> theDmsSentListIsForDms() {
		return PermissionMatrix.row("GET /messages", w -> get("/api/v1/messages"))
			.expect(200, DM_1, DM_2)
			.expect(403, ADMIN_X, ADMIN_Y, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> theAnnouncementListIsForAdmins() {
		return PermissionMatrix.row("GET /announcements", w -> get("/api/v1/announcements"))
			.expect(200, ADMIN_X, ADMIN_Y)
			.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	// Helpers

	private void assertNothingWritten() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM parent_message", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM notification_outbox WHERE event_type IN ('DM_MESSAGE', 'ANNOUNCEMENT')""",
				Integer.class)).isZero();
	}

	private MockHttpServletRequestBuilder dmPreview(Map<String, Object> fields) {
		return request("/api/v1/messages/preview", null, fields);
	}

	private MockHttpServletRequestBuilder dmSend(Map<String, Object> fields) {
		return request("/api/v1/messages", UUID.randomUUID(), fields);
	}

	private MockHttpServletRequestBuilder dmSend(UUID id, Map<String, Object> fields) {
		return request("/api/v1/messages", id, fields);
	}

	private MockHttpServletRequestBuilder adminPreview(Map<String, Object> fields) {
		return request("/api/v1/announcements/preview", null, fields);
	}

	private MockHttpServletRequestBuilder adminSend(Map<String, Object> fields) {
		return request("/api/v1/announcements", UUID.randomUUID(), fields);
	}

	private MockHttpServletRequestBuilder adminSend(UUID id, Map<String, Object> fields) {
		return request("/api/v1/announcements", id, fields);
	}

	/** The body defaults to {@link #TEXT}, no SMS, no plain SMS. */
	private MockHttpServletRequestBuilder request(String path, UUID id, Map<String, Object> fields) {
		Map<String, Object> body = new HashMap<>(Map.of("body", TEXT, "sms", false, "plainSms", false));
		if (id != null) {
			body.put("id", id);
		}
		body.putAll(fields);
		return post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
	}

	private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private UUID student(UUID classId, String matricule, String firstName) {
		return jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, ?, ?, 'Fictif', 'F', 2012) RETURNING id""", UUID.class, world.schoolX, classId, matricule,
				firstName);
	}

	/** {@code app}: the guardian has an app account (push, D7). */
	private UUID guardian(String name, String phone, boolean app) {
		UUID user = app ? jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, email, password_hash)
				VALUES (?, 'Parent', ?, ?, NULL) RETURNING id""", UUID.class, world.schoolX, name,
				UUID.randomUUID() + "@example.test") : null;
		return jdbc.queryForObject("""
				INSERT INTO guardian (school_id, user_id, name, phone) VALUES (?, ?, ?, ?) RETURNING id""", UUID.class,
				world.schoolX, user, name, phone);
	}

	private void link(UUID student, UUID guardian) {
		jdbc.update("""
				INSERT INTO student_guardian (school_id, student_id, guardian_id, relation, is_primary)
				VALUES (?, ?, ?, 'MOTHER', false)""", world.schoolX, student, guardian);
	}

}
