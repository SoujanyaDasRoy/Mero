package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {

    @Test
    fun `a higher patch is newer`() {
        assertTrue(isNewer("1.7.1", "1.7.0"))
    }

    @Test
    fun `the same version is not newer`() {
        assertFalse(isNewer("1.7.0", "1.7.0"))
    }

    @Test
    fun `an older version is not newer`() {
        assertFalse(isNewer("1.6.0", "1.7.0"))
    }

    /**
     * The reason this is compared numerically at all: as text, "1.10.0" sorts
     * before "1.9.0", so a string compare would stop offering updates forever
     * the moment the minor version reached double digits.
     */
    @Test
    fun `double-digit minor versions beat single-digit ones`() {
        assertTrue(isNewer("1.10.0", "1.9.0"))
        assertFalse(isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `a leading v is ignored`() {
        assertTrue(isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `a shorter version is treated as trailing zeroes`() {
        assertFalse(isNewer("1.7", "1.7.0"))
        assertTrue(isNewer("1.8", "1.7.9"))
    }

    @Test
    fun `a suffix after the numbers does not break the comparison`() {
        assertTrue(isNewer("1.8.0-beta", "1.7.0"))
    }

    @Test
    fun `an unparseable version is never offered`() {
        assertFalse(isNewer("latest", "1.7.0"))
    }
}

class ReleaseParsingTest {

    private fun json(vararg assetNames: String, tag: String = "v1.8.0", draft: Boolean = false) = """
        {
          "tag_name": "$tag",
          "name": "Mero $tag",
          "body": "Notes go here",
          "draft": $draft,
          "prerelease": false,
          "assets": [
            ${assetNames.joinToString(",") { name ->
        """{"name":"$name","size":1234,"browser_download_url":"https://example.invalid/$name"}"""
    }}
          ]
        }
    """.trimIndent()

    @Test
    fun `reads the version and notes`() {
        val release = parseRelease(json("app-arm64-v8a-release.apk"), listOf("arm64-v8a"))!!
        assertEquals("1.8.0", release.versionName)
        assertEquals("Notes go here", release.notes)
        assertEquals(1234L, release.sizeBytes)
    }

    /**
     * Mero ships one APK per architecture. Handing an arm64 phone the x86 file
     * fails to install with nothing useful on screen, so the picker has to
     * follow the device rather than the asset order.
     */
    @Test
    fun `picks the asset matching this device`() {
        val release = parseRelease(
            json("app-x86-release.apk", "app-arm64-v8a-release.apk", "app-armeabi-v7a-release.apk"),
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )!!
        assertTrue(release.apkUrl.endsWith("app-arm64-v8a-release.apk"))
    }

    @Test
    fun `honours ABI preference order`() {
        val release = parseRelease(
            json("app-armeabi-v7a-release.apk", "app-arm64-v8a-release.apk"),
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )!!
        assertTrue(release.apkUrl.endsWith("app-arm64-v8a-release.apk"))
    }

    @Test
    fun `falls back to a universal build when no split matches`() {
        val release = parseRelease(json("app-universal-release.apk"), listOf("riscv64"))!!
        assertTrue(release.apkUrl.endsWith("app-universal-release.apk"))
    }

    @Test
    fun `falls back to any apk rather than offering nothing`() {
        val release = parseRelease(json("mero.apk"), listOf("riscv64"))!!
        assertTrue(release.apkUrl.endsWith("mero.apk"))
    }

    @Test
    fun `a release with no apk is not an update`() {
        assertNull(parseRelease(json("release-notes.md", "sources.zip"), listOf("arm64-v8a")))
    }

    @Test
    fun `a draft is not an update`() {
        assertNull(parseRelease(json("app-arm64-v8a-release.apk", draft = true), listOf("arm64-v8a")))
    }

    /** GitHub returns "Not Found" text for a repo with no releases at all. */
    @Test
    fun `garbage is not an update`() {
        assertNull(parseRelease("{\"message\":\"Not Found\"}", listOf("arm64-v8a")))
        assertNull(parseRelease("not json at all", listOf("arm64-v8a")))
    }

    @Test
    fun `unknown fields do not break parsing`() {
        val body = """
            {"tag_name":"v9.9.9","body":"x","assets":[
              {"name":"a.apk","size":1,"browser_download_url":"https://example.invalid/a.apk",
               "uploader":{"login":"someone"},"content_type":"application/vnd.android.package-archive"}
            ],"author":{"login":"someone"},"reactions":{"total_count":0}}
        """.trimIndent()
        assertEquals("9.9.9", parseRelease(body, listOf("arm64-v8a"))!!.versionName)
    }
}
