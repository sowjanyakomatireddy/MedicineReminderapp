# Decisions log

Small decisions made during the build, so they can be revisited.

## Phase 1

- **Package / folder:** Java package `app.medreminder`; project lives in `medicine-reminder/`.
- **Spring Boot 3.5.x**, compiled with `--release 21` (works on any JDK 21+).
- **No Lombok**, to keep the build simple on new JDKs.
- **Rate limiting** is a small in-memory per-IP filter (fixed one-minute window). Fine for one server;
  replace with Bucket4j + Redis if we run more than one instance.
- **JWT lifetime** defaults to 7 days (`JWT_TTL`). No refresh tokens yet.
- **Email magic link** is not built yet: it needs an email-sending service (SMTP). Email + password works now.
- **Fonts:** the font stack lists Inter then all Noto Sans fonts, so the browser picks the right one per character.
  Mixed text (an English screen showing a Telugu medicine name) renders correctly.
- **Tailwind v4** (CSS-first `@theme` tokens) instead of a `tailwind.config.js`.
- **Elder screens** use the person's voice language, not the family member's app language
  (fixed to Telugu until pairing exists).

## Phase 2

- **One code system, two purposes.** `DEVICE` codes link the elder's phone to a person; `MEMBER` codes let a
  family member join with a role (the prototype says "Family members join with a code"). Both expire after
  24 hours and work once (an atomic `UPDATE … WHERE used_at IS NULL` stops two phones using the same code).
- **Code format** `K7QM-4XPA`: 8 characters from 32 easy-to-read symbols (no 0/O, 1/I). Typed in any case,
  with or without the dash. Only a SHA-256 hash is stored. Wrong, used, expired and wrong-purpose codes all get
  the same error, so nothing is leaked.
- **Pairing is two calls**: `lookup` (shows the family's people, does not use up the code) then `redeem`
  ("Which person is this phone?"). Both need no login and are rate-limited with the rest of `/api/pairing`.
- **One family per user** in this release (`family_member.user_id` is unique). The last editor cannot step down
  or leave.
- **Device token**: a paired phone gets its own JWT (`typ=device`) with no expiry, because the elder cannot log
  in again. It is checked against the `device` table on every request, so *Unlink* stops it at once.
  Device tokens can only call `/api/devices/me/**`; family endpoints need a user token.
- **API shape**: the caller's family is `/api/families/me` (a user is in one family). People are `/api/people`.
  Other families' data always returns 404, never 403, so IDs cannot be probed.
- **Voice language** is stored as a short code (`te`); the app maps it to the TTS locale (`te-IN`). The backend
  only checks the format, so adding a language needs no backend change.
- **Deferred:** person photos (built in Phase 3 with the photo-storage interface for medicine pictures);
  the on-phone "is this voice installed?" check and real permission status in the setup checklist (Phase 4,
  native plugin). Until then, checklist ticks are remembered per browser, and step 1 ticks itself once a phone
  is linked.

## Phase 3

- **Voice language chips on Add a medicine** show the person's voice language. Changing one updates the
  person (it applies to all their reminders) and the screen says so. A medicine has no language of its own,
  because its spoken name is written in the person's language.
- **The spoken sentence is built in the app** from per-language templates in the i18n files
  (`voice.sentence`, `voice.foodBEFORE/AFTER/ANY`). The preview uses the same function the Android alarm will
  use in Phase 4, so what you read is what is spoken. The backend stores no sentences.
- **Times are saved together with the medicine** (`POST/PUT /api/medicines` carry a `schedules` list).
  On update, times with an `id` are changed, new ones are added, missing ones are deleted, so ids stay stable
  for dose logs. Each time can be paused (`active`).
- **Alarm behaviour** (first reminder, repeat, auto-stop) is stored per time, as in the brief, but the screen
  sets it once for all times of a medicine, as in the prototype. Food rule is per time (e.g. morning before
  food, night after food).
- **Days** are a 7-bit mask in the database (Mon = 1 … Sun = 64) and day names in the API.
  Times are `HH:mm` local time in the person's time zone.
- **Shape and colour** are optional fixed lists (so they can be translated and drawn as an icon);
  **notes** are free text. Neither is in the prototype screen; both are in the brief.
- **Photos** (medicine and person) go through a `PhotoStorage` interface (since hosting: `DatabasePhotoStorage`,
  see "Free hosting" below). The type is checked from the file's first bytes (JPEG/PNG/WebP only, 5 MB max), files get random
  names, and old files are deleted when replaced or when the medicine/person is deleted. The app shrinks phone
  photos to 1024 px before upload. Photos need a login, so the app fetches them with the token.
- **Today's status** (`GET /api/families/me/today`) is worked out in each person's time zone:
  *Upcoming* → *Ringing now* (from the first reminder until the auto-stop) → *Not confirmed yet*.
  Phase 5 replaces "not confirmed" with *Taken* or *Missed* from the phone's dose log.
- **"Save and send to …'s phone"** saves now; the push that wakes the phone to fetch the change is Phase 5.
- **Listen** on the voice preview uses this browser's speech, only shown if it has a voice for the language.

## Phase 4

- **Capacitor 8.6** with a **local Java plugin** (`MedAlarm`, registered in `MainActivity`) instead of a separate
  npm plugin package: one app, one build, nothing to publish.
- **Gradle wrapper 9.1.0** instead of Capacitor's 8.14.3: Capacitor 8 needs Java 21+, and Gradle 8.14 does not
  run on Java 25 (the Mac has Java 17 and 25). 9.1 is the first Gradle that runs on Java 25.
- **`AlarmManager.setAlarmClock` for every ring**, including the 5-minute repeats. It is exact, wakes the phone
  from Doze, and is not throttled (the "allow while idle" kind is limited to about once per 9 minutes in Doze,
  which would break 5-minute repeats). Side effect: Android shows an alarm-clock icon in the status bar.
- **One planned event per alarm**: the next ring, or the automatic stop (marks MISSED). After each event the next
  one is planned, so nothing piles up. After a restart, update, or clock/time-zone change, everything is planned
  again from what is stored on the phone (SharedPreferences).
- **A dose missed while the phone was off** is marked MISSED when the phone comes back, but only for alarms that
  already existed at that time (a 7:00 medicine added at 10:00 is not "missed" today).
- **Sound and voice come from a short foreground service** (type media playback), not the alarm screen, so they
  also work when the phone is in use and Android shows a banner instead of the full screen. It rings for about a
  minute (alarm sound 3 s, then the sentence 3 times) and goes quiet until the next repeat; the notification
  stays so *Taken* still works. Text-to-Speech and the sound use the **alarm** volume.
- **Routine reminders** (Phase 6) use the same machinery with `kind: ROUTINE`: no siren or vibration, spoken
  twice, their own notification channel.
- **Everything the alarm says or shows is built in the React app** in the person's language (sentence, chips,
  button words) and handed to the native side. Native strings are only English fallbacks.
- **Fallback when there is no voice**: a family voice recording if the alarm has one (`voiceNoteUri`), otherwise
  the alarm sound. Recording voice notes is not in any phase; the hook is ready if you want it.
- **Voice check**: the elder's setup screen checks the phone's Text-to-Speech for the person's language and offers
  the system "install voice data" screen. The family app's *Listen* only checks the family member's own device.
- **Maker settings pages** (Xiaomi, Oppo, Vivo, Samsung, Huawei, OnePlus): opened only if the page really exists
  on the phone (checked through the package manager; the packages are declared in `<queries>`). A bug found on the
  emulator: `Intent.resolveActivity()` does not check explicit components, so the first version offered an
  "Autostart" button on a Google phone.
- **Exact alarms**: `USE_EXACT_ALARM` (granted automatically on Android 13+ to alarm-type apps) plus
  `SCHEDULE_EXACT_ALARM` for Android 12. Google Play restricts `USE_EXACT_ALARM` to alarm and calendar apps; we
  install the APK directly, and this app is an alarm app anyway.
- **The Android app opens on the elder screens** (`/` → `/elder`). Family members use the PWA.
- **Debug builds allow plain `http`** (home-network backend); release builds keep Android's default (https only).
- **Not done here (later phases)**: real medicines reaching the phone (sync, Phase 5), uploading the TAKEN/MISSED
  queue (Phase 5), medicine photos on the alarm screen (needs the sync to download them), the family's
  "Ring a test alarm now" button (needs a push, Phase 5).

## Phase 5

- **Push is optional.** Firebase needs a project only you can create. Without it (no `FIREBASE_CREDENTIALS`,
  no `google-services.json`), the phone syncs when the app opens, after a restart, and once a day, and missed doses
  still appear in the family app. With it, changes arrive within a minute, "Play the reminder again" works, and
  family phones get alerts. Setup: `docs/firebase-setup.md`.
- **The phone syncs natively** (Java, `SyncClient`), so a SYNC push or the daily sync works with the app closed.
  For that, the React app gives the native side the person's **sentence templates and word lists** (from the
  i18n files) once; `AlarmBuilder.java` fills them in. Translations still live only in the i18n files.
- **Sync endpoint** `GET /api/devices/me/sync`: person, medicines (with times, including paused ones), and today's
  and yesterday's answers, so a dose the family marked taken does not ring. The phone keeps the last sync for the
  offline Today screen, and downloads medicine photos to show on the alarm offline.
- **Dose logs**: one answer per dose (`schedule_id + date` unique). The phone's log ids are its own, so re-sending
  is harmless (`POST /api/devices/me/dose-logs`, batch). **Taken beats Missed**: a late Taken (phone back online,
  a replayed reminder, or the family's "Mark as taken") replaces Missed, never the other way round.
  Logs from test or sample alarms are accepted and dropped so the phone's queue empties.
- **Missed-dose job** (every minute): a dose with no answer by its automatic stop **+ 2 minutes** (time for the
  phone's own report) is marked Missed by the server and the family is alerted. It skips doses older than 12 hours
  and times added after that dose had started. Single server; with several, add a lock (e.g. ShedLock).
- **Family alerts** go to every family member's registered browsers/phones, each in their **own app language**
  (backend text in `src/main/resources/i18n/messages_*.properties`, drafts marked for review). Tapping opens
  `/missed/{id}`. Invalid push tokens are deleted automatically.
- **"Play the reminder again"** sends RING with the dose's date; the phone rings it at once, and Taken then counts
  for that dose (replacing Missed). Without push or a reachable phone the screen says so and suggests calling.
- **Phone numbers** (optional, on the person): *their phone* for "Call Thatha" on the Missed alert, and *the family
  number their phone calls* for "Call family" on his Today screen. Neither was in the brief's data model; both are
  needed by buttons in the prototype.
- **API paths**: phone endpoints live under `/api/devices/me/...` (device token only); family endpoints are
  `/api/dose-logs/alerts`, `/api/dose-logs/{id}`, `/{id}/taken`, `/{id}/replay`, `/api/people/{id}/week`,
  `/api/me/push-tokens`. The brief's `POST /dose-logs` batch is `POST /api/devices/me/dose-logs`.
- **Web push without Firebase's service-worker library**: our own small `public/sw.js` shows the alert and opens
  the link; the Firebase web SDK is only loaded (as separate chunks) when someone taps "Turn on alerts".
- **The family's "Ring a test alarm now" button** on Set up phone stays off: the debug screen on the phone does
  this, and with push the Missed alert's replay covers ringing from afar.

## Phase 6

- **Routine items** have a *label* for the family (any language) and optional *spoken words* in the person's
  language. If the spoken words are empty, the voice reads the label: the form says so and shows a preview.
- **Routine reminders ring "calmer"**: no siren or vibration, the sentence spoken twice, once more after 5 minutes,
  then stop after 10. The button is **సరే** (OK), there is no tablet picture or medicine chips, and **nothing is
  logged**: only medicines are taken or missed. (Found on the emulator: the first version showed a tablet picture on
  routine and period reminders, which could be mistaken for a dose; fixed.)
- **Period reminders are off by default**, turned on per person (person form or the Routine screen's settings).
  - **Next expected date** = last recorded start + average cycle. The average is over the last 6 gaps between
    recorded starts, ignoring gaps outside 15–60 days (likely a missed or mistyped date); until there are two dates,
    the "usual cycle length" setting (default 28) is used. If a start was not recorded, the estimate moves on by
    whole cycles once the expected days have passed.
  - **Spoken reminder** N days before (default 2) at a set time (default 9:00), as a one-off alarm on that date.
  - **Any family member can record "Period started today"** (or another date) and remove a date; **only editors
    change the settings**. The dates are visible to everyone in the family group.
  - "Dates are estimates and not medical advice." is shown on the card and the screen.
- **API**: `/api/people/{id}/routine` (list, add), `/api/routine/{id}` (change, delete);
  `/api/people/{id}/period` (read, settings), `/period/starts` (record), `/period/starts/{date}` (remove).
  The phone's sync now also carries `routine` and the next `period` reminder.
- **The elder's Today screen still lists medicines only**, as in the prototype; routine reminders just ring.

## Phase 7

- **Dependency warnings fixed (0 left):** React Router 6 → 7.18.5 (an open-redirect issue in `<Link>`; no code
  changes needed), vitest 3 → 5.1 (test tool only), and the full `firebase` bundle replaced by just
  `@firebase/app` + `@firebase/messaging` (the bundle pulled in Firestore's gRPC, which we never use).
- **No banner over an open alarm:** if the alarm screen is already showing when a repeat rings, the notification is
  updated quietly; if it was closed, the repeat posts fresh so the full screen opens again. Checked on the emulator.
- **Accessibility:**
  - Elder screens now meet the brief everywhere: text at least 20px, touch targets at least 56px.
  - Every colour pair was measured against WCAG AA: all pass (the lowest is 5.1:1). Disabled buttons fade less
    (75%) so they stay readable.
  - axe-core checks run on every main screen in the test suite (labels, names, roles, ARIA, duplicate ids). A probe
    confirmed axe does catch real problems in the test browser.
- **Error, empty and loading states:** every load error offers **Try again**; a crash shows a calm message with
  **Reload** instead of a blank page; the family app says when it is offline. Bug fixed: if an existing person
  failed to load, the edit page fell back to an empty "Add" form, which would have created a duplicate.
- **Translation review kit:** `npm run translations:export` makes one CSV per language (339 strings each, app and
  backend) with English, draft, an empty "Corrected" column and notes (placeholders to keep, spoken lines, singular
  and plural). `npm run translations:import` applies corrections and refuses any that drop a placeholder. Languages
  stay marked "needs review" until a reviewer signs off.
- **Release APK:** signed with a key kept outside the project (`~/.medicine-reminder/`, made by
  `scripts/create-release-key.sh`); `scripts/build-release-apk.sh https://…` refuses plain http. Not shrunk with R8
  (Capacitor finds plugins by reflection). Hosting is not part of the project; `docs/release.md` lists options.

## Free hosting (after Phase 7)

The family lives apart from him and wanted it free with no card, opened from an iPhone.

- **Neon (database) + Render (server, family app) + cron-job.org (keep awake)**, all without a card. Oracle's
  free server needed a card; Render's own free database is deleted after 30 days.
- **Photos moved into the database** (`photo` table, V6) because Render's free disk is wiped on restart.
  `LocalDiskPhotoStorage` and `PHOTO_DIR` are gone; photos saved to `uploads/` before this are not carried over.
- **`DATABASE_URL`** (`postgresql://user:pass@host/db`) is accepted as hosts give it; `DB_URL`/`DB_USER`/
  `DB_PASSWORD` still work.
- **Neon's free compute hours** do not cover a database awake all month, so on Render the pool closes idle
  connections and the missed-dose backup check runs every 15 minutes (`MISSED_CHECK_INTERVAL`, default 1 minute
  elsewhere). The phone still reports its own misses at once when online.
- Steps: [free-hosting.md](free-hosting.md).
