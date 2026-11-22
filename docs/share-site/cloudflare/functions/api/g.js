import { ID, MAX_COVER_BYTES, MAX_NAME, MAX_STEPS, json, newCode } from "../_lib.js";
import { LIMITS, appendSteps, busy, isJpeg, overLimit, readJson } from "../_groups.js";

// POST /api/g  { name, ids: [videoId...] (≤1000), cover?: base64 jpeg }  ->  { code, head }
// Makes a group playlist from a phone's playlist: one `add` step per song. Longer playlists send the
// rest through /api/g/<code>/steps. Anyone who finds this address can call it, so everything is checked.
export async function onRequestPost({ request, env }) {
  const read = await readJson(request, MAX_COVER_BYTES * 2 + 100_000);
  if (read.error) return read.error;
  if (await overLimit(env, request, "create", LIMITS.create)) return busy();
  const body = read.body;
  const name = typeof body.name === "string" ? body.name.trim() : "";
  const ids = Array.isArray(body.ids) ? [...new Set(body.ids)] : [];
  if (!name || name.length > MAX_NAME || ids.length === 0 || ids.length > MAX_STEPS || !ids.every((id) => typeof id === "string" && ID.test(id))) {
    return json({ error: "bad playlist" }, 400);
  }
  let cover = null;
  if (body.cover != null) {
    try {
      cover = Uint8Array.from(atob(String(body.cover)), (c) => c.charCodeAt(0));
    } catch {
      return json({ error: "bad cover" }, 400);
    }
    if (!isJpeg(cover)) return json({ error: "bad cover" }, 400);
  }

  const code = newCode();
  await env.DB.prepare("INSERT INTO groups (code, name, has_cover, head, created_at) VALUES (?1, ?2, ?3, 0, ?4)")
    .bind(code, name, cover ? 1 : 0, Date.now())
    .run();
  if (cover) await env.SHARES.put("g:" + code, cover);
  const head = await appendSteps(env, code, ids.map((id, i) => ({ opId: "c" + i, kind: "add", value: id })));
  return json({ code, head });
}
