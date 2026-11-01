package com.schoollink.detection;

/**
 * BR-16, D9: the three threshold rules. In the pilot each has one unit and one window (D28); the DM
 * sets the count, within {@code min} and {@code max}, and turns the rule on or off.
 */
public enum RuleType {

	/** D2: absent in every timetabled period of the day; {@code count} such days in a row (1: that day). */
	FULL_DAY_ABSENCE(Unit.FULL_DAYS, Window.CONSECUTIVE, 1, 5),

	/** D3: lateness on {@code count} school days in a row; a school day without lateness resets it. */
	CONSECUTIVE_LATENESS(Unit.SCHOOL_DAYS, Window.CONSECUTIVE, 2, 20),

	/** SPEC §2 Q1: {@code count} unjustified absent periods in the current term. */
	UNJUSTIFIED_ABSENCES(Unit.PERIODS, Window.CURRENT_TERM, 1, 200);

	public enum Unit {
		FULL_DAYS, SCHOOL_DAYS, PERIODS
	}

	public enum Window {
		CONSECUTIVE, CURRENT_TERM
	}

	private final Unit unit;

	private final Window window;

	private final int min;

	private final int max;

	RuleType(Unit unit, Window window, int min, int max) {
		this.unit = unit;
		this.window = window;
		this.min = min;
		this.max = max;
	}

	public Unit unit() {
		return unit;
	}

	public Window window() {
		return window;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

}
