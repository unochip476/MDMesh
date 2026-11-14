package com.mdmesh.proto

import org.junit.Assert.*
import org.junit.Test

class SimTelemetryTest {

    private val iccid = "0012345678901234567"

    @Test
    fun `serializes ICCID and SIM metadata`() {
        val identity = IdentityInfo(
            iccid = listOf(iccid),
            simSubscriptions = listOf(
                SimSubscriptionInfo(
                    iccid = iccid,
                    slotIndex = 0,
                    subscriptionId = 12,
                    embedded = false,
                    carrierName = "Example Carrier"
                )
            )
        )

        val json = ProtocolJson.json.encodeToString(
            IdentityInfo.serializer(),
            identity
        )

        val decoded = ProtocolJson.json.decodeFromString(
            IdentityInfo.serializer(),
            json
        )

        assertEquals(identity, decoded)
        assertTrue(json.contains("simSubscriptions"))
        assertTrue(json.contains(iccid))
    }

    @Test
    fun `legacy telemetry without subscriptions still decodes`() {
        val json = """{"iccid":["$iccid"]}"""

        val result = ProtocolJson.json.decodeFromString(
            IdentityInfo.serializer(),
            json
        )

        assertEquals(listOf(iccid), result.iccid)
        assertTrue(result.simSubscriptions.isEmpty())
    }

    @Test
    fun `missing ICCID uses empty defaults`() {
        val result = ProtocolJson.json.decodeFromString(
            IdentityInfo.serializer(),
            "{}"
        )

        assertTrue(result.iccid.isEmpty())
        assertTrue(result.simSubscriptions.isEmpty())
    }

    @Test
    fun `SIM metadata can exist without ICCID`() {
        val identity = IdentityInfo(
            simSubscriptions = listOf(
                SimSubscriptionInfo(
                    slotIndex = 1,
                    carrierName = "Unknown SIM"
                )
            )
        )

        val json = ProtocolJson.json.encodeToString(
            IdentityInfo.serializer(),
            identity
        )

        val decoded = ProtocolJson.json.decodeFromString(
            IdentityInfo.serializer(),
            json
        )

        assertEquals(identity, decoded)
        assertNull(decoded.simSubscriptions.single().iccid)
    }

    @Test
    fun `unknown future fields are ignored`() {
        val json = """{
          "iccid":["$iccid"],
          "futureField":"value"
        }"""

        val result = ProtocolJson.json.decodeFromString(
            IdentityInfo.serializer(),
            json
        )

        assertEquals(listOf(iccid), result.iccid)
    }
}
