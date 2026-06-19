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
}
