package com.schoollink.school;

import java.util.Set;
import java.util.UUID;

/** Classes of a school (school module API). */
public interface SchoolClasses {

	/** D5: the classes of the given blocks, within {@code schoolId} only (NFR-8). */
	Set<UUID> idsInBlocks(UUID schoolId, Set<UUID> blockIds);

}
