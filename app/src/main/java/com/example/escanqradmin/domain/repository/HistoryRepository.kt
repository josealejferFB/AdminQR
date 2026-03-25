package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.QrContent
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistory(): Flow<List<QrContent>>
    fun addRecord(record: QrContent)
    fun updateRecord(record: QrContent)
    fun deleteRecord(id: String)
}
