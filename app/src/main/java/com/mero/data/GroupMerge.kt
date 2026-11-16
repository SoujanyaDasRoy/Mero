package com.mero.data

/** A step in a group playlist's log on Mero's share site: add, remove, rename or cover. */
data class GroupStep(val seq: Long, val kind: String, val value: String?)

/** A step made on this phone and not yet accepted by the site. */
data class PendingStep(val kind: String, val value: String?)

/**
 * A group playlist after merging. [name] is the newest name if the steps renamed it (else null),
 * [coverChanged] says a new cover should be fetched, [added] counts songs that are new on this phone.
 */
data class Merged(val songIds: List<String>, val name: String?, val coverChanged: Boolean, val added: Int)

/**
 * Applies the site's [steps] (in seq order) and then this phone's [pending] ones to the [local] songs.
 *
 * The site puts every member's steps in one order, so the newest step about a song decides whether it
 * is in. Songs this phone already has keep this phone's order (reordering is personal); songs that
 * arrive go to the end. Pending steps go on top, so an edit waiting to be sent never flickers away.
 */
fun mergeGroup(local: List<String>, steps: List<GroupStep>, pending: List<PendingStep>): Merged {
    val songs = LinkedHashSet(local)
    var name: String? = null
    var coverChanged = false
    fun apply(kind: String, value: String?) {
        when (kind) {
            "add" -> if (value != null) songs.add(value)
            "remove" -> songs.remove(value)
            "rename" -> name = value
            "cover" -> coverChanged = true
        }
    }
    steps.forEach { apply(it.kind, it.value) }
    pending.forEach { apply(it.kind, it.value) }
    val before = local.toHashSet()
    return Merged(songs.toList(), name, coverChanged, songs.count { it !in before })
}
