import { CODE, page } from "../_lib.js";

// /p/<code>: a shared playlist. The name, songs and (optionally) cover live in KV for 30 days and are
// then deleted by Cloudflare. A phone with Mero never loads this page: Android opens the app instead.
export async function onRequestGet({ params, env, request }) {
  const origin = new URL(request.url).origin;
  const code = params.code;
  const data = CODE.test(code) ? await env.SHARES.get("p:" + code, "json") : null;
  if (!data) {
    return page({
      title: "Mero playlist",
      description: "This link has expired",
      image: origin + "/logo.png",
      heading: "This playlist link has expired",
      line: "Shared playlists are kept for 30 days. Ask your friend to share it again.",
    });
  }
  const n = data.s.length;
  const count = n + (n === 1 ? " song" : " songs");
  return page({
    title: data.n,
    description: `${count} · shared from Mero`,
    image: data.c ? `${origin}/c/${code}.jpg` : origin + "/logo.png",
    heading: data.n,
    line: count,
    // YouTube builds an instant playlist from up to 50 ids.
    action: { href: "https://www.youtube.com/watch_videos?video_ids=" + data.s.slice(0, 50).join(","), label: "Play on YouTube" },
  });
}
