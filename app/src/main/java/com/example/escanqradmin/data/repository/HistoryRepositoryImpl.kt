package com.example.escanqradmin.data.repository

import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor() : HistoryRepository {
    private val _history = MutableStateFlow<List<QrContent>>(emptyList())
    
    override fun getHistory(): Flow<List<QrContent>> = _history.asStateFlow()
    
    override fun addRecord(record: QrContent) {
        val currentList = _history.value.toMutableList()
        currentList.add(0, record) // Add at top
        _history.value = currentList
    }

    override fun updateRecord(record: QrContent) {
        val currentList = _history.value.toMutableList()
        val index = currentList.indexOfFirst { it.androidId == record.androidId }
        if (index != -1) {
            currentList[index] = record
            _history.value = currentList
        }
    }

    override fun deleteRecord(id: String) {
        val currentList = _history.value.toMutableList()
        currentList.removeAll { it.androidId == id }
        _history.value = currentList
    }

    override fun syncWithServer(records: List<QrContent>) {
        _history.value = records
    }
}
