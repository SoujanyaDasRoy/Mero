import { CODE, MAX_STEPS, json } from "../../../_lib.js";
import { LIMITS, appendSteps, busy, cleanStep, groupExists, overLimit, readJson } from "../../../_groups.js";

// POST /api/g/<code>/steps  { steps: [{opId, kind, value}] } (≤1000)  ->  { head }
export async function onRequestPost({ params, env, request }) {
  if (!CODE.test(params.code) || !(await groupExists(env, params.code))) return json({ error: "gone" }, 404);
  const read = await readJson(request, 200_000);
  if (read.error) return read.error;
  if (await overLimit(env, request, "steps", LIMITS.steps)) return busy();
  const body = read.body;
  const raw = Array.isArray(body.steps) ? body.steps : [];
  const steps = raw.map(cleanStep);
  if (steps.length === 0 || steps.length > MAX_STEPS || steps.includes(null)) return json({ error: "bad steps" }, 400);
  return json({ head: await appendSteps(env, params.code, steps) });
}
