package com.gestorfinances.app.ui.dashboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalanElisionTest {
    @Test
    fun deContractsBeforeVowelsAndSilentH() {
        listOf("Imagin", "estalvis", "Òmnia", "Hipoteca").forEach { assertTrue(it, elidesCatalanDe(it)) }
        listOf("Revolut", "BBVA", "Hbank", "", " ").forEach { assertFalse(it, elidesCatalanDe(it)) }
    }
}
