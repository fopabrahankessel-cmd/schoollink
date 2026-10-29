# SchoolLink web

Dashboard for administrators, Discipline Masters, counsellors and the researcher (SPEC §3). Generated with `npm create vite@latest` (react-ts template), then extended with MUI, React Router, i18next (FR/EN) and Vitest.

## Run

```bash
npm ci
npm run dev        # http://localhost:5173, proxies /api to the backend on :8080
npm run lint       # oxlint
npm test           # Vitest + Testing Library (jsdom)
npm run build      # type check (tsc -b) + production build in dist/
```

Start the backend first with the dev profile (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` in `backend/`). It seeds two fake schools, and every account signs in with `schoollink-dev` (logins are printed at startup).

## Layout

| Path | What |
| --- | --- |
| `src/api/client.ts` | HTTP client: access token in memory, refresh token in `sessionStorage`, one shared refresh on a 401, then retry |
| `src/api/auth.ts` | Sign-in, school picker, `/me`, password change |
| `src/auth/` | Session state (`AuthProvider`, `useAuth`), route guard, school remembered on the device (D16) |
| `src/i18n/` | `fr.json` and `en.json` (NFR-12: same keys, checked by a test), language switch remembered on the device |
| `src/pages/` | Sign-in, password change (D18), home |
| `src/test/` | Test setup and a scripted fake API |

All user-facing text goes through i18next: add every key to both `fr.json` and `en.json` in the same change. Server error messages come back already translated, in the language of the request (signed out) or of the account (signed in).
