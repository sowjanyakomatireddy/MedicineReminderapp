# Medicine Reminder

A family app that helps elderly people take their medicines on time. A family member sets up
medicines on their own phone; the elder's Android phone rings a full-screen alarm, speaks the
medicine name in their language, and tells the family if a dose is missed.

> **Not medical advice.** The app never suggests doses or checks drug safety.
> Confirm medicines and food timing with the doctor.

The full product brief is in [PROMPT.md](PROMPT.md).

## Layout

| Folder | What it is |
| --- | --- |
| `backend/` | Java 21, Spring Boot 3, PostgreSQL, Flyway, JWT auth, OpenAPI |
| `app/` | React 18 + TypeScript + Vite + Tailwind + react-i18next (family PWA and the `/elder` screens) |
| `app/android/` | The elder's Android app: the same React build wrapped by Capacitor 8, plus the native alarm plugin in Java (`app/src/main/java/app/medreminder/alarm/`) |
| `docs/` | Extra documentation |

## Run it locally

You need Java 21 or newer, Maven, Node 20 or newer, and PostgreSQL 16 (Docker or a local install).

1. **Start Postgres.** With Docker: `docker compose up -d`.
   Without Docker (local Postgres already running):
   `psql -d postgres -c "CREATE ROLE medreminder LOGIN PASSWORD 'medreminder'" -c "CREATE DATABASE medreminder OWNER medreminder"`
2. **Make your settings file:** `cp .env.example .env`, then set `JWT_SECRET` (run `openssl rand -base64 48`).
3. **Start the backend:** `cd backend && set -a && source ../.env && set +a && mvn spring-boot:run`
   Flyway creates the tables on first start. API docs: http://localhost:8080/swagger-ui.html
4. **Start the app** (new terminal): `cd app && npm install && npm run dev`
5. **Open** http://localhost:5173, create an account, and create your family.
6. **Try pairing:** on the Family tab add a person (e.g. Thatha, voice Telugu), open *Set up phone* and tap
   *Show a family code*. In a private window (standing in for his phone) open http://localhost:5173/elder/pair,
   type the code and pick the person.
7. **Add a medicine** from Home or the Medicines tab. Family Home then shows today's doses for each person.
   Photos are saved in `PHOTO_DIR` (default `backend/uploads/`, git-ignored).

**Postgres.app on a Mac:** the first time the backend connects, Postgres.app may ask whether to allow Java.
Choose Allow (or add it under Postgres.app → Settings → App Permissions); otherwise startup fails with
`Postgres.app failed to verify "trust" authentication`.

### The elder's Android app

You need Android Studio (for the Android SDK) and Java 21 or newer. The Gradle wrapper is set to Gradle 9.1, which
also runs on Java 25.

```sh
cd app
VITE_API_URL=http://<your Mac's Wi-Fi address>:8080 npm run build   # e.g. http://192.168.1.20:8080
npx cap sync android
cd android && ./gradlew assembleDebug                                # → app/build/outputs/apk/debug/app-debug.apk
```

Install the APK on the phone and follow [docs/android-phone-test.md](docs/android-phone-test.md).
Debug builds may use plain `http` on your home network; release builds (Phase 7) need `https`.

### Push messages (optional)

Push makes changes reach the phone within a minute, lets the family ring the phone from the Missed alert,
and sends missed-dose alerts to family phones. Everything else works without it.
Setup (free, about 15 minutes): [docs/firebase-setup.md](docs/firebase-setup.md).

### Tests

- Backend: `cd backend && mvn test` (uses an in-memory H2 database, no Postgres needed)
- App: `cd app && npm test && npm run build`
- Alarm logic (no phone needed): `cd app/android && ./gradlew testDebugUnitTest`

## Languages

English, Hindi, Telugu and Tamil are built in. To add another language (for example Kannada):

1. Copy `app/src/i18n/locales/en.json` to `kn.json` and translate it.
2. Add one entry to `app/src/i18n/locales.ts`: `{ code: 'kn', bcp47: 'kn-IN', englishName: 'Kannada', nativeName: 'ಕನ್ನಡ' }`.
3. Add the Noto Sans font for that script in `app/index.html` and `app/src/index.css`.

A test checks that every language file has exactly the same keys as English.
**All Hindi, Telugu and Tamil text is a draft** (marked `"_reviewStatus": "needs review by a native speaker"`)
and must be checked by someone who speaks the language.

## Security notes

- Secrets come only from environment variables (`.env` is git-ignored).
- Passwords are hashed with BCrypt; the API uses stateless JWTs (`Authorization: Bearer …`).
- CORS only allows the origins in `CORS_ALLOWED_ORIGINS`.
- Login, register and pairing endpoints are rate-limited per IP (default 10 per minute).
- SQL and request bodies are kept out of logs, because they may contain health data.

## iPhone limits

Family members can use **iPhone or Android**: the family app is a PWA that works in Safari
(add it to the Home Screen) and Chrome. The **elder's phone should be Android** in the first release, because:

- iOS does not let normal apps show an exact, full-screen alarm over the lock screen the way Android's alarm clock API does.
- iOS notification sounds are limited to about 30 seconds, so a long ringing alarm is not possible.
- Ringing through silent mode needs **Critical Alerts**, which require a special entitlement that Apple must approve.
- Building a native iOS app needs a Mac with Xcode.
- Installing on a real iPhone outside the App Store needs an Apple developer account. With the free account, the app stops working after 7 days and must be reinstalled.

The code keeps push notifications platform-neutral, so an iPhone can still receive reminder pushes later.

## Status

- [x] Phase 1: Foundation (monorepo, Postgres, Spring Boot, Flyway, JWT auth, React + Tailwind + i18n skeleton)
- [x] Phase 2: Family data and pairing (families, members and roles, people, one-time family codes, Family members and Set up phone screens)
- [x] Phase 3: Medicines and schedules (medicines with times, food rule and alarm settings, photos, voice preview, Family Home with today's status)
- [x] Phase 4: Android alarm plugin (exact full-screen alarm over the lock screen, Telugu/Tamil/Hindi/English voice, repeat and auto-stop, restart-safe, offline, phone setup checklist, test alarm; real-phone test guide)
- [x] Phase 5: Sync and dose logs (phone syncs on push, start, restart and daily; offline Taken/Missed queue; Taken and Today screens; missed-dose job and family alerts; Missed alert screen with call, replay and mark as taken)
- [ ] Phase 6: Routine and period reminders
- [ ] Phase 7: Polish and release APK
