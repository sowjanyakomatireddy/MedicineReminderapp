# Medicine Reminder

A family app that helps elderly people take their medicines on time, without ever having to open an app.

A family member sets up medicines on their own phone. The elder's Android phone then rings a full-screen alarm by
itself, even when locked and offline, speaks the medicine's name in their language (Telugu, Tamil, Hindi or
English), shows a picture of the tablet, and tells the family if a dose is missed. It also gives calm spoken
reminders for a daily routine, and optional period-date reminders.

> **Not medical advice.** The app never suggests doses or checks drug safety.
> Confirm medicines and food timing with the doctor.

## Set it up (9 steps)

You need: Java 21+, Maven, Node 20+, PostgreSQL 16 (Docker or a local install), and Android Studio (for the
Android SDK). The elder's phone must be Android.

1. **Start Postgres:** `docker compose up -d`
   (or, with a local Postgres: `psql -d postgres -c "CREATE ROLE medreminder LOGIN PASSWORD 'medreminder'" -c "CREATE DATABASE medreminder OWNER medreminder"`).
2. **Settings:** `cp .env.example .env` and set `JWT_SECRET` (`openssl rand -base64 48`).
3. **Backend:** `cd backend && set -a && source ../.env && set +a && mvn spring-boot:run` (creates the tables).
4. **Family app:** in a new terminal, `cd app && npm install && npm run dev`, then open http://localhost:5173.
5. **Your family:** create an account, create your family, and on the **Family** tab add the person you remind
   (name, voice language, optional phone numbers).
6. **His phone's app:** `cd app && VITE_API_URL=http://<this computer's Wi-Fi address>:8080 npm run build && npx cap sync android && cd android && ./gradlew assembleDebug`,
   then install `app/build/outputs/apk/debug/app-debug.apk` on his phone (USB, or send him the file).
7. **Pair:** in the family app, *Family → Set up phone → Show a family code*. On his phone, open the app, type the
   code and choose his name.
8. **Set up his phone:** allow each step on the checklist that opens (notifications, exact alarms, lock screen,
   battery, voice) and tap **Ring a test alarm in 10 seconds**, then lock the phone.
9. **Add a medicine** in the family app. His phone picks it up when the app opens (within a minute with push),
   rings at the time, and Family Home shows *Taken* or *Missed*.

Then work through the real-phone checks in [docs/android-phone-test.md](docs/android-phone-test.md), including the
overnight test.

**Optional:** turn on push messages (changes arrive within a minute, "Play the reminder again", missed-dose alerts
on family phones): [docs/firebase-setup.md](docs/firebase-setup.md). **To give him a permanent app** that works
away from home, see [docs/release.md](docs/release.md).

**Postgres.app on a Mac:** the first time the backend connects, Postgres.app may ask whether to allow Java. Choose
Allow (or Postgres.app → Settings → App Permissions); otherwise startup fails with
`Postgres.app failed to verify "trust" authentication`.

## Documents

| For | Document |
|---|---|
| Family members using the app | [docs/family-guide.md](docs/family-guide.md) |
| Testing on his real phone | [docs/android-phone-test.md](docs/android-phone-test.md) |
| Push messages (Firebase) | [docs/firebase-setup.md](docs/firebase-setup.md) |
| Release APK, signing key, hosting | [docs/release.md](docs/release.md) |
| Native speakers checking the translations | [docs/translations/README.md](docs/translations/README.md) |
| How it is built | [docs/architecture.md](docs/architecture.md) |
| Every decision made along the way | [docs/decisions.md](docs/decisions.md) |
| The original brief | [PROMPT.md](PROMPT.md) |

## Project layout

| Folder | What it is |
| --- | --- |
| `backend/` | Java 21, Spring Boot 3, PostgreSQL (Flyway), JWT, Firebase Admin, OpenAPI at `/swagger-ui.html` |
| `app/` | React 18 + TypeScript + Vite + Tailwind + react-i18next: the family PWA and the `/elder` screens |
| `app/android/` | The elder's Android app: the same React build in Capacitor 8, plus the native alarm in Java (`app/src/main/java/app/medreminder/alarm/`) |
| `scripts/` | Release key and release APK build |
| `docs/` | Guides, decisions, translation review files |

## Tests

- Backend: `cd backend && mvn test` (in-memory database; 57 tests)
- App: `cd app && npm test && npm run build` (53 tests, including accessibility checks on every main screen)
- Alarm logic, no phone needed: `cd app/android && ./gradlew testDebugUnitTest` (9 tests)

## Languages

English, Hindi, Telugu and Tamil are built in. **All Hindi, Telugu and Tamil text is a draft that needs review by a
native speaker**: give them the review file for their language from
[docs/translations/](docs/translations/README.md), then import their corrections with
`npm run translations:import`.

To add another language (for example Kannada):

1. Copy `app/src/i18n/locales/en.json` to `kn.json` and translate it.
2. Add one entry to `app/src/i18n/locales.ts`: `{ code: 'kn', bcp47: 'kn-IN', englishName: 'Kannada', nativeName: 'ಕನ್ನಡ' }`.
3. Add the Noto Sans font for that script in `app/index.html` and `app/src/index.css`.
4. Optional: `backend/src/main/resources/i18n/messages_kn.properties` for family push alerts.

A test checks that every language file has exactly the same keys as English.

## Security and privacy

- Secrets come only from environment variables and files outside git (`.env`, Firebase keys, the release signing
  key in `~/.medicine-reminder/`).
- Passwords are hashed (BCrypt). Family logins and paired phones use separate tokens; a phone's token works only for
  its own endpoints and stops at once when the family unlinks it. Family codes are one-time, expire in 24 hours, and
  are stored only as a hash.
- Every query is scoped to the caller's family; other families' data answers "not found".
- CORS is limited to the app's address; login, register and pairing are rate-limited per IP.
- Health data (medicine names, doses, period dates) is never written to logs. Photos are checked by their content
  and served only to the family (and to the linked phone).
- Release builds use https only and are not debuggable.

## iPhone limits

Family members can use **iPhone or Android**: the family app works in Safari (add it to the Home Screen; alerts
need iOS 16.4+) and Chrome. The **elder's phone should be Android** in this release, because:

- iOS does not let normal apps show an exact, full-screen alarm over the lock screen the way Android's alarm-clock API does.
- iOS notification sounds are limited to about 30 seconds, so a long ringing alarm is not possible.
- Ringing through silent mode needs **Critical Alerts**, a special entitlement Apple must approve.
- Building a native iOS app needs a Mac with Xcode.
- Installing on a real iPhone outside the App Store needs an Apple developer account; with the free account the app
  stops working after 7 days and must be reinstalled.

Push messages are platform-neutral, so an iPhone could still receive reminder pushes later.

## Definition of done

| Goal from the brief | Status |
|---|---|
| Phone locked, app closed: a 7:00 alarm rings full-screen, speaks Telugu, works with no internet | Done and checked on the emulator (locked, rebooted, offline schedule). **Overnight on his real phone: test M** in the phone test guide |
| A medicine added on my phone rings on his phone within a minute (with internet) | Done with push (Firebase set up); without it, when his app opens, after a restart, or daily |
| A missed dose sends a push to the family | Done (needs Firebase); without it, the family app shows the missed dose |
| Backend tests pass, the web app builds, README setup under 10 steps | Done |

## Status

- [x] Phase 1: Foundation
- [x] Phase 2: Family data and pairing
- [x] Phase 3: Medicines and schedules
- [x] Phase 4: Android alarm plugin
- [x] Phase 5: Sync and dose logs
- [x] Phase 6: Routine and period reminders
- [x] Phase 7: Polish (accessibility, empty/error/loading states, translation review kit, release APK, documentation)
