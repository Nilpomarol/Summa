plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// Shared SQL under ../shared is the single source of truth; syncSharedSqlForSqlDelight copies it
// into the (gitignored) SQLDelight/asset inputs below before any SQLDelight or asset task runs.
val sharedRoot = rootProject.layout.projectDirectory.dir("../shared")
val sqlDelightDir = layout.projectDirectory.dir("src/main/sqldelight/com/gestorfinances/app/data/db")
val generatedSharedSql = sqlDelightDir.file("SharedSchema.sq")
val generatedAnalysisSql = sqlDelightDir.file("Analysis.sq")
val generatedSharedAccountIntegrityAsset = layout.projectDirectory.file(
    "src/main/assets/shared_account_integrity.sql",
)

fun sharedSqlFiles(dir: String): List<File> =
    sharedRoot.dir(dir).asFile.listFiles { file -> file.extension == "sql" }!!.sortedBy { it.name }

fun migrationNumber(file: File): Int = file.name.substringBefore('_').toInt()

// Shared migration NNN_*.sql upgrades the database to version NNN. SQLDelight names a migration
// after the version it upgrades from, so it becomes (NNN - 1).sqm. The 001 baseline is not an
// upgrade: fresh installs are created from schema.sql instead.
val sharedMigrations = sharedSqlFiles("migrations")
val latestSchemaVersion = migrationNumber(sharedMigrations.last())
val sqlDelightMigrations = sharedMigrations
    .filter { migrationNumber(it) > 1 }
    .map { it to sqlDelightDir.file("${migrationNumber(it) - 1}.sqm") }

// Fresh-install views in dependency order: each view is created after the views it reads, so this
// cannot be the sorted directory listing.
val sharedViewFiles = listOf(
    "v_movement_shared.sql",
    "v_movement_summary.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_account_value.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
    "v_trip_actual_total.sql",
    "v_goal_allocation.sql",
    "v_goal_progress.sql",
    "v_account_allocation.sql",
).map { sharedRoot.file("queries/$it").asFile }

// Every other shared query is a parameterized query, exposed as its camelCased file name.
val sharedAnalysisQueries = sharedSqlFiles("queries").filterNot { it.name.startsWith("v_") }

fun queryName(file: File): String = file.nameWithoutExtension
    .split('_')
    .mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar(Char::uppercase) }
    .joinToString("")

val syncSharedSqlForSqlDelight by tasks.registering {
    val sharedSchema = sharedRoot.file("schema/schema.sql").asFile
    val sharedAccountIntegrityMigration = sharedRoot.file(
        "migrations/016_enforce_shared_account_integrity.sql",
    ).asFile

    inputs.file(sharedSchema)
    inputs.files(sharedMigrations, sharedViewFiles, sharedAnalysisQueries)
    outputs.files(generatedSharedSql, generatedAnalysisSql, generatedSharedAccountIntegrityAsset)
    outputs.files(sqlDelightMigrations.map { it.second })

    doLast {
        generatedSharedSql.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/schema/schema.sql and shared view queries.")
                appendLine("-- Do not edit directly; edit the shared SQL files instead.")
                appendLine("-- Connection PRAGMAs are applied by DatabaseDriverFactory.")
                appendLine()
                append(
                    sharedSchema
                        .readLines()
                        .filterNot { it.trimStart().startsWith("PRAGMA ") }
                        .joinToString(separator = "\n"),
                )
                appendLine()
                appendLine()
                appendLine("INSERT INTO meta (key, value) VALUES")
                appendLine("    ('schema_version', '$latestSchemaVersion'),")
                appendLine("    ('snapshot_version', '0');")
                sharedViewFiles.forEach { queryFile ->
                    appendLine()
                    appendLine("-- ${queryFile.name}")
                    append(queryFile.readText())
                    if (!endsWith("\n")) appendLine()
                }
            },
        )
        generatedAnalysisSql.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/queries/ parameterized queries.")
                appendLine("-- Do not edit directly; edit the shared SQL files instead.")
                appendLine()
                sharedAnalysisQueries.forEach { queryFile ->
                    appendLine("${queryName(queryFile)}:")
                    append(queryFile.readText())
                    if (!endsWith("\n")) appendLine()
                    appendLine()
                }
            },
        )
        sqlDelightMigrations.forEach { (sharedMigration, sqm) ->
            sqm.asFile.writeText(
                buildString {
                    appendLine("-- Generated from ../../shared/migrations/${sharedMigration.name}.")
                    appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                    appendLine()
                    append(sharedMigration.readText())
                },
            )
        }
        // Fresh installs need only the triggers migration 016 installs; DatabaseDriverFactory
        // applies them on create.
        generatedSharedAccountIntegrityAsset.asFile.apply {
            parentFile.mkdirs()
            writeText(
                sharedAccountIntegrityMigration.readText()
                    .substringAfter("DROP TABLE shared_account_integrity_guard;")
                    .substringBefore("UPDATE meta SET value = '16' WHERE key = 'schema_version';")
                    .trim(),
            )
        }
    }
}

android {
    namespace = "com.gestorfinances.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gestorfinances.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // A debug install sits beside the release app instead of replacing it: its own package,
        // so its own launcher entry and its own database, never the release app's real data.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("GestorDatabase") {
            packageName.set("com.gestorfinances.app.data.db")
            dialect("app.cash.sqldelight:sqlite-3-35-dialect:2.1.0")
        }
    }
}

kotlin {
    sourceSets.maybeCreate("main")

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.matching {
    it.name != syncSharedSqlForSqlDelight.name &&
        (
            it.name.contains("SqlDelight", ignoreCase = true) ||
                it.name.contains("GestorDatabase", ignoreCase = true)
            )
}
    .configureEach {
        dependsOn(syncSharedSqlForSqlDelight)
    }

// The same task writes the integrity SQL the app reads at runtime into src/main/assets, so asset
// packaging has to wait for it as well.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach {
        dependsOn(syncSharedSqlForSqlDelight)
    }

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    systemProperty(
        "gestor.repo.root",
        rootProject.layout.projectDirectory.dir("..").asFile.absolutePath,
    )
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.vico.compose.m3)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.sqlite.jdbc)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
