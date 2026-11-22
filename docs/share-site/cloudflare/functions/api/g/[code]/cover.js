import { CODE, json } from "../../../_lib.js";
import { MAX_COVER_BYTES } from "../../../_lib.js";
import { LIMITS, appendSteps, busy, groupExists, isJpeg, overLimit, readBody } from "../../../_groups.js";

// PUT /api/g/<code>/cover  (raw JPEG, ≤200 KB)  ->  { head }
// Replaces the group's cover and adds a `cover` step so every phone knows to fetch it.
export async function onRequestPut({ params, env, request }) {
  if (!CODE.test(params.code) || !(await groupExists(env, params.code))) return json({ error: "gone" }, 404);
  const bytes = await readBody(request, MAX_COVER_BYTES);
  if (bytes === null) return json({ error: "too big" }, 413);
  if (!isJpeg(bytes)) return json({ error: "bad cover" }, 400);
  if (await overLimit(env, request, "cover", LIMITS.cover)) return busy();
  await env.SHARES.put("g:" + params.code, bytes);
  await env.DB.prepare("UPDATE groups SET has_cover = 1 WHERE code = ?1").bind(params.code).run();
  const head = await appendSteps(env, params.code, [{ opId: "cover-" + crypto.randomUUID(), kind: "cover", value: null }]);
  return json({ head });
}
