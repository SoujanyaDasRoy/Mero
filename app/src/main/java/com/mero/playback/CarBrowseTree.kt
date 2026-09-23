package com.mero.playback

/**
 * What the car's screen can browse, and how its ids are spelled.
 *
 * Android Auto shows a media app's library as a tree it walks one level at a
 * time, and when someone taps a song it sends back nothing but that item's id.
 * So each playable id carries the list it was tapped in — `liked|<songId>` —
 * because "play this" from a car should mean "play this, then the rest of the
 * list it sits in", and without the list name there would be no way to know
 * what the rest was.
 *
 * The prefixed ids never reach the player. The items the player queues are
 * built from plain song ids, which is what the app, the queue and the saved
 * resume point all expect.
 */
object CarBrowseTree {

    const val ROOT = "root"

    /** The top level, in the order a driver most likely wants them. */
    val sections: List<Section> = listOf(
        Section("recent", "Recently played"),
        Section("liked", "Liked songs"),
        Section("playlists", "Playlists"),
        Section("phone", "On this phone"),
        Section("downloads", "Downloads"),
    )

    data class Section(val key: String, val title: String) {
        val id: String get() = SECTION + key
    }

    fun playlistNodeId(playlistId: String): String = PLAYLIST + playlistId

    /** An id for a song tapped inside a list. */
    fun playableId(parentId: String, songId: String): String = parentId + SEP + songId

    /** A node that holds other nodes or songs, or null if [id] is a song. */
    sealed interface Node {
        data object Root : Node
        data class SectionNode(val key: String) : Node
        data class PlaylistNode(val playlistId: String) : Node
    }

    fun nodeOf(id: String): Node? = when {
        id == ROOT -> Node.Root
        SEP in id -> null
        id.startsWith(SECTION) -> Node.SectionNode(id.removePrefix(SECTION))
        id.startsWith(PLAYLIST) -> Node.PlaylistNode(id.removePrefix(PLAYLIST))
        else -> null
    }

    /**
     * Splits a tapped id into the list and the song. A bare id — one that did
     * not come from a list, such as the resume queue — has no parent.
     */
    fun parse(id: String): Pair<String?, String> {
        val at = id.lastIndexOf(SEP)
        return if (at < 0) null to id else id.substring(0, at) to id.substring(at + 1)
    }

    private const val SECTION = "section:"
    private const val PLAYLIST = "playlist:"

    /**
     * Not a character any id on either side can contain: YouTube ids are
     * letters, digits, - and _; Mero's own are `local:` and `pod:` plus hex.
     */
    private const val SEP = "|"
}

/**
 * One page of a list, as the car asks for it. A page past the end is an empty
 * page rather than an error: cars ask for the next page until one comes back
 * empty, and an exception there would end browsing on the last full page.
 */
internal fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return items
    // Long throughout: "give me everything" arrives as pageSize = Int.MAX_VALUE,
    // and from + pageSize in Int wraps negative and crashes subList.
    val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong())
    val to = (from + pageSize).coerceAtMost(items.size.toLong())
    return items.subList(from.toInt(), to.toInt())
}
