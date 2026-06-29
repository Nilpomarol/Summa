package com.gestorfinances.app.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.Test

class DataSeederTest {

    @Test
    fun testDataSeederSucceedsWithoutConstraintViolations() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GestorDatabase.Schema.create(driver)
        val seeder = DataSeeder(driver)
        
        // This will throw SQLiteException if there are syntax errors or constraint violations (e.g. check constraints, foreign keys).
        seeder.seed()
    }
}
