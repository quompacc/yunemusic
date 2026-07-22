import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.yunemusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yunemusic"
        minSdk = 26
        targetSdk = 34
        // CI (Gitea Actions) setzt -PciVersionCode/-PciVersionName pro Build
        versionCode = (project.findProperty("ciVersionCode") as? String)?.toIntOrNull() ?: 5
        versionName = (project.findProperty("ciVersionName") as? String) ?: "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Umgebungsvariablen (CI) haben Vorrang vor local.properties (lokal)
            storeFile     = rootProject.file(System.getenv("KEYSTORE_FILE") ?: localProps["KEYSTORE_FILE"] as? String ?: "yunemusic.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localProps["KEYSTORE_PASSWORD"] as? String ?: ""
            keyAlias      = System.getenv("KEY_ALIAS")         ?: localProps["KEY_ALIAS"]          as? String ?: ""
            keyPassword   = System.getenv("KEY_PASSWORD")      ?: localProps["KEY_PASSWORD"]       as? String ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = false  // R8 8.5.35 CME-Bug mit Rhino — nach AGP-Upgrade reaktivieren
            isShrinkResources = false
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix   = "-debug"
        }
    }

    // Universelle APK für alle ABIs — verhindert "ungültiges Paket" bei manuellem Install
    splits {
        abi {
            isEnable = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/*.kotlin_module"
            // Rhino referenziert javax.script (Java SE) — auf Android nicht vorhanden
            excludes += "META-INF/services/javax.script.ScriptEngineFactory"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Media3 / ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media:media:1.7.0")

    // NewPipe Extractor (YouTube anonym, kein API-Key)
    // dev-SNAPSHOT für aktuelle YouTube-Kompatibilität; URLEncoder-Crash via coreLibraryDesugaring behoben
    implementation("com.github.TeamNewPipe:NewPipeExtractor:dev-SNAPSHOT") { isChanging = true }
    implementation("org.mozilla:rhino:1.7.15")

    // OkHttp (HTTP-Client für NewPipe)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Java Desugaring — stellt URLEncoder.encode(String, Charset) (Java 10+) auf Android zur Verfügung
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
