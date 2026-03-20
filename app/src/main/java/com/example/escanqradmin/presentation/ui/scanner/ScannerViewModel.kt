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
            // El formato ESTRICTO debe ser separado por comas
            val parts = rawValue.split(",").map { it.trim() }
            
            if (parts.size == 5) {
                val androidId = parts[0]
                val userName = parts[1]
                val cedula = parts[2]
                val phone = parts[3]
                val plate = parts[4]

                // Validaciones de reglas de negocio
                val isValidCedula = cedula.startsWith("V-")
                val isValidPhone = phone.startsWith("+58")
                val isValidPlate = plate.isNotEmpty() && plate[0].isLetter() && plate[0].isUpperCase()

                if (isValidCedula && isValidPhone && isValidPlate) {
                    _scannedData.value = QrContent(
                        androidId = androidId,
                        userName = userName,
                        cedula = cedula,
                        phone = phone,
                        plate = plate
                    )
                }
                // Si no es válido, lo ignora y la cámara sigue escaneando buscando uno válido.
            }
        } catch (e: Exception) {
            // Ignora errores y sigue escaneando
        }
    }
    
    fun clearData() {
        _scannedData.value = null
    }
}
