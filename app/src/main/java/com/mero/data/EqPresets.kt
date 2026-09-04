package com.mero.data

/** Real DSP configuration, not fixtures — the gains applied by M4's equalizer. */
object EqPresets {

    val bandLabels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    /** Centre frequencies in Hz, matching [bandLabels]. */
    val bandFrequencies = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

    val presets: Map<String, List<Int>> = linkedMapOf(
        "Flat" to List(10) { 0 },
        "Bass Boost" to listOf(9, 8, 6, 3, 0, 0, 0, 1, 2, 2),
        "Vocal" to listOf(-3, -2, 0, 2, 5, 6, 4, 2, 0, -1),
        "Rock" to listOf(5, 4, 2, -1, -2, 0, 3, 5, 5, 4),
        "Electronic" to listOf(7, 6, 2, 0, -2, 1, 2, 4, 6, 6),
        "Custom" to listOf(2, 1, 0, -1, 0, 2, 3, 1, 0, 3),
    )
}
