package com.houneteam.airodump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanViewModel : ViewModel() {

    val apList: StateFlow<List<AccessPoint>> = ScanDataBus.apList
    val ifaceList: StateFlow<List<String>>   = ScanDataBus.ifaceList
    val status: StateFlow<ScanDataBus.StatusState> = ScanDataBus.status

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val chrootOk = RootShell.chrootExists()
            val dumpOk   = RootShell.binaryExistsInKali("airodump-ng")
            val monIface = WirelessManager.getCurrentMonitorIface()
            val ifaces   = WirelessManager.listIfaceNames()

            ScanDataBus.updateInterfaces(ifaces)
            ScanDataBus.updateStatus(
                ScanDataBus.StatusState(
                    chrootFound = chrootOk,
                    airodumpFound = dumpOk,
                    monitorIface = monIface
                )
            )
        }
    }

    fun setMonitorEnabled(enable: Boolean, baseIface: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (enable) {
                if (!baseIface.isNullOrBlank()) {
                    RootShell.runInKaliBash("airmon-ng start $baseIface")
                }
            } else {
                val monIface = WirelessManager.getCurrentMonitorIface()
                if (!monIface.isNullOrBlank()) {
                    RootShell.runInKaliBash("airmon-ng stop $monIface")
                }
            }
            refreshAll()
        }
    }
}
