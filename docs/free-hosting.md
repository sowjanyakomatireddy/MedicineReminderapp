# Free hosting (no card)

Puts the server and the family app on the internet for free, so the family app opens on any phone (including
iPhone) and his phone syncs from anywhere with its own internet. Your computer can be off.

| Part | Service | Free plan |
|---|---|---|
| Database | [Neon](https://neon.tech) | Does not expire; sleeps when idle |
| Server and family app | [Render](https://render.com) | Server sleeps after 15 min idle |
| Keeps the server awake | [cron-job.org](https://cron-job.org) | Free |

Free plans can change. His **alarms never depend on the server**: his phone rings from its own saved schedule.

## 1. Database (Neon)

1. Sign up at **neon.tech** with Google or GitHub.
2. **Create project**: name `medicine-reminder`, Postgres 17 (or newest), region **AWS Asia Pacific (Singapore)**
   (closest to India).
3. On the project dashboard click **Connect**. Turn **Connection pooling off**. Copy the line that starts with
   `postgresql://`. This line contains the database password: paste it only into Render (step 2), never in chat
   or git.
4. **Settings → Compute**: keep the smallest size (0.25) and scale-to-zero on.

## 2. Server and family app (Render)

1. Sign up at **render.com** with **GitHub**.
2. **New → Blueprint** → connect the **MedicineReminderapp** repository → **Connect**. Render reads
   `render.yaml` and shows two services, `medicine-reminder-api` and `medicine-reminder-app`.
3. It asks for the values marked "sync: false":
   - `DATABASE_URL`: the Neon line from step 1.
   - `CORS_ALLOWED_ORIGINS` and `APP_URL`: `https://medicine-reminder-app.onrender.com`
   - `VITE_API_URL`: `https://medicine-reminder-api.onrender.com`
4. **Apply**. The first build takes 5–10 minutes.
5. Open each service and check its address at the top. If Render added letters (e.g.
   `medicine-reminder-app-x1y2.onrender.com`), fix the three values above under **Environment** and redeploy.
6. Check: `https://medicine-reminder-api.onrender.com/api/health` shows `{"status":"ok"}`.

## 3. Keep it awake (cron-job.org)

1. Sign up at **cron-job.org**.
2. **Create cronjob**: URL `https://medicine-reminder-api.onrender.com/api/health`, every **10 minutes**. Save.

This uses about 744 of Render's 750 free hours a month: one server, always on. The health check does not touch the
database, so the database can still sleep.

## 4. Use it

- **Family app:** open `https://medicine-reminder-app.onrender.com` on your phone. iPhone: Safari →
  **Share → Add to Home Screen**.
- **His phone:** build the app with the server address and install it:
  `scripts/build-release-apk.sh https://medicine-reminder-api.onrender.com` (see [release.md](release.md)).
- **Push** (optional): [firebase-setup.md](firebase-setup.md). On Render, paste the whole Firebase
  service-account JSON as the value of `FIREBASE_CREDENTIALS` on the server (a file path also works); add the
  `VITE_FIREBASE_*` values to the app service. If the key cannot be used, the log says "Push messages are off"
  and the server keeps running without push.

## What the free plan changes

- To keep Neon inside its free hours, the server lets the database sleep and checks for missed doses every
  **15 minutes** instead of every minute (`MISSED_CHECK_INTERVAL`). This backup only matters when his phone is
  offline; when it is online, his phone reports a missed dose itself at once.
- If the server ever sleeps, the first request takes about a minute; his phone simply syncs a bit later.
