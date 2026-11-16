import { CODE, page } from "../_lib.js";

// /g/<code>: a group playlist's link. Mero 1.15+ opens it directly; anyone else sees this page.
export async function onRequestGet({ params, env, request }) {
  const origin = new URL(request.url).origin;
  const group = CODE.test(params.code)
    ? await env.DB.prepare(
        `SELECT name, has_cover,
                (SELECT COUNT(*) FROM (SELECT value, MAX(seq) AS m FROM steps WHERE code = ?1 AND kind IN ('add','remove') GROUP BY value) t
                   JOIN steps s ON s.code = ?1 AND s.seq = t.m AND s.kind = 'add') AS songs
         FROM groups WHERE code = ?1`,
      ).bind(params.code).first()
    : null;
  if (!group) {
    return page({ title: "Mero", description: "", image: origin + "/logo.png", heading: "This group playlist doesn't exist", line: "Check the link with whoever sent it." });
  }
  const count = group.songs + (group.songs === 1 ? " song" : " songs");
  return page({
    title: group.name,
    description: `${count} · group playlist on Mero`,
    image: group.has_cover ? `${origin}/c/g/${params.code}.jpg` : origin + "/logo.png",
    heading: group.name,
    line: `${count}. Get Mero 1.15 or newer to join and add songs together.`,
  });
}
