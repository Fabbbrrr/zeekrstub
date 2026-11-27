# Releases

Same `applicationId`, same signing key, higher `versionCode`. Equal versionCode
is refused by Android and reads as "the update did nothing"; a different key
breaks every later update.

## Current

| versionCode | versionName | tag | status |
|---|---|---|---|
| 5 | 1.0.4 | `v1.0.4` | diagnostics name the guest view; self-update measurement |
| 4 | 1.0.3 | `v1.0.3` | spare identity: two APKs per release |
| 3 | 1.0.2 | `v1.0.2` | fallback update sources; self-install guard |
| 2 | 1.0.1 | `v1.0.1` | back control on every screen; first self-update target |
| 1 | 1.0.0 | `v1.0.0` | first install, superseded by 1.0.1 |

## Cutting a release

1. Bump `stub.versionCode` in `gradle.properties`. It must be strictly higher
   than the number in the table above. Bump `stub.versionName` too: the app
   compares release tags to the installed versionName to decide there is an
   update, and compares versionCode to accept it. Both, every release.
2. Add the new row to the table above and set the previous row's status.
3. Run the gates:

   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:distRelease
   ```

4. Build **both** identities and check both artifacts before publishing:

   ```bash
   ./gradlew :app:distAll     # dist/zeekrstub-<ver>.apk and dist/zeekrstub-spare-<ver>.apk
   ```

   Attach both to the release. Each app picks only its own asset by name
   (`spare` token), so a release with one APK leaves the other identity with
   nothing to install.

5. Check the artifacts before publishing:

   ```bash
   BT=$ANDROID_HOME/build-tools/36.0.0
   $BT/aapt2 dump badging dist/zeekrstub-<versionName>.apk | head -4
   $BT/apksigner verify --print-certs dist/zeekrstub-<versionName>.apk
   ```

   Expect `versionCode` = the bumped number, `certificate SHA-256 digest` =
   `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`, and a file
   well under 5 MB.

6. Commit the bump, tag `v<versionName>`, push the tag.
7. Publish a GitHub release named `<versionName>` with both APKs attached and the
   SHA-256 digest in the body. GitHub computes the asset digest itself; the app
   verifies against it when present, and says so plainly when it is absent.
7. On the car: open the app, Check for updates, download, install. When the
   installer finishes, tap **BACK** at the top left — never **OPEN**.

## What the first post-patch self-update measures

If the install completes on a patched global build, the gate is on fresh installs
only and the community reading is confirmed. If it is refused, the report from
Diagnostics says why: install permission state, `INSTALL_PACKAGES` holdings,
installer handlers, and the certificate the platform saw.

Record the result in the release notes of that version.
