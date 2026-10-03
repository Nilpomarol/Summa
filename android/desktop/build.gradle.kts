plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

compose.resources {
    packageOfResClass = "com.gestorfinances.desktop.resources"
}

compose.desktop {
    application {
        mainClass = "com.gestorfinances.desktop.MainKt"

        // `packageMsi` needs a full JDK (jpackage); Android Studio's bundled runtime has none.
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi)
            packageName = "Summa"
            packageVersion = "1.1.0"
            description = "Summa"
            vendor = "Nil Pomarol"
            // SQLite through JDBC, the settings in the user's preferences, what Skia needs, and
            // the locale data without which dates come out in English.
            modules("java.sql", "java.prefs", "java.naming", "jdk.unsupported", "jdk.localedata")
            windows {
                iconFile.set(project.file("icon.ico"))
                menu = true
                menuGroup = "Summa"
                shortcut = true
                perUserInstall = true
                // Fixed, so a newer installer upgrades the installed app instead of sitting beside it.
                upgradeUuid = "6f0c2c8e-3a57-4d0b-9c1e-5b8d2f4a7e31"
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.sqldelight.sqlite.driver)
    implementation(libs.sqlite.jdbc)
    // Only to tell Windows how to draw the title bar.
    implementation(libs.jna)

    testImplementation(libs.junit)
}
