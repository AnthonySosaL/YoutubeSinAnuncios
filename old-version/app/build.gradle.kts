import com.android.build.api.dsl.Packaging

plugins {
    id("com.android.application")
    id("com.google.gms.google-services") apply false
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    packagingOptions {
        resources.pickFirsts.add("META-INF/DEPENDENCIES")
    }
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {

    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation ("com.google.android.gms:play-services-auth:20.2.0")
    implementation ("com.github.bumptech.glide:glide:4.13.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.13.0")
    implementation ("com.google.api-client:google-api-client-android:1.32.1")
    implementation ("com.google.apis:google-api-services-youtube:v3-rev222-1.25.0")
    implementation ("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.0")
    implementation ("com.google.api-client:google-api-client-gson:1.32.1")
    implementation ("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation ("com.google.http-client:google-http-client-jackson2:1.40.1")
    implementation ("com.google.android.material:material:1.4.0")

    implementation ("com.google.api-client:google-api-client-gson:1.33.0")

}