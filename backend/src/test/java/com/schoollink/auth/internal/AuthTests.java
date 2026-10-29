package com.schoollink.auth.internal;

import com.schoollink.TestcontainersConfiguration;
import com.schoollink.shared.ratelimit.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff login, JWT access tokens and refresh rotation (NFR-7, D15, D16). Fake data only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthTests {

	static final String PHONE = "+237699200001";

	static final String SHARED_PHONE = "+237699200002";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder passwords;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	JsonMapper json;

	@Autowired
	RateLimiter limiter;

	UUID schoolA;

	UUID schoolB;

	UUID adminA;

	@BeforeEach
	void createAccounts() {
		limiter.clear(); // the limiter is shared by every test in the Spring context
		schoolA = school("Collège Test A");
		schoolB = school("Collège Test B");
		// D15: the same phone in two schools, with different passwords
		adminA = user(schoolA, PHONE, "admin@a.test", "Secret-A1!", "FR", "ACTIVE", "ADMIN");
		user(schoolB, PHONE, null, "Secret-B1!", "EN", "ACTIVE", "ADMIN");
		// the same phone in two schools, with the same password
		user(schoolA, SHARED_PHONE, null, "Same-Pass1", "FR", "ACTIVE", "COUNSELLOR");
		user(schoolB, SHARED_PHONE, null, "Same-Pass1", "FR", "ACTIVE", "COUNSELLOR");
		user(schoolA, "+237699200003", null, null, "FR", "ACTIVE", "PARENT");
		user(schoolA, "+237699200004", null, "Secret-D1!", "FR", "DISABLED", "ADMIN");
	}

	@Test
	void loginWithPhoneReturnsTokensAndTheUser() throws Exception {
		login(null, PHONE, "Secret-A1!").andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user.id").value(adminA.toString()))
			.andExpect(jsonPath("$.user.schoolId").value(schoolA.toString()))
			.andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
			.andExpect(jsonPath("$.user.language").value("FR"));
	}

	@Test
	void accessTokenWorksOnProtectedEndpoints() throws Exception {
		String access = tokens(null, PHONE, "Secret-A1!").get("accessToken").asString();

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(adminA.toString()))
			.andExpect(jsonPath("$.schoolId").value(schoolA.toString()));
	}

	@Test
	void phoneCanBeTypedInLocalFormatAndEmailInAnyCase() throws Exception {
		login(null, "699 20 00 01", "Secret-A1!").andExpect(status().isOk());
		login(null, " ADMIN@A.test ", "Secret-A1!").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(adminA.toString()));
	}

	@Test
	void wrongPasswordUnknownLoginAndForeignPhoneGiveTheSameError() throws Exception {
		for (String[] attempt : List.of(new String[] { PHONE, "wrong" }, new String[] { "+237699999999", "Secret-A1!" },
				new String[] { "+33612345678", "Secret-A1!" })) {
			login(null, attempt[0], attempt[1]).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("auth.invalid_credentials"))
				.andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect."));
		}
	}

	@Test
	void samePhoneInTwoSchoolsResolvesByPassword() throws Exception { // D15, D16
		login(null, PHONE, "Secret-B1!").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.schoolId").value(schoolB.toString()));
	}

	@Test
	void schoolFromThePickerRestrictsTheLogin() throws Exception { // D16
		login(schoolA, PHONE, "Secret-B1!").andExpect(status().isUnauthorized());
		login(schoolB, PHONE, "Secret-B1!").andExpect(status().isOk());
	}

	@Test
	void ambiguousLoginAsksForTheSchool() throws Exception { // D16
		login(null, SHARED_PHONE, "Same-Pass1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("auth.school_required"))
			.andExpect(jsonPath("$.field").value("schoolId"));

		login(schoolB, SHARED_PHONE, "Same-Pass1").andExpect(status().isOk())
			.andExpect(jsonPath("$.user.schoolId").value(schoolB.toString()));
	}

	@Test
	void parentsAndDisabledUsersCannotUsePasswordLogin() throws Exception {
		login(null, "+237699200003", "").andExpect(status().isBadRequest()); // blank password: validation
		login(null, "+237699200003", "anything").andExpect(status().isUnauthorized());
		login(null, "+237699200004", "Secret-D1!").andExpect(status().isUnauthorized());
	}

	@Test
	void missingTokenIsRejectedInTheErrorShape() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("Accept-Language", "en"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", "Bearer"))
			.andExpect(jsonPath("$.code").value("auth.unauthorized"))
			.andExpect(jsonPath("$.message").value("Please sign in."));
	}

	@Test
	void expiredAccessTokenIsRejected() throws Exception {
		Instant past = Instant.now().minus(Duration.ofHours(1));
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
				JwtClaimsSet.builder()
					.issuer("schoollink")
					.subject(adminA.toString())
					.issuedAt(past)
					.expiresAt(past.plus(Duration.ofMinutes(15)))
					.claim("roles", List.of("ADMIN"))
					.build()))
			.getTokenValue();

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("auth.unauthorized"));
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		var otherKey = new SecretKeySpec("another-secret-another-secret-123456".getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		Instant now = Instant.now();
		String forged = NimbusJwtEncoder.withSecretKey(otherKey)
			.algorithm(MacAlgorithm.HS256)
			.build()
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
					JwtClaimsSet.builder()
						.issuer("schoollink")
						.subject(adminA.toString())
						.issuedAt(now)
						.expiresAt(now.plusSeconds(600))
						.claim("roles", List.of("ADMIN"))
						.build()))
			.getTokenValue();

		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenLastsFifteenMinutesAndCarriesSchoolRolesAndLanguage() throws Exception { // NFR-7
		Jwt jwt = jwtDecoder.decode(tokens(null, PHONE, "Secret-A1!").get("accessToken").asString());

		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		assertThat(jwt.getClaimAsString("school")).isEqualTo(schoolA.toString());
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(jwt.getClaimAsString("lang")).isEqualTo("FR");
	}

	@Test
	void refreshTokenLastsThirtyDaysAndIsStoredOnlyAsAHash() throws Exception { // NFR-7
		String refresh = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();

		assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?", Integer.class, refresh))
			.isZero();
		assertThat(jdbc.queryForObject(
				"SELECT expires_at - created_at = interval '30 days' FROM refresh_token WHERE token_hash = ?",
				Boolean.class, RefreshTokens.hash(refresh)))
			.isTrue();
	}

	@Test
	void refreshRotatesAndTheOldTokenIsRejected() throws Exception { // NFR-7
		JsonNode first = tokens(null, PHONE, "Secret-A1!");
		String oldRefresh = first.get("refreshToken").asString();

		JsonNode second = json.readTree(refresh(oldRefresh).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(adminA.toString()))
			.andReturn()
			.getResponse()
			.getContentAsString());

		assertThat(second.get("refreshToken").asString()).isNotEqualTo(oldRefresh);
		refresh(oldRefresh).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("auth.invalid_refresh_token"));
	}

	@Test
	void reusingAnOldRefreshTokenRevokesTheWholeFamily() throws Exception {
		String r1 = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();
		String r2 = json.readTree(refresh(r1).andReturn().getResponse().getContentAsString())
			.get("refreshToken")
			.asString();

		refresh(r1).andExpect(status().isUnauthorized()); // reuse: likely stolen
		refresh(r2).andExpect(status().isUnauthorized()); // so the newest token is revoked too
	}

	@Test
	void logoutEndsTheSessionOfTheRefreshToken() throws Exception {
		String r1 = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();
		String r2 = json.readTree(refresh(r1).andReturn().getResponse().getContentAsString())
			.get("refreshToken")
			.asString();
		String otherDevice = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();

		logout(r1).andExpect(status().isNoContent()); // an older token of the session is enough

		refresh(r2).andExpect(status().isUnauthorized());
		refresh(otherDevice).andExpect(status().isOk()); // other sessions go on
	}

	@Test
	void logoutAnswersTheSameForAnyToken() throws Exception { // reveals nothing
		logout("not-a-token").andExpect(status().isNoContent());
		mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void expiredRefreshTokenIsRejected() throws Exception {
		String refresh = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();
		jdbc.update("UPDATE refresh_token SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
				RefreshTokens.hash(refresh));

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshFailsOnceTheUserIsDisabled() throws Exception {
		String refresh = tokens(null, PHONE, "Secret-A1!").get("refreshToken").asString();
		jdbc.update("UPDATE app_user SET status = 'DISABLED' WHERE id = ?", adminA);

		refresh(refresh).andExpect(status().isUnauthorized());
	}

	@Test
	void signedInUsersGetErrorsInTheirOwnLanguage() throws Exception { // NFR-12
		String access = tokens(schoolB, PHONE, "Secret-B1!").get("accessToken").asString(); // EN user

		mockMvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + access)
			.header("Accept-Language", "fr"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("The requested resource was not found."));
	}

	@Test
	void schoolPickerIsPublicAndListsNamesOnly() throws Exception { // D16
		mockMvc.perform(get("/api/v1/auth/schools"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name == 'Collège Test A')].id").value(schoolA.toString()))
			.andExpect(jsonPath("$[0].language").doesNotExist());
	}

	private ResultActions login(UUID school, String login, String password) throws Exception {
		var body = json.createObjectNode().put("login", login).put("password", password);
		if (school != null) {
			body.put("schoolId", school.toString());
		}
		return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.header("Accept-Language", "fr")
			.content(body.toString()));
	}

	private JsonNode tokens(UUID school, String login, String password) throws Exception {
		return json.readTree(login(school, login, password).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	private ResultActions logout(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
			.content(json.createObjectNode().put("refreshToken", refreshToken).toString()));
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content(json.createObjectNode().put("refreshToken", refreshToken).toString()));
	}

	private UUID school(String name) {
		return jdbc.queryForObject("INSERT INTO school (name) VALUES (?) RETURNING id", UUID.class, name);
	}

	private UUID user(UUID school, String phone, String email, String password, String language, String status,
			String role) {
		UUID id = jdbc.queryForObject("""
				INSERT INTO app_user (school_id, first_name, last_name, phone, email, password_hash, language, status)
				VALUES (?, 'Agent', 'Test', ?, ?, ?, ?, ?) RETURNING id""", UUID.class, school, phone, email,
				password == null ? null : passwords.encode(password), language, status);
		jdbc.update("INSERT INTO role_assignment (school_id, user_id, role) VALUES (?, ?, ?)", school, id, role);
		return id;
	}

}
