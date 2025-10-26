package com.houneteam.airodump

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Глобальная "шина" данных между сервисом (ScanService) и UI (ViewModel/UI).
 *
 * ifaceList:    реальные интерфейсы (wlan0, wlan0mon и т.д.) из `iw dev`
 * apList:       точки доступа из airodump-ng CSV
 * status:       текущее состояние окружения
 */
object ScanDataBus {

    data class StatusState(
        val chrootFound: Boolean = false,
        val airodumpFound: Boolean = false,
        val monitorIface: String? = null
    )

    private val _ifaceList = MutableStateFlow<List<String>>(emptyList())
    val ifaceList: StateFlow<List<String>> = _ifaceList

    private val _apList = MutableStateFlow<List<AccessPoint>>(emptyList())
    val apList: StateFlow<List<AccessPoint>> = _apList

    private val _status = MutableStateFlow(StatusState())
    val status: StateFlow<StatusState> = _status

    fun updateInterfaces(newIfaces: List<String>) {
        _ifaceList.value = newIfaces.distinct()
    }

    fun updateAccessPoints(newAps: List<AccessPoint>) {
        _apList.value = newAps
    }

    fun updateStatus(newStatus: StatusState) {
        _status.value = newStatus
    }
}
