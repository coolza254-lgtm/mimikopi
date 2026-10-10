# Installing ミミコピ on the Galaxy A54 (Android 16, One UI)

The app is not on Google Play. Releases are APK files on the public
[mimikopi-releases](https://github.com/coolza254-lgtm/mimikopi-releases/releases) page.

## First install
1. On the phone, open the releases page in Chrome or Samsung Internet and download the newest
   `mimikopi-vX.Y.Z.apk` (under "Assets").
2. Open the downloaded file (from the download notification or **My Files → Downloads**).
3. If Android says installing from this source is not allowed, tap **Settings** and turn on
   **Allow from this source** for the browser or My Files, then go back and tap **Install**.
4. If One UI blocks the install with a message about **Auto Blocker**:
   **Settings → Security and privacy → Auto Blocker** → turn it off, install, and you may turn it back on
   afterwards. (Auto Blocker exists since One UI 6; check the exact menu on your phone.)
5. If **Play Protect** shows "unknown app", choose **More details → Install anyway**. It may offer to
   scan the app; that is fine.
6. Open ミミコピ, go to **ข้อมูลเครื่อง** (Diagnostics), tap **คัดลอก JSON** and paste the result into the chat.

## Updating
- Until M2: download and install the newer APK the same way. Android updates the installed app in place
  and keeps your data, because every release uses the same package name (`com.id.mimikopi`) and the same
  signing key.
- From M2: **Settings → Check for updates** inside the app.
- If Android ever says "App not installed" / "package conflicts", **do not uninstall** without making a
  backup first: it means the signature differs, and uninstalling deletes your progress.

## Debug builds (optional)
CI also produces debug APKs (`com.id.mimikopi.debug`) as run artifacts. They install next to the real app,
have their own separate data, and are only for checking a build. Normal use: release APKs only.

## Android developer verification (checked 2026-10-10)
Confirmed from developer.android.com (overview, guides, FAQ):
- Since **30 Sep 2026**, Thailand (with Brazil, Indonesia, Singapore) enforces developer verification
  **only for installs from participating stores** (Google Play, Galaxy Store, HONOR, OPPO, Transsion,
  vivo, Xiaomi). The FAQ says: *"if users sideload your app directly, these new verification requirements
  won't apply to your app yet."* So installing ミミコピ from GitHub is not affected today.
- In **2027** Google plans to extend this to all apps on certified devices. After that, an
  **unregistered** app can only be installed **or updated** with the "advanced flow" turned on, or via ADB.

**Plan, before 2027:** register the app with a free **limited distribution account**
(no ID verification, no fee, up to 20 devices, for personal use):
1. Sign up at the Android Developer Console: https://android.google.com/developerconsole
   and choose the limited distribution account type.
2. Register the package name `com.id.mimikopi`. Registration asks for an APK signed with our release
   key. Use the published release APK (it carries the key; the private key is never needed).
   Release certificate SHA-256: see `docs/SIGNING.md`.
3. Add/invite the A54 as one of the allowed devices and accept the invitation on the phone.

Not confirmed (re-check when doing this): the exact device-invitation steps, and whether in-app
updates through `PackageInstaller` are treated like other sideloads.

**Advanced flow** (only if the app is not registered once enforcement reaches sideloading):
turn on Developer options → confirm you are not being coached → restart and re-authenticate → wait the
one-time 24 hours → confirm with fingerprint/PIN → allow installs from unverified developers
for 7 days or indefinitely. ADB installs are exempt.
