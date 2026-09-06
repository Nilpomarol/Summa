plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val generatedSharedSql = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/SharedSchema.sq",
)
val generatedAnalysisSql = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/Analysis.sq",
)
val generatedMigration1 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/1.sqm",
)
val generatedMigration2 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/2.sqm",
)
val generatedMigration3 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/3.sqm",
)
val generatedMigration4 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/4.sqm",
)
val generatedMigration5 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/5.sqm",
)
val generatedMigration6 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/6.sqm",
)
val generatedMigration7 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/7.sqm",
)
val generatedMigration8 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/8.sqm",
)
val generatedMigration9 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/9.sqm",
)
val generatedMigration10 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/10.sqm",
)
val generatedMigration11 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/11.sqm",
)
val generatedMigration12 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/12.sqm",
)
val generatedMigration13 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/13.sqm",
)
val generatedMigration14 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/14.sqm",
)
val generatedMigration15 = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/15.sqm",
)
val generatedSharedAccountIntegrityAsset = layout.projectDirectory.file(
    "src/main/assets/shared_account_integrity.sql",
)

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
)
val sharedAnalysisQueryFiles = listOf(
    "goal_account_allocations.sql" to "goalAccountAllocations",
    "analysis_activity_months.sql" to "activityMonths",
    "analysis_actual_breakdown.sql" to "analysisActualBreakdown",
    "analysis_actual_by_category.sql" to "analysisActualByCategory",
    "analysis_account_flow_over_time.sql" to "analysisAccountFlowOverTime",
    "analysis_income_vs_expense.sql" to "analysisIncomeVsExpense",
    "analysis_period_totals.sql" to "analysisPeriodTotals",
)

val syncSharedSqlForSqlDelight by tasks.registering {
    val sharedRoot = rootProject.layout.projectDirectory.dir("../shared")
    val sharedSchema = sharedRoot.file("schema/schema.sql")
    val sharedBaselineMigration = sharedRoot.file("migrations/001_initial.sql")
    val sharedMigration002 = sharedRoot.file("migrations/002_add_splits_tag_id.sql")
    val sharedMigration003 = sharedRoot.file("migrations/003_add_tag_category_and_type.sql")
    val sharedMigration004 = sharedRoot.file("migrations/004_add_v_trip_actual_total_view.sql")
    val sharedMigration005 = sharedRoot.file("migrations/005_fix_v_movement_summary_external_amount.sql")
    val sharedMigration006 = sharedRoot.file("migrations/006_add_yearly_budget_period.sql")
    val sharedMigration007 = sharedRoot.file("migrations/007_simplify_budget_rules.sql")
    val sharedMigration008 = sharedRoot.file("migrations/008_add_budget_inclusion_rules.sql")
    val sharedMigration009 = sharedRoot.file("migrations/009_derive_refund_attribution.sql")
    val sharedMigration010 = sharedRoot.file("migrations/010_remove_auto_categorization.sql")
    val sharedMigration011 = sharedRoot.file("migrations/011_add_recurring_settlements.sql")
    val sharedMigration012 = sharedRoot.file("migrations/012_add_savings_goals.sql")
    val sharedMigration013 = sharedRoot.file("migrations/013_add_identity_colors_to_movement_summary.sql")
    val sharedMigration014 = sharedRoot.file("migrations/014_add_destination_account_color.sql")
    val sharedMigration015 = sharedRoot.file("migrations/015_add_shared_accounts.sql")
    val sharedMigration016 = sharedRoot.file("migrations/016_enforce_shared_account_integrity.sql")
    val sharedViews = sharedViewFiles.map { sharedRoot.file("queries/$it") }
    val sharedAnalysisQueries = sharedAnalysisQueryFiles.map { sharedRoot.file("queries/${it.first}") }

    inputs.file(sharedBaselineMigration)
    inputs.file(sharedSchema)
    inputs.file(sharedMigration002)
    inputs.file(sharedMigration003)
    inputs.file(sharedMigration004)
    inputs.file(sharedMigration005)
    inputs.file(sharedMigration006)
    inputs.file(sharedMigration007)
    inputs.file(sharedMigration008)
    inputs.file(sharedMigration009)
    inputs.file(sharedMigration010)
    inputs.file(sharedMigration011)
    inputs.file(sharedMigration012)
    inputs.file(sharedMigration013)
    inputs.file(sharedMigration014)
    inputs.file(sharedMigration015)
    inputs.file(sharedMigration016)
    inputs.files(sharedViews)
    inputs.files(sharedAnalysisQueries)
    outputs.file(generatedSharedSql)
    outputs.file(generatedAnalysisSql)
    outputs.file(generatedMigration1)
    outputs.file(generatedMigration2)
    outputs.file(generatedMigration3)
    outputs.file(generatedMigration4)
    outputs.file(generatedMigration5)
    outputs.file(generatedMigration6)
    outputs.file(generatedMigration7)
    outputs.file(generatedMigration8)
    outputs.file(generatedMigration9)
    outputs.file(generatedMigration10)
    outputs.file(generatedMigration11)
    outputs.file(generatedMigration12)
    outputs.file(generatedMigration13)
    outputs.file(generatedMigration14)
    outputs.file(generatedMigration15)
    outputs.file(generatedSharedAccountIntegrityAsset)

    doLast {
        val sharedOutputFile = generatedSharedSql.asFile
        sharedOutputFile.parentFile.mkdirs()
        sharedOutputFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/schema/schema.sql and shared view queries.")
                appendLine("-- Do not edit directly; edit the shared SQL files instead.")
                appendLine("-- Connection PRAGMAs are applied by DatabaseDriverFactory.")
                appendLine()
                append(
                    sharedSchema.asFile
                        .readLines()
                        .filterNot { it.trimStart().startsWith("PRAGMA ") }
                        .joinToString(separator = "\n"),
                )
                appendLine()
                appendLine()
                appendLine("INSERT INTO meta (key, value) VALUES")
                appendLine("    ('schema_version', '16'),")
                appendLine("    ('snapshot_version', '0');")
                sharedViews.forEach { queryFile ->
                    appendLine()
                    appendLine("-- ${queryFile.asFile.name}")
                    append(queryFile.asFile.readText())
                    if (!endsWith("\n")) appendLine()
                }
            },
        )
        generatedAnalysisSql.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/queries/ parameterized queries.")
                appendLine("-- Do not edit directly; edit the shared SQL files instead.")
                appendLine()
                sharedAnalysisQueryFiles.forEach { (fileName, queryName) ->
                    val queryFile = sharedRoot.file("queries/$fileName").asFile
                    appendLine("$queryName:")
                    append(queryFile.readText())
                    if (!endsWith("\n")) appendLine()
                    appendLine()
                }
            },
        )
        generatedMigration1.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/002_add_splits_tag_id.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration002.asFile.readText())
            },
        )
        generatedMigration2.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/003_add_tag_category_and_type.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration003.asFile.readText())
            },
        )
        generatedMigration3.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/004_add_v_trip_actual_total_view.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration004.asFile.readText())
            },
        )
        generatedMigration4.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/005_fix_v_movement_summary_external_amount.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration005.asFile.readText())
            },
        )
        generatedMigration5.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/006_add_yearly_budget_period.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration006.asFile.readText())
            },
        )
        generatedMigration6.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/007_simplify_budget_rules.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration007.asFile.readText())
            },
        )
        generatedMigration7.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/008_add_budget_inclusion_rules.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration008.asFile.readText())
            },
        )
        generatedMigration8.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/009_derive_refund_attribution.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration009.asFile.readText())
            },
        )
        generatedMigration9.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/010_remove_auto_categorization.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration010.asFile.readText())
            },
        )
        generatedMigration10.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/011_add_recurring_settlements.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration011.asFile.readText())
            },
        )
        generatedMigration11.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/012_add_savings_goals.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration012.asFile.readText())
            },
        )
        generatedMigration12.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/013_add_identity_colors_to_movement_summary.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration013.asFile.readText())
            },
        )
        generatedMigration13.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/014_add_destination_account_color.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration014.asFile.readText())
            },
        )
        generatedMigration14.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/015_add_shared_accounts.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration015.asFile.readText())
            },
        )
        generatedMigration15.asFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/016_enforce_shared_account_integrity.sql.")
                appendLine("-- Do not edit directly; edit the shared SQL file instead.")
                appendLine()
                append(sharedMigration016.asFile.readText())
            },
        )
        generatedSharedAccountIntegrityAsset.asFile.apply {
            parentFile.mkdirs()
            writeText(
                sharedMigration016.asFile.readText()
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
