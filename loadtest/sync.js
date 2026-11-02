// Task 3.14 (SPEC §11, NFR-3, NFR-5, NFR-6): two schools × 40 classes × 8 periods synced within
// 10 minutes. Every teacher of loadtest/seed.sql uploads the 8 sessions of a school day and every
// assistant DM the day's lateness at the gate, as phones coming back online would (BR-6): each
// record is sent twice at once (two devices' retries racing) and then once more (a retry after a
// lost answer). The server must store each one once (idempotent PUT on the device's UUID).
//
//   docker run --rm -i -v "$PWD/loadtest:/scripts" grafana/k6:1.0.0 run /scripts/sync.js
//
// Environment: BASE (default http://host.docker.internal:8081/api/v1), DATE (the school day to
// record, ISO; default the last school day before today).
import http from 'k6/http'
import { check, fail, sleep } from 'k6'
import { Counter } from 'k6/metrics'
import exec from 'k6/execution'

const BASE = __ENV.BASE || 'http://host.docker.internal:8081/api/v1'
const PASSWORD = 'schoollink-dev'
const SCHOOLS = 2
const CLASSES = 40
const BLOCKS = 4
const PERIODS = 8
const ABSENT_SHARE = 0.05
const LATE_SHARE = 0.05
// LOGIN_PER_IP (NFR-7): 30 sign-ins a minute from one address, and every VU shares the runner's
const LOGIN_GAP = 2.1

const sessionsStored = new Counter('sessions_stored')
const latenessStored = new Counter('lateness_stored')

export const options = {
  setupTimeout: '6m',
  scenarios: {
    teachers: { executor: 'per-vu-iterations', vus: SCHOOLS * CLASSES, iterations: 1, maxDuration: '10m', exec: 'teacher' },
    gates: { executor: 'per-vu-iterations', vus: SCHOOLS * BLOCKS, iterations: 1, maxDuration: '10m', exec: 'gate' },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    sessions_stored: [`count==${SCHOOLS * CLASSES * PERIODS}`],
    // 5% of 500 students per block, rounded down
    lateness_stored: [`count==${SCHOOLS * BLOCKS * Math.floor(500 * LATE_SHARE)}`],
    'http_req_duration{kind:sync}': ['p(95)<2000'],
  },
}

const pad = (n) => String(n).padStart(2, '0')
const iso = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`

/** The last Monday-to-Friday day before today (UTC; Douala is UTC+1, the backend checks the school's zone). */
function lastSchoolDay() {
  const d = new Date()
  do {
    d.setUTCDate(d.getUTCDate() - 1)
  } while (d.getUTCDay() === 0 || d.getUTCDay() === 6)
  return iso(d)
}

const json = (token) => ({
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
})

function login(schoolId, email) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ schoolId, login: email, password: PASSWORD }), {
    ...json(),
    tags: { kind: 'setup' },
  })
  if (res.status !== 200) fail(`sign-in of ${email}: ${res.status} ${res.body}`)
  sleep(LOGIN_GAP)
  return res.json('accessToken')
}

function getJson(path, token) {
  const res = http.get(`${BASE}${path}`, { ...json(token), tags: { kind: 'setup' } })
  if (res.status !== 200) fail(`GET ${path}: ${res.status} ${res.body}`)
  return res.json()
}

/** Signs everyone in and reads what the phones would have cached before going offline. */
export function setup() {
  const date = __ENV.DATE || lastSchoolDay()
  const schools = getJson('/auth/schools')
  const teachers = []
  const gates = []
  for (let s = 1; s <= SCHOOLS; s++) {
    const school = schools.find((x) => x.name === `Load School ${s} (fictional)`)
    if (!school) fail(`Load School ${s} is not seeded (loadtest/seed.sql)`)
    for (let c = 1; c <= CLASSES; c++) {
      const token = login(school.id, `teacher-s${s}-c${pad(c)}@load.test`)
      const [klass] = getJson('/me/classes', token)
      const day = getJson(`/me/sessions?date=${date}`, token)
      if (!day.schoolDay || day.sessions.length !== PERIODS) fail(`${date} is not a full school day for teacher s${s}-c${c}`)
      teachers.push({
        token,
        classId: klass.classId,
        students: klass.students.map((x) => x.id),
        periods: day.sessions.map((x) => x.periodId),
      })
    }
    for (let b = 1; b <= BLOCKS; b++) {
      const token = login(school.id, `gate-s${s}-b${b}@load.test`)
      gates.push({ token, students: getJson('/lateness/students', token).map((x) => x.id) })
    }
  }
  return { date, teachers, gates }
}

/** Sends the same PUT from two devices at once, then once more; returns the statuses. */
function putThrice(path, body, token) {
  const params = { ...json(token), tags: { kind: 'sync' } }
  const raced = http.batch([
    ['PUT', `${BASE}${path}`, body, params],
    ['PUT', `${BASE}${path}`, body, params],
  ])
  const retried = http.put(`${BASE}${path}`, body, params)
  return [...raced.map((r) => r.status).sort(), retried.status]
}

function pick(list, share) {
  const shuffled = [...list].sort(() => Math.random() - 0.5)
  return shuffled.slice(0, Math.floor(list.length * share))
}

export function teacher(data) {
  const t = data.teachers[exec.scenario.iterationInTest]
  if (!t) fail(`no teacher for iteration ${exec.scenario.iterationInTest}`)
  for (const periodId of t.periods) {
    const body = JSON.stringify({
      classId: t.classId,
      date: data.date,
      periodId,
      confirmedAt: new Date().toISOString(),
      absentStudentIds: pick(t.students, ABSENT_SHARE),
    })
    const statuses = putThrice(`/sessions/${crypto.randomUUID()}`, body, t.token)
    // one of the two racing uploads stores it (201), the other and the retry see it stored (200)
    if (check(statuses, { 'session stored once': (s) => s.join() === '200,201,200' }, { kind: 'session' })) {
      sessionsStored.add(1)
    } else {
      console.error(`session statuses ${statuses.join()}`)
    }
    sleep(Math.random())
  }
}

export function gate(data) {
  const g = data.gates[exec.scenario.iterationInTest]
  if (!g) fail(`no gate for iteration ${exec.scenario.iterationInTest}`)
  for (const studentId of pick(g.students, LATE_SHARE)) {
    const minute = 31 + Math.floor(Math.random() * 29)
    const body = JSON.stringify({
      studentId,
      date: data.date,
      arrivalTime: `07:${pad(minute)}`,
      recordedAt: new Date().toISOString(),
    })
    const statuses = putThrice(`/lateness/${crypto.randomUUID()}`, body, g.token)
    if (check(statuses, { 'lateness stored once': (s) => s.join() === '200,201,200' }, { kind: 'lateness' })) {
      latenessStored.add(1)
    } else {
      console.error(`lateness statuses ${statuses.join()}`)
    }
    sleep(Math.random() * 3)
  }
}
