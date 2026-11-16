// Shared bits for the share-link pages. Not a route (the underscore keeps Pages from serving it).

export const ID = /^[A-Za-z0-9_-]{11}$/;
export const CODE = /^[A-Za-z0-9]{8}$/;
export const MAX_SONGS = 500;
export const MAX_NAME = 80;
export const MAX_COVER_BYTES = 200 * 1024;
/** A shared playlist is deleted by Cloudflare itself this long after it was made. */
export const TTL_SECONDS = 30 * 24 * 60 * 60;

export function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

/**
 * The page a person (or WhatsApp's link preview) sees. The Open Graph tags are what a messaging app
 * reads to draw the preview; the body is for someone who opened the link in a browser without Mero.
 */
export function page({ title, description, image, heading, line, action, redirect }) {
  const html = `<!doctype html>
<html lang="en"><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${esc(title)}</title>
<meta property="og:site_name" content="Mero">
<meta property="og:type" content="website">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:image" content="${esc(image)}">
<meta name="twitter:card" content="summary_large_image">
<link rel="icon" href="/logo.png">
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(circle at 50% 40%,#e9dafc,#c19cfc);color:#2a0a6b;font:16px/1.5 system-ui,-apple-system,"Segoe UI",sans-serif;text-align:center}
  img{width:96px;height:96px;border-radius:24px;box-shadow:0 12px 32px rgba(58,10,140,.25)}
  h1{margin:16px 0 4px;font-size:26px}p{margin:8px 0}
  a.button{display:inline-block;margin-top:8px;padding:12px 24px;border-radius:28px;background:#4b1fb0;color:#fff;text-decoration:none;font-weight:600}
  a.plain{color:#4b1fb0}
</style></head>
<body><main>
<img src="/logo.png" alt="Mero">
<h1>${esc(heading)}</h1>
<p>${esc(line)}</p>
${action ? `<p><a class="button" href="${esc(action.href)}">${esc(action.label)}</a></p>` : ""}
<p><a class="plain" href="https://github.com/SoujanyaDasRoy/Mero/releases/latest">Get Mero, the free music player this was shared from</a></p>
</main>
${redirect ? `<script>location.replace(${JSON.stringify(redirect)})</script>` : ""}
</body></html>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8", "cache-control": "public, max-age=300" } });
}

export function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
}
