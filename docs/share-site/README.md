# Mero share site

Published as the GitHub Pages **user site** `https://soujanyadasroy.github.io/` (repo `SoujanyaDasRoy/SoujanyaDasRoy.github.io`,
files from this folder at the repo root). It serves two things:

- `/.well-known/assetlinks.json`: tells Android that the app `com.mero`, signed with the certificate whose SHA-256 is listed,
  may open links on this host. With that, a tap on `https://soujanyadasroy.github.io/s?v=<videoId>` opens Mero directly.
  The fingerprint must match the key the installed APK was signed with (docs/signing-fingerprint.md).
- `/s/`: the page everyone without Mero lands on. It sends them to the same song in YouTube Music.

Nothing on the site is secret: a package name, a public certificate fingerprint and a redirect page.
`.nojekyll` makes GitHub publish the `.well-known` folder, which starts with a dot.
