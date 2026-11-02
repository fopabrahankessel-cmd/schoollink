package com.schoollink.reporting.internal;

import com.schoollink.attendance.AttendanceCounts;
import com.schoollink.attendance.AttendanceCounts.DayClass;
import com.schoollink.detection.AlertCounts;
import com.schoollink.detection.AlertCounts.RuleCount;
import com.schoollink.lateness.LatenessHistory;
import com.schoollink.lateness.LatenessHistory.StudentLateness;
import com.schoollink.notification.NotificationStats;
import com.schoollink.notification.NotificationStats.ChannelDelivery;
import com.schoollink.notification.NotificationStats.SmsCounter;
import com.schoollink.people.Students;
import com.schoollink.people.Students.Card;
import com.schoollink.school.SchoolCalendar;
import com.schoollink.school.SchoolClasses;
import com.schoollink.shared.error.ApiException;
import com.schoollink.shared.security.CurrentUser;
import com.schoollink.shared.security.Role;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The school dashboard for the admin (UC-A6) and the scope dashboard for the DM (UC-D8), over a
 * range of dates, by default the last 30 days (D34). The scope is the DM's classes (D5), never a
 * parameter (NFR-8). Attendance and lateness are counted side by side, never merged (BR-8, BR-9);
 * cancelled records are left out (BR-15).
 */
@Service
class Dashboards {

	/** Without dates: the last 30 days. */
	static final int DEFAULT_DAYS = 30;

	/** Every count of a day, a class or the whole range. */
	record Counts(int sessions, int studentPeriods, int absentPeriods, int unjustifiedAbsentPeriods, int lateness,
			int unjustifiedLateness) {
	}

	record Day(LocalDate date, Counts counts) {
	}

	record ClassCounts(UUID classId, String name, Counts counts) {
	}

	/**
	 * BR-24: {@code deliveryRate} is the share of finished sends that reached the gateway or the
	 * phone, {@code (sent + delivered + opened) / (sent + delivered + opened + failed)}; null when
	 * none is finished.
	 */
	record Channel(int pending, int sent, int delivered, int opened, int failed, Double deliveryRate) {
	}

	record Delivery(Channel push, Channel sms) {
	}

	/** BR-25, NFR-14: this calendar month, whatever the range. */
	record Sms(int sentThisMonth, int monthlyCap, boolean capReached) {
	}

	/**
	 * @param days the days with a recorded session or lateness, oldest first
	 * @param classes every class of the scope, by name, even without any record
	 * @param alerts one row per rule: open now, raised and closed in the range
	 */
	record ScopeDashboard(LocalDate from, LocalDate to, Counts totals, List<Day> days, List<ClassCounts> classes,
			List<RuleCount> alerts) {
	}

	record SchoolDashboard(LocalDate from, LocalDate to, Counts totals, List<Day> days, List<ClassCounts> classes,
			List<RuleCount> alerts, Delivery delivery, Sms sms) {
	}

	private final AttendanceCounts attendance;

	private final LatenessHistory lateness;

	private final AlertCounts alerts;

	private final NotificationStats notifications;

	private final Students students;

	private final SchoolClasses schoolClasses;

	private final SchoolCalendar calendar;

	private final Clock clock;

	Dashboards(AttendanceCounts attendance, LatenessHistory lateness, AlertCounts alerts,
			NotificationStats notifications, Students students, SchoolClasses schoolClasses, SchoolCalendar calendar,
			Clock clock) {
		this.attendance = attendance;
		this.lateness = lateness;
		this.alerts = alerts;
		this.notifications = notifications;
		this.students = students;
		this.schoolClasses = schoolClasses;
		this.calendar = calendar;
		this.clock = clock;
	}

	/** UC-A6: the whole school, with delivery and SMS usage. */
	@Transactional(readOnly = true)
	SchoolDashboard school(CurrentUser user, LocalDate from, LocalDate to) {
		if (user.schoolId() == null || !user.has(Role.ADMIN)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		ZoneId zone = calendar.zone(school);
		DateRange range = range(zone, from, to);
		ScopeDashboard core = scope(school, zone, schoolClasses.ids(school), range);
		NotificationStats.Delivery delivery = notifications.delivery(school, range.start(zone), range.end(zone));
		SmsCounter counter = notifications.smsCounter(school);
		return new SchoolDashboard(core.from(), core.to(), core.totals(), core.days(), core.classes(), core.alerts(),
				new Delivery(channel(delivery.push()), channel(delivery.sms())),
				new Sms(counter.sentThisMonth(), counter.monthlyCap(), counter.sentThisMonth() >= counter.monthlyCap()));
	}

	/** UC-D8: the DM's classes only (D5). */
	@Transactional(readOnly = true)
	ScopeDashboard scope(CurrentUser user, LocalDate from, LocalDate to) {
		if (user.schoolId() == null || !user.has(Role.DM)) {
			throw ApiException.forbidden();
		}
		UUID school = user.schoolId();
		ZoneId zone = calendar.zone(school);
		return scope(school, zone, user.classesAs(Role.DM), range(zone, from, to));
	}

	private DateRange range(ZoneId zone, LocalDate from, LocalDate to) {
		return DateRange.of(from, to, clock.instant().atZone(zone).toLocalDate(), DEFAULT_DAYS);
	}

	private ScopeDashboard scope(UUID school, ZoneId zone, Set<UUID> classIds, DateRange range) {
		Map<LocalDate, Tally> byDay = new TreeMap<>();
		Map<UUID, Tally> byClass = new HashMap<>();
		classIds.forEach(id -> byClass.put(id, new Tally()));
		Tally totals = new Tally();

		for (DayClass row : attendance.daily(school, classIds, range.from(), range.to())) {
			for (Tally tally : List.of(byDay.computeIfAbsent(row.date(), d -> new Tally()), byClass.get(row.classId()),
					totals)) {
				tally.sessions += row.sessions();
				tally.studentPeriods += row.studentPeriods();
				tally.absentPeriods += row.absentPeriods();
				tally.unjustifiedAbsentPeriods += row.unjustifiedAbsentPeriods();
			}
		}
		// lateness belongs to the student's current class, as on the gate list (UC-AD2)
		Map<UUID, UUID> classOf = students.inClasses(school, classIds, null, 0)
			.stream()
			.collect(Collectors.toMap(Card::id, Card::classId));
		for (StudentLateness late : lateness.latenessOf(school, classOf.keySet(), range.from(), range.to())) {
			for (Tally tally : List.of(byDay.computeIfAbsent(late.date(), d -> new Tally()),
					byClass.get(classOf.get(late.studentId())), totals)) {
				tally.lateness++;
				if (!late.justified()) {
					tally.unjustifiedLateness++;
				}
			}
		}

		Map<UUID, String> names = schoolClasses.names(school, classIds);
		List<ClassCounts> classes = byClass.entrySet()
			.stream()
			.map(e -> new ClassCounts(e.getKey(), names.get(e.getKey()), e.getValue().counts()))
			.sorted(Comparator.comparing(ClassCounts::name, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(ClassCounts::classId))
			.toList();
		List<Day> days = byDay.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue().counts())).toList();
		return new ScopeDashboard(range.from(), range.to(), totals.counts(), days, classes,
				alerts.byRule(school, classIds, range.start(zone), range.end(zone)));
	}

	private static Channel channel(ChannelDelivery c) {
		int reached = c.sent() + c.delivered() + c.opened();
		Double rate = reached + c.failed() == 0 ? null : (double) reached / (reached + c.failed());
		return new Channel(c.pending(), c.sent(), c.delivered(), c.opened(), c.failed(), rate);
	}

	private static final class Tally {

		int sessions;

		int studentPeriods;

		int absentPeriods;

		int unjustifiedAbsentPeriods;

		int lateness;

		int unjustifiedLateness;

		Counts counts() {
			return new Counts(sessions, studentPeriods, absentPeriods, unjustifiedAbsentPeriods, lateness,
					unjustifiedLateness);
		}

	}

}
