package forpdateam.ru.forpda.model.data.remote.api.checker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CheckerParserTest {

    private val parser = CheckerParser()

    @Test
    fun parse_fillsScalarsListsAndPatternsVersion() {
        val json = """
            {
              "update": {
                "version_code": 301,
                "version_build": 13644,
                "version_name": "2.0.1",
                "build_date": "2026-04-10",
                "patternsVersion": 12,
                "links": [
                  { "name": "APK", "url": "https://example.com/a.apk", "type": "direct" }
                ],
                "important": [ "Critical" ],
                "added": [ "Feature A" ],
                "fixed": [ "Bug B" ],
                "changed": [ "Tweak C" ]
              }
            }
        """.trimIndent()

        val data = parser.parse(json)

        assertEquals(301, data.code)
        assertEquals(13644, data.build)
        assertEquals("2.0.1", data.name)
        assertEquals("2026-04-10", data.date)
        assertEquals(12, data.patternsVersion)

        assertEquals(1, data.links.size)
        assertEquals("APK", data.links[0].name)
        assertEquals("https://example.com/a.apk", data.links[0].url)
        assertEquals("direct", data.links[0].type)

        assertEquals(listOf("Critical"), data.important)
        assertEquals(listOf("Feature A"), data.added)
        assertEquals(listOf("Bug B"), data.fixed)
        assertEquals(listOf("Tweak C"), data.changed)
    }

    @Test
    fun parse_usesDefaultsForMissingOptionalScalars() {
        val json = """
            {
              "update": {
                "patternsVersion": 0,
                "links": [],
                "important": [],
                "added": [],
                "fixed": [],
                "changed": []
              }
            }
        """.trimIndent()

        val data = parser.parse(json)

        assertEquals(Int.MAX_VALUE, data.code)
        assertEquals(Int.MAX_VALUE, data.build)
        assertEquals("", data.name)
        assertEquals("", data.date)
        assertTrue(data.links.isEmpty())
    }
}
