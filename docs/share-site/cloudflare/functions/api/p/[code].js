import { CODE, json } from "../../_lib.js";

// GET /api/p/<code>  ->  { name, ids, cover }   (404 once it has expired)
export async function onRequestGet({ params, env }) {
  const data = CODE.test(params.code) ? await env.SHARES.get("p:" + params.code, "json") : null;
  if (!data) return json({ error: "gone" }, 404);
  return json({ name: data.n, ids: data.s, cover: data.c });
}
