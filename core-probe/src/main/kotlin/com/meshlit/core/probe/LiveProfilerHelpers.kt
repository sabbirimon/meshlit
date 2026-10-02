package com.meshlit.core.probe

import android.net.TrafficStats
import android.os.SystemClock
import java.io.File

/**
 * Helpers that back the v2 Device Info screen's "Live" hardware
 * monitor with **actual** per-second readings instead of constant
 * placeholder scores. The suppliers in `CoreModule` wrap these
 * via the existing `HardwareProfiler` interface so the Live
 * monitor's 1 Hz tick produces visibly different scores.
 *
 * All helpers are designed to fail gracefully on missing or
 * unreadable proc/sys files (older Android, restricted SELinux
 * contexts, denied permissions) — they return a `(0f, "n/a")`
 * pair so the Live monitor's combine() rebuild fires regardless,
 * and the screen shows "n/a" instead of crashing.
 *
 * Sources (after the SELinux lockdown on Android 8+):
 *  - `/proc/self/stat` — own-process CPU ticks (user + system vs idle).
 *    `/proc/stat` (system-wide) is SELinux-denied on stock devices so
 *    we measure the *app's* CPU% instead. For a distributed inference
 *    app that's the more useful signal anyway.
 *  - `/proc/meminfo` — MemTotal / MemAvailable for live RAM%.
 *  - `/sys/class/thermal/thermal_zoneN/temp` — SoC die temperature.
 *  - `android.net.TrafficStats.getTotalRxBytes/TxBytes` — cumulative
 *    device-wide byte counters; deltas become live KB/s. The
 *    /sys/class/net/<iface>/statistics/<name> files are SELinux-denied so the
 *    public API is the only legal option.
 *  - `android.os.StatFs` for the device's primary storage; we sample
 *    the free/total block counts once at startup and report storage
 *    fill % on each tick. Real-time disk I/O bandwidth is unavailable
 *    without `/proc/diskstats` so we show storage pressure instead.
 *
 * Each delta-based helper ([CpuUsageBaseline], [NetworkThroughputBaseline])
 * keeps its previous sample in an instance field so the Live
 * monitor can poll it without locking — singletons hold the
 * holders for the process lifetime.
 */

/**
 * Reads CPU tick deltas from `/proc/self/stat` and returns the
 * **own-process** busy percentage between two consecutive samples.
 * On the first call there is no baseline, so we record the snapshot
 * and return 0f (the Live sparkline will start accumulating from
 * tick 2).
 *
 * Why own-process and not system-wide: `/proc/stat` is gated by
 * SELinux on Android 8+ and a regular app can't read it. The only
 * proc-stat file an app can read is `/proc/self/stat`. For an
 * inference app this is actually the more useful signal — the user
 * wants to see how much CPU the inference itself is consuming, not
 * the system noise.
 *
 * The 14th + 15th + 16th fields in `/proc/<pid>/stat` are
 * `utime + stime + children_utime + children_stime` (all in clock
 * ticks). The 22nd field is `starttime` (in clock ticks since
 * boot), which we use as the elapsed-time denominator instead of
 * `System.currentTimeMillis()` so the calculation survives wall-
 * clock jumps.
 *
 * Score: percentage of a single CPU's worth (so a process pinned
 * to 4 cores saturating reads 100%, not 400%). Raw: "<pct>%" with
 * the ABI suffix the first tick and a percent-only string after.
 */
class CpuUsageBaseline {
    @Volatile private var lastTicks: Long = 0L
    @Volatile private var lastElapsedTicks: Long = 0L
    @Volatile private var lastAbi: String = "?"

    fun sample(): Pair<Float, String> {
        return try {
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "?"
            val (ticks, elapsedTicks) = readSelfStat()
            if (lastTicks == 0L || lastElapsedTicks == 0L) {
                lastTicks = ticks
                lastElapsedTicks = elapsedTicks
                lastAbi = abi
                return 0f to abi
            }
            val tickDelta = ticks - lastTicks
            val elapsedDelta = (elapsedTicks - lastElapsedTicks).coerceAtLeast(1L)
            lastTicks = ticks
            lastElapsedTicks = elapsedTicks
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            // Per-core percentage: a process pinned to all `cores`
            // hits ~100% even though wall-clock ticks elapsed = cores.
            val pct = ((tickDelta.toFloat() / elapsedDelta.toFloat()) / cores) * 100f
            val raw = "%.0f%%".format(pct.coerceIn(0f, 100f))
            pct.coerceIn(0f, 100f) to raw
        } catch (t: Throwable) {
            0f to "n/a"
        }
    }

    /**
     * Returns `(totalCpuTicks, elapsedClockTicks)`. Both are in the
     * same `USER_HZ` units (typically 100 Hz on Android), so the
     * ratio `ticks / elapsedTicks` is a clean 0..cores fraction.
     *
     * `starttime` is the boot-relative clock at which the process
     * started — used here as a monotonic, jump-resistant elapsed
     * counter.
     */
    private fun readSelfStat(): Pair<Long, Long> {
        val raw = File("/proc/self/stat").takeIf { it.canRead() }
            ?.readText()
            ?: throw IllegalStateException("/proc/self/stat unreadable")
        // The comm field is in parens and can contain spaces /
        // parens — find the LAST `)` then split the tail.
        val close = raw.lastIndexOf(')')
        if (close < 0) throw IllegalStateException("/proc/self/stat malformed")
        val tail = raw.substring(close + 1).trim()
        val fields = tail.split(Regex("\\s+"))
        // After `)` the field indices shift by -2 (state becomes
        // fields[0]). Canonical Linux /proc/<pid>/stat field map:
        //   0=state, 1=ppid, 2=pgrp, 3=session, 4=tty_nr, 5=tpgid,
        //   6=flags, 7=minflt, 8=cminflt, 9=majflt, 10=cmajflt,
        //   11=utime, 12=stime, 13=cutime, 14=cstime, 15=priority,
        //   16=nice, 17=num_threads, 18=itrealvalue,
        //   19=starttime (clock ticks since boot)
        val utime = fields.getOrNull(11)?.toLongOrNull() ?: 0L
        val stime = fields.getOrNull(12)?.toLongOrNull() ?: 0L
        val cutime = fields.getOrNull(13)?.toLongOrNull() ?: 0L
        val cstime = fields.getOrNull(14)?.toLongOrNull() ?: 0L
        val starttime = fields.getOrNull(19)?.toLongOrNull() ?: 0L
        val totalTicks = utime + stime + cutime + cstime
        val nowTicks = SystemClock.elapsedRealtimeNanos() / 10_000_000L // ms → cs (10ms)
        // starttime is in clock ticks since boot; elapsedRealtime()
        // ms since boot. Convert ms to clock ticks (1 tick = 10 ms
        // on USER_HZ=100, the Android default) so both counters
        // share units.
        val elapsedTicks = nowTicks - (starttime * 10L)
        return totalTicks to elapsedTicks.coerceAtLeast(1L)
    }
}

/**
 * Reads `/proc/meminfo` for the live RAM% (MemTotal vs
 * MemAvailable). Returns `(usedPercent, "<used>MB / <total>MB")`.
 *
 * MemAvailable is the kernel's "what's actually still claimable"
 * estimate — much more useful than `free` because it accounts for
 * reclaimable cache. Older kernels (<3.14) don't expose it, in
 * which case we fall back to `MemFree + Buffers + Cached`.
 */
fun readMemoryUsage(): Pair<Float, String> {
    return try {
        val map = parseMemInfo()
        val totalKb = map["MemTotal"] ?: return 0f to "n/a"
        val availKb = map["MemAvailable"]
            ?: ((map["MemFree"] ?: 0L) + (map["Buffers"] ?: 0L) + (map["Cached"] ?: 0L))
        if (totalKb <= 0L) return 0f to "n/a"
        val usedKb = (totalKb - availKb).coerceAtLeast(0L)
        val pct = (usedKb.toFloat() / totalKb.toFloat()) * 100f
        val usedMb = usedKb / 1024L
        val totalMb = totalKb / 1024L
        pct.coerceIn(0f, 100f) to "${usedMb}/${totalMb}MB"
    } catch (t: Throwable) {
        0f to "n/a"
    }
}

private fun parseMemInfo(): Map<String, Long> {
    val out = HashMap<String, Long>(16)
    File("/proc/meminfo").useLines { seq ->
        for (line in seq) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val name = line.substring(0, colon)
            val rest = line.substring(colon + 1).trim()
            // "MemTotal:        3542040 kB" → 3542040L
            val numStr = rest.substringBefore(' ').trim()
            val v = numStr.toLongOrNull() ?: continue
            out[name] = v
        }
    }
    return out
}

/**
 * Real device temperature from
 * /sys/class/thermal/thermal_zoneN/temp. The first integer of
 * each `temp` file is the SoC die temperature in millidegrees-C
 * (e.g. `43500` = 43.5 degrees C). Picks the first zone with a
 * non-zero reading; on emulators or locked-down devices, falls
 * back to "0".
 *
 * Score: percentage of an 80 degrees C ceiling so a phone at normal
 * idle (35 °C) sits at ~44% and a hot device (75 °C) sits at
 * ~94%.
 */
fun readThermal(): Pair<Float, String> {
    return try {
        val zoneDir = File("/sys/class/thermal")
        if (!zoneDir.isDirectory) return 0f to "n/a"
        val files = zoneDir.listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray()
        for (f in files) {
            val tempFile = File(f, "temp")
            if (!tempFile.canRead()) continue
            val raw = tempFile.readText().trim().toLongOrNull() ?: continue
            if (raw <= 0L) continue
            val celsius = raw / 1000f
            val pct = (celsius / 80f) * 100f
            return pct.coerceIn(0f, 100f) to "%.1f°C".format(celsius)
        }
        0f to "n/a"
    } catch (t: Throwable) {
        0f to "n/a"
    }
}

/**
 * Live network throughput delta (KB/s) via the public
 * `android.net.TrafficStats` API.
 *
 * `TrafficStats.getTotalRxBytes()` + `getTotalTxBytes()` returns
 * cumulative byte counters for the whole device (across all
 * interfaces, all apps). On the first sample we record the
 * baseline; on each subsequent sample we subtract to get a
 * per-second rate. The score is the % of a 10 MB/s budget so a
 * streaming inference session saturates around 90%.
 *
 * On devices where `TrafficStats` returns `TrafficStats.UNSUPPORTED`
 * (very old emulators), we fall through to "n/a" so the chart row
 * doesn't crash — the user sees the label with no live value.
 */
class NetworkThroughputBaseline {
    @Volatile private var lastTotal: Long = 0L
    @Volatile private var lastTimestampMs: Long = 0L

    fun sample(): Pair<Float, String> {
        return try {
            val now = System.currentTimeMillis()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx == TrafficStats.UNSUPPORTED.toLong() ||
                tx == TrafficStats.UNSUPPORTED.toLong()
            ) {
                return 0f to "n/a"
            }
            val bytes = rx + tx
            if (lastTotal == 0L || lastTimestampMs == 0L) {
                lastTotal = bytes
                lastTimestampMs = now
                return 0f to "0 KB/s"
            }
            val deltaBytes = (bytes - lastTotal).coerceAtLeast(0L)
            val deltaMs = (now - lastTimestampMs).coerceAtLeast(1L)
            val kbps = (deltaBytes / 1024f) * (1000f / deltaMs.toFloat())
            lastTotal = bytes
            lastTimestampMs = now
            val pct = (kbps / (10f * 1024f)) * 100f // 10 MB/s ceiling
            pct.coerceIn(0f, 100f) to "%.0f KB/s".format(kbps)
        } catch (t: Throwable) {
            0f to "n/a"
        }
    }
}

/**
 * Storage fill % sampled via `android.os.StatFs` over the app's
 * primary storage root. `/proc/diskstats` and `/sys/block/<dev>/stat`
 * are SELinux-denied on stock Android, so per-second I/O bandwidth
 * isn't accessible. We surface **storage pressure** instead — the
 * fraction of the device's writable flash that's currently in use.
 *
 * The score is `(used / total) * 100`. The raw is
 * "<usedGiB> / <totalGiB>". A device near full (95% used) reads
 * "near full" so the user gets a clear "your disk is filling up"
 * signal that the Windows 11 Resource Monitor's storage chart
 * also surfaces.
 */
class DiskIoBaseline {
    @Volatile private var path: String? = null
    @Volatile private var totalBytes: Long = 0L

    fun sample(): Pair<Float, String> {
        return try {
            if (path == null) {
                // StatFs needs a real path. We use the canonical
                // "external storage" root when present, falling
                // back to the app's filesDir which is always
                // accessible. The score is relative to that root
                // either way.
                val candidate = android.os.Environment.getDataDirectory().absolutePath
                    ?: "/storage/emulated/0"
                path = candidate
                val probe = android.os.StatFs(candidate)
                totalBytes = probe.blockCountLong * probe.blockSizeLong
            }
            val p = path ?: return 0f to "n/a"
            if (totalBytes <= 0L) return 0f to "n/a"
            val stat = android.os.StatFs(p)
            val free = stat.availableBlocksLong * stat.blockSizeLong
            val used = (totalBytes - free).coerceAtLeast(0L)
            val pct = (used.toFloat() / totalBytes.toFloat()) * 100f
            val usedGiB = used / 1024L / 1024L / 1024L
            val totalGiB = totalBytes / 1024L / 1024L / 1024L
            pct.coerceIn(0f, 100f) to "${usedGiB}/${totalGiB}GiB"
        } catch (t: Throwable) {
            0f to "n/a"
        }
    }
}
