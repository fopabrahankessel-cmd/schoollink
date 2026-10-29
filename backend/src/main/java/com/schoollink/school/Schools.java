package com.schoollink.school;

import java.util.List;

/** School module API. */
public interface Schools {

	/** All schools, by name, for the school picker at login (D16). Names only, nothing else. */
	List<SchoolSummary> listForPicker();

}
