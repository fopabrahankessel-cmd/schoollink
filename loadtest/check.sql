-- Task 3.14: what the load run left in the database (psql -f loadtest/check.sql).
-- Run it once after k6 (push), and again after the SMS digest slot set in the README (SMS).

\echo '== Records: each upload stored once (BR-6). Expected 640 sessions, 32000 records, 200 lateness'
SELECT count(*) AS sessions,
       count(DISTINCT (s.class_id, s.date, s.period_id)) AS distinct_lessons,
       (SELECT count(*) FROM attendance_record r JOIN school sc ON sc.id = r.school_id
         WHERE sc.name LIKE 'Load School %') AS records,
       (SELECT count(*) FROM attendance_record r JOIN school sc ON sc.id = r.school_id
         WHERE sc.name LIKE 'Load School %' AND r.status = 'ABSENT') AS absences,
       (SELECT count(*) FROM lateness_record l JOIN school sc ON sc.id = l.school_id
         WHERE sc.name LIKE 'Load School %') AS lateness,
       (SELECT count(DISTINCT (l.student_id, l.date)) FROM lateness_record l JOIN school sc ON sc.id = l.school_id
         WHERE sc.name LIKE 'Load School %') AS distinct_lateness
  FROM attendance_session s JOIN school sc ON sc.id = s.school_id
 WHERE sc.name LIKE 'Load School %';

\echo '== Sync window: first and last upload stored'
SELECT min(synced_at) AS first_sync, max(synced_at) AS last_sync, max(synced_at) - min(synced_at) AS span
  FROM (SELECT synced_at, school_id FROM attendance_session UNION ALL SELECT synced_at, school_id FROM lateness_record) x
  JOIN school sc ON sc.id = x.school_id
 WHERE sc.name LIKE 'Load School %';

\echo '== Outbox: one row per event, guardian and channel (BR-20, NFR-6)'
SELECT o.event_type, o.channel, o.status, count(*) AS rows,
       count(DISTINCT (o.event_type, o.event_id, o.guardian_id, o.channel)) AS distinct_rows
  FROM notification_outbox o JOIN school sc ON sc.id = o.school_id
 WHERE sc.name LIKE 'Load School %'
 GROUP BY 1, 2, 3 ORDER BY 1, 2, 3;

\echo '== NFR-5: 95% of pushes sent within 1 min of the sync'
SELECT count(*) AS pushes,
       count(*) FILTER (WHERE o.sent_at IS NULL) AS not_sent,
       round(100.0 * count(*) FILTER (WHERE o.sent_at - o.created_at <= interval '1 minute') / nullif(count(*), 0), 1)
           AS pct_within_1_min,
       percentile_cont(0.95) WITHIN GROUP (ORDER BY extract(epoch FROM o.sent_at - o.created_at)) AS p95_seconds,
       max(o.sent_at - o.created_at) AS slowest
  FROM notification_outbox o JOIN school sc ON sc.id = o.school_id
 WHERE sc.name LIKE 'Load School %' AND o.channel = 'PUSH';

\echo '== NFR-5: SMS digest sent within 5 min of its scheduled time (BR-22)'
SELECT sc.name, r.run_date, r.slot, r.digests,
       r.ran_at - (r.run_date + r.slot) AT TIME ZONE sc.timezone AS run_late_by,
       (SELECT count(*) FROM notification_outbox d
         WHERE d.school_id = sc.id AND d.event_type = 'DIGEST' AND d.sent_at IS NOT NULL) AS digests_sent,
       (SELECT max(d.sent_at) FROM notification_outbox d
         WHERE d.school_id = sc.id AND d.event_type = 'DIGEST') - (r.run_date + r.slot) AT TIME ZONE sc.timezone
           AS last_sent_late_by,
       (SELECT count(*) FROM notification_outbox h
         WHERE h.school_id = sc.id AND h.status = 'HELD') AS still_held
  FROM sms_digest_run r JOIN school sc ON sc.id = r.school_id
 WHERE sc.name LIKE 'Load School %'
 ORDER BY sc.name, r.run_date, r.slot;
