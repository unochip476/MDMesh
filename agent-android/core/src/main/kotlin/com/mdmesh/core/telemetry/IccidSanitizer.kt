package com.mdmesh.core.telemetry

/** Validates ICCIDs obtained from Android without converting them to numeric types.
 * No Luhn enforcement: operators can issue identifiers for which a strict check
 * would incorrectly reject a real SIM. Never log input values.
 */
internal object IccidSanitizer {
    fun validOrNull(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (value.length !in 18..22 || !value.all { it in '0'..'9' }) return null
        return value
    }

    fun unique(raw: Iterable<String?>): List<String> =
        raw.mapNotNull(::validOrNull).distinct()
}
