package com.schoollink.people.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

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
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-P5, D39: a parent reads who teaches and supervises their child, with phone numbers (NFR-8:
 * own children only). PARENT_A is the guardian of student A (6e A, block 1). Generated data only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ChildContactsTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	TestWorld world;

	/** A student of class A with no link to PARENT_A. */
	UUID otherChild;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
		otherChild = jdbc.queryForObject("""
				INSERT INTO student (school_id, class_id, matricule, first_name, last_name, sex, birth_year)
				VALUES (?, ?, 'TW-0201', 'Autre', 'Enfant', 'M', 2012) RETURNING id""", UUID.class, world.schoolX,
				world.classA);
		person(TEACHER_A, "Paul", "Enseignant", "+237690000010");
		person(DM_1, "Marie", "Surveillante", "+237690000020");
		person(ASSISTANT_DM_1, "Jean", "Adjoint", null);
		person(DM_2, "Autre", "Bloc", "+237690000030");
	}

	// Permissions (NFR-8)

	@TestFactory
	Stream<DynamicTest> onlyAParentReadsTheirChildsContacts() {
		return parentOnly("GET /me/children/{own}/contacts", w -> contacts(w.studentA), 200);
	}

	@TestFactory
	Stream<DynamicTest> aParentCannotReadAnotherFamilysChildContacts() {
		return parentOnly("GET /me/children/{other}/contacts", w -> contacts(otherChild), 404);
	}

	// Contents

	@Test
	void listsTheClassTeachersWithTheirSubjectsAndTheBlockDisciplineStaff() throws Exception {
		teaches(world.user(TEACHER_A), world.classA, "Physique");

		perform(contacts(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.className").value("6e A"))
			.andExpect(jsonPath("$.teachers.length()").value(1))
			.andExpect(jsonPath("$.teachers[0].name").value("Paul Enseignant"))
			.andExpect(jsonPath("$.teachers[0].subjects", contains("Maths", "Physique")))
			.andExpect(jsonPath("$.teachers[0].phone").value("+237690000010"))
			.andExpect(jsonPath("$.discipline.length()").value(2))
			.andExpect(jsonPath("$.discipline[0].name").value("Marie Surveillante"))
			.andExpect(jsonPath("$.discipline[0].role").value("DM"))
			.andExpect(jsonPath("$.discipline[0].phone").value("+237690000020"))
			.andExpect(jsonPath("$.discipline[1].name").value("Jean Adjoint"))
			.andExpect(jsonPath("$.discipline[1].role").value("ASSISTANT_DM"));
	}

	@Test
	void leavesOutOtherClassesOtherBlocksAndDisabledStaff() throws Exception {
		teaches(staff("Hors", "Classe", "+237690000040", "ACTIVE"), world.classB, "Anglais");
		teaches(staff("Compte", "Désactivé", "+237690000050", "DISABLED"), world.classA, "Histoire");

		perform(contacts(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.teachers[*].name", contains("Paul Enseignant")))
			.andExpect(jsonPath("$.discipline[*].name", contains("Marie Surveillante", "Jean Adjoint")));
	}

	@Test
	void aStaffMemberWithNoPhoneHasANullPhone() throws Exception {
		perform(contacts(world.studentA)).andExpect(jsonPath("$.discipline[1].phone").doesNotExist());
	}

	@Test
	void aClassWhoseBlockHasNoDmHasAnEmptyDisciplineList() throws Exception {
		jdbc.update("DELETE FROM role_assignment WHERE user_id IN (?, ?)", world.user(DM_1), world.user(ASSISTANT_DM_1));

		perform(contacts(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.discipline.length()").value(0))
			.andExpect(jsonPath("$.teachers.length()").value(1));
	}

	@Test
	void aTeacherWhoIsAlsoAnAssistantDmIsInBothListsOnceEach() throws Exception {
		// the schema allows one DM per block, so the second role is the assistant's
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role, block_id) VALUES (?, ?, 'ASSISTANT_DM', ?)",
				world.schoolX, world.user(TEACHER_A), world.block1);
		teaches(world.user(TEACHER_A), world.classA, "Physique");

		perform(contacts(world.studentA)).andExpect(status().isOk())
			.andExpect(jsonPath("$.teachers[*].name", contains("Paul Enseignant")))
			.andExpect(jsonPath("$.discipline[*].name", contains("Marie Surveillante", "Jean Adjoint", "Paul Enseignant")));
	}

	private static MockHttpServletRequestBuilder contacts(UUID child) {
		return get("/api/v1/me/children/{id}/contacts", child);
	}

	private void person(Actor actor, String first, String last, String phone) {
		jdbc.update("UPDATE app_user SET first_name = ?, last_name = ?, phone = ? WHERE id = ?", first, last, phone,
				world.user(actor));
	}

	private UUID staff(String first, String last, String phone, String status) {
		return jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, status, password_hash)
				VALUES (?, ?, ?, ?, ?, 'not-a-real-hash') RETURNING id""", UUID.class, world.schoolX, first, last, phone,
				status);
	}

	private void teaches(UUID teacher, UUID schoolClass, String subject) {
		jdbc.update("INSERT INTO teaching_assignment (school_id, teacher_id, class_id, subject) VALUES (?, ?, ?, ?)",
				world.schoolX, teacher, schoolClass, subject);
	}

	private Stream<DynamicTest> parentOnly(String name,
			Function<TestWorld, MockHttpServletRequestBuilder> request, int parentStatus) {
		return PermissionMatrix.row(name, request::apply)
			.expect(parentStatus, PARENT_A)
			.expect(403, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, DM_2, COUNSELLOR_X, RESEARCHER, ADMIN_Y)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(PARENT_A)));
	}

}
