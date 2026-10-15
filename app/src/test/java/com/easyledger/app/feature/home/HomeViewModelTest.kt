package com.easyledger.app.feature.home

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    @Test fun guidanceStartsCollapsedAndCanBeToggled() {
        val model = HomeViewModel(SavedStateHandle())
        assertFalse(model.guidanceExpanded.value)
        model.toggleGuidance()
        assertTrue(model.guidanceExpanded.value)
        model.toggleGuidance()
        assertFalse(model.guidanceExpanded.value)
    }

    @Test fun restoredGuidanceStateIsKept() {
        val model = HomeViewModel(SavedStateHandle(mapOf("guidanceExpanded" to true)))
        assertTrue(model.guidanceExpanded.value)
        model.toggleGuidance()
        assertFalse(model.guidanceExpanded.value)
    }
}
