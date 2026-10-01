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

    @Test
    fun `between two members the other takes the rest, in the column typed`() {
        val two = listOf(member("owner"), member("pere", enabled = false), member("anna")).splitEqually()

        val both = two.withPercent(0, "70", ownership = true, expenses = true)
        assertEquals(listOf("70", "0,00", "30,00"), both.map { it.ownershipPercent })
        assertEquals(listOf("70", "0,00", "30,00"), both.map { it.defaultExpensePercent })

        val expensesOnly = two.withPercent(2, "25", ownership = false, expenses = true)
        assertEquals(listOf("50,00", "0,00", "50,00"), expensesOnly.map { it.ownershipPercent })
        assertEquals(listOf("75,00", "0,00", "25"), expensesOnly.map { it.defaultExpensePercent })

        // With three, nobody is "the other": what is typed stays as typed.
        val three = two.withMember(1, enabled = true, equal = true).withPercent(0, "70", ownership = true, expenses = true)
        assertEquals(listOf("70", "33,33", "33,33"), three.map { it.ownershipPercent })
    }

    @Test
    fun `adding or removing a member shares the hundred out again when parts are equal`() {
        val members = listOf(member("owner", ownership = "100,00"), member("anna", enabled = false))
        val added = members.withMember(1, enabled = true, equal = true)
        assertEquals(listOf("50,00", "50,00"), added.map { it.ownershipPercent })
        val removed = added.withMember(1, enabled = false, equal = true)
        assertEquals(listOf("100,00", "0,00"), removed.map { it.defaultExpensePercent })
        assertEquals(false, removed[1].enabled)
    }

    @Test
    fun `typed percentages survive a member joining or leaving`() {
        val members = listOf(member("owner", ownership = "70,00"), member("anna", ownership = "30,00"), member("pere", enabled = false))
        val added = members.withMember(2, enabled = true, equal = false)
        assertEquals(listOf("70,00", "30,00", "0,00"), added.map { it.ownershipPercent })
        assertEquals(listOf("70,00", "0,00", "0,00"), added.withMember(1, enabled = false, equal = false).map { it.ownershipPercent })
    }
}
