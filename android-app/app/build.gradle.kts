plugins {
    id("com.android.application")
}
android {
    namespace = "com.ankit.syncmesh"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("syncmesh-release.jks")
            storePassword = "707089Ankit"
            keyAlias = "syncmesh"
            keyPassword = "707089Ankit"
        }
    }

    defaultConfig {
        applicationId = "com.ankit.syncmesh"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(projects.keyboardFloris)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
