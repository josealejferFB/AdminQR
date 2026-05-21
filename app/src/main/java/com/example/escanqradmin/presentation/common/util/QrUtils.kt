package com.example.escanqradmin.presentation.common.util

import com.example.escanqradmin.data.network.ApiConstants
import com.example.escanqradmin.domain.model.SecurityConstants

fun buildProvisioningJson(): String =
    """{"endpoint":"${ApiConstants.BASE_URL}","token":"${SecurityConstants.PROVISIONING_TOKEN}"}"""
