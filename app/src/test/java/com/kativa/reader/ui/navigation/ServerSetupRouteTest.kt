package com.kativa.reader.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The setup route carries an explicit `showBack` flag instead of implying it from the presence
 * of a server id: first-run setup (welcome → setup) has nothing behind it, while "add another
 * server" and "edit server" were pushed on top of the picker and must be dismissible.
 */
class ServerSetupRouteTest {

    @Test
    fun firstRunSetupHasNoBackFlag() {
        val route = Routes.serverSetup(showBack = false)

        assertEquals(Routes.SERVER_SETUP, route)
        assertFalse(route.contains(Routes.ARG_SHOW_BACK))
    }

    @Test
    fun addingAServerFromThePickerKeepsBack() {
        val route = Routes.serverSetup(showBack = true)

        assertTrue(route.startsWith(Routes.SERVER_SETUP))
        assertTrue(route.contains("${Routes.ARG_SHOW_BACK}=true"))
        assertFalse(route.contains(Routes.ARG_SERVER_ID))
    }

    @Test
    fun editingAServerHasAnIdAndBack() {
        val route = Routes.serverSetup("abc", showBack = true)

        assertEquals(
            "${Routes.SERVER_SETUP}?${Routes.ARG_SERVER_ID}=abc&${Routes.ARG_SHOW_BACK}=true",
            route,
        )
    }

    @Test
    fun editingDefaultsToShowingBack() {
        assertTrue(Routes.serverSetup("abc").contains("${Routes.ARG_SHOW_BACK}=true"))
    }
}
