package com.houneteam.airodump

object WirelessManager {

    data class WirelessIface(
        val name: String,
        val type: String // managed / monitor / unknown
    )

    // Читаем iw dev, чтобы понять какой iface реально в monitor mode.
    private fun parseIw(): List<WirelessIface> {
        val res = RootShell.runInKaliBash("iw dev")
        val lines = res.output.lines()

        val out = mutableListOf<WirelessIface>()
        var curName: String? = null
        var curType: String? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("Interface ")) {
                if (curName != null && curType != null) {
                    out.add(WirelessIface(curName!!, curType!!))
                }
                curName = line.removePrefix("Interface ").trim()
                curType = null
            } else if (line.startsWith("type ") && curName != null) {
                curType = line.removePrefix("type ").trim()
            }
        }
        if (curName != null && curType != null) {
            out.add(WirelessIface(curName!!, curType!!))
        }

        return out
    }

    // Парсим ifconfig -a и оставляем только wlan*/wlan*mon.
    // Мы режем rmnet0/tun0/lo и прочий шум.
    fun listIfaceNames(): List<String> {
        val res = RootShell.runInKaliBash("ifconfig -a || ip -o link show")
        val names = mutableListOf<String>()

        for (ln in res.output.lines()) {
            val t = ln.trim()
            if (t.isEmpty()) continue
            if (ln.startsWith(" ") || ln.startsWith("\t")) continue

            val first = t.split(Regex("\\s+"))[0]
            val iface = first.substringBefore(":")
            if (iface.isNotBlank()) {
                val wifi = iface.startsWith("wl") || iface.endsWith("mon")
                if (wifi && !names.contains(iface)) {
                    names.add(iface)
                }
            }
        }
        return names
    }

    fun getCurrentMonitorIface(): String? {
        val iwIfaces = parseIw()
        return iwIfaces.firstOrNull { it.type == "monitor" }?.name
    }

    // После airmon-ng start wlan0 карта может стать wlan0mon,
    // а может остаться wlan0 но уже тип monitor. Это норм поведение драйверов. :contentReference[oaicite:7]{index=7}
    fun detectMonitorIface(baseIface: String): String {
        val iwIfaces = parseIw()
        iwIfaces.firstOrNull {
            it.type == "monitor" &&
                    (it.name == baseIface || it.name.startsWith(baseIface))
        }?.let { return it.name }

        iwIfaces.firstOrNull { it.type == "monitor" }?.let { return it.name }

        return baseIface
    }
}
