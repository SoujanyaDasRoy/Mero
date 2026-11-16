import { CODE } from "../_lib.js";

// /c/<code>.jpg: the cover someone chose for a shared playlist (WhatsApp's preview, and a friend's Mero).
export async function onRequestGet({ params, env }) {
  const code = String(params.code).replace(/\.jpg$/, "");
  if (!CODE.test(code)) return new Response("Not found", { status: 404 });
  const bytes = await env.SHARES.get("c:" + code, "arrayBuffer");
  if (!bytes) return new Response("Not found", { status: 404 });
  return new Response(bytes, { headers: { "content-type": "image/jpeg", "cache-control": "public, max-age=86400" } });
}
