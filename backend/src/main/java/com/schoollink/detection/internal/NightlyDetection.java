package com.schoollink.detection.internal;

import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolSummary;
import com.schoollink.school.Schools;
import java.time.Clock;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BR-17: the nightly run, at 20:15 in each school's time zone, after the day's sessions and the
 * last SMS digest. Off in tests ({@code schoollink.detection.nightly-enabled=false}), which call
 * {@link Detection#evaluateSchool} themselves.
 */
@Component
@ConditionalOnProperty(name = "schoollink.detection.nightly-enabled", havingValue = "true", matchIfMissing = true)
class NightlyDetection {

	static final int HOUR = 20;

	private static final Logger log = LoggerFactory.getLogger(NightlyDetection.class);

	private final Detection detection;

	private final Schools schools;

	private final SchoolCalendar calendar;

	private final Clock clock;

	NightlyDetection(Detection detection, Schools schools, SchoolCalendar calendar, Clock clock) {
		this.detection = detection;
		this.schools = schools;
		this.calendar = calendar;
		this.clock = clock;
	}

	/** Every hour; each school's run when its local hour comes, one transaction per school. */
	@Scheduled(cron = "0 15 * * * *")
	void run() {
		for (SchoolSummary school : schools.listForPicker()) {
			ZonedDateTime local = clock.instant().atZone(calendar.zone(school.id()));
			if (local.getHour() != HOUR) {
				continue;
			}
			try {
				int raised = detection.evaluateSchool(school.id(), local.toLocalDate());
				log.info("Nightly detection for school {}: {} alerts raised", school.id(), raised);
			}
			catch (RuntimeException ex) {
				log.error("Nightly detection failed for school {}", school.id(), ex);
			}
		}
	}

}
