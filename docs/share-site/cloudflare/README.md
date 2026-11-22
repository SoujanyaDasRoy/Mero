# Mero share site (Cloudflare Pages)

Serves <https://meroapp.pages.dev>: the short links Mero shares.

| Path | What |
|---|---|
| `/s/<video id>` | A song. Preview (name, artist, cover) from YouTube's oEmbed; sends everyone else to YouTube Music |
| `/p/<code>` | A playlist stored in KV for 30 days (name, up to 500 song ids, optional cover up to 200 KB), deleted by Cloudflare afterwards |
| `/c/<code>.jpg` | That playlist's cover, used as the link preview image and downloaded by a friend's Mero |
| `POST /api/p`, `GET /api/p/<code>` | Create / read a playlist (validated: id format, counts, sizes, JPEG header) |
| `/g/<code>` | A group playlist's link: preview page (name, song count, cover) |
| `POST /api/g`, `GET /api/g/<code>?since=`, `POST /api/g/<code>/steps`, `PUT /api/g/<code>/cover`, `/c/g/<code>.jpg` | Group playlists: an ordered log of add/remove/rename/cover steps per group in D1 (`schema.sql`), never deleted. 1,000 steps per request |
| `/.well-known/assetlinks.json` | Tells Android that `com.mero` (with the signing certificate listed) may open these links |

Deploy (needs `wrangler login`):

    cd docs/share-site/cloudflare
    wrangler pages deploy public --project-name meroapp --branch main

The KV namespace `SHARES` and the D1 database `mero` are bound in `wrangler.toml`. A new D1 database needs
`wrangler d1 execute mero --remote --file schema.sql`; the live one was made before rate limits and got
`migrate-001.sql` on top. Rate limits (`_groups.js`, `LIMITS`) count senders by a salted hash; the salt is the
Pages secret `LIMIT_SALT` (`wrangler pages secret put LIMIT_SALT --project-name meroapp`).

`bash test-groups.sh` checks the group endpoints against the live site and deletes the groups it made.
`RATE=1 bash test-groups.sh` also checks the new-group limit, which uses up this hour's allowance for your address. If Mero's signing key ever changes, update the
fingerprint in `public/.well-known/assetlinks.json` and redeploy. Free-tier KV allows 1,000 writes a day
(each shared playlist is one or two).
