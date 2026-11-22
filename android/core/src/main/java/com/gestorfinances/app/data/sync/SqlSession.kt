package com.gestorfinances.app.data.sync

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * One connection to a database file, for work that must all happen on the same connection: other
 * databases attached to it, and a transaction around every statement. The app's shared driver
 * gives no such guarantee, so a merge opens its own.
 */
interface SqlSession : AutoCloseable {
    fun execute(sql: String, vararg args: String?)

    /** Every value as SQLite renders it in text, NULL as null. */
    fun query(sql: String, vararg args: String?): List<List<String?>>

    /** Commits when [block] returns, rolls back when it throws. */
    fun <T> transaction(block: () -> T): T
}

/** A [SqlSession] over JDBC, for the desktop app and for tests. */
class JdbcSqlSession(file: File) : SqlSession {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").also {
        it.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA busy_timeout = 5000")
        }
    }

    override fun execute(sql: String, vararg args: String?) {
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.execute()
        }
    }

    override fun query(sql: String, vararg args: String?): List<List<String?>> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { rows ->
                val columns = rows.metaData.columnCount
                buildList { while (rows.next()) add(List(columns) { rows.getString(it + 1) }) }
            }
        }

    override fun <T> transaction(block: () -> T): T {
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }

    override fun close() = connection.close()
}
