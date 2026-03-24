package com.example.escanqradmin.presentation.ui.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.example.escanqradmin.domain.model.QrContent
import org.json.JSONObject

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {
    private val _scannedData = MutableStateFlow<QrContent?>(null)
    val scannedData = _scannedData.asStateFlow()

    fun processBarcode(rawValue: String?) {
        if (rawValue == null || _scannedData.value != null) return
        
        try {
            // 1. Deserializar el JSON a UserData
            val format = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val userData = format.decodeFromString<com.example.escanqradmin.domain.model.UserData>(rawValue)
            
            // 2. Descifrar el Android ID
            val decryptedAid = decryptAndroidId(userData.aid)
            
            // 3. Mapear a QrContent
            val plateStr = userData.p.joinToString(", ")
            
            _scannedData.value = QrContent(
                androidId = decryptedAid,
                userName = userData.u,
                cedula = userData.c,
                phone = "", // Phone is not in UserData based on specs
                plate = plateStr
            )
            
        } catch (e: Exception) {
            // Ignorar errores de parseo o decodificación, el escáner seguirá intentando
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
                // Formato: base64(IV):base64(cifrado+tag)
                val parts = combinedEncrypted.split(":")
                ivBytes = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
                encryptedBytes = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
            } else {
                // Fallback para formato compacto sin separador (IV de 12 bytes para GCM es lo común)
                val combinedBytes = android.util.Base64.decode(combinedEncrypted, android.util.Base64.DEFAULT)
                ivBytes = combinedBytes.copyOfRange(0, 12) // GCM suele usar 12 bytes para IV
                encryptedBytes = combinedBytes.copyOfRange(12, combinedBytes.size)
            }
            
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            // GCMParameterSpec(tagLenInBits, iv) - 128 es el valor estándar para el tag de autenticación
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
