# Group playlists: design

**Status:** proposed, awaiting approval · **Date:** 2026-11-16 · **Ships after:** 1.14.0 (short links) is released

## Intent

Friends want a playlist that stays the same on all of their phones. Today a shared playlist is a one-time
copy: once a friend adds it, the two copies drift apart. A **group playlist** is one that everyone who joined
can change (add or remove songs), and every phone picks up the changes.

## Goals

- Anyone in a group playlist can add and remove songs; every member's Mero shows the same set of songs.
- Joining needs only the link. No accounts, no sign-in (spec §3.1 still holds).
- Edits made offline are kept and sent when the phone is back online. Nothing a friend adds is ever silently lost.
- Leaving a group keeps your copy as an ordinary playlist.
- The name and cover are shared too.

## Non-goals

- Instant updates. Phones check when Mero opens, when the playlist is opened, after an edit, and every few
  hours in the background. Push would need Google's messaging service, which the no-Google rule excludes.
- Synced order. Songs everyone sees are ordered by when they were added; dragging songs around changes only
  your phone.
- "Added by Ravi" names, chat, likes on songs, a member list. There are no accounts to hang names on.
- Kicking someone out, or revoking a link. Anyone with the link is in.
- Syncing liked songs, history, or a person's whole library.

## Anti-goals (would make this a failure even if it works)

- A friend's addition disappearing because two people edited at the same time.
- Any Google account, any personal data, or anything about the person sent to the server.
- A phone's ordinary playlists changing because of a group playlist.
- A broken or unreachable server breaking playback or the library. Sync failing must only mean "not up to date yet".

## Constraints

- Spec §3.1: no account or login of any kind. The playlist's random code is the only key.
- Spec §3.2 said "there is no server". 1.14.0 introduced Mero's share site (Cloudflare Pages, `meroapp.pages.dev`,
  free tier). This extends it; `docs/architecture.md` gets a decision record for it, with a revisit trigger.
- Spec §2 listed "no shared playlists" as a **v1** non-goal; this is a deliberate post-v1 change.
- Free tier only. D1 (Cloudflare's SQLite) allows about 100,000 writes a day, KV 1,000. Ten friends editing
  playlists will use a tiny fraction of D1.
- Database migration 8 → 9 in the app. The on-hold recommender Phase 2b also planned 8 → 9 (`radio_edges`);
  whichever ships first takes 9 and the other moves to 10.
- Never edit `innertube/`. Domain types never expose server or upstream types.
- Covers are stored as Mero's own file per playlist (`PlaylistCovers`, 1.13.2), and included in backups.

## Approaches considered

| | How | Trade-off | Verdict |
|---|---|---|---|
| **A. Owner curates, friends follow** | One writer; server keeps the latest list | Simplest, no clashes; friends can't add anything | Rejected: "between friends" means a group playlist |
| **B. Group playlist, changes as steps** | Each add/remove is one step in a log; phones replay the log | About twice A; order is not synced | **Chosen** by the user |
| C. Group playlist, newest save replaces the list | Whole list overwritten on every save | Cheapest group version, but simultaneous edits silently lose one person's change | Rejected: violates an anti-goal |

## Design

### How changes merge

The server keeps, per group, a **log of steps** in the order it received them, each with a number (`seq`)
that only goes up. A step is one of: `add <songId>`, `remove <songId>`, `rename <name>`, `cover`.

The playlist's songs are worked out by replaying the log: for each song, **the last step about it wins**
(added → in, removed → out). Because the server puts every step in one order, there is no clash to resolve:
two friends adding different songs at once both end up in; one adding and one removing the same song at once
ends with whichever reached the server second. Nothing needs a CRDT: the server is the single order.

The shared order is "when the song was last added" (the `seq` of its winning add).

### Server (extends `docs/share-site/cloudflare`)

D1 database `mero` with two tables:

```sql
CREATE TABLE groups (
  code       TEXT PRIMARY KEY,      -- 8 chars, same alphabet as share codes
  name       TEXT NOT NULL,
  has_cover  INTEGER NOT NULL DEFAULT 0,
  head       INTEGER NOT NULL DEFAULT 0,   -- seq of the newest step
  touched_at INTEGER NOT NULL             -- ms; last time any phone synced it (at most one write a day)
);
CREATE TABLE steps (
  code    TEXT NOT NULL,
  seq     INTEGER NOT NULL,
  op_id   TEXT NOT NULL,       -- random id made by the phone; makes retries harmless
  kind    TEXT NOT NULL,       -- add | remove | rename | cover
  value   TEXT,                -- song id or new name
  at      INTEGER NOT NULL,
  PRIMARY KEY (code, seq),
  UNIQUE (code, op_id)
);
```

The cover image stays in KV (`g:<code>`, no expiry), as today's share covers are.

Endpoints (all validated like `/api/p`: id format, name length, JPEG header, cover ≤200 KB). There is **no limit on how
many songs a group holds**; requests carry at most 1,000 steps each, so a large playlist is uploaded in several
requests (`POST /api/g` with the first 1,000, then `/steps` for the rest) and `GET` pages through steps 1,000 at a time:

| Call | Does |
|---|---|
| `POST /api/g` `{name, ids, cover?}` | Creates a group from an existing playlist: one `add` step per song. Returns `{code, head}` |
| `GET /api/g/<code>?since=<seq>` | Steps after `seq`, plus `head`, `name`, `has_cover`. `since=0` gives everything (joining) |
| `POST /api/g/<code>/steps` `{steps:[{op_id, kind, value}]}` | Appends steps in one transaction; a repeated `op_id` is skipped. Returns the new `head` |
| `PUT /api/g/<code>/cover` (JPEG body) | Replaces the cover and appends a `cover` step, so other phones know to fetch it |
| `GET /c/g/<code>.jpg` | The group's cover |
| `GET /g/<code>` | Page with Open Graph preview (name, count, cover) for people without Mero / older Mero: "Get Mero 1.15 to join" |

Clean-up: a group that **no member's phone has synced for 180 days** is deleted (steps, row and cover). Every
sync, including the 6-hourly background one, refreshes `touched_at` (at most once a day per group), so a group is
only deleted once everyone has left it or uninstalled Mero. Pages has no scheduled jobs,
so this is a small separate Worker (`meroapp-cleanup`) with a daily cron, bound to the same D1 and KV.

### App

**Data (migration 8 → 9):**
- `playlists` gains `groupCode TEXT NULL` and `groupSeq INTEGER NOT NULL DEFAULT 0` (the last `seq` this
  phone has applied).
- New table `group_outbox(opId PK, playlistId, kind, value, createdAt)`: edits made on this phone and not yet
  accepted by the server.
- Backups carry both (they're in the database). A restored phone simply continues syncing.

**Pure merge logic (strict TDD):** a function that takes the local playlist's songs, the steps since
`groupSeq`, and the pending outbox, and returns the new song list. Rules:
- Apply steps in order; last step per song wins.
- Songs already on this phone keep **this phone's order**; newly added songs go to the end in `seq` order.
- Pending outbox steps are applied on top, so your own unsent edits never flicker away while waiting.
- Tested cases include: concurrent add and remove, re-adding a removed song, a step for a song the phone has
  never seen, duplicate steps, an empty group, 10,000 songs.

**Editing:** when a playlist has a `groupCode`, `addToPlaylist` / `removeFromPlaylist` (and rename/cover) also
write an outbox row in the same transaction, then ask for a sync. Ordinary playlists are untouched.

**Syncing** (`GroupSync`, one at a time per playlist): send the outbox, then fetch steps since `groupSeq`,
merge, save, advance `groupSeq`, clear sent outbox rows. Missing songs' details (title, artist, cover) are
fetched with the existing `resolveIds`. Runs:
- when Mero comes to the foreground (for all group playlists),
- when a group playlist is opened,
- a few seconds after an edit,
- every 6 hours in the background with WorkManager (already used by song reminders), only on a network.

Failures are quiet and retried; the playlist screen shows "Not synced yet" with a retry when the last attempt
failed (spec §9: visible and retryable, never a silent hang).

**Edge cases (decided):**
- Making a group: the phone's playlist gets the returned `groupCode`, `groupSeq = head`, and an empty outbox.
- Joining: a new local playlist with `groupSeq = 0`; the first sync replays the whole log and downloads the cover.
  The playlist appears at once; song details (title, artist, art) are filled in by `resolveIds` in batches in the
  background, so a very long playlist doesn't hold up the screen.
- Phone files and podcast episodes have no YouTube id, so they never become steps: they're left out when a
  group is made, and if added to a group playlist later they stay on this phone only (shown as usual).
- A song whose details can't be fetched (offline, removed from YouTube) is saved with just its id and shown as
  "Unavailable song"; its details are fetched again on the next sync. It still counts as in the playlist.
- A `cover` step makes phones download the new cover into `PlaylistCovers`; a `rename` step renames locally.
- Deleting a group playlist on your phone is the same as leaving it.
- Smart playlists can't become group playlists (they fill themselves).
- Abuse limits on the server match `/api/p`: validation on every field; no accounts, so no per-person limits.

**Screens:**
- Playlist ⋮ → **Make it a group playlist**: confirms what it means ("Anyone with the link can add and
  remove songs"), uploads it, then opens the share sheet with the card and `meroapp.pages.dev/g/<code>`.
- A group playlist shows a small "group" icon next to its name in the library and on its screen.
- Opening a `/g/` link: a dialog **Join** (adds it as a group playlist, with its cover) or **Just add a
  copy** (an ordinary playlist, as today) or **Not now**. Joining a group you're already in opens it.
- Group playlist ⋮ → **Invite friends** (share again), **Leave group** (keeps your songs as an ordinary
  playlist; the server is not told, there's nothing to tell it).
- Toast when a sync brings changes: "Road trip: 3 new songs".
- The manifest's verified-link filter for `meroapp.pages.dev` gains `/g/`.

**Privacy text** (README, SECURITY): a group playlist's name, song ids and cover are stored on Mero's share
site for as long as it's in use, deleted after 180 days untouched. Nothing about the people in it is stored.

## Calls made without asking (check these)

1. **Anyone in the group can remove any song**, including ones others added. (That's what B is; a removal is
   undone by adding the song back.)
2. A group is deleted from the server after **180 days with no member's phone syncing it** (background syncs count).
3. **Name and cover are shared**, the most recent change wins.
4. **"Make it a group playlist" is a separate action**; today's **Share playlist** still sends a copy.
5. **Leaving doesn't tell the server**; with no accounts there's no member list to leave.
6. **No limit on songs per group** (user's decision: mostly used by two people). Requests are batched at 1,000 steps.
7. **D1 for the steps** (not KV), because of KV's 1,000 writes a day.

## Left to the builder

- Exact D1 query shapes and indexes; how the Pages Functions are split into files.
- Debounce timing after an edit; WorkManager constraints beyond "needs network".
- Wording of dialogs and toasts (match the existing friendly tone).
- How `GroupSync` is wired into `AppContainer` (hand-written DI, no Koin).
- Test plan for the server (a curl script like the 1.14.0 checks is enough; the merge logic carries the unit tests).

## Revisit triggers

- More than about 50 people using it, or KV/D1 free limits within 25%: reconsider storage and add rate limits.
- Friends ask for synced order or "who added this": both need identity, which reopens spec §3.1.
- Someone abuses a leaked link: add per-group owner tokens and a "reset link" action.
