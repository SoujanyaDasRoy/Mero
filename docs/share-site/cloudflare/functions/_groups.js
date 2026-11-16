// Group playlists: an ordered log of steps per group in D1 (see schema.sql). Not a route.
import { ID, MAX_COVER_BYTES, MAX_NAME } from "./_lib.js";

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

export function isJpeg(bytes) {
  return bytes.length > 3 && bytes.length <= MAX_COVER_BYTES && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

/**
 * Appends [steps] after the group's newest one, in one transaction, and returns the new head.
 *
 * One INSERT for all of them (rows come from a JSON parameter): the free plan allows only a few dozen
 * queries per request, so a statement per step would not fit 1,000 steps. A repeated op id is skipped
 * (UNIQUE(code, op_id)), which makes a phone's retry after a lost reply harmless; it leaves a gap in
 * seq numbers, which nothing relies on.
 */
export async function appendSteps(env, code, steps) {
  const now = Date.now();
  const [, , head] = await env.DB.batch([
    env.DB.prepare(
      `INSERT OR IGNORE INTO steps (code, seq, op_id, kind, value, at)
       SELECT ?1, base.m + j.key + 1, json_extract(j.value, '$.opId'), json_extract(j.value, '$.kind'),
              json_extract(j.value, '$.value'), ?3
       FROM json_each(?2) AS j, (SELECT COALESCE(MAX(seq), 0) AS m FROM steps WHERE code = ?1) AS base`,
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
