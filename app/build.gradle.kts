plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val numeroCompilacion = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.midia.organizador"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.midia.organizador"
        minSdk = 26
        targetSdk = 34
        versionCode = numeroCompilacion
        versionName = "1.$numeroCompilacion"
    }

    // Firma fija: así cada versión nueva se instala encima de la anterior sin perder las tareas
    signingConfigs {
        create("fija") {
            storeFile = file("firma.keystore")
            storePassword = "midia2026"
            keyAlias = "midia"
            keyPassword = "midia2026"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
