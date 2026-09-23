package com.mero.playback

import com.mero.data.LibraryRepository
import com.mero.data.SettingsStore
import com.mero.domain.Song
import kotlinx.coroutines.flow.first

/** What to resume: the track that was playing, what was after it, and where in it. */
data class ResumeQueue(val songs: List<Song>, val positionMs: Long)

/**
 * The saved queue holds only what comes *after* the current track — that is
 * what the app writes — so the current track is stored separately, by id,
 * and put back in front here.
 *
 * Resuming used to take an index into the whole playing list and apply it to
 * that upcoming-only list: playing track 4 of an album, a headset press the
 * next morning resumed track 8, at track 4's position.
 */
internal fun resumeQueueOf(current: Song?, upcoming: List<Song>, positionMs: Long): ResumeQueue? {
    val songs = listOfNotNull(current) + upcoming.filterNot { it.id == current?.id }
    if (songs.isEmpty()) return null
    // The position belongs to the current track; without it, start from the top.
    return ResumeQueue(songs, if (current != null) positionMs.coerceAtLeast(0) else 0L)
}

suspend fun resumeQueue(library: LibraryRepository, settings: SettingsStore): ResumeQueue? {
    val currentId = settings.string(SettingsStore.RESUME_SONG_ID, "")
    val current = currentId.takeIf { it.isNotEmpty() }?.let { library.song(it) }
    return resumeQueueOf(current, library.queue.first(), settings.long(SettingsStore.RESUME_POSITION_MS, 0L))
}
