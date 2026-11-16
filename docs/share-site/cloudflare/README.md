# Mero share site (Cloudflare Pages)

Serves <https://meroapp.pages.dev>: the short links Mero shares.

| Path | What |
|---|---|
| `/s/<video id>` | A song. Preview (name, artist, cover) from YouTube's oEmbed; sends everyone else to YouTube Music |
| `/p/<code>` | A playlist stored in KV for 30 days (name, up to 500 song ids, optional cover up to 200 KB), deleted by Cloudflare afterwards |
| `/c/<code>.jpg` | That playlist's cover, used as the link preview image and downloaded by a friend's Mero |
| `POST /api/p`, `GET /api/p/<code>` | Create / read a playlist (validated: id format, counts, sizes, JPEG header) |
| `/.well-known/assetlinks.json` | Tells Android that `com.mero` (with the signing certificate listed) may open these links |

Deploy (needs `wrangler login`):

    cd docs/share-site/cloudflare
    wrangler pages deploy public --project-name meroapp --branch main

The KV namespace `SHARES` is bound in `wrangler.toml`. If Mero's signing key ever changes, update the
fingerprint in `public/.well-known/assetlinks.json` and redeploy. Free-tier KV allows 1,000 writes a day
(each shared playlist is one or two).
