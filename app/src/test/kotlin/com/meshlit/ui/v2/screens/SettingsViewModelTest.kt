package com.meshlit.ui.v2.screens

import androidx.lifecycle.viewModelScope
import com.meshlit.ui.screens.settings.SettingsCategory
import com.meshlit.ui.screens.settings.SettingsSearchIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the v2 `SettingsViewModel`. Confirms the
 * sealed UiState + lifecycle-aware collect pattern is
 * wired correctly:
 *  - `Ready` on first emission (no Loading state for the v2
 *    screen since SettingsCategory.entries is a static list).
 *  - `query = "model"` filters the category list to MODELS.
 *  - `setQuery("")` clears the filter.
 *
 * The ViewModel launches a `_query.collect { … }` loop on
 * `init { … }` that never returns, so `runTest` waits forever
 * for the test scope to drain. The tests cancel the ViewModel's
 * `viewModelScope` after each assertion so `runTest` can return.
 *
 * Wiring is per the plan's step 5 audit: every v2 screen
 * surfaces a sealed UiState via `viewModel.uiState: StateFlow`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Ready with all categories`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(searchIndex = SettingsSearchIndex)
        // Flush the initial empty-string emission through the
        // `_query.collect` loop.
        runCurrent()
        val state = vm.uiState.value as SettingsUiState.Ready
        assertEquals(SettingsCategory.entries, state.categories)
        assertEquals("", state.query)
        assertFalse(state.isFiltering)
        assertEquals(SettingsCategory.entries.size, state.results.size)
        // Cancel the `_query.collect` loop so `runTest` can return.
        vm.viewModelScope.cancel()
    }

    @Test
    fun `setQuery with model filters to MODELS category`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(searchIndex = SettingsSearchIndex)
        runCurrent()
        vm.setQuery("model")
        runCurrent()
        val state = vm.uiState.value as SettingsUiState.Ready
        assertTrue(state.isFiltering)
        assertEquals("model", state.query)
        // The "Models" category name matches; "Model" also appears in
        // Performance subtitle ("GPU layers") but substring match is
        // case-insensitive on the category name only.
        assertTrue(
            "expected results to contain MODELS, got ${state.results.map { it.name }}",
            state.results.any { it == SettingsCategory.MODELS },
        )
        vm.viewModelScope.cancel()
    }

    @Test
    fun `setQuery blank restores full list`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(searchIndex = SettingsSearchIndex)
        runCurrent()
        vm.setQuery("privacy")
        runCurrent()
        vm.setQuery("")
        runCurrent()
        val state = vm.uiState.value as SettingsUiState.Ready
        assertFalse(state.isFiltering)
        assertEquals(SettingsCategory.entries.size, state.results.size)
        vm.viewModelScope.cancel()
    }
}
