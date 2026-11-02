package com.schoollink.school;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Classes and blocks of a school (school module API). Everything is within {@code schoolId} only (NFR-8). */
public interface SchoolClasses {

	/** Every class of the school, whatever its year (UC-A6: the school dashboard). */
	Set<UUID> ids(UUID schoolId);

	/** D5: the classes of the given blocks. */
	Set<UUID> idsInBlocks(UUID schoolId, Set<UUID> blockIds);

	/** D5: the blocks the given classes belong to. */
	Set<UUID> blocksOf(UUID schoolId, Set<UUID> classIds);

	/** D5: the block of each given class (classId -> blockId); classes of other schools are left out. */
	Map<UUID, UUID> blockOf(UUID schoolId, Set<UUID> classIds);

	/** The names of the given blocks of the school: id -> name; blocks of other schools are left out. */
	Map<UUID, String> blockNames(UUID schoolId, Set<UUID> blockIds);

	boolean classExists(UUID schoolId, UUID classId);

	/** The names of the given classes of the school: id -> name; ids of other schools are left out. */
	Map<UUID, String> names(UUID schoolId, Set<UUID> classIds);

	boolean yearExists(UUID schoolId, UUID academicYearId);

	/** The classes of an academic year: name -> id (UC-A3 import). */
	Map<String, UUID> classesInYear(UUID schoolId, UUID academicYearId);

	boolean blockExists(UUID schoolId, UUID blockId);

	/** {@code FR} or {@code EN}: the default language for the school's new accounts. */
	String language(UUID schoolId);

}
