package com.schoollink.school.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.testsupport.Actor;
import com.schoollink.testsupport.PermissionMatrix;
import com.schoollink.testsupport.TestWorld;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-A7, D36: the school letterhead printed on generated documents. The admin writes it; the admin
 * and the DM read it (NFR-8). Generated data only; the logo bytes are a made-up PNG.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LetterheadTests {

	static final String LETTERHEAD = "/api/v1/school/letterhead";

	static final String LOGO = LETTERHEAD + "/logo";

	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R' };

	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F' };

	static final String BODY = """
			{"layout": "BILINGUAL",
			 "french": [{"text": "RÉPUBLIQUE DU CAMEROUN", "bold": false, "size": "NORMAL"},
			            {"text": "LYCÉE FICTIF X", "bold": true, "size": "LARGE"}],
			 "english": [{"text": "REPUBLIC OF CAMEROON", "bold": false, "size": "SMALL"}]}""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JwtEncoder encoder;

	TestWorld world;

	@BeforeEach
	void createWorld() {
		world = TestWorld.create(jdbc, encoder);
	}

	@TestFactory
	Stream<DynamicTest> theAdminAndTheDmReadTheLetterhead() {
		return PermissionMatrix.row("GET letterhead", w -> get(LETTERHEAD))
			.expect(200, ADMIN_X, DM_1, DM_2, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> theAdminAndTheDmReadTheLogoOfTheirOwnSchool() {
		jdbc.update("""
				INSERT INTO school_letterhead (school_id, layout, french, english, logo, logo_type, logo_at, updated_at, updated_by)
				VALUES (?, 'BILINGUAL', '[]', '[]', ?, 'image/png', now(), now(), ?)""", world.schoolX, PNG,
				world.user(ADMIN_X));
		return PermissionMatrix.row("GET logo", w -> get(LOGO))
			.expect(200, ADMIN_X, DM_1, DM_2)
			.expect(404, ADMIN_Y)
			.expect(403, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminWritesTheLetterhead() {
		return PermissionMatrix.row("PUT letterhead",
				w -> put(LETTERHEAD).contentType(MediaType.APPLICATION_JSON).content(BODY))
			.expect(200, ADMIN_X, ADMIN_Y)
			.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
			.expect(401, ANONYMOUS)
			.tests(mockMvc, world);
	}

	@TestFactory
	Stream<DynamicTest> onlyTheAdminChangesTheLogo() {
		return Stream.concat(
				PermissionMatrix.row("PUT logo", w -> multipart(HttpMethod.PUT, LOGO).file(png("logo.png", PNG)))
					.expect(200, ADMIN_X, ADMIN_Y)
					.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
					.expect(401, ANONYMOUS)
					.tests(mockMvc, world),
				PermissionMatrix.row("DELETE logo", w -> delete(LOGO))
					.expect(200, ADMIN_X, ADMIN_Y)
					.expect(403, DM_1, DM_2, TEACHER_A, ASSISTANT_DM_1, COUNSELLOR_X, PARENT_A, RESEARCHER)
					.expect(401, ANONYMOUS)
					.tests(mockMvc, world));
	}

	@Test
	void anUnsetLetterheadIsTheSchoolNameOnly() throws Exception {
		read(DM_1).andExpect(status().isOk())
			.andExpect(jsonPath("$.schoolName").value("Lycée Fictif X"))
			.andExpect(jsonPath("$.configured").value(false))
			.andExpect(jsonPath("$.layout").value("BILINGUAL"))
			.andExpect(jsonPath("$.french", empty()))
			.andExpect(jsonPath("$.english", empty()))
			.andExpect(jsonPath("$.hasLogo").value(false))
			.andExpect(jsonPath("$.logoVersion").doesNotExist());
		as(DM_1, get(LOGO)).andExpect(status().isNotFound());
	}

	@Test
	void theAdminSavesLinesAndLayoutAndTheDmReadsThem() throws Exception {
		save(BODY).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));

		read(DM_1).andExpect(jsonPath("$.layout").value("BILINGUAL"))
			.andExpect(jsonPath("$.french[*].text", contains("RÉPUBLIQUE DU CAMEROUN", "LYCÉE FICTIF X")))
			.andExpect(jsonPath("$.french[1].bold").value(true))
			.andExpect(jsonPath("$.french[1].size").value("LARGE"))
			.andExpect(jsonPath("$.english[0].size").value("SMALL"));

		save("""
				{"layout": "ENGLISH", "french": [], "english": [{"text": "FICTIONAL SCHOOL", "bold": true, "size": "NORMAL"}]}""")
			.andExpect(status().isOk());
		read(ADMIN_X).andExpect(jsonPath("$.layout").value("ENGLISH"))
			.andExpect(jsonPath("$.french", empty()))
			.andExpect(jsonPath("$.english[*].text", contains("FICTIONAL SCHOOL")));
	}

	@Test
	void refusesTooManyOrTooLongLines() throws Exception {
		String line = "{\"text\": \"Ligne\", \"bold\": false, \"size\": \"NORMAL\"}";
		String thirteen = String.join(",", java.util.Collections.nCopies(13, line));
		save("{\"layout\": \"FRENCH\", \"french\": [" + thirteen + "], \"english\": []}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("letterhead.too_many_lines"));

		String long101 = "x".repeat(101);
		save("{\"layout\": \"FRENCH\", \"french\": [{\"text\": \"" + long101 + "\", \"bold\": false, \"size\": \"NORMAL\"}],"
				+ " \"english\": []}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.size"));
		save("{\"layout\": \"FRENCH\", \"french\": [{\"text\": \"  \", \"bold\": false, \"size\": \"NORMAL\"}], \"english\": []}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation.size"));
	}

	@Test
	void refusesLogosThatAreNotPngOrJpegOrTooLarge() throws Exception {
		upload(png("logo.png", "not a png".getBytes())).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("letterhead.logo_type"))
			.andExpect(jsonPath("$.message").value("Le logo doit être une image PNG ou JPEG."));

		byte[] big = Arrays.copyOf(PNG, 1_048_577);
		upload(png("logo.png", big)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("letterhead.logo_too_large"));

		upload(png("logo.png", Arrays.copyOf(PNG, 1_048_576))).andExpect(status().isOk());
	}

	@Test
	void aLogoIsServedWithItsTypeAndCanBeRemoved() throws Exception {
		upload(new MockMultipartFile("file", "logo.jpg", "image/jpeg", JPEG)).andExpect(status().isOk())
			.andExpect(jsonPath("$.hasLogo").value(true))
			.andExpect(jsonPath("$.logoVersion").isNotEmpty())
			.andExpect(jsonPath("$.configured").value(true));

		as(DM_1, get(LOGO)).andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(content().bytes(JPEG));

		as(ADMIN_X, delete(LOGO)).andExpect(status().isOk()).andExpect(jsonPath("$.hasLogo").value(false));
		as(DM_1, get(LOGO)).andExpect(status().isNotFound());
	}

	@Test
	void changesAreAudited() throws Exception { // NFR-11
		save(BODY).andExpect(status().isOk());
		upload(png("logo.png", PNG)).andExpect(status().isOk());

		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT action, entity_id, after::text AS after FROM audit_log
				WHERE school_id = ? AND entity = 'school_letterhead' ORDER BY id""", world.schoolX);
		assertThat(rows).hasSize(2);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("action")).isEqualTo("UPDATE");
			assertThat(row.get("entity_id")).isEqualTo(world.schoolX.toString());
		});
		assertThat((String) rows.get(0).get("after")).contains("LYCÉE FICTIF X");
		assertThat((String) rows.get(1).get("after")).contains("\"type\": \"image/png\"", "\"bytes\": 16");
	}

	@Test
	void eachSchoolHasItsOwnLetterhead() throws Exception { // NFR-8
		save(BODY).andExpect(status().isOk());

		read(ADMIN_Y).andExpect(jsonPath("$.schoolName").value("Fictional High School Y"))
			.andExpect(jsonPath("$.configured").value(false));
	}

	private ResultActions read(Actor actor) throws Exception {
		return as(actor, get(LETTERHEAD));
	}

	private ResultActions save(String body) throws Exception {
		return as(ADMIN_X, put(LETTERHEAD).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions upload(MockMultipartFile file) throws Exception {
		return as(ADMIN_X, multipart(HttpMethod.PUT, LOGO).file(file));
	}

	private ResultActions as(Actor actor,
			AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header("Authorization", "Bearer " + world.token(actor)));
	}

	private static MockMultipartFile png(String name, byte[] bytes) {
		return new MockMultipartFile("file", name, "image/png", bytes);
	}

}
