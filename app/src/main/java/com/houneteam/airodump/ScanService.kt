package com.houneteam.airodump

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.io.BufferedReader
import kotlin.concurrent.thread

class ScanService : Service() {

    companion object {
        const val ACTION_START = "com.houneteam.airodump.action.START_SCAN"
        const val ACTION_STOP  = "com.houneteam.airodump.action.STOP_SCAN"
        const val EXTRA_IFACE  = "iface"

        private const val TAG = "ScanService"
        private const val CHROOT_TMP_DIR = "/tmp/airodump_android"
        private const val PREFIX_IN_CHROOT = "$CHROOT_TMP_DIR/current"
    }

    @Volatile
    private var running = false
    private var workerThread: Thread? = null
    @Volatile
    private var activeMonIface: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val iface = intent.getStringExtra(EXTRA_IFACE) ?: "wlan0"
                startScan(iface)
            }
            ACTION_STOP -> {
                stopScan()
            }
        }
        return START_STICKY
    }

    private fun startScan(interfaceName: String) {
        if (running) return
        running = true

        workerThread = thread(name = "AirodumpThread") {
            Log.d(TAG, "Starting scan on $interfaceName")

            // подготовка окружения chroot (bind /proc,/sys,/dev + mountpoint внутри Kali)
            RootShell.runInKaliBash("true")

            // убедимся, что tmp-папка существует в Kali
            RootShell.runInKaliBash("mkdir -p $CHROOT_TMP_DIR")

            // включаем монитор-режим
            val startRes = RootShell.runInKaliBash("airmon-ng start $interfaceName")
            Log.d(TAG, "airmon-ng start:\n${startRes.output}\nexit=${startRes.exitCode}")

            // определяем, какой iface реально монитор
            val monIface = WirelessManager.detectMonitorIface(interfaceName)
            activeMonIface = monIface
            Log.d(TAG, "Monitor iface: $monIface")

            // проверяем окружение для UI
            val chrootOk = RootShell.chrootExists()
            val dumpOk   = RootShell.binaryExistsInKali("airodump-ng")

            ScanDataBus.updateStatus(
                ScanDataBus.StatusState(
                    chrootFound = chrootOk,
                    airodumpFound = dumpOk,
                    monitorIface = monIface
                )
            )

            // запускаем airodump-ng внутри Kali. он будет писать current-01.csv и т.д. в /tmp/airodump_android внутри Kali
            val dumpCmd = "airodump-ng --output-format csv --write $PREFIX_IN_CHROOT --write-interval 1 $monIface"
            Log.d(TAG, "airodump-ng cmd in chroot: $dumpCmd")

            // поднимаем отдельный процесс su -c "chroot ... bash -lc 'airodump-ng ...'"
            val fullCmd = "chroot ${RootShell.KALI_CHROOT_PATH} /bin/bash -lc '$dumpCmd'"
            val proc = ProcessBuilder("su", "-c", fullCmd)
                .redirectErrorStream(true)
                .start()

            // читаем начальный вывод airodump-ng (если он сразу умер с ошибкой)
            val reader = BufferedReader(proc.inputStream.reader())
            val firstLines = StringBuilder()
            if (reader.ready()) {
                repeat(10) {
                    if (reader.ready()) {
                        firstLines.appendLine(reader.readLine())
                    }
                }
            }
            if (firstLines.isNotEmpty()) {
                Log.d(TAG, "airodump-ng initial output:\n$firstLines")
            }

            // основной цикл
            while (running) {
                try {
                    Thread.sleep(2000)

                    // обновим список iface в UI
                    ScanDataBus.updateInterfaces(WirelessManager.listIfaceNames())

                    // статус монитор-интерфейса
                    val nowMon = WirelessManager.getCurrentMonitorIface()
                    ScanDataBus.updateStatus(
                        ScanDataBus.StatusState(
                            chrootFound = chrootOk,
                            airodumpFound = dumpOk,
                            monitorIface = nowMon
                        )
                    )

                    // читаем CSV изнутри Kali напрямую через runInKaliBash()
                    // cat вернёт пустую строку, если файла ещё нет -> ok
                    val csvRes = RootShell.runInKaliBash(
                        "cat ${PREFIX_IN_CHROOT}-01.csv 2>/dev/null || true"
                    )
                    val csvText = csvRes.output
                    if (csvText.isNotBlank()) {
                        val aps = AirodumpParser.parseAccessPoints(csvText)
                        ScanDataBus.updateAccessPoints(aps)
                        Log.d(TAG, "AP count=${aps.size}")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "loop err", t)
                }
            }

            // останавливаем процесс airodump-ng
            try {
                proc.destroy()
            } catch (_: Throwable) {}

            // выключаем монитор-режим
            activeMonIface?.let { monIf ->
                val stopRes = RootShell.runInKaliBash("airmon-ng stop $monIf")
                Log.d(TAG, "airmon-ng stop:\n${stopRes.output}")
            }

            val finalMon = WirelessManager.getCurrentMonitorIface()
            ScanDataBus.updateStatus(
                ScanDataBus.StatusState(
                    chrootFound = RootShell.chrootExists(),
                    airodumpFound = RootShell.binaryExistsInKali("airodump-ng"),
                    monitorIface = finalMon
                )
            )

            Log.d(TAG, "Scan stopped")
        }
    }

    private fun stopScan() {
        running = false
        workerThread?.interrupt()
        workerThread = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        workerThread?.interrupt()
        workerThread = null
    }
}
