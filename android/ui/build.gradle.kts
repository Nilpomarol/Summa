plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.components.resources)
            api(libs.jetbrains.lifecycle.viewmodel.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.text.google.fonts.versioned)
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.gestorfinances.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The Android resources are the one copy of the strings and the logo: Android-only code reads
// them as R.string/R.drawable, shared code as Res. Compose resources read a generated copy with
// Android's string escapes resolved, because their parser keeps them as written.
val androidStrings = layout.projectDirectory.file("src/androidMain/res/values/strings.xml")
val androidLogo = layout.projectDirectory.file("src/androidMain/res/drawable-nodpi/summa_mark.webp")
val sharedComposeResources = layout.buildDirectory.dir("generated/sharedComposeResources")
val syncSharedStrings by tasks.registering {
    inputs.files(androidStrings, androidLogo)
    outputs.dir(sharedComposeResources)
    doLast {
        val copy = sharedComposeResources.get().file("values/strings.xml").asFile
        copy.parentFile.mkdirs()
        copy.writeText(androidStrings.asFile.readText().replace("\\'", "'").replace("%%", "%"))
        androidLogo.asFile.copyTo(sharedComposeResources.get().file("drawable/summa_mark.webp").asFile, overwrite = true)
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.gestorfinances.ui.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.dir(syncSharedStrings.map { sharedComposeResources.get().asFile }),
    )
}
