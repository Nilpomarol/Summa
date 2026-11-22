package com.gestorfinances.app.data.db

/**
 * The shared-account integrity triggers a fresh install runs after [GestorDatabase.Schema] is
 * created, read from the resource the build generates from migration 016, whichever line endings
 * the file was checked out with.
 */
fun sharedAccountIntegrityStatements(): List<String> =
    requireNotNull(GestorDatabase::class.java.getResource("/shared_account_integrity.sql")) {
        "Missing generated resource shared_account_integrity.sql"
    }
        .readText()
        .replace("\r\n", "\n")
        .splitToSequence("\nEND;")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { "$it\nEND;" }
        .toList()
