package com.gestorfinances.app.ui

import com.gestorfinances.ui.resources.Res
import com.gestorfinances.ui.resources.budget_saving_rate
import com.gestorfinances.ui.resources.category_archive_dependencies_warning
import com.gestorfinances.ui.resources.home_database_failed
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Assert.assertEquals
import org.junit.Test

/** The strings file is written for Android's parser; shared code must read it the same way. */
class SharedStringsTest {
    @Test
    fun androidEscapesAndPositionalArgumentsReadTheSame() = runBlocking {
        assertEquals("No s'ha pogut obrir la base de dades. Torna-ho a provar.", getString(Res.string.home_database_failed))
        assertEquals(
            "Es pausaran 2 recurrents i s'eliminaran 3 pressupostos. Les 4 subcategories passaran al nivell principal.",
            getString(Res.string.category_archive_dependencies_warning, 2, 3, 4),
        )
        assertEquals("12%", getString(Res.string.budget_saving_rate, 12))
    }
}
