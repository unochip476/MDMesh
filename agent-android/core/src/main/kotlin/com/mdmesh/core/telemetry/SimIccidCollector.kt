package com.mdmesh.core.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Best-effort snapshot of ACTIVE subscriptions only, with reported slot metadata.
 * Android's slotIndex is not an IMEI index and subscriptionId is not persistent.
 * This cannot enumerate inactive eSIM profiles.
 */
@Singleton
class SimIccidCollector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    internal fun collect(): List<SimIdentity> {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE)
            as? TelephonyManager ?: return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return listOfNotNull(SimIdentityMapper.create(
                runCatching { tm.simSerialNumber }.getOrNull(), null, null, null, null))
        }
        val subscriptions = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
            as? SubscriptionManager ?: return emptyList()
        val active = runCatching { subscriptions.activeSubscriptionInfoList }.getOrNull()
            ?: return emptyList()
        val records = active.mapNotNull { sub ->
            val fromTm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    tm.createForSubscriptionId(sub.subscriptionId).simSerialNumber
                }.getOrNull()
            } else null
            val fromSub = runCatching { sub.iccId }.getOrNull()
            SimIdentityMapper.create(
                IccidSanitizer.validOrNull(fromTm) ?: fromSub,
                runCatching { sub.simSlotIndex }.getOrNull(),
                runCatching { sub.subscriptionId }.getOrNull(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    runCatching { sub.isEmbedded }.getOrNull() else null,
                runCatching { sub.carrierName?.toString() }.getOrNull(),
            )
        }
        return SimIdentityMapper.unique(records)
    }
}
