package forpdateam.ru.forpda.model.system

import android.content.Context
import forpdateam.ru.forpda.model.data.storage.IPatternProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.regex.Pattern

/**
 * A-06: centralised tests for [PatternProvider], the single point of truth
 * for forum / news / theme / QMS / reputation regex patterns. The 17
 * individual parser tests already cover the patterns' *content*; this
 * file covers the *provider* contract: lazy init from the bundled
 * `assets/patterns.json`, caching of compiled [Pattern] objects, and
 * version-update behaviour.
 *
 * NOTE: Robolectric cannot bootstrap `AndroidKeyStore`, which the
 * application's Hilt graph needs (CookieManager → SecureCookiesPreferences
 * → MasterKey). To run this test today, swap the test's Application to a
 * stripped-down one — see `app/src/test/java/forpdateam/ru/forpda/testutil/StrippedApp.kt`
 * in the deferred follow-up tracked under `docs/BACKLOG_DEFERRED.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PatternProviderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun getPattern_unknownScope_throws() {
        val provider: IPatternProvider = PatternProvider(context, mockPrefs())
        // "missing" is not a scope defined in `assets/patterns.json`;
        // the provider must throw, not silently return null.
        assertThrows(Exception::class.java) {
            provider.getPattern("missing", "key")
        }
    }

    @Test
    fun getPattern_sameKeyTwice_returnsSameCompiledInstance() {
        val provider = PatternProvider(context, mockPrefs())
        val first: Pattern = provider.getPattern("forum", "announce")
        val second: Pattern = provider.getPattern("forum", "announce")
        // Caching: same in-memory Pattern instance, not recompiled.
        assertTrue(
            "PatternProvider must cache compiled Pattern objects",
            first === second,
        )
    }

    @Test
    fun getPattern_differentKeys_differentInstances() {
        val provider = PatternProvider(context, mockPrefs())
        val a = provider.getPattern("forum", "announce")
        val b = provider.getPattern("forum", "rules_items")
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(
            "Distinct patterns must not share a single instance",
            System.identityHashCode(a),
            System.identityHashCode(b),
        )
    }

    @Test
    fun update_bumpsCurrentVersion_andReplacesPattern() {
        val provider = PatternProvider(context, mockPrefs())
        val before = provider.getCurrentVersion()

        val payload = """
            {
              "version": ${before + 1},
              "scopes": [
                {
                  "scope": "forum",
                  "patterns": [
                    { "key": "announce", "value": "<title>([\\s\\S]*?)(?: - 4PDA)?<\\/title>" }
                  ]
                }
              ]
            }
        """.trimIndent()

        provider.update(payload)

        val after = provider.getCurrentVersion()
        assertNotEquals("version must advance", before, after)

        val updated: Pattern = provider.getPattern("forum", "announce")
        val m = updated.matcher("<title>Hello - 4PDA</title>")
        assertTrue("updated pattern must match a real title", m.find())
        // Group 1 captures the title up to the optional " - 4PDA" suffix.
        // The captured text is "Hello" — the trailing space belongs to the
        // literal " - " in the pattern.
        assertEquals("Hello", m.group(1))
    }

    private fun mockPrefs(): android.content.SharedPreferences =
        object : android.content.SharedPreferences {
            override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
            override fun getString(k: String?, d: String?): String? = d
            override fun getStringSet(k: String?, d: MutableSet<String>?) = d
            override fun getInt(k: String?, d: Int): Int = d
            override fun getLong(k: String?, d: Long): Long = d
            override fun getFloat(k: String?, d: Float): Float = d
            override fun getBoolean(k: String?, d: Boolean): Boolean = d
            override fun contains(k: String?): Boolean = false
            override fun edit(): android.content.SharedPreferences.Editor = editor()
            override fun registerOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(l: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        }

    private fun editor(): android.content.SharedPreferences.Editor =
        object : android.content.SharedPreferences.Editor {
            override fun putString(k: String?, v: String?): android.content.SharedPreferences.Editor = this
            override fun putStringSet(k: String?, v: MutableSet<String>?): android.content.SharedPreferences.Editor = this
            override fun putInt(k: String?, v: Int): android.content.SharedPreferences.Editor = this
            override fun putLong(k: String?, v: Long): android.content.SharedPreferences.Editor = this
            override fun putFloat(k: String?, v: Float): android.content.SharedPreferences.Editor = this
            override fun putBoolean(k: String?, v: Boolean): android.content.SharedPreferences.Editor = this
            override fun remove(k: String?): android.content.SharedPreferences.Editor = this
            override fun clear(): android.content.SharedPreferences.Editor = this
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
}
