plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.derekwinters.intervaltrainer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.derekwinters.intervaltrainer"
        minSdk = 24
        targetSdk = 35
        // No versionCode or versionName: versioning is a separate concern (BUILD-013).
    }

    // Java and Kotlin target the same bytecode version, so the two cannot disagree about the
    // class-file version they produce (BUILD-011).
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // The unit tests run on the JVM alone (BUILD-021), so nothing here needs a device.
    testImplementation("junit:junit:4.13.2")
}
