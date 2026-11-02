# Load test (task 3.14)

SPEC §11 sizes the pilot at 2 schools × 40 classes × 8 periods, all synced within 10 minutes.
This folder checks it against a real backend and PostgreSQL:

| File | What |
| --- | --- |
| `seed.sql` | Two fictional schools: 40 classes each in 4 blocks, 8 periods, 50 students per class, one guardian per student (half with the app and a fake device token, half SMS only), one teacher per class, a DM and an assistant DM per block. Generated data only. |
| `sync.js` | k6 script. Every teacher uploads the 8 sessions of a school day (about 5% absent) and every assistant DM the day's lateness (5% of the block). Each upload is sent twice at once and then once more, as retries from phones coming back online (BR-6, NFR-3). |
| `check.sql` | What the run left: each record stored once, the outbox rows, the push delay and the digest delay (NFR-5, NFR-6). |

The run uses its own database, `schoollink_load`, and a backend on port 8081, so the dev
database and a backend on 8080 are left alone. k6 runs from its Docker image.

## Run it

From the repo root, with `docker compose up -d db` running:

```bash
docker compose exec -T db createdb -U schoollink schoollink_load

# a backend on the load database (Flyway creates the schema); leave it running
cd backend
DB_NAME=schoollink_load ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.port=8081
cd ..

docker compose exec -T db psql -v ON_ERROR_STOP=1 -U schoollink -d schoollink_load < loadtest/seed.sql

# Git Bash on Windows: MSYS_NO_PATHCONV=1 and "$(pwd -W)" instead of "$PWD"
docker run --rm -i -v "$PWD/loadtest:/scripts" grafana/k6:1.0.0 run /scripts/sync.js
```

The setup signs the 88 staff in first, about 2 s apart, because the login limit is 30 a minute
per address (NFR-7) and every virtual user shares the runner's address. That takes about three
minutes and is not part of the sync. The script records the last school day before today; set
`-e DATE=2026-10-30` to choose another one (a weekday in the seeded term, 7 Sep – 18 Dec 2026).

k6 fails the run unless every request succeeds, every upload is stored exactly once (one 201, two
200), all 640 sessions and 200 lateness records are stored, and 95% of uploads answer within 2 s.

## Check the outbox

Pushes go out at once (dev profile: the logging provider stands in for Firebase). SMS wait for
the school's digest (BR-22), at 13:00 by default. To see the digest without waiting, move the
first digest of the load schools to a few minutes from now (school time, Africa/Douala):

```bash
docker compose exec -T db psql -U schoollink -d schoollink_load -c \
  "UPDATE school SET digest_first = date_trunc('minute', (now() AT TIME ZONE timezone)::time) + interval '3 minutes' WHERE name LIKE 'Load School %'"
```

Then, once that time has passed:

```bash
docker compose exec -T db psql -U schoollink -d schoollink_load < loadtest/check.sql
```

Expected: 640 sessions on 640 distinct lessons, 32,000 attendance records, 200 lateness records,
no outbox row twice, 95% or more of pushes sent within 1 minute, each digest run within 5 minutes
of its slot and no SMS left HELD.

Drop the load database afterwards with `docker compose exec -T db dropdb -U schoollink schoollink_load`
(stop the 8081 backend first).

## Last result

2 Nov 2026, one Windows laptop running the backend (dev profile, default pool of 10 connections),
PostgreSQL in Docker and k6, recording Friday 30 Oct:

| Measure | Result |
| --- | --- |
| Requests | 2,777, none failed; uploads p95 834 ms, slowest 1.8 s |
| Sync window | 640 sessions and 200 lateness stored in 45 s (SPEC §11: 10 minutes) |
| Duplicates | none: 640 sessions on 640 lessons, 32,000 records, 200 lateness; each racing pair answered 201 then 200, and the retry 200 |
| Outbox | one row per event, guardian and channel; 715 pushes, all sent, p95 9.6 s, slowest 10 s (NFR-5: 95% within 1 min) |
| SMS digest | both schools ran 0.03 s and 1.2 s after the slot; 894 digests, the last sent 9 s after the slot, none left held (NFR-5: 5 min) |

The logging providers stand in for Firebase and Twilio, so this measures SchoolLink up to the
gateway, not the gateways themselves.

The first run found a connection-pool deadlock: the rules that run after each commit (BR-17) took a
second connection while the request still held its first, and under load every connection was
held by a request waiting for another (Hikari timeouts after 30 s, 500s). The rules now run on a
small executor of their own (`DetectionListeners`).
