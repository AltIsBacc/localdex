plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.localdex"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.localdex"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("be.mygod.librootkotlinx:librootkotlinx:2.0.0-beta02")

    compileOnly("de.robv.android.xposed:api:82")
}
