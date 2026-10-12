plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rokkystudio.qrscan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.rokkystudio.qrscan"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.neoui)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
}
