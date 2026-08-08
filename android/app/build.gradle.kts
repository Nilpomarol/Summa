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

val sharedViewFiles = listOf(
    "v_movement_shared.sql",
    "v_movement_summary.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
    "v_trip_actual_total.sql",
)
val sharedAnalysisQueryFiles = listOf(
    "analysis_activity_months.sql" to "activityMonths",
    "analysis_actual_breakdown.sql" to "analysisActualBreakdown",
    "analysis_actual_by_category.sql" to "analysisActualByCategory",
    "analysis_account_flow_over_time.sql" to "analysisAccountFlowOverTime",
    "analysis_income_vs_expense.sql" to "analysisIncomeVsExpense",
    "analysis_period_totals.sql" to "analysisPeriodTotals",
    "analysis_category_trends.sql" to "analysisCategoryTrends",
    "analysis_largest_expenses.sql" to "analysisLargestExpenses",
    "analysis_net_worth_over_time.sql" to "analysisNetWorthOverTime",
    "analysis_top_merchants.sql" to "analysisTopMerchants",
    "analysis_category_frequency.sql" to "analysisCategoryFrequency",
    "analysis_weekday_spend.sql" to "analysisWeekdaySpend",
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
    val sharedViews = sharedViewFiles.map { sharedRoot.file("queries/$it") }
    val sharedAnalysisQueries = sharedAnalysisQueryFiles.map { sharedRoot.file("queries/${it.first}") }

    inputs.file(sharedBaselineMigration)
    inputs.file(sharedSchema)
    inputs.file(sharedMigration002)
    inputs.file(sharedMigration003)
    inputs.file(sharedMigration004)
    inputs.file(sharedMigration005)
    inputs.file(sharedMigration006)
    inputs.files(sharedViews)
    inputs.files(sharedAnalysisQueries)
    outputs.file(generatedSharedSql)
    outputs.file(generatedAnalysisSql)
    outputs.file(generatedMigration1)
    outputs.file(generatedMigration2)
    outputs.file(generatedMigration3)
    outputs.file(generatedMigration4)
    outputs.file(generatedMigration5)

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
                appendLine("    ('schema_version', '8'),")
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
                appendLine("-- Generated from ../../shared/queries/analysis_*.sql.")
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

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.sqlite.jdbc)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
