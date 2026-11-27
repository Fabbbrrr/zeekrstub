# Notice

## Origin

The update mechanism in `app/src/main/java/com/fab/zeekrstub/update/` is a port
of the approach in [dts88/zeekr-shortcut-car](https://github.com/dts88/zeekr-shortcut-car)
(GPL-3.0-only, portions copyright wysie): list releases instead of
`/releases/latest`, mandatory `User-Agent`, https-only redirects, SHA-256 and
size verification against the release asset, and the installer BACK-not-OPEN
warning. The diagnostics report follows the shape of that repo's
`DiagnosticsCollector`, whose export UX it mirrors. Those decisions were learned
on this car; the code here is rewritten for a much smaller app.

This project is GPL-3.0-only for that reason.

## Signing key

`keystore/release.jks` is a copy of the reference repo's checked-in public AOSP
test keystore: alias `apkeasytool`, store and key password `android`, certificate
SHA-256
`A4:0D:A8:0A:59:D1:70:CA:A9:50:CF:15:C1:8C:45:4D:47:A3:9B:26:98:9D:8B:64:0E:CD:74:5B:A7:1B:F5:DC`.

It is public on purpose. Zeekr OS is built on AOSP test keys, so an app signed
with this key may already be accepted by the platform's install check. The key is
also why the update path verifies the release digest: anyone can sign with it, so
the app proves the bytes came from the release page, not that the publisher is
trustworthy.

The identity must never change. `verifyStubKey` fails the build if the keystore
on disk is not this certificate.

## Trademarks

ZEEKR is a trademark of its owner. This app is unofficial and is not affiliated
with, sponsored by, or endorsed by ZEEKR. It is not certified for any vehicle
safety function.
