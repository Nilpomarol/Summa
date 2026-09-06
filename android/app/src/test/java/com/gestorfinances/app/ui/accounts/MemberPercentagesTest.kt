package com.gestorfinances.app.ui.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The percentage arithmetic behind the shared-account member editor's live totals. */
class MemberPercentagesTest {

    private fun member(name: String, enabled: Boolean = true, ownership: String = "0,00") =
        AccountMemberFormState(
            personId = name.takeIf { it != "owner" },
            name = name,
            enabled = enabled,
            ownershipPercent = ownership,
            defaultExpensePercent = ownership,
        )

    @Test
    fun `total counts only enabled members`() {
        val members = listOf(
            member("owner", ownership = "60,00"),
            member("anna", ownership = "40,00"),
            member("pere", enabled = false, ownership = "99,00"),
        )
        assertEquals(10_000L, members.basisPointTotal { it.ownershipPercent })
    }

    @Test
    fun `total is null when a member's input cannot be read`() {
        val members = listOf(member("owner", ownership = "60,00"), member("anna", ownership = "40,001"))
        assertNull(members.basisPointTotal { it.ownershipPercent })
    }

    @Test
    fun `even split over three members totals exactly 100 percent`() {
        val split = listOf(member("owner"), member("anna"), member("pere")).splitEqually()
        assertEquals(listOf("33,34", "33,33", "33,33"), split.map { it.ownershipPercent })
        assertEquals(10_000L, split.basisPointTotal { it.ownershipPercent })
        assertEquals(10_000L, split.basisPointTotal { it.defaultExpensePercent })
    }

    @Test
    fun `even split leaves disabled members alone`() {
        val split = listOf(
            member("owner"),
            member("anna"),
            member("pere", enabled = false, ownership = "0,00"),
        ).splitEqually()
        assertEquals(listOf("50,00", "50,00", "0,00"), split.map { it.ownershipPercent })
        assertEquals(10_000L, split.basisPointTotal { it.ownershipPercent })
    }
}
