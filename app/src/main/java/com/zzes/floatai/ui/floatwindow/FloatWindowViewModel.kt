package com.zzes.floatai.ui.floatwindow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FloatWindowViewModel : ViewModel() {
    
    var uiState by mutableStateOf(FloatWindowUiState())
        private set
    
    fun updateAiResponse(response: String) {
        uiState = uiState.copy(
            aiResponse = response,
            isLoading = false
        )
    }
    
    fun setLoading(loading: Boolean) {
        uiState = uiState.copy(isLoading = loading)
    }
    
    fun clearResponse() {
        uiState = uiState.copy(aiResponse = "")
    }
    
    fun executeWithLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            setLoading(true)
            try {
                block()
            } finally {
                setLoading(false)
            }
        }
    }
}

data class FloatWindowUiState(
    val aiResponse: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
