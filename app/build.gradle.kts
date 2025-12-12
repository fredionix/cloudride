import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.amitshekhar.ridesharing"
    compileSdk = 34

    defaultConfig {
        applicationId = "me.amitshekhar.ridesharing"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            resValue(
                "string",
                "google_maps_key",
                gradleLocalProperties(rootDir).getProperty("apiKey")
            )
        }
        release {
            isMinifyEnabled = false
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
    packaging{
        resources {
            excludes += "META-INF/native-image/**"
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    //implementation("com.github.dangiashish:Google-Direction-Api:1.6")
    implementation(project(":simulator"))


    //implementation(platform("org.mongodb:mongodb-driver-sync-kotlin-bom")) // Check for the latest version

    // Add the coroutine driver dependency
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.6.0")
    implementation("org.mongodb:bson:5.6.1")

    // Add Kotlin serialization dependencies (necessary for data class mapping)
    //implementation("org.jetbrains.kotlinx:kotlinx-serialization-core") // Check for the latest version
    //implementation("org.mongodb:kotlin-serialization-bson") // Check for the latest version
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Places Library (required for search places)
    implementation("com.google.android.libraries.places:places:3.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))

    // Add the dependency for the Firebase Authentication library
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation("com.google.firebase:firebase-auth")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}