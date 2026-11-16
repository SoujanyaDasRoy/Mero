package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for a real org.json (the plain-JVM android.jar only has stubs).
@Config(sdk = [34], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class GroupApiTest {

    @Test fun `a page of steps is parsed in order`() {
        val page = parseGroupPage(
            """{"name":"Road trip","hasCover":true,"head":3,"steps":[
                {"seq":1,"kind":"add","value":"dQw4w9WgXcQ"},
                {"seq":2,"kind":"remove","value":"dQw4w9WgXcQ"},
                {"seq":3,"kind":"cover","value":null}]}""",
        )
        assertEquals("Road trip", page.name)
        assertTrue(page.hasCover)
        assertEquals(3L, page.head)
        assertEquals(
            listOf(GroupStep(1, "add", "dQw4w9WgXcQ"), GroupStep(2, "remove", "dQw4w9WgXcQ"), GroupStep(3, "cover", null)),
            page.steps,
        )
        assertFalse(page.more)
    }

    @Test fun `more is read when present`() {
        val page = parseGroupPage("""{"name":"N","hasCover":false,"head":2000,"steps":[],"more":true}""")
        assertTrue(page.more)
        assertFalse(page.hasCover)
    }
}
