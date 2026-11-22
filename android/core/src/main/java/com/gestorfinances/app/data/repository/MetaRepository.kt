package com.gestorfinances.app.data.repository

import com.gestorfinances.app.data.db.MetaQueries

data class DatabaseMeta(
    val schemaVersion: String,
    val snapshotVersion: String,
)

class MetaRepository(
    private val queries: MetaQueries,
) {
    fun load(): DatabaseMeta {
        return DatabaseMeta(
            schemaVersion = queries.valueForKey("schema_version").executeAsOne(),
            snapshotVersion = queries.valueForKey("snapshot_version").executeAsOne(),
        )
    }

    fun incrementSnapshotVersion(): Long =
        queries.transactionWithResult {
            val current = queries.valueForKey("snapshot_version").executeAsOne().toLong()
            val next = current + 1L
            queries.updateValueForKey(value = next.toString(), key = "snapshot_version")
            next
        }
}
