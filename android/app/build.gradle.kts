plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.sqldelight)
}

val generatedSharedSql = layout.projectDirectory.file(
    "src/main/sqldelight/com/gestorfinances/app/data/db/SharedSchema.sq",
)

val sharedQueryFiles = listOf(
    "v_movement_shared.sql",
    "v_account_flow.sql",
    "v_account_balance.sql",
    "v_actual_expense.sql",
    "v_actual_income.sql",
    "v_person_balance.sql",
)

val syncSharedSqlForSqlDelight by tasks.registering {
    val sharedRoot = rootProject.layout.projectDirectory.dir("../shared")
    val sharedBaselineMigration = sharedRoot.file("migrations/001_initial.sql")
    val sharedQueries = sharedQueryFiles.map { sharedRoot.file("queries/$it") }

    inputs.file(sharedBaselineMigration)
    inputs.files(sharedQueries)
    outputs.file(generatedSharedSql)

    doLast {
        val outputFile = generatedSharedSql.asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            buildString {
                appendLine("-- Generated from ../../shared/migrations/001_initial.sql and ../../shared/queries.")
                appendLine("-- Do not edit directly; edit the shared SQL files instead.")
                appendLine("-- Connection PRAGMAs are applied by DatabaseDriverFactory.")
                appendLine()
                append(
                    sharedBaselineMigration.asFile
                        .readLines()
                        .filterNot { it.trimStart().startsWith("PRAGMA ") }
                        .joinToString(separator = "\n"),
                )
                appendLine()
                appendLine()
                sharedQueries.forEach { queryFile ->
                    appendLine()
                    appendLine("-- ${queryFile.asFile.name}")
                    append(queryFile.asFile.readText())
                    if (!endsWith("\n")) appendLine()
                }
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
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.sqldelight.android.driver)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.sqlite.jdbc)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
