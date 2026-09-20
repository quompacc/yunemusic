package com.yunemusic.data

import com.yunemusic.domain.model.TasteProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.utils.JavaScript

/** JVM checks complement, but do not replace, testing the R8 APK on Android. */
class RuntimeCompatibilityTest {
    @Test fun storedProfileKeepsStableFieldNames() {
        // Persisted names must remain readable when app model classes are obfuscated.
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"favoriteArtists":{"Artist":2.5},"favoriteGenres":{"Jazz":1.0},"dislikedArtists":["Other"],"topTrackIds":["saved-id"],"playCount":12,"lastUpdated":123,"futureField":true}"""
        val profile = json.decodeFromString<TasteProfile>(old)
        assertEquals(12, profile.playCount)
        assertEquals(2.5f, profile.favoriteArtists["Artist"])
        assertEquals(setOf("Other"), profile.dislikedArtists)
        assertEquals(listOf("saved-id"), profile.topTrackIds)
        val encoded = json.encodeToString(profile)
        assertTrue(encoded.contains("\"favoriteArtists\""))
        assertTrue(encoded.contains("\"topTrackIds\""))
        assertEquals(profile, json.decodeFromString<TasteProfile>(encoded))
    }

    @Test fun newPipeRhinoInterpreterExecutesSignatureStyleOperations() {
        // Uses NewPipe's actual interpreted-mode bridge, including Rhino builtins.
        val result = JavaScript.run("function decode(s) { var a=s.split(''); a.reverse(); a.splice(0,1); return a.join(''); }",
            "decode", "abcdef")
        assertEquals("edcba", result)
    }
}
