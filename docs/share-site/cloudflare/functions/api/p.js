import { CODE, ID, MAX_COVER_BYTES, MAX_NAME, MAX_SONGS, TTL_SECONDS, json } from "../_lib.js";

const ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

function newCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join("");
}

function fromBase64(s) {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

// POST /api/p  { name, ids: [videoId...], cover?: base64 jpeg }  ->  { code }
// Everything is validated here, since anyone who finds this address can call it.
export async function onRequestPost({ request, env }) {
  const length = Number(request.headers.get("content-length") || 0);
  if (length > MAX_COVER_BYTES * 2) return json({ error: "too big" }, 413);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad json" }, 400);
  }

  const name = typeof body.name === "string" ? body.name.trim().slice(0, MAX_NAME) : "";
  const ids = Array.isArray(body.ids) ? [...new Set(body.ids)] : [];
  if (!name || ids.length === 0 || ids.length > MAX_SONGS || !ids.every((id) => typeof id === "string" && ID.test(id))) {
    return json({ error: "bad playlist" }, 400);
  }

  let cover = null;
  if (body.cover != null) {
    if (typeof body.cover !== "string") return json({ error: "bad cover" }, 400);
    try {
      cover = fromBase64(body.cover);
    } catch {
      return json({ error: "bad cover" }, 400);
    }
    // A JPEG starts FF D8 FF.
    if (cover.length > MAX_COVER_BYTES || cover[0] !== 0xff || cover[1] !== 0xd8 || cover[2] !== 0xff) return json({ error: "bad cover" }, 400);
  }

  const code = newCode();
  await env.SHARES.put("p:" + code, JSON.stringify({ n: name, s: ids, c: cover !== null }), { expirationTtl: TTL_SECONDS });
  if (cover) await env.SHARES.put("c:" + code, cover, { expirationTtl: TTL_SECONDS });
  return json({ code });
}
