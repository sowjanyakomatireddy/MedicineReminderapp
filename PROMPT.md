## Role and goal

You are a senior full-stack engineer. Build **"Medicine Reminder"**, a family app that helps elderly people (my grandfather, who only understands Telugu) take their medicines on time without ever having to open the app.

A family member sets up the medicines and times on their own phone. The grandfather's phone then rings a full-screen alarm by itself, speaks the medicine name in his language, shows a picture of the tablet, and tells the family if he does not take it. The same system also handles daily routine reminders for each person, and period-date reminders for women.

Work in small phases (listed at the end). After each phase: run the build and tests, tell me what works, and wait for my "continue" before the next phase. Ask me before making any decision that is not covered here.

## Tech stack (use exactly this)

- **Backend:** Java 21, Spring Boot 3, Spring Web, Spring Data JPA, Spring Security with JWT, Bean Validation, Flyway migrations, PostgreSQL (Docker Compose for local; H2 only for tests), springdoc OpenAPI, JUnit 5 + Testcontainers or MockMvc.
- **Family app (web, mobile-first PWA):** React 18 + TypeScript + Vite, React Router, TanStack Query, Tailwind CSS, react-i18next.
- **Grandfather's phone app:** the same React code wrapped with **Capacitor** for Android, plus a **custom native Capacitor plugin written in Java** for the alarm (see "Why native" below).
- **Push messages:** Firebase Cloud Messaging via the Firebase Admin SDK on the backend (free). No other Firebase service is needed.
- **Monorepo layout:** `/backend`, `/app` (React + Capacitor, with the `android` folder created by Capacitor), `/docs`, `docker-compose.yml`, `README.md`.

### Why native (important, do not skip)

A web page or plain React app cannot ring an alarm reliably when the phone is locked or the app is closed. So the alarm must be native Android code, in the Capacitor plugin:
- Schedule alarms with `AlarmManager.setAlarmClock` or `setExactAndAllowWhileIdle` (needs the exact-alarm permission).
- Show a full-screen activity over the lock screen (`USE_FULL_SCREEN_INTENT`, `setShowWhenLocked`, `setTurnScreenOn`).
- Speak with Android `TextToSpeech` using the locale of the person (`te-IN`, `ta-IN`, `hi-IN`, `en-IN`). If the voice pack is missing, open the system dialog to install it and fall back to a recorded voice note if one exists.
- Reschedule all alarms after reboot (`BOOT_COMPLETED` receiver) and after app update.
- Store the schedule locally on the phone (SQLite or SharedPreferences) so alarms work with no internet.
- Provide a "Setup" checklist helper: check and open the settings for notifications, exact alarms, lock-screen display, and battery optimization (including Xiaomi/Samsung/Oppo/Vivo autostart pages where possible).

## Platforms and languages

**Platforms.**
- **Family members can use iPhone or Android.** The family app is a mobile-first PWA that works in Safari on iPhone (installed to the Home Screen, with web push) and in Chrome on Android. Family login is by email, not phone number, and pairing is by family code, so the type of phone does not matter.
- **The grandfather's phone should be Android** (the full-screen alarm over the lock screen needs the native Android plugin). Do not build an iOS version of the alarm in the first release. Instead, in the README, explain the iOS limits: no exact full-screen alarm for normal apps, limited notification sound length, Critical Alerts need an Apple entitlement, native iOS builds need a Mac and Xcode, and installing on a real iPhone outside the App Store needs an Apple developer account (the free one expires every 7 days). Keep the code ready so an iOS phone can still receive push notifications later.

**Languages.**
- Support **English, Hindi, Telugu, Tamil** from day one, and make the language list data-driven so I can add Kannada, Malayalam, Bengali, Marathi, Gujarati, etc. by adding one i18n file and one locale entry (BCP-47 code such as `te-IN`, `ta-IN`, `hi-IN`, `en-IN`).
- Each person has their own voice language, independent of the family member's app language.
- Before saving a person's language, check on the device whether Text-to-Speech has a voice for it. If not, show a clear message and a button to install the voice pack.
- All sentences are drafts and must be reviewed by a native speaker.

## Design reference

Follow my prototype exactly for layout and flow: https://claude.ai/artifact/G928tx3txwKjqdeHGG6XGr (9 screens). Use these tokens:
- Colors: primary teal `#0B6E6E`, dark teal `#0B3C40`, cream background `#F6F2E9`, ink `#14262B`, muted `#4A5D62`, before-food chip `#FCE9C8` on `#6B3900`, after-food chip `#E1EEDD` on `#234D1B`, danger `#A61B1B`.
- Fonts: Inter, and Noto Sans Telugu / Tamil / Devanagari for those scripts.
- **Grandfather screens (route `/elder`)**: very large text (min 20px body, 28px main button), one main action per screen, touch targets at least 56px, Telugu first. Screens: Alarm, Today, Taken.
- **Family screens**: Family Home, Add a medicine, Missed dose alert, Daily routine (per person, period card only when enabled), Family members, Set up his phone.
- Accessibility: real buttons and labels, 4.5:1 contrast, no meaning by color alone.

## Product rules

1. **People and family.** One family has many "people we remind" (each with name, voice language te/ta/hi/en, photo optional) and many "family who get alerts" (roles: `EDITOR`, `VIEWER`). Only editors can change medicines.
2. **Pairing by code.** An editor creates a short one-time family code. On the grandfather's phone the app asks for the code and "Which person is this phone?". After that, the phone is linked to that person. Codes expire after 24 hours and can be used once. Do not use SMS/phone-number login (it costs money). Family members log in by email + password or email magic link.
3. **Medicine.** Name, "what the voice calls it" (e.g. `బీపీ మాత్ర`), photo, shape and color, optional notes. Each medicine has one or more **schedules**: time, days of week, food rule `BEFORE` / `AFTER` / `ANY`, `remindBeforeMin` (default 5), `repeatEveryMin` (default 5), `stopAfterMin` (default 30), active flag.
4. **Alarm behavior on the phone.** At `time - remindBeforeMin` the first reminder rings and speaks, repeats every `repeatEveryMin`, and **stops automatically** at `time + stopAfterMin` and is marked `MISSED`. Spoken sentence is built from a per-language template, for example Telugu: `"{person}, ఇది {voiceName}. {foodPhrase}."` Food phrases per language must be in the i18n files. Tapping the big "వేసుకున్నాను" button logs `TAKEN`, stops the alarm, and shows the Taken screen. A "listen again" button repeats the voice.
5. **Missed dose.** If a dose is `MISSED`, or no `TAKEN` arrives, the backend sends an FCM push to all family members, and the Family Home / Missed alert screen shows it. Actions: call him, replay reminder (sends a push that wakes his phone and rings), mark as taken.
6. **Sync.** When an editor saves a medicine or schedule, the backend stores it and sends an FCM **data** message `SYNC` to the person's phone. The phone fetches `GET /api/devices/me/sync`, stores the result locally, and reschedules all alarms. Also sync on app start and once a day. The phone queues `TAKEN` logs offline and uploads them later (idempotent by id).
7. **Daily routine.** Each person has routine items (time, label, days, optional spoken text) that ring like medicine alarms but with a calmer style. They are managed per person on the Routine screen.
8. **Period reminders (optional per person).** Store cycle start dates and average cycle length; show next expected date; send a spoken reminder N days before (default 2). Always show: "Dates are estimates and not medical advice."
9. **No medical advice.** The app never suggests doses or checks drug safety. Show a note: "Confirm medicines and food timing with the doctor."

## Backend design

Entities: `Family`, `FamilyMember` (user, role), `Person`, `Device` (person, FCM token, last sync), `PairingCode`, `Medicine`, `Schedule`, `DoseLog`, `RoutineItem`, `PeriodCycle`.
Endpoints (REST, JSON, prefix `/api`): auth (register, login), families, pairing (create code, redeem code), people, medicines, schedules, dose logs (`POST /dose-logs` batch, idempotent), routine items, period cycles, `GET /devices/me/sync`, `POST /dose-logs/{id}/replay`, `GET /families/{id}/today`.
Missed-dose job: a `@Scheduled` task every minute finds overdue schedules without a log, marks them `MISSED`, and sends FCM pushes.
Rules: every query is scoped to the caller's family; validate all input; store times with the person's time zone (default `Asia/Kolkata`); photos stored on local disk in dev and behind a storage interface for later cloud use; never log health data in plain logs; secrets come from environment variables; CORS limited to the app's origin; basic rate limiting on auth and pairing endpoints.

## Phases (stop after each one)

1. **Foundation:** monorepo, Docker Compose with Postgres, Spring Boot skeleton, Flyway, auth with JWT, React + Vite + Tailwind + i18n skeleton, README with run steps.
2. **Family data:** families, members, people, pairing code create/redeem, with tests. Family screens: Family members, Set up his phone (checklist UI only for now).
3. **Medicines and schedules:** full CRUD + the Add a medicine screen exactly like the prototype (language chips, food toggle, alarm behavior settings, voice preview text), Family Home with today's status.
4. **Android alarm plugin (Java):** Capacitor plugin with exact alarms, full-screen alarm activity, TextToSpeech in te/ta/hi/en, boot reschedule, local storage, setup-permission helper. Include a debug screen to fire a test alarm in 10 seconds. Write step-by-step manual test instructions for a real phone.
5. **Sync and dose logs:** FCM `SYNC`, sync endpoint, offline `TAKEN` queue, Taken screen, missed-dose job and push, Missed alert screen with actions.
6. **Routine and period:** routine items per person, period tracker, spoken reminders.
7. **Polish:** accessibility pass, empty/error/loading states, Telugu/Tamil/Hindi strings reviewed by a native speaker (mark them clearly as "needs review"), release build of the APK for direct install, documentation.

## Definition of done

- With the phone locked and the app closed, an alarm at 7:00 rings full-screen, speaks the Telugu name, and works with no internet.
- A medicine added on my phone shows up and rings on his phone within a minute (with internet).
- A missed dose sends a push to the family.
- Backend tests pass, the web app builds, and the README explains setup in under 10 steps.

## Notes

- All Telugu, Tamil, and Hindi sample sentences are drafts. Keep them in i18n files so a family member who speaks the language can correct them.
- Do not add features outside this list without asking.
