---
name: ship-release
description: Use when shipping a Mero build to friends — after finishing a feature request, fixing a bug, or re-syncing innertube. Builds a signed release APK, tags it, and publishes a GitHub Release that Obtainium auto-delivers to everyone's phone.
---

# Shipping a release

Mero's whole justification is turnaround: a friend asks, you ship, their phone
updates itself. This is that loop. Keep it cheap enough that shipping a
one-line fix is never a chore.

Distribution is **GitHub Releases + Obtainium**. Friends point Obtainium at the
repo once; every release afterward arrives automatically.

## Before building

- [ ] Working tree clean, tests green: `./gradlew :app:testDebugUnitTest`
- [ ] Bump `versionCode` (integer, +1 — **Obtainium and Android both compare
      this**, not `versionName`) and `versionName` in `app/build.gradle.kts`

Forgetting `versionCode` is the most common failure here: the release publishes
fine and simply never installs, because Android refuses a downgrade or
same-version install.

## Signing

Release builds must be signed with **the same keystore every time**. A new
keystore makes Android treat the APK as a different app, and friends get an
install failure that only a full uninstall — losing their library — resolves.

- Keystore lives outside the repo
- **Never commit the keystore or its passwords.** Verify `.gitignore` covers
  `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`
- Credentials come from `keystore.properties` (gitignored) or environment
  variables, never literals in `build.gradle.kts`
- Back the keystore up somewhere durable. Losing it ends the ability to ship
  updates to existing installs, permanently

If no keystore exists yet, ask the user to generate one — do not create signing
keys or handle their passwords on their behalf.

## Build

```bash
./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Test the release APK, not the debug one

**Release builds fail in ways debug builds cannot.** R8 minification strips
code that is only reached reflectively, and this project has three things that
depend on exactly that:

- `kotlinx.serialization` — stripped serializers surface as runtime crashes when
  parsing InnerTube responses, so search and playback break in release while
  working perfectly in debug
- **Ktor** engine and plugin classes
- **Media3** — some session and datasource classes

If release-only crashes appear, add keep rules to `app/proguard-rules.pro`
rather than disabling minification.

Install the actual release APK on a device and check:

1. Search returns results
2. A song plays
3. Whatever changed in this release actually works
4. Downloads still play offline (if M3 has shipped)

A green build is not evidence. Run it.

## Publish

```bash
git tag -a v<versionName> -m "v<versionName>"
git push origin main --tags

gh release create v<versionName> \
  app/build/outputs/apk/release/app-release.apk \
  --title "v<versionName>" \
  --notes "..."
```

Write release notes **for your friends, not for developers**. They asked for
features in plain language; describe what changed the same way. "Playlist
import no longer mangles remixes" beats "fix resolver scoring on title
normalization."

## After publishing

Obtainium picks the release up on its next check — no action needed from
anyone. Mention the release in the group chat if it fixes something someone
reported; otherwise let it arrive silently.

If a release is broken, publish a fixed version rather than deleting the bad
one. Obtainium may already have delivered it, and deleting a release does not
un-install it from anyone's phone.
