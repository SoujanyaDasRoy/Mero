import { CODE, MAX_STEPS, json } from "../../../_lib.js";
import { appendSteps, cleanStep, groupExists } from "../../../_groups.js";

// POST /api/g/<code>/steps  { steps: [{opId, kind, value}] } (≤1000)  ->  { head }
export async function onRequestPost({ params, env, request }) {
  if (!CODE.test(params.code) || !(await groupExists(env, params.code))) return json({ error: "gone" }, 404);
  if (Number(request.headers.get("content-length") || 0) > 200_000) return json({ error: "too big" }, 413);
  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "bad json" }, 400);
  }
  const raw = Array.isArray(body.steps) ? body.steps : [];
  const steps = raw.map(cleanStep);
  if (steps.length === 0 || steps.length > MAX_STEPS || steps.includes(null)) return json({ error: "bad steps" }, 400);
  return json({ head: await appendSteps(env, params.code, steps) });
}
