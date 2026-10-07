import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.nexoraofficial.console"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.nexoraofficial.console"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.9.0"

        /* The service the console talks to. Changed here, not in code, so a
           staging build is one line. It is also overridable at run time from
           the gate screen, the way the desktop app lets the URL be moved. */
        buildConfigField("String", "API_BASE", "\"https://nexora-api-55jv.onrender.com\"")
    }

    /* 1.8.0 — the audit before the release: until now every published console
       was the debug build, signed with the throwaway key on this computer that
       any Android SDK makes for itself. The release build is signed with
       Nexora's own key, kept OUTSIDE this repository in D:\nexora-signing (in
       the same keystore as Nexora Mobile's, under an alias of its own), so the
       key never reaches GitHub. On a computer without that folder the release
       build falls back to the debug key, which is fine for trying it on a
       phone but must never be published — release.ps1 refuses to. */
    val consoleSigning = file("D:/nexora-signing/keystore-console.properties")
    signingConfigs {
        if (consoleSigning.exists()) {
            val p = Properties().apply { consoleSigning.inputStream().use { load(it) } }
            create("release") {
                storeFile = file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        /* 1.6.0 — every screen drawn on the computer, phone-sized, from sample companies (as Nexora Mobile) */
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    /* The quarter-hourly check for new enquiries and new registrations. */
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    /* 1.8.0 — fingerprint (or the phone's own screen lock) to open the console, as Nexora Mobile. */
    implementation("androidx.biometric:biometric:1.1.0")
    /* biometric 1.1.0 brings fragment 1.2.5, whose FragmentActivity refuses the request codes the
       permission and result launchers use ("Can only use lower 16 bits for requestCode") — Nexora
       Mobile 0.4.2 closed on sign-in on Android 13 and later until this was pinned */
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.13.0")
}
