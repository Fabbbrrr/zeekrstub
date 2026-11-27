# Installing apps with Zeekrstub

Two different jobs. Do not mix them up.

1. **Updating Zeekrstub itself** — the path this app exists to prove. Same
   applicationId, same key, higher versionCode.
2. **Installing another APK** — the stub hands the file to the system installer.
   Whether that install is allowed is the platform's decision, not the stub's.

First install of the stub has to happen while the door is still open (USB stick
or a file manager). After that: **do not uninstall it.**

## Self-update, every release

On the build machine:

1. Bump `stub.versionCode` and `stub.versionName` in `gradle.properties`. Both,
   every time: the app compares tags to decide there is an update, and Android
   compares versionCode to accept one.
2. `./gradlew :app:testDebugUnitTest :app:distRelease`
3. Check the artifact: `aapt2 dump badging` shows the new versionCode,
   `apksigner verify --print-certs` shows `a40d…f5dc`.
4. Tag `v<versionName>`, push, publish a release with the APK attached.

On the car:

5. Open Zeekrstub → **Check for updates** → **Download and verify** → **Install**.
6. When the installer finishes, tap **BACK** at the top left. Never **OPEN**.
7. Reopen Zeekrstub → About: versionCode changed, `matches recorded` = `yes`.
8. If the install is refused, Diagnostics → Save to file, and keep that report.
   It says which permission or key the platform objected to.

## Installing another APK (one at a time)

1. Put the APK on a **FAT32** stick (root folder is fine) or in the head unit's
   Downloads. exFAT and NTFS sticks do not mount on this car.
2. Zeekrstub → **Install an APK**.
3. Pick the file:
   - **Open the system file picker** — works even when Android hides Downloads
     and USB sticks from the app.
   - or browse **App files**, **Downloads**, **Removable**; grant all-files
     access when the app offers it, then come back.
4. Read the dialog. It shows the file, the install-permission state, the route,
   and the BACK-not-OPEN rule.
5. Tap **Install**. If "install unknown apps" is denied, either grant it in
   settings and retry, or **Continue anyway** — on this platform the system
   installer still opens.
6. Installer: tap **BACK** at the top left, never **OPEN**.
7. Confirm in Diagnostics: the package line shows the app, its installer and its
   certificate.

**More than one app:** repeat steps 3–6 per file. The stub installs one file per
pass; it does not batch. It copies nothing except a file another app hands it
through the share/open path, and that copy is capped at 200 MB.

## When an install is refused

- **Refused instantly, right after the installer screen**: the signing-certificate
  gate. That is the platform, not the stub. Only apps whose certificate is on
  Zeekr's list will install fresh.
- **`INSTALL_PACKAGES: not held`** on About is normal on a stock car. Without it
  the stub cannot install silently; it uses the system installer.
- **The stub itself updates fine but other apps do not**: that is the expected
  shape of the gate. Fresh installs are gated; in-place updates from the original
  key are not.
- Record the Diagnostics package line for the refused app, plus the settings and
  props sections. That is the evidence a maintainer needs.

## What not to do

- Do not uninstall Zeekrstub to "start clean". There is no door back in.
- Do not sign a later build with another key. Every update after that is refused.
- Do not tap OPEN on the installer screen.
- Do not expect a batch installer: split/bundle packages (xapk, apkm, apks) are
  out of scope, and the picker says so instead of failing silently.

## Re-signing another app with this key

This is the right instinct, with one correction: the stub is not what makes such
an app install. The certificate list is. Re-signing puts the APK on that list by
construction; the stub only carries the file to the installer.

```bash
# Re-sign before the first install, never after.
apksigner sign \
  --ks keystore/release.jks --ks-pass pass:android \
  --ks-key-alias apkeasytool --key-pass pass:android \
  --out app-resigned.apk app.apk
apksigner verify --print-certs app-resigned.apk   # expect a40d…f5dc
```

Then: Zeekrstub → Install an APK → the file → Install → BACK, not OPEN.

Rules that bite:

- **Before first install only.** If the app is already installed under another
  key, Android refuses the re-signed build as an update; the only way in is
  uninstall then fresh install, which is gated and loses its data.
- **Re-signing breaks signature-bound features.** Google Sign-In, Firebase,
  Play Integrity, Android Auto, and Widevine DRM all check the signer. A
  re-signed streaming client will likely fail to play protected content.
- **Upstream updates stop working.** The app's own updater wants its original
  key. Every later build of it has to come through you, signed with this key.
- **Anyone with this public key can sign an update** to the app you re-signed,
  and its own updater would accept it. Fine for a private car; not a trust story.
- **Licence still applies.** GPL or Apache obligations do not vanish because you
  changed the signature; you are now distributing a modified build.
- **Do not give it the stub's applicationId** unless you intend to replace the
  stub: same id + same key + higher versionCode turns `com.fab.zeekrstub` into
  that app, and the stub's update and install screens are gone with it.

If you want the app to be installed with no prompt at all, the stub must hold
`INSTALL_PACKAGES` (About says `held`). Without it, the system installer appears
and asks, exactly as it does for any file.
