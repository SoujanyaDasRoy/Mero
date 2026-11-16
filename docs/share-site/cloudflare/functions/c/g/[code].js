import { CODE } from "../../_lib.js";

// /c/g/<code>.jpg: a group playlist's cover (link previews, and members' phones).
export async function onRequestGet({ params, env }) {
  const code = String(params.code).replace(/\.jpg$/, "");
  if (!CODE.test(code)) return new Response("Not found", { status: 404 });
  const bytes = await env.SHARES.get("g:" + code, "arrayBuffer");
  if (!bytes) return new Response("Not found", { status: 404 });
  return new Response(bytes, { headers: { "content-type": "image/jpeg", "cache-control": "public, max-age=300" } });
}
