package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.data.network.model.GateRegisterResponse
import com.example.escanqradmin.domain.model.QrContent

interface SyncRepository {
    suspend fun syncEntry(data: QrContent): Result<Unit>
    suspend fun refreshConductores(): Result<List<QrContent>>
    suspend fun deleteEntry(cedula: String): Result<Unit>
    suspend fun updateEntry(
        data: QrContent,
        addGateIds: List<Int> = emptyList(),
        removeGateIds: List<Int> = emptyList()
    ): Result<Unit>
    suspend fun registerGate(name: String, macAddress: String): Result<GateRegisterResponse>
    suspend fun getGateUsers(gateId: Int): Result<List<QrContent>>
}
