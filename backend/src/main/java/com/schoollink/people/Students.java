package com.schoollink.people;

import java.util.Set;
import java.util.UUID;

/** Students of a school (people module API). Everything is within {@code schoolId} only (NFR-8). */
public interface Students {

	/** The students currently enrolled in the class (BR-2: the roll of an attendance session). */
	Set<UUID> idsInClass(UUID schoolId, UUID classId);

}
