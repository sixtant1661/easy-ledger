package com.easyledger.app.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    val guidanceExpanded: StateFlow<Boolean> = savedStateHandle.getStateFlow(GUIDANCE_KEY, false)

    fun toggleGuidance() {
        savedStateHandle[GUIDANCE_KEY] = !guidanceExpanded.value
    }

    private companion object { const val GUIDANCE_KEY = "guidanceExpanded" }
}
