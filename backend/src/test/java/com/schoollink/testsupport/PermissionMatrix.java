package com.schoollink.testsupport;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One row of the SPEC §3 permission matrix for one endpoint (NFR-8): the expected HTTP status for
 * every {@link Actor}. Forgetting an actor fails the row, so a new endpoint cannot skip the
 * forbidden cases.
 *
 * <pre>{@code
 * @TestFactory
 * Stream<DynamicTest> readClassA() {
 *     return PermissionMatrix.row("GET class A", w -> get("/api/v1/classes/{id}", w.classA))
 *         .expect(200, ADMIN_X, TEACHER_A, ASSISTANT_DM_1, DM_1, COUNSELLOR_X)
 *         .expect(404, DM_2, ADMIN_Y)
 *         .expect(403, PARENT_A, RESEARCHER)
 *         .expect(401, ANONYMOUS)
 *         .tests(mockMvc, world);
 * }
 * }</pre>
 *
 * Convention: 403 when the role has no access to that kind of data, 404 when the record is in
 * another school or outside the user's scope, so its existence does not leak.
 */
public final class PermissionMatrix {

	private final String name;

	private final Function<TestWorld, ? extends AbstractMockHttpServletRequestBuilder<?>> request;

	private final Map<Actor, Integer> expected = new EnumMap<>(Actor.class);

	private PermissionMatrix(String name, Function<TestWorld, ? extends AbstractMockHttpServletRequestBuilder<?>> request) {
		this.name = name;
		this.request = request;
	}

	/** {@code request} builds a fresh request for each actor. */
	public static PermissionMatrix row(String name, Function<TestWorld, ? extends AbstractMockHttpServletRequestBuilder<?>> request) {
		return new PermissionMatrix(name, request);
	}

	public PermissionMatrix expect(int status, Actor... actors) {
		for (Actor actor : actors) {
			if (expected.put(actor, status) != null) {
				throw new IllegalArgumentException(name + ": " + actor + " is listed twice");
			}
		}
		return this;
	}

	/** One dynamic test per actor; fails at once if an actor has no expected status. */
	public Stream<DynamicTest> tests(MockMvc mockMvc, TestWorld world) {
		Set<Actor> missing = EnumSet.complementOf(EnumSet.copyOf(expected.keySet()));
		assertThat(missing).as(name + ": every actor needs an expected status").isEmpty();
		return expected.entrySet().stream().map(e -> DynamicTest.dynamicTest(e.getKey() + " -> " + e.getValue(), () -> {
			AbstractMockHttpServletRequestBuilder<?> builder = request.apply(world);
			String token = world.token(e.getKey());
			if (token != null) {
				builder.header("Authorization", "Bearer " + token);
			}
			int status = mockMvc.perform(builder).andReturn().getResponse().getStatus();
			assertThat(status).as(name + " as " + e.getKey()).isEqualTo(e.getValue());
		}));
	}

}
