# SchoolLink mobile

The Android app for teachers, assistant DMs, DMs, counsellors and parents (one app, role-based
screens). See [docs/SPEC.md](../docs/SPEC.md) §8 and the commands in the root `CLAUDE.md`.

## Backend address

Debug builds call `http://10.0.2.2:8080/api/v1`, which the Android emulator maps to the backend
running on the development machine (plain HTTP is allowed in debug builds only). On a real phone,
or for a release build, pass the address:

```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080/api/v1
flutter build apk --release --split-per-abi --dart-define=API_BASE_URL=https://api.example.org/api/v1
```

Release builds accept HTTPS only (NFR-7).

## Layout

| Folder | What |
| --- | --- |
| `lib/core/` | API client (dio, token refresh), session and device storage, theme, providers |
| `lib/core/db/` | drift database (`database.g.dart` is generated: `dart run build_runner build`) |
| `lib/core/sync/` | The offline sync queue and when it runs (BR-6, NFR-3, D22) |
| `lib/features/auth/` | Staff sign-in and the first password (D16, D18) |
| `lib/features/shell/` | Role-based navigation: the tabs each role gets |
| `lib/l10n/` | `app_en.arb` and `app_fr.arb` (NFR-12); `flutter gen-l10n` writes the Dart files next to them |
| `test/support/` | A fake backend for dio and a harness that starts the app with it |
