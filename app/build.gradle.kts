plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.notes"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.legacy.support.v4)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    dependencies {
        implementation("org.apache.poi:poi:5.2.5") // Для базовой поддержки .docx
        implementation("org.apache.poi:poi-ooxml:5.2.5") // Для полной поддержки OOXML (.docx)
        implementation("androidx.recyclerview:recyclerview:1.2.1")
        implementation("androidx.multidex:multidex:2.0.1")
        implementation("androidx.activity:activity:1.8.0")
    }
}