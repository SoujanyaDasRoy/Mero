# Mero share site

The website behind Mero's share links, published with GitHub Pages at <https://soujanyadasroy.github.io/>.

When someone shares a song from [Mero](https://github.com/SoujanyaDasRoy/Mero), the link is
`https://soujanyadasroy.github.io/song/?v=<video id>`.

- **On a phone with Mero**, Android opens the link in the app and the song plays. That works because of
  `docs/.well-known/assetlinks.json`, which tells Android that the app `com.mero`, signed with the certificate
  listed in it, may open links on this site.
- **On any other phone**, the page in `docs/song/` sends the person to the same song in YouTube Music, with a
  link to get Mero.

## Layout

| Path | What it is |
|---|---|
| `docs/` | The published site (Pages is set to *main branch, /docs folder*) |
| `docs/.well-known/assetlinks.json` | Android's proof that Mero owns these links. Android requires this exact path |
| `docs/song/index.html` | The page for people without Mero |
| `docs/index.html` | The landing page |
| `docs/logo.png` | The Mero logo |
| `docs/.nojekyll` | Makes GitHub publish `.well-known` as it is |

Nothing here is secret: a package name, a public certificate fingerprint and two web pages.
If Mero's signing key ever changes, the fingerprint in `assetlinks.json` has to change with it.
