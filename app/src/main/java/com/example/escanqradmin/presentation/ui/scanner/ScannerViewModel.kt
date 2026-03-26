package com.example.escanqradmin.presentation.ui.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.example.escanqradmin.domain.model.QrContent

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {
    private val _scannedData = MutableStateFlow<QrContent?>(null)
    val scannedData = _scannedData.asStateFlow()

    fun processBarcode(rawValue: String?) {
        if (rawValue == null || _scannedData.value != null) return
        
        try {
            val format = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val userData = format.decodeFromString<com.example.escanqradmin.domain.model.UserData>(rawValue)
            
            val decryptedAid = decryptAndroidId(userData.aid)
            val plateStr = userData.p.joinToString(", ")
            
            _scannedData.value = QrContent(
                androidId = decryptedAid,
                userName = userData.u,
                cedula = userData.c,
                plate = plateStr
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun decryptAndroidId(combinedEncrypted: String): String {
        try {
            val keyBytes = com.example.escanqradmin.domain.model.SecurityConstants.SHARED_AES_KEY.toByteArray(Charsets.UTF_8)
            val secretKeySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            
            val ivBytes: ByteArray
            val encryptedBytes: ByteArray

            if (combinedEncrypted.contains(":")) {
                val parts = combinedEncrypted.split(":")
                ivBytes = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
                encryptedBytes = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
            } else {
                val combinedBytes = android.util.Base64.decode(combinedEncrypted, android.util.Base64.DEFAULT)
                ivBytes = combinedBytes.copyOfRange(0, 12)
                encryptedBytes = combinedBytes.copyOfRange(12, combinedBytes.size)
            }
            
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val spec = javax.crypto.spec.GCMParameterSpec(128, ivBytes)
            
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKeySpec, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return "Error: Descifrado GCM fallido"
        }
    }
    
    fun clearData() {
        _scannedData.value = null
    }
}
