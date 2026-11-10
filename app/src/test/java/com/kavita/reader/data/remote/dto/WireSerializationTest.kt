package com.kavita.reader.data.remote.dto

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the exact JSON the app sends.
 *
 * Regression: API-key login serialised to `{"apiKey":"..."}` because `username`/`password`
 * declared `= ""` defaults, and kotlinx.serialization omits defaults unless `encodeDefaults` is on.
 * Kavita's `LoginDto` marks both as required, so the server answered
 * `400 One or more validation errors occurred.`
 *
 * A MockWebServer test would not have caught this: it asserts what the app *intends* to send, and
 * the intent was fine — the encoder silently dropped fields. Asserting the encoded string does.
 */
class WireSerializationTest {

    private val json = WireJson.instance

    @Test
    fun `api key login includes empty username and password`() {
        val payload = json.encodeToString(LoginRequest(apiKey = "abc123"))

        // Both fields must be present even though they equal their defaults.
        assertTrue("username must be present: $payload", payload.contains("\"username\""))
        assertTrue("password must be present: $payload", payload.contains("\"password\""))
        assertTrue("apiKey must be present: $payload", payload.contains("\"apiKey\":\"abc123\""))
    }

    @Test
    fun `password login carries both credentials`() {
        val payload = json.encodeToString(
            LoginRequest(username = "reader", password = "secret"),
        )
        assertTrue(payload.contains("\"username\":\"reader\""))
        assertTrue(payload.contains("\"password\":\"secret\""))
        // apiKey is null and must be omitted (explicitNulls = false).
        assertTrue("null apiKey must be omitted: $payload", !payload.contains("\"apiKey\""))
    }

    @Test
    fun `filter body keeps combination even when it equals the default`() {
        val filter = SeriesFilterV2Dto(
            statements = listOf(SeriesFilterStatementDto(field = 19, comparison = 0, value = "1")),
            // combination = 1 (And) is the declared default: it must still be encoded.
        )

        val payload = json.encodeToString(filter)
        assertTrue("combination must be present: $payload", payload.contains("\"combination\":1"))
        assertTrue(payload.contains("\"field\":19"))
    }

    @Test
    fun `filter body keeps limitTo default`() {
        val payload = json.encodeToString(SeriesFilterV2Dto())
        assertTrue(payload.contains("\"limitTo\":0"))
        assertTrue(payload.contains("\"statements\":[]"))
    }

    @Test
    fun `sort options keep ascending default`() {
        val payload = json.encodeToString(SeriesSortOptionDto(sortField = 1))
        // isAscending defaults to true; dropping it would change server behaviour.
        assertTrue("isAscending must be present: $payload", payload.contains("\"isAscending\":true"))
    }

    /**
     * Regression: the name search used `Contains(5)`, which Kavita's `ComparisonProfile`
     * rejects for `SeriesName` (allowed: Equal, NotEqual, BeginsWith, EndsWith, and Matches).
     * The server threw, the request came back as an error, and the grid looked simply
     * "filtered to nothing". The comparison integer is the whole contract, so it is pinned here
     * against the exact serialised body of a library-scoped filter.
     */
    @Test
    fun `library scoped name search uses Matches and pins the And combination`() {
        val body = com.kavita.reader.domain.model.SeriesFilter.Default
            .withNameQuery("Love")
            .withWantToRead(true)
            .toLibraryScopedDto(libraryId = 5)

        val payload = json.encodeToString(body)

        assertTrue("combination must be 1: $payload", payload.contains("\"combination\":1"))
        assertTrue(
            "library scope must be first: $payload",
            payload.contains("""{"field":19,"comparison":0,"value":"5"}"""),
        )
        assertTrue(
            "name must use Matches(7), never Contains(5): $payload",
            payload.contains("""{"field":1,"comparison":7,"value":"Love"}"""),
        )
        assertTrue(
            "want-to-read is a boolean string: $payload",
            payload.contains("""{"field":26,"comparison":0,"value":"true"}"""),
        )
        assertTrue(
            "sortOptions must be encoded: $payload",
            payload.contains("\"sortOptions\":{\"sortField\":1,\"isAscending\":true}"),
        )
    }

    @Test
    fun `save progress encodes every required field`() {
        val payload = json.encodeToString(
            SaveProgressRequest(
                libraryId = 2,
                seriesId = 471,
                volumeId = 1027,
                chapterId = 47436,
                pageNum = 3,
            ),
        )
        listOf("libraryId", "seriesId", "volumeId", "chapterId", "pageNum").forEach {
            assertTrue("$it must be present: $payload", payload.contains("\"$it\""))
        }
    }

    @Test
    fun `unknown fields are ignored when decoding`() {
        val decoded = json.decodeFromString(
            UserDto.serializer(),
            """{"id":1,"username":"u","brandNewFieldFromAFutureVersion":42}""",
        )
        assertEquals(1, decoded.id)
        assertEquals("u", decoded.username)
    }
}
