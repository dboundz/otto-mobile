package to.ottomot.driftd

import org.junit.Assert.assertEquals
import org.junit.Test
import to.ottomot.driftd.core.network.dto.CircleDto
import to.ottomot.driftd.core.session.circlesSortedByRecentAccess

class SquadRecencyTest {
    private fun circle(id: String, name: String = id) =
        CircleDto(
            id = id,
            name = name,
            description = null,
            ownerId = "owner",
            members = emptyList(),
            photoUrl = null,
        )

    @Test
    fun sortsMostRecentlyAccessedFirst() {
        val circles = listOf(circle("a"), circle("b"), circle("c"))
        val recency =
            mapOf(
                "a" to 100.0,
                "b" to 300.0,
                "c" to 200.0,
            )

        assertEquals(listOf("b", "c", "a"), circlesSortedByRecentAccess(circles, recency).map { it.id })
    }

    @Test
    fun preservesApiOrderWhenNoRecencyTimestamps() {
        val circles = listOf(circle("a"), circle("b"), circle("c"))

        assertEquals(listOf("a", "b", "c"), circlesSortedByRecentAccess(circles, emptyMap()).map { it.id })
    }

    @Test
    fun preservesApiOrderOnRecencyTie() {
        val circles = listOf(circle("a"), circle("b"), circle("c"))
        val recency =
            mapOf(
                "a" to 100.0,
                "b" to 100.0,
                "c" to 200.0,
            )

        assertEquals(listOf("c", "a", "b"), circlesSortedByRecentAccess(circles, recency).map { it.id })
    }

    @Test
    fun matchesRecencyWithCaseInsensitiveIds() {
        val circles = listOf(circle("ABC"), circle("def"))
        val recency = mapOf("abc" to 50.0, "DEF" to 100.0)

        assertEquals(listOf("def", "ABC"), circlesSortedByRecentAccess(circles, recency).map { it.id })
    }
}
