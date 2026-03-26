package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.QrContent

interface SyncRepository {
    suspend fun syncEntry(data: QrContent): Result<Unit>
    suspend fun fetchEntries(): Result<List<QrContent>>
    suspend fun refreshConductores(): Result<List<QrContent>>
    suspend fun deleteEntry(cedula: String): Result<Unit>
    suspend fun updateEntry(data: QrContent): Result<Unit>
}
