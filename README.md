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
| `app/` | React 18 + TypeScript + Vite + Tailwind + react-i18next (family PWA, `/elder` screens). Capacitor Android wrapper is added in Phase 4. |
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
5. **Open** http://localhost:5173, create an account, and log in. The elder screen preview is at http://localhost:5173/elder.

### Tests

- Backend: `cd backend && mvn test` (uses an in-memory H2 database, no Postgres needed)
- App: `cd app && npm test && npm run build`

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
- [ ] Phase 2: Family data and pairing
- [ ] Phase 3: Medicines and schedules
- [ ] Phase 4: Android alarm plugin
- [ ] Phase 5: Sync and dose logs
- [ ] Phase 6: Routine and period reminders
- [ ] Phase 7: Polish and release APK
