package com.schoollink.people;

import java.util.Set;
import java.util.UUID;

/**
 * The assignments that set a user's scope (SPEC §3): classes they teach, blocks they are DM or
 * assistant DM of (D5), and, for a parent, their own children.
 */
public record ScopeAssignments(Set<UUID> taughtClasses, Set<UUID> dmBlocks, Set<UUID> assistantDmBlocks,
		Set<UUID> children) {

	public ScopeAssignments {
		taughtClasses = Set.copyOf(taughtClasses);
		dmBlocks = Set.copyOf(dmBlocks);
		assistantDmBlocks = Set.copyOf(assistantDmBlocks);
		children = Set.copyOf(children);
	}

}
