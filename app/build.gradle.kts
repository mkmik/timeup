import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

// CI passes these so every build on main gets a higher versionCode (required for in-place updates).
val ciVersionCode = System.getenv("TIMEUP_VERSION_CODE")?.toIntOrNull() ?: 1
val ciVersionName = System.getenv("TIMEUP_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.1.0-dev"
// Release signing: set TIMEUP_KEYSTORE_FILE / _PASSWORD / TIMEUP_KEY_ALIAS / TIMEUP_KEY_PASSWORD.
// Without them the release build is signed with the debug key so it is still installable.
val keystorePath = System.getenv("TIMEUP_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "pub.mkm.timeup"
    compileSdk = 36

    defaultConfig {
        applicationId = "pub.mkm.timeup"
        minSdk = 36
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("TIMEUP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("TIMEUP_KEY_ALIAS")
                keyPassword = System.getenv("TIMEUP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
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

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sqldelight {
    databases {
        create("TimeUpDatabase") {
            packageName.set("pub.mkm.timeup.db")
        }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.sqldelight.android.driver)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}

// Build-time check (PRD §14): the base APK must never request INTERNET.
val noInternetChecks = mutableListOf<TaskProvider<*>>()
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val capitalised = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register("check${capitalised}NoInternetPermission") {
            group = "verification"
            description = "Fails if the merged ${variant.name} manifest requests android.permission.INTERNET"
            inputs.file(manifest)
            doLast {
                val text = manifest.get().asFile.readText()
                if (text.contains("android.permission.INTERNET")) {
                    throw GradleException("The ${variant.name} manifest requests android.permission.INTERNET; the base app must stay offline.")
                }
                println("OK: ${variant.name} manifest has no INTERNET permission")
            }
        }
        noInternetChecks += task
    }
}
tasks.register("checkNoInternetPermission") {
    group = "verification"
    description = "Verifies no variant requests the INTERNET permission"
    dependsOn(provider { noInternetChecks.toList() })
}
tasks.named("check") { dependsOn("checkNoInternetPermission") }
