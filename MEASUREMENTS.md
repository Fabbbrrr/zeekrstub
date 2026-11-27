# Measurements

Dated, on the car. Facts only, with the method that produced them. A guess belongs
in a decision note, not here.

## 2026-11-27 — in-app self-update works

**Result: a package signed with the public AOSP test key updated itself in place
on this head unit, started from inside the app.**

| step | evidence |
|---|---|
| Installed 1.0.3 (versionCode 4) | `[1 packages]` line for `com.fab.zeekrstub` |
| Tapped **Check for updates** in the app | owner's account, no adb, no stick |
| Downloaded + SHA-256 verified against the release asset | status line before install |
| Installer completed | `[5 this app] version_code=5`, `version_name=1.0.4`, `cert_matches_recorded=true` |
| Spare identity also at code 5 | `com.fab.zeekrstub.spare | 5/1.0.4` |

Second, independent data point from the same session: **ZeekrShortcut moved
4.12.0 → 4.20.0** under the same certificate `a40d…f5dc`. Its updater is older
and does nothing clever, so the update path is not a trick of this app.

Conclusion: the certificate gate does **not** block in-place updates for a
package that is already installed and keeps its key. That is the strategy's
premise, now measured rather than assumed.

## 2026-11-27 — what the guest reports about itself

The app runs inside the App Lab guest (a Qualcomm GVM), so its permission answers
describe the guest, not the head unit. Read them that way.

```
virtualized=yes
count=13                      packages visible in the guest
install_sources=unavailable    every line lacks an installer
install_permission=denied      REQUEST_INSTALL_PACKAGES appop
install_packages_held=denied   INSTALL_PACKAGES (signature|privileged)
tcp.127.0.0.1:5555=closed (1003 ms)
mdns._adb-tls-pairing._tcp=not-found (3001 ms)
mdns._adb-tls-connect._tcp=not-found (3000 ms)
installer_handler=resolvable:2  (names added in 1.0.5)
```

Build facts from `[3 props]`: `ro.build.type=userdebug`,
`ro.build.tags=test-keys`, `ro.build.display.id=zeekr_dhu_sa8295_ovs-userdebug …`,
`ro.product.first_api_level=32`, `ro.ota.allow_downgrade=true`,
`persist.adb.tcp.port=5555`, `init.svc.adbd=running`.

Consequences:

- **adb is not a route.** The port is closed from inside the guest and mDNS
  advertises nothing, despite adbd running. Do not plan around adb.
- **`install_packages=denied` is the guest's answer.** It says nothing about
  whether the host would grant it.
- **Installs are proxied.** Nothing in the guest's package list claims
  `application/vnd.android.package-archive`, yet installs complete. The installer
  is host side.

## Open questions

1. **Which installers resolve?** `resolvable:2` — ours plus one unknown. 1.0.5
   prints the names. If the second is a vendor store, the gate is installer-based.
2. **Does a fresh install of a foreign-key APK work today?** Untested. This is the
   difference between "new apps can still arrive" and "only updates".
3. **Does the host enforce a certificate list at all right now?** Untested on a
   fresh install; not observable from the guest.

## How to settle the fresh-install question

Download the two probes from the `probes-1` release, put them on the stick, and
install each one from Zeekrstub → Install an APK. They are empty packages; the
only thing being measured is whether the gate accepts them.

| test key | foreign key | reading |
|---|---|---|
| installs | installs | no cert gate right now — install the shelf today |
| installs | refused | cert allowlist and our key is on it — re-sign other apps |
| refused | refused | only in-place updates pass; new packages are closed |

The probes are inert, so uninstall whichever lands.

## Why not a CVE

The app runs in a VM guest. An app in the guest can attack the guest kernel or
the hypervisor; there is no public escape for Gunyah/GVM, and the failure mode is
a bricked head unit. The cheaper paths are configuration, not exploitation: a
`userdebug` build with `adb` enabled would give root over a service USB port, and
that is a build property, not a vulnerability. Check for a service port before
spending weeks on an exploit.

## How to take a report

Zeekrstub → Diagnostics → **Save to file**, then get the file off the car. Keep
the date and the firmware id from `[3 props]` with it. Two reports with different
`generated_utc` and the same firmware are the baseline for a diff after a patch.
