package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCaseTest {

    @Test
    fun `capitalises every word, not just the first`() {
        assertEquals("Bollywood Hits", "bollywood hits".titleCase())
        assertEquals("Late Night Drive", "late night drive".titleCase())
    }

    @Test
    fun `treats hyphens as word breaks`() {
        assertEquals("Lo-Fi Beats", "lo-fi beats".titleCase())
        assertEquals("K-Pop", "k-pop".titleCase())
    }

    @Test
    fun `leaves acronyms and decade labels alone`() {
        assertEquals("EDM Bangers", "edm bangers".titleCase())
        assertEquals("R&B", "r&b".titleCase())
        assertEquals("90s Bollywood", "90s bollywood".titleCase())
        assertEquals("2000s Pop", "2000s pop".titleCase())
    }
}
