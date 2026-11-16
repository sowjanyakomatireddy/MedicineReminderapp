# How it is built

```
 Family member's phone / computer                    Elder's Android phone
 ┌───────────────────────────────┐                   ┌──────────────────────────────────────────┐
 │ Family app (React PWA)        │                   │ Same React app in Capacitor (/elder)      │
 │ Home · Medicines · Routine ·  │                   │ Pairing · Setup checklist · Today         │
 │ Family · Missed alert         │                   │ ─────────────────────────────────────────│
 │ sw.js shows web-push alerts   │                   │ Native alarm (Java, MedAlarm plugin)      │
 └──────────────┬────────────────┘                   │ AlarmScheduler → AlarmManager.setAlarmClock│
                │ JSON over https (user token)       │ AlarmService: sound + Text-to-Speech      │
                ▼                                    │ AlarmActivity: full screen over lock      │
 ┌───────────────────────────────┐   device token    │ SyncClient: sync, upload queue, photos    │
 │ Backend (Spring Boot)         │◄──────────────────┤ PushService: SYNC / RING                  │
 │ /api/... families, people,    │                   │ BootReceiver: re-plan after restart       │
 │ medicines, routine, period,   │                   │ AlarmStore: schedule + queue on the phone │
 │ pairing, dose logs, sync      │                   └──────────────────────────────────────────┘
 │ MissedDoseJob (every minute)  │                                   ▲
 │ Notifier → PushSender         │──── Firebase Cloud Messaging ─────┘ (SYNC, RING)
 └──────────────┬────────────────┘──── web push ──► family phones (missed-dose alerts)
                ▼
          PostgreSQL (Flyway migrations V1–V5)
```

## The main flows

**Adding a medicine.** The family app saves it (`POST /api/medicines`). After the commit, the backend sends the
person's phone a data-only **SYNC** push. The phone's `SyncClient` (native, works with the app closed) uploads its
queue, fetches `GET /api/devices/me/sync`, builds alarms with `AlarmBuilder` from the person's sentence templates,
and re-plans every alarm. Without push, the same sync runs when the app opens, after a restart, and daily.

**A dose ringing.** `AlarmScheduler` keeps one exact alarm-clock entry per schedule: the next ring, or the automatic
stop. At each ring, `AlarmService` posts the full-screen notification, plays the alarm sound and speaks the sentence
at alarm volume. **Taken** marks the dose handled, queues a TAKEN log (id made on the phone, so uploads are
idempotent) and shows the Taken screen. At the automatic stop with no answer, the dose is queued as MISSED.

**A missed dose.** The phone's MISSED log reaches the backend; if the phone is off, `MissedDoseJob` marks it MISSED
2 minutes after the stop time. Either way, `Notifier` alerts every family member in their own language. The Missed
alert screen can call, **replay** (a RING push that rings his phone now), or **mark as taken**. Taken always replaces
Missed, never the other way round.

**Languages.** All words live in `app/src/i18n/locales/*.json`. The elder screens and his alarm use the person's
voice language, not the family member's app language. The native side receives the raw templates once
(`setProfile`), so a background sync can build Telugu sentences without the app running.

## Where things are

| Part | Code |
|---|---|
| API and rules | `backend/src/main/java/app/medreminder/{family,person,pairing,device,medicine,dose,routine,period,sync,push,today}` |
| Database | `backend/src/main/resources/db/migration` |
| Family screens | `app/src/pages/*` (not `Elder*`) |
| Elder screens | `app/src/pages/Elder*.tsx`, `app/src/elder/` |
| Bridge to native | `app/src/native/` |
| Native alarm | `app/android/app/src/main/java/app/medreminder/alarm/` |
| Words | `app/src/i18n/locales/`, `backend/src/main/resources/i18n/` |

Decisions and their reasons: [decisions.md](decisions.md).
