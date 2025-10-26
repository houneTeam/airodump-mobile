package com.houneteam.airodump

import java.io.BufferedReader
import java.io.InputStreamReader

data class ShellResult(
    val output: String,
    val exitCode: Int
)

object RootShell {

    private val knownChrootPaths = listOf(
        "/data/local/nhsystem/kali-arm64",
        "/data/local/nhsystem/kali-armhf",
        "/data/local/nhsystem/chroot/kali-arm64",
        "/data/local/nhsystem/chroot/kali-armhf"
    )

    val KALI_CHROOT_PATH: String by lazy { detectChrootPath() }

    fun runAsRoot(cmd: String): ShellResult {
        val pb = ProcessBuilder("su", "-c", cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val r = BufferedReader(InputStreamReader(p.inputStream))
        val out = buildString {
            var line: String?
            while (r.readLine().also { line = it } != null) appendLine(line)
        }
        val code = p.waitFor()
        return ShellResult(out.trim(), code)
    }

    private fun prepareChroot() {
        val ch = KALI_CHROOT_PATH
        // bind /proc /sys /dev /dev/pts /system/bin -> chroot
        // и дать chroot свой mountpoint (airmon-ng его требует, иначе падает и жалуется что /sys не смонтирован). :contentReference[oaicite:4]{index=4}
        val prepCmd = """
            CH="$ch"
            mkdir -p $ch/proc
            mkdir -p $ch/sys
            mkdir -p $ch/dev/pts
            mkdir -p $ch/usr/bin
            mkdir -p $ch/system/bin

            mount --bind /proc $ch/proc 2>/dev/null || true
            mount --bind /sys $ch/sys 2>/dev/null || true
            mount --bind /dev $ch/dev 2>/dev/null || true
            mount --bind /dev/pts $ch/dev/pts 2>/dev/null || true
            mount --bind /system/bin $ch/system/bin 2>/dev/null || true

            if [ ! -x $ch/usr/bin/mountpoint ]; then
              if [ -x /system/bin/mountpoint ]; then
                ln -sf /system/bin/mountpoint $ch/usr/bin/mountpoint
              else
                echo '#!/bin/sh' > $ch/usr/bin/mountpoint
                echo '/system/bin/toybox mountpoint "$@"' >> $ch/usr/bin/mountpoint
                chmod 755 $ch/usr/bin/mountpoint
              fi
            fi
        """.trimIndent()
        runAsRoot(prepCmd)
    }

    fun runInKaliBash(inner: String): ShellResult {
        prepareChroot()
        val ch = KALI_CHROOT_PATH
        val wrapped = "chroot $ch /bin/bash -lc '$inner'"
        return runAsRoot(wrapped)
    }

    fun chrootExists(): Boolean {
        val ch = KALI_CHROOT_PATH
        val r = runAsRoot("[ -d $ch ] && echo OK || echo NO")
        return r.output.contains("OK")
    }

    // Проверяем бинарь надёжно (учитываем /usr/sbin). aircrack-ng кладёт airodump-ng и airmon-ng именно туда. :contentReference[oaicite:5]{index=5}
    fun binaryExistsInKali(bin: String): Boolean {
        if (!chrootExists()) return false
        val checkCmd = """
            if command -v $bin >/dev/null 2>&1; then echo FOUND;
            elif [ -x /usr/sbin/$bin ] || [ -x /usr/bin/$bin ] || [ -x /sbin/$bin ]; then echo FOUND;
            else echo NOTFOUND; fi
        """.trimIndent().replace("\n", " ")
        val res = runInKaliBash(checkCmd)
        return res.output.contains("FOUND")
    }

    private fun detectChrootPath(): String {
        for (c in knownChrootPaths) {
            val r = runAsRoot("[ -d $c ] && echo OK || echo NO")
            if (r.output.contains("OK")) return c
        }
        // дефолт NetHunter у большинства устройств. :contentReference[oaicite:6]{index=6}
        return "/data/local/nhsystem/kali-arm64"
    }
}
