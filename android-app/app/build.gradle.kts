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
        versionCode = 20
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = !gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
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
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.google.android.play:app-update:2.1.0")
    implementation(libs.material)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    implementation(projects.keyboardHeliboard)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
