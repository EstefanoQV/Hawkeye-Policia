package com.example.policia

data class ClienteLocation(
    val clientId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val estado: String = "",
    val activo: Boolean = false,
    val placa: String = "",
    val nombreConductor: String = "",
    val origen: String = "",
    val destino: String = ""
)
