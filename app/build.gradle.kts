plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "nl.taalinq.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.taalinq.app"
        minSdk = 26          // Android 8 en nieuwer
        targetSdk = 35
        versionCode = 2      // verhoog dit getal bij elke nieuwe versie die je installeert
        versionName = "1.1"
    }

    // Vaste sleutel, zodat een nieuwe APK over de oude heen geïnstalleerd kan worden
    // zonder dat je woordenlijst verdwijnt. Zie LEESMIJ.md, kopje "De sleutel".
    signingConfigs {
        create("taalinq") {
            storeFile = file("taalinq.keystore")
            storePassword = "taalinq"
            keyAlias = "taalinq"
            keyPassword = "taalinq"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("taalinq")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("taalinq")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")         // hulpjes, o.a. voor de schermranden
    implementation("androidx.activity:activity-ktx:1.9.3")  // bestand kiezen, terugknop, schermranden
    implementation("androidx.webkit:webkit:1.12.1")         // laadt de web-app veilig uit de app zelf
}
