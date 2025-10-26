package com.houneteam.airodump

import java.io.File

data class AccessPoint(
    val bssid: String,
    val channel: String,
    val privacy: String,
    val power: String,
    val essid: String,
    val clients: Int // пока не считаем клиентов, будет 0
)

object AirodumpParser {

    /**
     * Прочитать CSV, который генерит airodump-ng (--output-format csv ...),
     * вытащить список точек доступа (верхняя таблица).
     */
    fun parseAccessPointsFromCsvFile(csvFile: File): List<AccessPoint> {
        if (!csvFile.exists()) return emptyList()
        val text = csvFile.readText()
        return parseAccessPoints(text)
    }

    fun parseAccessPoints(csvContent: String): List<AccessPoint> {
        val lines = csvContent.lines()

        val apLines = mutableListOf<String>()
        var headerFound = false

        for (line in lines) {
            if (line.startsWith("Station MAC")) {
                // вторая секция началась — стоп
                break
            }

            if (!headerFound) {
                if (line.startsWith("BSSID")) {
                    headerFound = true
                }
                continue
            }

            if (line.isBlank()) continue
            apLines.add(line)
        }

        return apLines.mapNotNull { parseApLine(it) }
    }

    private fun parseApLine(line: String): AccessPoint? {
        // Столбцы по образцу:
        // BSSID, First time seen, Last time seen, channel, Speed, Privacy,
        // Cipher, Authentication, Power, # beacons, # IV, LAN IP, ID-length, ESSID, Key
        val parts = line.split(",").map { it.trim() }
        if (parts.size < 14) return null

        val bssid = parts[0]
        val channel = parts[3]
        val privacy = buildString {
            append(parts[5])
            if (parts[6].isNotBlank()) append("/${parts[6]}")
            if (parts[7].isNotBlank()) append("/${parts[7]}")
        }
        val power = parts[8]
        val essid = parts[13]

        return AccessPoint(
            bssid = bssid,
            channel = channel,
            privacy = privacy,
            power = power,
            essid = essid,
            clients = 0
        )
    }
}
