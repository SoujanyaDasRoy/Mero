# Security

## The `AIzaSy…` keys in `innertube/` are not a leak

GitHub's secret scanner flags these, and the alert is a false positive. They are
in `innertube/src/main/java/com/zionhuang/innertube/models/YouTubeClient.kt`:

```
AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI   // YouTube Android client
AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3    // YouTube Music (WEB_REMIX)
AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc   // used by /player requests
```

**These are YouTube's own public client keys.** They are hardcoded into
YouTube's web player and mobile apps and can be read out of any YouTube page's
JavaScript. They are not credentials belonging to this project or to anyone who
forks it.

Concretely:

- They are **not tied to any Google Cloud project we own**, so there is no quota
  or billing to abuse.
- They **cannot be rotated by us** — they belong to Google.
- Rotating or removing them would break the app without improving anything,
  because the InnerTube API cannot be called without them.
- Every project in this space ships the same constants: NewPipe, InnerTune,
  yt-dlp and others.

The detector matches the *shape* of a Google API key (`AIzaSy` + 33 chars)
without knowing whose key it is. That is all that has happened here.

### What to do with the alert

Dismiss it: **Security → Secret scanning alerts → Dismiss → "Used in tests" or
"False positive"**.

### Why we don't just delete them

`innertube/` is vendored, unmodified, from
[z-huang/InnerTune](https://github.com/z-huang/InnerTune) as a git subtree. Edits
there are lost on the next upstream sync and cause merge conflicts — see
[`innertube/VENDORED.md`](innertube/VENDORED.md) and hard constraint 3 in
[`CLAUDE.md`](CLAUDE.md).

---

## What would actually be a secret

If any of these ever appear in the repository, treat it as a real incident and
rotate immediately:

- The release signing keystore (`*.jks`, `*.keystore`) or its passwords
- `keystore.properties`, `local.properties`
- A Spotify client secret (Mero uses PKCE specifically so that no secret is
  needed on the device)
- Any Google account cookie or OAuth token — Mero never signs in, so one
  appearing here would mean something is badly wrong

`.gitignore` already covers all of these.

## Reporting something real

Open an issue, or contact the maintainer directly if it's sensitive.
