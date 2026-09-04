package com.mero.data

import com.mero.domain.LyricLine
import com.mero.domain.Playlist
import com.mero.domain.Song

/**
 * The design's fixture data, lifted from `Mero.dc.html`. Exists so the screens
 * render standalone in @Preview and on-device before any repository is wired up.
 * Delete once real data flows (M1 Task 4 onward).
 */
object SampleData {

    val songs = listOf(
        Song("s0", "Mango Tree", "Angus & Julia Stone", "Snow", 228, downloaded = true),
        Song("s1", "Big Jet Plane", "Angus & Julia Stone", "Down the Way", 236, downloaded = true),
        Song("s2", "Riptide", "Vance Joy", "Dream Your Life Away", 204, downloaded = true),
        Song("s3", "Electric Feel", "MGMT", "Oracular Spectacular", 229),
        Song("s4", "Sunflower", "Rex Orange County", "Apricot Princess", 176),
        Song("s5", "Nightcall", "Kavinsky", "OutRun", 258, downloaded = true),
        Song("s6", "Redbone", "Childish Gambino", "Awaken, My Love!", 327),
        Song("s7", "Cigarettes", "Ruel", "Bright Lights, Red Eyes", 202),
    )

    val quickPicks = songs.take(3)

    val recentlyPlayed = listOf(
        Playlist("p0", "Indie Chill", 42, downloaded = true),
        Playlist("p1", "Snow", 11),
        Playlist("p2", "Late Drive", 18),
    )

    val playlists = listOf(
        Playlist("p0", "Indie Chill", 42, downloaded = true),
        Playlist("p3", "Monsoon", 27),
        Playlist("p4", "Gym", 63, downloaded = true),
        Playlist("p5", "Bengali Classics", 34),
    )

    val moods = listOf(
        "local_fire_department" to "Trending",
        "bedtime" to "Sleep",
        "directions_run" to "Workout",
        "self_improvement" to "Focus",
        "celebration" to "Party",
        "cloud" to "Rainy day",
    )

    val recentSearches = listOf(
        "angus julia stone",
        "nightcall kavinsky",
        "lo-fi study",
        "redbone",
    )

    val trending = listOf("Arijit Singh", "lo-fi", "90s Bollywood", "Tame Impala", "chillhop")

    val searchTabs = listOf("Songs", "Albums", "Artists", "Playlists")

    val libraryTabs = listOf("Playlists", "Songs", "Albums", "Artists", "Downloads")

    val lyrics = listOf(
        LyricLine(0, "I was sitting in the shade of a mango tree"),
        LyricLine(14, "Waiting on the rain to come"),
        LyricLine(27, "You said the summer wouldn't last"),
        LyricLine(41, "And you were right, it never does"),
        LyricLine(58, "So I packed the car up slow"),
        LyricLine(74, "Left the porch light burning low"),
        LyricLine(92, "Every road out of this town"),
        LyricLine(108, "Runs back to where you are now"),
    )

    val eqBandLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val eqPresets: Map<String, List<Int>> = linkedMapOf(
        "Flat" to List(10) { 0 },
        "Bass Boost" to listOf(9, 8, 6, 3, 0, 0, 0, 1, 2, 2),
        "Vocal" to listOf(-3, -2, 0, 2, 5, 6, 4, 2, 0, -1),
        "Rock" to listOf(5, 4, 2, -1, -2, 0, 3, 5, 5, 4),
        "Electronic" to listOf(7, 6, 2, 0, -2, 1, 2, 4, 6, 6),
        "Custom" to listOf(2, 1, 0, -1, 0, 2, 3, 1, 0, 3),
    )
}
