# Zeekrstub

A tiny app that has to stay installed on a ZEEKR 7X head unit.

Zeekr's 2.1.1x global firmware added a signing-certificate check at install time.
Fresh installs of APKs whose certificate is not on their list fail at once. What
is already installed keeps its rights, and the community reading is that it can
keep updating in place from its original signing key. Nobody has proven an in-app
self-update completing on a patched global build yet. This app is that proof and
the lifeline at the same time.

- `applicationId` `com.fab.zeekrstub` — forever. A rename is a fresh install,
  which the gate may refuse.
- Signed with the public AOSP test key the reference repo uses, because Zeekr OS
  is built on AOSP test keys: if the platform accepts that key, this app is on
  the list by construction.
- Updates itself from a configurable GitHub repo.
- **Do not uninstall it. The install door is closed; this app is the window.**

## Screen

The head unit gives apps **1600 x 848 dp at density 2.0** (measured; reference
repo `docs/ui-spec.md`), landscape. One phone-sized column at that width is
unreadable from the driver's seat, so:

- `values-sw600dp/dimens.xml` carries the car type scale (32sp title, 22sp body,
  20sp muted, 64dp buttons), close to the reference app's own ramp.
- `layout-sw600dp/activity_main.xml` puts the facts left and the actions right;
  the phone layout stays one column.
- Content is capped at 1100 dp and centred; forms at 760 dp.

Verified at 1600 x 848 dp on the emulator, and at phone size.

## Build

Needs JDK 21, Android SDK with platform 36 and build-tools 36. AGP 9.0.0, Gradle
wrapper 9.1.0. `local.properties` holds `sdk.dir` with the colon escaped and
forward slashes; a backslash path there dies later at `compileJavaWithJavac` with
a bogus `IOException`.

```bash
./gradlew :app:testDebugUnitTest    # 80 unit tests, JUnit 4.13.2 + Robolectric 4.16
./gradlew :app:distRelease          # dist/zeekrstub-<versionName>.apk
```

The build refuses to run if `keystore/release.jks` is not the recorded
certificate (`verifyStubKey`). That check exists so the signing identity cannot
drift by accident: a new key means no future update, on a car with no door back
in.

Signing key: alias `apkeasytool`, store and key password `android`,
SHA-256 `A4:0D:A8:0A:59:D1:70:CA:A9:50:CF:15:C1:8C:45:4D:47:A3:9B:26:98:9D:8B:64:0E:CD:74:5B:A7:1B:F5:DC`.
Environment overrides (`STUB_KEYSTORE`, `STUB_KEYSTORE_PASSWORD`, `STUB_KEY_ALIAS`,
`STUB_KEY_PASSWORD`) exist for a different private key, but the identity rule
still stands: one key for the life of the app.

## Configuration

Every knob is a Gradle property in `gradle.properties` (override with `-P`):

| property | default | meaning |
|---|---|---|
| `stub.versionCode` | `1` | must strictly increase every release |
| `stub.versionName` | `1.0.0` | tag and release name |
| `stub.owner` | `Fabbbrrr` | default GitHub owner in the app |
| `stub.repo` | `zeekrstub` | default repo |
| `stub.tagFilter` | `v*` | default tag filter (`*` wildcard, `!` excludes) |
| `stub.apiHost` | `api.github.com` | API host, for a mirror or GHE |
| `stub.branch` | `main` | branch this build came from, shown on About |
| `stub.fallbacks` | empty | extra sources, `owner/repo` or an https APK URL, tried in order |

At runtime the same fields are editable in Settings, so a fork or a private
release train needs no new APK. See [RELEASES.md](RELEASES.md) for the bump.

## What it does

- **Fallback channels.** After the configured repo, the app tries each fallback:
  another `owner/repo`, or a plain https URL to an APK. One deleted repo, one
  rate limit or one blocked host no longer ends the only update path. A direct
  link has no release digest, so the app says plainly that only the package id,
  versionCode and signing key were checked.
- **Self-update.** Lists `api.github.com/repos/<owner>/<repo>/releases`, keeps
  the tags the filter accepts, picks the arm64 (or universal) APK, downloads it
  to the app's own external files dir, checks size and the release's SHA-256
  digest, reads the APK's real package, versionCode and certificate, and only
  then offers install. Equal versionCode, older versionCode, a different package
  id, or a different signing key are all refused with the reason printed.
  The `User-Agent` header is mandatory; without it GitHub answers 403.
- **Self-install guard.** A file that claims to be `com.fab.zeekrstub` is checked
  before the installer sees it: a different key, an equal or lower versionCode is
  refused with the reason, instead of a silent "app not installed".
- **Install route.** Silent `PackageInstaller` session only when the app really
  holds `INSTALL_PACKAGES`; otherwise the system installer UI. The permission
  state is re-checked before every install because this platform drops some
  appops across reboots. `canRequestPackageInstalls()` is shown but never used
  as a gate: the reference repo measured that it returns false inside the car's
  container while the installer still opens.
- **Manual install.** A browser limited to the app's own dirs, Downloads, and
  mounted removable volumes. Plain APKs only; xapk/apkm/apks are listed as out of
  scope. It also registers for `application/vnd.android.package-archive`, so
  another app can hand it a file.
- **Diagnostics.** A sorted, plain-text report: package inventory with installer
  and certificate per package, a highlight block for the store/installer/verifier
  packages, settings by name, filtered `getprop` with a SPOOFED marker when the
  container's `ro.product.*` disagrees with the real display id, loopback
  `127.0.0.1:5555` and mDNS `_adb-tls-*` reachability, and this app's own state.
  Every probe is caught on its own; `unavailable` is a valid printed answer.

## The installer rule

When the installer finishes, tap **BACK** at the top left. Tapping **OPEN**
wedges the head unit's installer and later installs and updates fail. The app
shows this before every install, because once the installer is in front it can
no longer say anything.

## Limits, stated plainly

- No root, no `su`, no shell assumptions beyond `/system/bin/getprop`, no system
  writes.
- No wake locks, no exact alarms, no foreground service. The hourly auto-check
  uses `JobScheduler`, is off by default, and is re-armed on each launch rather
  than persisted across reboot — persisting would need `RECEIVE_BOOT_COMPLETED`.
  It needs `ACCESS_NETWORK_STATE`: JobScheduler throws a `SecurityException` for a
  connectivity-constrained job without it.
- Split/bundle packages are out of scope.
- The car reads FAT32 sticks only.
- Whether a patched global build still accepts this key for a self-update is the
  measurement, not a promise. The report is how you find out.

## What the review found, and what changed

- The hourly check never scheduled on a first build: `ACCESS_NETWORK_STATE` was
  missing (WorkManager used to bring it). Now present, and `UpdateScheduler` logs
  the result instead of swallowing it.
- Our own package-archive handler appeared in the "Open with" chooser, so an
  install could loop back into the stub. Installs now target the real installer.
- A rebuilt release under an unchanged tag was a dead end. The "same version"
  dialog now offers **Download anyway**; the versionCode gate still decides.
- `PackageInstaller` sessions were committed through a second session handle.
  Now one handle writes and commits.
- Scoped storage hides Downloads and USB sticks on API 30+. The picker now offers
  the system file picker and an all-files-access link instead of an empty folder.
- Phone-scale type at car width, and a single column that overflowed 848 dp.
  Fixed by the sw600dp dimens and layout above.

See [INSTALL.md](INSTALL.md) for the step-by-step install and update procedure.

## Licence

GPL-3.0-only. The update mechanism, its checks and the diagnostics shape come
from [dts88/zeekr-shortcut-car](https://github.com/dts88/zeekr-shortcut-car)
(GPL-3.0-only, portions by wysie); see [NOTICE.md](NOTICE.md). Not affiliated
with, sponsored by, or endorsed by ZEEKR.
