package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.QrContent

interface SyncRepository {
    suspend fun syncEntry(data: QrContent): Result<Unit>
}
