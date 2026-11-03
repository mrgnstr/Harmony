package com.mrgnstr.harmony.core.device

/** How much visual work this device can afford. Every expensive effect reads this. */
enum class VisualTier {
    /** Live backdrop blur, animated backgrounds. */
    HIGH,
    /** Static translucent surfaces, cheap compositing-only motion. */
    MID,
    /** Translucent surfaces, gradients instead of images, short tweens instead of springs. */
    LOW;

    companion object {
        private const val GIB = 1024L * 1024L * 1024L

        /** A phone sold with N GB reports noticeably less (a "4 GB" one shows about 3.3-3.7 GiB), so thresholds allow for that. */
        private const val REPORTED_SHARE = 0.8

        private fun hasRam(totalMemBytes: Long, marketedGb: Int) = totalMemBytes >= (marketedGb * GIB * REPORTED_SHARE).toLong()

        /** Pure so it can be unit-tested; [DeviceCapabilities] feeds it real values. */
        fun resolve(sdk: Int, isLowRam: Boolean, totalMemBytes: Long, cores: Int): VisualTier = when {
            isLowRam || !hasRam(totalMemBytes, 2) || sdk < 26 -> LOW
            sdk >= 31 && hasRam(totalMemBytes, 4) && cores >= 8 -> HIGH
            else -> MID
        }
    }
}
