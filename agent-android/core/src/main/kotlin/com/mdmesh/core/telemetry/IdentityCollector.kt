package com.mdmesh.core.telemetry

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.TelephonyManager
import com.mdmesh.proto.IdentityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads identity attributes. Requires READ_PHONE_STATE (+ READ_PHONE_NUMBERS for numbers),
 *  DO-auto-granted. Each read is guarded; a SecurityException/absence yields empty, never a crash. */
@Singleton
class IdentityCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val simIccidCollector: SimIccidCollector,
) {
    @SuppressLint("HardwareIds", "MissingPermission")
    fun collect(): IdentityInfo {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) (tm?.phoneCount ?: 1) else 1
        val imei = mutableListOf<String>()
        val phones = mutableListOf<String>()
        if (tm != null) {
            for (i in 0 until slots) {
                runCatching {
                    val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        tm.getImei(i)
                    } else {
                        @Suppress("DEPRECATION") tm.deviceId
                    }
                    if (!id.isNullOrBlank()) imei.add(id)
                }
            }
            runCatching { tm.line1Number }.getOrNull()?.takeIf { it.isNotBlank() }?.let { phones.add(it) }
        }
        val serial = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Build.getSerial() else @Suppress("DEPRECATION") Build.SERIAL
        }.getOrNull()?.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val sims = runCatching { simIccidCollector.collect() }.getOrDefault(emptyList())
        return IdentityInfo(
            serial = serial,
            imei = imei.distinct(),
            imsi = emptyList(), // IMSI/subscriberId restricted; left empty unless a privileged path is added
            iccid = sims.mapNotNull { it.iccid }.distinct(),
            simSubscriptions = sims.map { com.mdmesh.proto.SimSubscriptionInfo(
                iccid = it.iccid,
                slotIndex = it.slotIndex,
                subscriptionId = it.subscriptionId,
                embedded = it.embedded,
                carrierName = it.carrierName,
            ) },
            phoneNumber = phones.distinct(),
        )
    }
}
