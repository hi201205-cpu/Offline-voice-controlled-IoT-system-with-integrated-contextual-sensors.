package com.example.doan2.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.doan2.data.model.AutoLog
import com.example.doan2.data.model.DoorLog
import com.example.doan2.data.model.VoiceLog
import com.example.doan2.data.model.WebLog
import com.example.doan2.data.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class HistoryItem(open val id: String, open val ts: Long) {
    data class Door(val log: DoorLog) : HistoryItem(log.id, log.ts)
    data class Auto(val log: AutoLog) : HistoryItem(log.id, log.ts)
    data class Voice(val log: VoiceLog) : HistoryItem(log.id, log.ts)
    data class Web(val log: WebLog) : HistoryItem(log.id, log.ts)
}

data class HistoryUiState(
    val selectedFilterIndex: Int = 0,
    val searchQuery: String = "",
    val allItems: List<HistoryItem> = emptyList(),
    val filteredItems: List<HistoryItem> = emptyList()
)

class HistoryViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var doorLogs: List<DoorLog> = emptyList()
    private var autoLogs: List<AutoLog> = emptyList()
    private var voiceLogs: List<VoiceLog> = emptyList()
    private var webLogs: List<WebLog> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeDoorLogs().collect { logs ->
                doorLogs = logs.filter { log ->
                    val src = log.source.lowercase()
                    src != "web" && src != "app" && src != "remote" && src != "unknown"
                }
                updateItems()
            }
        }

        viewModelScope.launch {
            repository.observeAutoLogs().collect { logs ->
                autoLogs = logs
                updateItems()
            }
        }

        viewModelScope.launch {
            repository.observeVoiceLogs().collect { logs ->
                voiceLogs = logs
                updateItems()
            }
        }

        viewModelScope.launch {
            repository.observeWebLogs().collect { logs ->
                webLogs = logs
                updateItems()
            }
        }
    }

    fun setFilterIndex(index: Int) {
        _uiState.value = _uiState.value.copy(selectedFilterIndex = index)
        filterList()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterList()
    }

    private fun updateItems() {
        val list = mutableListOf<HistoryItem>()
        doorLogs.forEach { list.add(HistoryItem.Door(it)) }
        autoLogs.forEach { list.add(HistoryItem.Auto(it)) }
        voiceLogs.forEach { list.add(HistoryItem.Voice(it)) }
        webLogs.forEach { list.add(HistoryItem.Web(it)) }
        list.sortByDescending {
            val ts = it.ts
            if (ts < 10_000_000_000L) ts * 1000L else ts
        }

        _uiState.value = _uiState.value.copy(allItems = list)
        filterList()
    }

    private fun filterList() {
        val current = _uiState.value
        val categoryFiltered = when (current.selectedFilterIndex) {
            1 -> current.allItems.filterIsInstance<HistoryItem.Voice>()
            2 -> current.allItems.filterIsInstance<HistoryItem.Door>()
            3 -> current.allItems.filterIsInstance<HistoryItem.Web>()
            4 -> current.allItems.filterIsInstance<HistoryItem.Auto>()
            else -> current.allItems
        }

        val query = current.searchQuery.trim().lowercase()
        val finalFiltered = if (query.isBlank()) {
            categoryFiltered
        } else {
            categoryFiltered.filter { item ->
                when (item) {
                    is HistoryItem.Door -> item.log.event.lowercase().contains(query) || item.log.source.lowercase().contains(query) || (item.log.who?.lowercase()?.contains(query) == true)
                    is HistoryItem.Auto -> item.log.action.lowercase().contains(query) || item.log.reason.lowercase().contains(query)
                    is HistoryItem.Voice -> item.log.keyword.lowercase().contains(query)
                    is HistoryItem.Web -> item.log.label.lowercase().contains(query) || item.log.user.lowercase().contains(query) || item.log.device.lowercase().contains(query)
                }
            }
        }

        _uiState.value = current.copy(filteredItems = finalFiltered)
    }
}
