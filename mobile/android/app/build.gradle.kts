import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "cm.schoollink.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        // flutter_local_notifications needs the newer java.time APIs on older Android versions
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "cm.schoollink.app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // NFR-4: Android 8.0 (API 26) and later.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // D23, BR-21: the Firebase project of the app, from android/app/google-services.json (never
    // committed), turned into the Android resources Firebase reads at start-up — what the
    // google-services plugin does, without the plugin. Every build of a machine that has the
    // file gets push, however the app is started or installed; without it the app runs
    // without push, unless the build is given the FIREBASE_* dart-defines.
    buildFeatures {
        resValues = true
    }
    val googleServices = file("google-services.json")
    if (googleServices.exists()) {
        val config = groovy.json.JsonSlurper().parse(googleServices) as Map<*, *>
        val project = config["project_info"] as Map<*, *>
        val client = (config["client"] as List<*>).map { it as Map<*, *> }.firstOrNull {
            val info = (it["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>
            info["package_name"] == "cm.schoollink.app"
        } ?: throw GradleException("google-services.json has no Android app cm.schoollink.app")
        val apiKey = ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"] as String
        defaultConfig {
            resValue("string", "google_app_id", (client["client_info"] as Map<*, *>)["mobilesdk_app_id"] as String)
            resValue("string", "gcm_defaultSenderId", project["project_number"] as String)
            resValue("string", "project_id", project["project_id"] as String)
            resValue("string", "google_api_key", apiKey)
        }
    }

    // The upload key for Google Play (docs/DEPLOYMENT.md): android/key.properties names the keystore
    // and its passwords. Neither is ever committed. Without the file, release builds use the debug
    // key, so `flutter run --release` still works on a development machine.
    val keyProperties = Properties().apply {
        rootProject.file("key.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (!keyProperties.isEmpty) {
            create("upload") {
                storeFile = rootProject.file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
