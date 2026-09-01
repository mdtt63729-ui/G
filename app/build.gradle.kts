import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.gitofy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gitofy"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "4.3.0-sky-blue"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Optional release-time integrity anchors. Set these from CI/Gradle properties
        // rather than committing signing material into source control.
        buildConfigField(
            "String",
            "SECURITY_EXPECTED_CERT_SHA256",
            "\"${providers.gradleProperty("GITOFY_RELEASE_CERT_SHA256").orNull.orEmpty()}\""
        )
        buildConfigField(
            "String",
            "SECURITY_EXPECTED_DEX_SHA256",
            "\"${providers.gradleProperty("GITOFY_RELEASE_DEX_SHA256").orNull.orEmpty()}\""
        )
        buildConfigField(
            "String",
            "SECURITY_EXPECTED_MANIFEST_SHA256",
            "\"${providers.gradleProperty("GITOFY_RELEASE_MANIFEST_SHA256").orNull.orEmpty()}\""
        )
        buildConfigField(
            "String",
            "SECURITY_EXPECTED_RESOURCES_ARSC_SHA256",
            "\"${providers.gradleProperty("GITOFY_RELEASE_RESOURCES_ARSC_SHA256").orNull.orEmpty()}\""
        )
        buildConfigField(
            "boolean",
            "SECURITY_ENFORCE_RELEASE",
            providers.gradleProperty("GITOFY_ENFORCE_INTEGRITY").orNull?.toBooleanStrictOrNull()?.toString() ?: "true"
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}


/**
 * Prints the trusted release APK hashes that can be supplied as Gradle
 * properties on the next release build. Resource/image hashes are checked
 * directly from the installed APK at runtime.
 */
tasks.register("printReleaseResourceIntegrity") {
    dependsOn("assembleRelease")
    doLast {
        val apk = fileTree(layout.buildDirectory.dir("outputs/apk/release")).matching {
            include("*.apk")
        }.files.maxByOrNull { it.lastModified() }
            ?: error("No release APK found under build/outputs/apk/release")

        fun hashEntry(name: String): String {
            ZipFile(apk).use { zip ->
                val entry = zip.getEntry(name) ?: return ""
                val digest = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) digest.update(buffer, 0, count)
                    }
                }
                return digest.digest().joinToString("") { "%02X".format(it) }
            }
        }

        println("GITOFY_RELEASE_APK=${apk.absolutePath}")
        println("GITOFY_RELEASE_MANIFEST_SHA256=${hashEntry("AndroidManifest.xml")}")
        println("GITOFY_RELEASE_RESOURCES_ARSC_SHA256=${hashEntry("resources.arsc")}")
        println("GITOFY_RELEASE_DEX_SHA256=${hashEntry("classes.dex")}")
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.runtime)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Activity & Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coil
    implementation(libs.coil.compose)

    // DataStore
    implementation(libs.datastore.preferences)

    // Security
    implementation(libs.security.crypto)

    // Splash
    implementation(libs.splashscreen)

    // Coroutines
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.core)

    // Serialization & Datetime
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Adaptive Layout — PRD Addendum: Responsive Layouts
    implementation(libs.adaptive.navigation)
    implementation(libs.adaptive.layout)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
