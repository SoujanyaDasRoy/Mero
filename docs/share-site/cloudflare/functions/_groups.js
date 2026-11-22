// Group playlists: an ordered log of steps per group in D1 (see schema.sql). Not a route.
import { ID, MAX_COVER_BYTES, MAX_NAME, json } from "./_lib.js";

const OP_ID = /^[A-Za-z0-9_-]{1,64}$/;

/** A step as a phone sends it, or null if anything about it is wrong. `cover` steps only come from PUT cover. */
export function cleanStep(s) {
  if (!s || typeof s.opId !== "string" || !OP_ID.test(s.opId)) return null;
  if ((s.kind === "add" || s.kind === "remove") && typeof s.value === "string" && ID.test(s.value)) {
    return { opId: s.opId, kind: s.kind, value: s.value };
  }
  if (s.kind === "rename" && typeof s.value === "string") {
    const name = s.value.trim();
    if (name && name.length <= MAX_NAME) return { opId: s.opId, kind: "rename", value: name };
  }
  return null;
}

/**
 * The request body, or null when it is larger than [maxBytes]. Counted as it arrives, because a body sent
 * in chunks has no content-length to check.
 */
export async function readBody(request, maxBytes) {
  if (!request.body) return new Uint8Array();
  const reader = request.body.getReader();
  const parts = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.length;
    if (size > maxBytes) {
      await reader.cancel();
      return null;
    }
    parts.push(value);
  }
  const out = new Uint8Array(size);
  let at = 0;
  for (const p of parts) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}

/** The body as JSON: `{ body }`, or `{ error: Response }` when it is too big or not JSON. */
export async function readJson(request, maxBytes) {
  const bytes = await readBody(request, maxBytes);
  if (bytes === null) return { error: json({ error: "too big" }, 413) };
  try {
    return { body: JSON.parse(new TextDecoder().decode(bytes)) };
  } catch {
    return { error: json({ error: "bad json" }, 400) };
  }
}

/**
 * True when this sender has used up [max] requests of [bucket] this hour. A sender is counted by a hash of
 * their address, the bucket, the hour and a secret salt: the address itself is never stored, the hash
 * can't be turned back into it, and counters older than an hour are deleted.
 */
export async function overLimit(env, request, bucket, max) {
  const hour = Math.floor(Date.now() / 3_600_000);
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${env.LIMIT_SALT || ""}|${bucket}|${hour}|${ip}`));
  const key = Array.from(new Uint8Array(digest).slice(0, 16), (b) => b.toString(16).padStart(2, "0")).join("");
  const row = await env.DB.prepare(
    "INSERT INTO limits (key, hour, n) VALUES (?1, ?2, 1) ON CONFLICT(key) DO UPDATE SET n = n + 1 RETURNING n",
  ).bind(key, hour).first();
  if (row.n === 1) await env.DB.prepare("DELETE FROM limits WHERE hour < ?1").bind(hour).run();
  return row.n > max;
}

export const LIMITS = { create: 30, steps: 300, cover: 30 };

export function busy() {
  return json({ error: "busy" }, 429);
}

export function isJpeg(bytes) {
  return bytes.length > 3 && bytes.length <= MAX_COVER_BYTES && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

/**
 * Appends [steps] after the group's newest one, in one transaction, and returns the new head.
 *
 * One INSERT for all of them (rows come from a JSON parameter): the free plan allows only a few dozen
 * queries per request, so a statement per step would not fit 1,000 steps. Only a repeated op id is skipped
 * (UNIQUE(code, op_id)), which makes a phone's retry after a lost reply harmless; it leaves a gap in seq
 * numbers, which nothing relies on. Any other clash (two steps given one seq) fails the request loudly
 * instead of losing a step while telling the phone it was saved. ("WHERE true" is SQLite's way of telling
 * an INSERT ... SELECT apart from its ON CONFLICT clause.)
 */
export async function appendSteps(env, code, steps) {
  const now = Date.now();
  const [, , head] = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO steps (code, seq, op_id, kind, value, at)
       SELECT ?1, base.m + j.key + 1, json_extract(j.value, '$.opId'), json_extract(j.value, '$.kind'),
              json_extract(j.value, '$.value'), ?3
       FROM json_each(?2) AS j, (SELECT COALESCE(MAX(seq), 0) AS m FROM steps WHERE code = ?1) AS base
       WHERE true
       ON CONFLICT(code, op_id) DO NOTHING`,
    ).bind(code, JSON.stringify(steps), now),
    env.DB.prepare(
      `UPDATE groups SET
         head = (SELECT COALESCE(MAX(seq), 0) FROM steps WHERE code = ?1),
         name = COALESCE((SELECT value FROM steps WHERE code = ?1 AND kind = 'rename' ORDER BY seq DESC LIMIT 1), name)
       WHERE code = ?1`,
    ).bind(code),
    env.DB.prepare("SELECT head FROM groups WHERE code = ?1").bind(code),
  ]);
  return head.results[0].head;
}

export async function groupExists(env, code) {
  return (await env.DB.prepare("SELECT 1 FROM groups WHERE code = ?1").bind(code).first()) !== null;
}
