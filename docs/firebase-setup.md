# Turning on push messages (Firebase)

Without Firebase the app already works: the grandfather's phone fetches changes when the app opens, after a
restart and once a day, and missed doses show up in the family app. Firebase adds three things:

- a medicine you add **reaches his phone within a minute** (SYNC push), even when his app is closed;
- **"Play the reminder again"** on the Missed alert rings his phone straight away (RING push);
- **missed-dose alerts on the family's own phones** (web push), even when the family app is closed.

Firebase Cloud Messaging is free. You need a Google account. About 15 minutes.

## 1. Create the project

1. Open <https://console.firebase.google.com> and choose **Create a project** (e.g. "medicine-reminder").
   Google Analytics is not needed.

## 2. The grandfather's Android app

1. In the project, **Add app → Android**. Package name: `app.medreminder`. Nickname: anything.
2. Download **`google-services.json`** and put it at `app/android/app/google-services.json`
   (it is in `.gitignore`).
3. Rebuild the APK (README, "The elder's Android app") and install it again. Nothing else changes in the code:
   the build turns push on when that file exists.

## 3. The server

1. **Project settings → Service accounts → Generate new private key.** A JSON file downloads.
   This is a secret: keep it outside the project folder, e.g. `~/secrets/medicine-reminder-firebase.json`.
2. In your `.env`:
   ```
   FIREBASE_CREDENTIALS=/Users/<you>/secrets/medicine-reminder-firebase.json
   APP_URL=http://localhost:5173          # the family app's address, used for links in alerts
   ```
3. Restart the backend. The log says **"Push messages enabled (Firebase Cloud Messaging)"**.

## 4. Family alerts in the browser (web push)

1. **Project settings → General → Your apps → Add app → Web.** Copy the values into `app/.env`:
   ```
   VITE_FIREBASE_API_KEY=...
   VITE_FIREBASE_PROJECT_ID=...
   VITE_FIREBASE_SENDER_ID=...      # "messagingSenderId"
   VITE_FIREBASE_APP_ID=...
   ```
2. **Project settings → Cloud Messaging → Web Push certificates → Generate key pair.** Copy the key:
   ```
   VITE_FIREBASE_VAPID_KEY=...
   ```
3. Restart `npm run dev` (or rebuild). Family Home now shows **"Get missed-dose alerts on this device"**.
   - Android / computer: tap **Turn on alerts** and allow.
   - **iPhone (iOS 16.4 or newer):** open the app in Safari, **Share → Add to Home Screen**, open it from the
     Home Screen, then tap **Turn on alerts**. Apple only allows web push for Home Screen apps.
   - Web push needs `https` (or `localhost`). For phones on your home network, use a real https address
     once the app is hosted (Phase 7).

## 5. Check it works

| Do this | Expected |
|---|---|
| Add a medicine in the family app for a time 3 minutes from now. Don't touch his phone. | Within a minute *Set up phone → Linked phones* shows a new "last sync", and the alarm rings on time. |
| Let a dose go unanswered. | Every family member with alerts on gets "Thatha has not taken a medicine". Tapping it opens the Missed alert. |
| On the Missed alert, tap **Play the reminder again**. | His phone rings that medicine at once, over the lock screen. If he taps Taken, the family app changes to Taken. |
