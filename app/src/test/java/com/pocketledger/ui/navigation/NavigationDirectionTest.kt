package com.pocketledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way a screen slides in, and which dock tab owns it.
 *
 * Verified here rather than on a device because the transition is 150ms: a screenshot
 * cannot catch it, and the one attempt to slow Compose's animation clock was refused by the
 * phone. The rule is pure string logic, so it can be pinned exactly.
 *
 * The failure this guards was reported as "entering any sub-page should slide in from the
 * right": comparing dock indices made two sub-pages of the *same* tab compare equal, so
 * 设置 → 关于 → 功能介绍 slid in from the left -- the direction a deeper screen must never
 * come from.
 */
class NavigationDirectionTest {

    // Route *patterns*, which is what `destination.route` returns at runtime.

    @Test
    fun `a sub-page opened from its own tab travels forward`() {
        assertTrue(isForward(Routes.SETTINGS, Routes.ABOUT))
        assertTrue(isForward(Routes.LEDGER, Routes.SEARCH))
    }

    @Test
    fun `the bug - a sub-page opened from another sub-page of the same tab`() {
        assertTrue(isForward(Routes.ABOUT, Routes.FEATURES))
        assertTrue(isForward(Routes.FEATURES, Routes.TERMS_OF_USE))
        assertTrue(isForward(Routes.LEDGERS, "${Routes.BUDGET}/{${Routes.BUDGET_ARG}}"))
    }

    @Test
    fun `climbing back out to a tab is the reverse of going in`() {
        assertFalse(isForward(Routes.ABOUT, Routes.SETTINGS))
        assertFalse(isForward(Routes.FEATURES, Routes.SETTINGS))
        assertFalse(isForward(Routes.SEARCH, Routes.LEDGER))
    }

    @Test
    fun `moving between docks follows the order of the bar`() {
        assertTrue(isForward(Routes.LEDGER, Routes.STATS))
        assertTrue(isForward(Routes.STATS, Routes.ACCOUNTS))
        assertTrue(isForward(Routes.ACCOUNTS, Routes.SETTINGS))
        assertFalse(isForward(Routes.ACCOUNTS, Routes.STATS))
        assertFalse(isForward(Routes.SETTINGS, Routes.LEDGER))
    }

    @Test
    fun `full-window pages count as deeper`() {
        assertTrue(isForward(Routes.LEDGER, Routes.ENTRY))
        assertTrue(isForward(Routes.LEDGER, "${Routes.DETAIL}/{${Routes.DETAIL_ARG}}"))
        assertTrue(isForward(Routes.LEDGER, "${Routes.EDIT}/{${Routes.EDIT_ARG}}"))
    }

    @Test
    fun `every settings sub-page keeps the settings tab lit`() {
        val subPages = listOf(
            Routes.LEDGERS, Routes.CATEGORIES, Routes.INSTALLMENTS, Routes.PINNED,
            Routes.IMPORT, Routes.EXPORT, Routes.ABOUT, Routes.TERMS, Routes.FEATURES,
            Routes.TERMS_OF_USE, "${Routes.BUDGET}/{${Routes.BUDGET_ARG}}",
        )
        subPages.forEach { route ->
            assertEquals("$route should light 设置", Routes.SETTINGS, owningTab(route))
        }
    }

    @Test
    fun `entry, search, detail and edit belong to the list tab`() {
        assertEquals(Routes.LEDGER, owningTab(Routes.ENTRY))
        assertEquals(Routes.LEDGER, owningTab(Routes.SEARCH))
        assertEquals(Routes.LEDGER, owningTab("${Routes.DETAIL}/{${Routes.DETAIL_ARG}}"))
        assertEquals(Routes.LEDGER, owningTab("${Routes.EDIT}/{${Routes.EDIT_ARG}}"))
    }

    @Test
    fun `the docks own themselves`() {
        assertEquals(Routes.LEDGER, owningTab(Routes.LEDGER))
        assertEquals(Routes.STATS, owningTab(Routes.STATS))
        assertEquals(Routes.ACCOUNTS, owningTab(Routes.ACCOUNTS))
        assertEquals(Routes.SETTINGS, owningTab(Routes.SETTINGS))
    }

    @Test
    fun `nothing owns an unknown or missing route`() {
        assertNull(owningTab(null))
        assertNull(owningTab("somethingElse"))
    }
}
