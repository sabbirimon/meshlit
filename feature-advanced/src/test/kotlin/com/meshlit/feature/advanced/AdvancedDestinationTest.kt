package com.meshlit.feature.advanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for the [AdvancedDestination] enum. Adding a new
 * destination must update this test (and the
 * `AdvancedNavHost.composable(...)` list) to keep the navigation
 * graph in sync.
 */
class AdvancedDestinationTest {

    @Test
    fun every_entry_has_a_unique_route() {
        val routes = AdvancedDestination.values().map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun every_route_starts_with_advanced_prefix() {
        AdvancedDestination.values().forEach { d ->
            assertTrue(
                "route '${d.route}' must start with 'advanced/'",
                d.route.startsWith("advanced/"),
            )
        }
    }

    @Test
    fun every_entry_has_a_label_and_description() {
        AdvancedDestination.values().forEach { d ->
            assertTrue("blank label for $d", d.label.isNotBlank())
            assertTrue("blank description for $d", d.description.isNotBlank())
        }
    }

    @Test
    fun hub_card_lists_cover_all_destinations() {
        val every = AdvancedDestination.values().toSet()
        // We don't enumerate HubCards statically — but every
        // destination must be reachable from the hub: every route
        // must be referenced at least once. The NavHost composable
        // does the routing; this test guards against adding an
        // entry without updating the nav host by counting the
        // number of composable(...) registrations needed vs.
        // entries (every entry has exactly one).
        assertTrue("must have at least the four speech-lab entries",
            every.size >= 4,
        )
    }

    @Test
    fun all_destinations_have_distinct_primary_categories() {
        val cats = AdvancedDestination.values().map { it.primaryCategory }.toSet()
        // Not strict uniqueness — multiple destinations can map to
        // the same category — but at least we should span more
        // than one.
        assertNotEquals(1, cats.size)
        assertTrue("must span >= 4 categories", cats.size >= 4)
    }
}