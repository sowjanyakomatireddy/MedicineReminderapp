# Testing the alarm on a real Android phone

The alarm must be checked on the grandfather's actual phone: phone makers change how alarms, the lock screen
and battery saving behave, and an emulator cannot show that. Allow about 30 minutes, plus one overnight test.

Write the result of each test in the table at the end.

## 1. Build and install (on the Mac)

1. Find the Mac's address on your home Wi-Fi: `ipconfig getifaddr en0` (for example `192.168.1.20`).
2. Start the backend as in the README. If macOS asks whether Java may accept incoming connections, choose **Allow**.
3. Build the app pointing at the Mac:
   ```sh
   cd app
   VITE_API_URL=http://192.168.1.20:8080 npm run build
   npx cap sync android
   cd android && ./gradlew assembleDebug
   ```
   The APK is `app/android/app/build/outputs/apk/debug/app-debug.apk`.
4. Install it, either:
   - **USB:** on the phone turn on *Developer options → USB debugging*, connect the cable, then
     `~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`; or
   - **File:** send the APK to the phone (WhatsApp to yourself, Google Drive, cable), open it, and allow
     "install unknown apps" when asked.

The phone and the Mac must be on the same Wi-Fi for pairing. Alarms themselves need no internet.

## 2. Before you start, on the phone

- **Telugu voice offline:** *Settings → System → Languages → Text-to-speech output → Google (gear) →
  Install voice data → తెలుగు (India)*. Without this the voice may need internet.
- **Alarm volume** up (it is separate from ringtone and media volume).

## 3. Tests

| # | Do this | Expected |
|---|---|---|
| A | In the family app: *Family → (person) → Set up phone → Show a family code*. On the phone open **Medicine Reminder**, type the code, tap తర్వాత, pick the person. | The phone shows **ఈ ఫోన్‌ను సెటప్ చేయండి** (Set up this phone) in Telugu. The family app shows "Phone linked" with the phone's model. |
| B | On the setup screen, tap each button and allow it in settings. Come back. | Each row turns green ✓. On Xiaomi/Redmi/POCO also allow **Autostart** and **Show on lock screen**; on Oppo/Vivo/Realme **Autostart**; on Samsung set battery to **Unrestricted**. |
| C | Look at the **తెలుగు వాయిస్** row. | ✓. If not, tap *వాయిస్ ఇన్‌స్టాల్ చేయండి* and install the Telugu voice. |
| D | Tap **10 సెకన్లలో టెస్ట్ అలారం మోగించండి**, then press the power button to lock the phone. | Within ~10 s: screen turns on, the big alarm shows **over the lock screen** (no unlock), alarm sound for ~3 s, then the Telugu sentence is spoken 3 times. |
| E | On the alarm, tap **మళ్ళీ వినండి**. | The sentence is spoken again. |
| F | Tap **వేసుకున్నాను**. | Big ✓ "బాగుంది! మందు వేసుకున్నారు.", sound stops, alarm closes after 4 s. |
| G | *Setup → సాంకేతిక వివరాలు* (debug). Tap **Medicine test alarm in 10 seconds**, lock, and **do not answer**. | Rings at +10 s, again at about +1 min and +2 min (each rings ~1 minute), stops by itself at +3 min. |
| H | Debug: set **Daily sample alarm** about 4 minutes ahead, tap **Set**, then **restart the phone**. Don't unlock. | It still rings 1 minute before the chosen time, over the lock screen. |
| I | Leave the sample alarm unanswered. Wait 6 minutes after the chosen time. Open the debug screen. | It stopped by itself. *Waiting to upload* shows **MISSED** for today. |
| J | Turn on **Airplane mode**, run test D again. | Rings and speaks exactly the same (no internet needed). |
| K | Put the phone on **silent / Do not disturb**, run test D again. | Still rings and speaks (alarms are allowed through by default; if not, allow *Alarms* in Do not disturb). |
| L | Use the phone (e.g. open YouTube), run test D. | A banner appears with **వేసుకున్నాను**; the voice still speaks. Tapping the banner opens the big alarm. |
| M | **Overnight:** in the debug screen set the sample alarm for **7:00**. Leave the phone untouched, locked and unplugged overnight. | At 6:59 it rings over the lock screen and speaks. This is the main "definition of done" test. |
| N | In the family app, *Set up phone → Unlink*. Open the app on the phone. | The phone goes back to the code screen; no more alarms ring. |

### Phase 5: real medicines, sync and missed doses

| # | Do this | Expected |
|---|---|---|
| O | In the family app, add a medicine for 4 minutes from now. On his phone, open the app (or, with Firebase set up, do nothing). | His **Today** screen lists it; it rings on time over the lock screen with its photo, name and Telugu sentence. |
| P | Tap **వేసుకున్నాను**. | The Taken screen: "చాలా బాగుంది!", the next medicine, and "✓ కుటుంబానికి తెలియజేశాం". The family app shows **Taken**. |
| Q | Turn on **Airplane mode**, let another dose ring and tap Taken. Then turn Airplane mode off and open the app. | Taken screen says the family will be told later. After going online, the family app shows **Taken**. |
| R | Leave a dose unanswered. | It stops by itself; the family app shows the red **Missed** banner (and, with Firebase, an alert on family phones). |
| S | On the Missed alert tap **Mark as taken**. | The family app shows Taken. |
| T | (Firebase only) On the Missed alert tap **Play the reminder again**. | His phone rings that medicine at once. |
| U | Press **Call family** on his Today screen. | The phone dialler opens with the family number. |

### Phase 6: routine and period reminders

| # | Do this | Expected |
|---|---|---|
| V | In the family app *Routine*, add "Drink a glass of water" (voice: "నీళ్ళు తాగండి") for 3 minutes from now. Open the app on the phone. | It rings softly (no siren, no vibration), shows "నీళ్ళు తాగండి" with **సరే**, no tablet picture, and says "అమ్మ, నీళ్ళు తాగండి." |
| W | Tap **సరే**. | It closes. Nothing appears as taken or missed in the family app. |
| X | Turn on *Period date reminders* for her, record a start date 26 days ago, and set the reminder time to 3 minutes from now. Open the app on the phone. | The Routine screen shows "In 2 days"; the phone speaks "అమ్మ, పీరియడ్స్ తేదీ దగ్గరలో ఉంది." at that time. |

Before handing the phone over: open the debug screen and tap **Cancel all alarms on this phone** to remove the
sample alarm (real medicines arrive in the next phase).

## 4. Results

Phone make and model: ____________ Android version: ______

| Test | Pass / fail | Notes |
|---|---|---|
| A | | |
| B | | |
| C | | |
| D | | |
| E | | |
| F | | |
| G | | |
| H | | |
| I | | |
| J | | |
| K | | |
| L | | |
| M | | |
| N | | |
| O | | |
| P | | |
| Q | | |
| R | | |
| S | | |
| T | | |
| U | | |
| V | | |
| W | | |
| X | | |

## Already checked on the Android emulator (Pixel 8, Android 17)

A (pairing), B (status of each step), C (Telugu voice found), D (full-screen over the lock screen, alarm
sound, Telugu speech at alarm volume, repeated), F (Taken), G-style repeat while locked, H (restart:
alarm re-planned from the phone's own storage), I (marked MISSED at the automatic stop and queued for upload;
test alarms not queued). Not possible on an emulator: real phone-maker battery rules, overnight Doze, silent
mode, and the actual sound.
