package com.schoollink.school;

import java.util.List;
import java.util.UUID;

/** School module API. */
public interface Schools {

	/** All schools, by name, for the school picker at login (D16). Names only, nothing else. */
	List<SchoolSummary> listForPicker();

	/** The school's name, as parents know it (it ends every SMS, BR-22). */
	String name(UUID schoolId);

}
