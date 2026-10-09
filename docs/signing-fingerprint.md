# Signing certificate

Every Mero APK (debug and release) is signed with this PC's `~/.android/debug.keystore`. A new key makes
Android refuse to update over an installed Mero, and the only way out is uninstalling, which wipes the
library. **Back that file up.** (The previous key was lost on 2026-10-09 and could not be recovered.)

SHA-256 of the certificate, current since 2026-10-09:

    69:50:37:04:05:7d:d3:9b:ae:34:32:3a:7a:77:a9:62:63:37:79:5c:a5:0f:fe:40:77:02:88:58:64:51:22:00

Check a build before it goes to anyone:

    apksigner verify --print-certs app/build/outputs/apk/release/mero-<version>-arm64-v8a.apk

and compare the "SHA-256 digest" line (without the colons) with the one above. If it differs, do not publish.
