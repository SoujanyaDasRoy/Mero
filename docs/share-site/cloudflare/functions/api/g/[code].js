import { CODE, MAX_STEPS, json } from "../../_lib.js";

// GET /api/g/<code>?since=<seq>  ->  { name, hasCover, head, steps: [{seq, kind, value}], more? }
// At most 1,000 steps after `since`, oldest first; `more: true` when the phone should ask again.
export async function onRequestGet({ params, env, request }) {
  if (!CODE.test(params.code)) return json({ error: "gone" }, 404);
  const group = await env.DB.prepare("SELECT name, has_cover, head FROM groups WHERE code = ?1").bind(params.code).first();
  if (!group) return json({ error: "gone" }, 404);
  const since = Math.max(0, parseInt(new URL(request.url).searchParams.get("since") || "0", 10) || 0);
  const { results } = await env.DB.prepare("SELECT seq, kind, value FROM steps WHERE code = ?1 AND seq > ?2 ORDER BY seq LIMIT ?3")
    .bind(params.code, since, MAX_STEPS + 1)
    .all();
  const more = results.length > MAX_STEPS;
  const body = { name: group.name, hasCover: group.has_cover === 1, head: group.head, steps: results.slice(0, MAX_STEPS) };
  if (more) body.more = true;
  return json(body);
}
