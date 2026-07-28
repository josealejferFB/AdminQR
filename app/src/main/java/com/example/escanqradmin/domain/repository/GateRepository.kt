package com.example.escanqradmin.domain.repository

import com.example.escanqradmin.domain.model.GateInfo

interface GateRepository {
    suspend fun getGates(): Result<List<GateInfo>>
    suspend fun updateGateName(gateId: Int, newName: String): Result<Unit>
    suspend fun deleteGate(gateId: Int): Result<Unit>
}
