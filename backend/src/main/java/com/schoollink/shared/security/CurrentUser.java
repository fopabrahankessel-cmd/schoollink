package com.schoollink.shared.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The signed-in user of the current request, resolved from the database on every request from the
 * access token's subject (NFR-8, SPEC §3). School and scope come from here, never from the request
 * body or query string. Controllers take it with {@code @AuthenticationPrincipal CurrentUser user}.
 *
 * <p>Scope is set by assignments, not by role alone (SPEC §3): {@code classes} holds, per role, the
 * classes that role covers (TEACHER: classes taught; DM and ASSISTANT_DM: the classes of their
 * blocks, D5). ADMIN and COUNSELLOR cover the whole school; the counsellor then only sees referred
 * students. {@code children} are a parent's own children.
 *
 * @param schoolId {@code null} only for the researcher
 * @param language {@code FR} or {@code EN}
 * @param passwordChangeRequired the user still has a temporary password (D18); until it is
 *        replaced, only {@code GET /me} and {@code POST /me/password} are allowed
 */
public record CurrentUser(UUID userId, UUID schoolId, String language, boolean passwordChangeRequired,
		Set<Role> roles, Map<Role, Set<UUID>> classes, Set<UUID> children) {

	public CurrentUser {
		roles = Set.copyOf(roles);
		classes = Map.copyOf(classes);
		children = Set.copyOf(children);
	}

	public boolean has(Role role) {
		return roles.contains(role);
	}

	public boolean hasAny(Role... any) {
		return Arrays.stream(any).anyMatch(roles::contains);
	}

	/** The classes covered by any of the given roles the user holds; empty when they hold none. */
	public Set<UUID> classesAs(Role... asRoles) {
		Set<UUID> result = new HashSet<>();
		for (Role role : asRoles) {
			result.addAll(classes.getOrDefault(role, Set.of()));
		}
		return Set.copyOf(result);
	}

}
