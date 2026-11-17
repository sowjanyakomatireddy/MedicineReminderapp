# Release APK and hosting

The debug APK (README step 6) is fine for trying things at home. For his everyday phone, build a **signed release
APK**: it is smaller, not debuggable, and can be updated in place.

## 1. The signing key (once)

```sh
scripts/create-release-key.sh
```

This creates `~/.medicine-reminder/release.jks` and `~/.medicine-reminder/keystore.properties` (random password),
**outside** the project so they can never be committed.

**Back up both files** (USB stick or a password manager). Every update must be signed with the same key. Without it
you cannot install an update over the existing app: you would have to uninstall it (his phone loses its saved
schedule until it syncs again and must be paired again) and install the new one.

## 2. A server the phone can reach over https

**Free, no card:** [free-hosting.md](free-hosting.md) (Neon + Render). Other options:

Release builds only talk **https** (Android's default; it protects his data on public Wi-Fi). The family app and
his phone both need to reach the backend:

- **A small cloud server** (any VPS): run the backend jar with your `.env`, PostgreSQL, and a web server that gets a
  free certificate automatically (e.g. Caddy: `your-domain { reverse_proxy localhost:8080 }`). Serve the family app
  (`app/dist` after `npm run build`) from the same domain, and set `APP_URL` and `CORS_ALLOWED_ORIGINS` to it.
- **Or a computer at home**, exposed with an https tunnel (e.g. Cloudflare Tunnel or Tailscale Funnel). His phone
  then also reaches it when away from home.

Hosting is not part of this project; any of the above works with the backend as it is
(`cd backend && mvn package` → `target/medicine-reminder-backend-0.1.0.jar`, run with `java -jar`).

If you only ever use it at home and do not want https, keep using the **debug APK**: it has every feature and is
allowed plain `http` on your home network.

## 3. Build and install

```sh
scripts/build-release-apk.sh https://your-domain.example
```

Output: `dist-apk/medicine-reminder-<version>.apk` (git-ignored). Install it like the debug APK (USB with
`adb install`, or send him the file). If the debug APK is already installed, uninstall it first: the two are signed
with different keys.

**Before each new release**, raise `versionCode` (and `versionName`) in `app/android/app/build.gradle`, so the
phone accepts it as an update.

## What was checked

On an Android 17 emulator: the release APK installs, opens on the pairing screen, is signed with the release key,
targets Android 16 (API 36), is not debuggable, and has no plain-http permission.
