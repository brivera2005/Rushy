plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


val defaultCredentialsFile = file("src/main/kotlin/com/rushy/app/DefaultCredentials.kt")
val defaultCredentialsExample = file("src/main/kotlin/com/rushy/app/DefaultCredentials.kt.example")

tasks.register("ensureDefaultCredentials") {
    doLast {
        if (!defaultCredentialsFile.exists() && defaultCredentialsExample.exists()) {
            defaultCredentialsExample.copyTo(defaultCredentialsFile)
        }
        if (!defaultCredentialsFile.exists()) {
            throw GradleException(
                "Missing DefaultCredentials.kt. Copy DefaultCredentials.kt.example to DefaultCredentials.kt."
            )
        }
    }
}

tasks.named("preBuild").configure { dependsOn("ensureDefaultCredentials") }

android {
    namespace = "com.rushy.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rushy.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 6
        versionName = "1.0.5"

    }

    signingConfigs {
        create("release") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.tv.foundation.ExperimentalTvFoundationApi",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
}