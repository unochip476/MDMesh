package com.mdmesh.core.telemetry

/** Observed active subscription, not a persistent SIM-to-slot binding. */
internal data class SimIdentity(
    val iccid: String?,
    val slotIndex: Int?,
    val subscriptionId: Int?,
    val embedded: Boolean?,
    val carrierName: String?,
)

/** Pure assembly logic: testable without Android framework or an attached SIM. */
internal object SimIdentityMapper {
    fun create(iccid: String?, slotIndex: Int?, subscriptionId: Int?,
               embedded: Boolean?, carrierName: String?): SimIdentity? {
        val value = IccidSanitizer.validOrNull(iccid)
        return SimIdentity(value, slotIndex?.takeIf { it >= 0 },
            subscriptionId?.takeIf { it >= 0 }, embedded,
            carrierName?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun unique(records: Iterable<SimIdentity>): List<SimIdentity> =
        records.distinctBy {
            when {
                it.subscriptionId != null -> "subscription:${it.subscriptionId}"
                it.iccid != null -> "iccid:${it.iccid}"
                else -> "slot:${it.slotIndex}:${it.embedded}:${it.carrierName}"
            }
        }
}
