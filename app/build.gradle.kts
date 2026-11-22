plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    //id("kotlin-android")
    //id("kotlin-android-extensions")
}

android {
    namespace = "ru.archzar.nearfieldstorage"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.archzar.nearfieldstorage"
        minSdk = 26
        targetSdk = 35
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

    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    //implementation("com.android.tools.build:gradle:8.9.1")
    //implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)

    //implementation("com.github.Maliotis:NFCLib-Android:1.0.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)



    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.0")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")


    //nfc

    //implementation fileTree(dir: 'libs', include: ['*.jar'])
    //implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.21")
    //implementation("androidx.appcompat:appcompat:1.6.1")
    //implementation("androidx.core:core-ktx:1.12.0")
    testImplementation("unit:junit:4.13.2")
    //testImplementation("ndroidx.test:core-ktx:1.5.0")
    testImplementation("ndroidx.test:runner:1.5.2")
    testImplementation("ndroidx.test:rules:1.5.0")
    testImplementation("ndroidx.test.ext:junit:1.1.5")
    testImplementation("ndroidx.test.ext:truth:1.5.0")
    testImplementation("om.google.truth:truth:1.1.5")

    // Core library
    androidTestImplementation("androidx.test:core-ktx:1.5.0")

    // AndroidJUnitRunner and JUnit Rules
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")

    //Mockito
    //androidTestImplementation 'org.mockito.kotlin:mockito-kotlin:3.1.0'

    // Assertions
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:truth:1.5.0")
    androidTestImplementation("com.google.truth:truth:1.1.5")
    // Espresso dependencies
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.5.1")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.5.1")


    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.21")

}