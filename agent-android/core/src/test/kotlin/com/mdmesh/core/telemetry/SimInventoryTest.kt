package com.mdmesh.core.telemetry

import org.junit.Assert.*
import org.junit.Test

class SimInventoryTest {

    private val iccidA = "0012345678901234567"
    private val iccidB = "8912345678901234567"

    @Test
    fun `valid ICCID preserves leading zeros`() {
        assertEquals(iccidA, IccidSanitizer.validOrNull(iccidA))
        assertEquals(iccidA, IccidSanitizer.validOrNull(" $iccidA "))
    }

    @Test
    fun `accepts 18 through 22 digits`() {
        for (length in 18..22) {
            val value = "0".repeat(length)
            assertEquals(value, IccidSanitizer.validOrNull(value))
        }
    }

    @Test
    fun `rejects invalid ICCID values`() {
        val invalid = listOf(
            null,
            "",
            " ",
            "1".repeat(17),
            "1".repeat(23),
            "12345678901234567A",
            "1234-56789012345678",
            "123456789012345678\n9"
        )

        invalid.forEach {
            assertNull(IccidSanitizer.validOrNull(it))
        }
    }

    @Test
    fun `deduplicates valid ICCIDs`() {
        val result = IccidSanitizer.unique(
            listOf(iccidA, iccidA, null, "invalid", iccidB)
        )
        assertEquals(listOf(iccidA, iccidB), result)
    }

    @Test
    fun `preserves physical SIM metadata`() {
        val sim = SimIdentityMapper.create(
            iccidA, 0, 12, false, "Example Carrier"
        )!!

        assertEquals(iccidA, sim.iccid)
        assertEquals(0, sim.slotIndex)
        assertEquals(12, sim.subscriptionId)
        assertEquals(false, sim.embedded)
        assertEquals("Example Carrier", sim.carrierName)
    }

    @Test
    fun `preserves eSIM metadata`() {
        val sim = SimIdentityMapper.create(
            iccidB, 1, 23, true, "Example eSIM"
        )!!

        assertEquals(1, sim.slotIndex)
        assertEquals(23, sim.subscriptionId)
        assertEquals(true, sim.embedded)
    }

    @Test
    fun `keeps subscription metadata when ICCID is unavailable`() {
        val sim = SimIdentityMapper.create(
            null, 0, 12, false, "Example Carrier"
        )!!

        assertNull(sim.iccid)
        assertEquals(0, sim.slotIndex)
        assertEquals(12, sim.subscriptionId)
    }

    @Test
    fun `normalizes invalid metadata`() {
        val sim = SimIdentityMapper.create(
            "invalid", -1, -1, null, "  "
        )!!

        assertNull(sim.iccid)
        assertNull(sim.slotIndex)
        assertNull(sim.subscriptionId)
        assertNull(sim.embedded)
        assertNull(sim.carrierName)
    }

    @Test
    fun `keeps distinct subscriptions`() {
        val first = SimIdentityMapper.create(
            iccidA, 0, 10, false, "Carrier A"
        )!!
        val second = SimIdentityMapper.create(
            iccidB, 1, 11, true, "Carrier B"
        )!!

        assertEquals(
            listOf(first, second),
            SimIdentityMapper.unique(listOf(first, second))
        )
    }

    @Test
    fun `deduplicates repeated subscription IDs`() {
        val first = SimIdentityMapper.create(
            iccidA, 0, 10, false, "Carrier A"
        )!!
        val duplicate = first.copy(carrierName = "Other")

        assertEquals(
            listOf(first),
            SimIdentityMapper.unique(listOf(first, duplicate))
        )
    }

    @Test
    fun `deduplicates ICCID when subscription ID missing`() {
        val first = SimIdentityMapper.create(
            iccidA, 0, null, false, null
        )!!
        val duplicate = first.copy(slotIndex = 1)

        assertEquals(
            listOf(first),
            SimIdentityMapper.unique(listOf(first, duplicate))
        )
    }
}
