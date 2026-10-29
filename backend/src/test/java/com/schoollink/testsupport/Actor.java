package com.schoollink.testsupport;

import com.schoollink.shared.security.Role;

/**
 * One user per row of the SPEC §3 matrix, plus the out-of-scope variants that NFR-8 tests need.
 * See {@link TestWorld} for who is assigned to what.
 */
public enum Actor {

	/** No token at all. */
	ANONYMOUS(null),
	ADMIN_X(Role.ADMIN),
	/** Teaches class A only. */
	TEACHER_A(Role.TEACHER),
	/** Assistant DM of block 1 (class A). */
	ASSISTANT_DM_1(Role.ASSISTANT_DM),
	/** DM of block 1 (class A). */
	DM_1(Role.DM),
	/** DM of block 2 (class B): same school, other scope. */
	DM_2(Role.DM),
	COUNSELLOR_X(Role.COUNSELLOR),
	/** Guardian of a student in class A. */
	PARENT_A(Role.PARENT),
	RESEARCHER(Role.RESEARCHER),
	/** Admin of school Y: other school. */
	ADMIN_Y(Role.ADMIN);

	final Role role;

	Actor(Role role) {
		this.role = role;
	}

}
