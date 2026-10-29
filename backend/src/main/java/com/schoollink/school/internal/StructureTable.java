package com.schoollink.school.internal;

import java.util.Map;
import java.util.UUID;

/**
 * How one school structure table is read, scoped and checked (UC-A1).
 *
 * @param table the table, also the audit entity name (NFR-11)
 * @param columns the columns returned, mapped by name onto {@code view}
 * @param scope for teachers, assistant DMs and DMs: an SQL condition over {@code :ids}, the classes
 *        in their scope; {@code null} for calendar data that every staff member reads in full
 * @param duplicateCode error code when a unique constraint is violated
 * @param rules checks run before every create and update, in the same transaction
 */
record StructureTable<V>(String table, String columns, Class<V> view, String scope, String duplicateCode,
		Rules rules) {

	@FunctionalInterface
	interface Rules {

		/** @param id {@code null} on create */
		void check(UUID school, UUID id, Map<String, Object> values);

	}

}
