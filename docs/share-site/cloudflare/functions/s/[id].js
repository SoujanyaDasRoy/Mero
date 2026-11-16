import { ID, page } from "../_lib.js";

// /s/<video id>: a shared song. The preview WhatsApp draws uses the song's name and cover, read from
// YouTube's public oEmbed endpoint (no key, no account). Anyone without Mero lands in YouTube Music.
export async function onRequestGet({ params, request }) {
  const id = params.id;
  if (!ID.test(id)) return page({ title: "Mero", description: "", image: new URL("/logo.png", request.url).href, heading: "Mero", line: "This link is missing its song." });

  let title = "A song on Mero";
  let artist = "";
  try {
    const r = await fetch(`https://www.youtube.com/oembed?url=${encodeURIComponent("https://www.youtube.com/watch?v=" + id)}&format=json`, {
      cf: { cacheTtl: 86400, cacheEverything: true },
    });
    if (r.ok) {
      const j = await r.json();
      title = j.title || title;
      artist = (j.author_name || "").replace(/ - Topic$/, "");
    }
  } catch {}

  const yt = "https://music.youtube.com/watch?v=" + id;
  return page({
    title,
    description: artist ? `${artist} · shared from Mero` : "Shared from Mero",
    image: `https://i.ytimg.com/vi/${id}/hqdefault.jpg`,
    heading: title,
    line: artist,
    action: { href: yt, label: "Open in YouTube Music" },
    redirect: yt,
  });
}
