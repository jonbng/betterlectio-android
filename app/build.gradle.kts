import java.io.FileInputStream
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing (Play upload key). See SIGNING.md.
// key.properties is gitignored — copy from key.properties.example.
val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// local.properties is gitignored (sdk.dir + optional secrets). AGP does not expose
// custom keys via project.findProperty — load them explicitly for BuildConfig.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}
val adminBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.substringAfterLast(':').contains("admin", ignoreCase = true)
}
val adminEnvironmentFile = rootProject.file("../admin/.env.local")
val adminEnvironment = mutableMapOf<String, String>()
if (adminBuildRequested) {
    if (!adminEnvironmentFile.isFile) {
        throw GradleException("Admin builds require ../admin/.env.local")
    }
    adminEnvironmentFile.forEachLine { rawLine ->
        val line = rawLine.trim().removePrefix("export ")
        if (line.isNotEmpty() && !line.startsWith("#") && '=' in line) {
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
            if (key == "ADMIN_MOBILE_TOKEN" || key == "ADMIN_MOBILE_API_ORIGIN") {
                adminEnvironment[key] = value
            }
        }
    }
    val token = adminEnvironment["ADMIN_MOBILE_TOKEN"].orEmpty()
    val origin = adminEnvironment["ADMIN_MOBILE_API_ORIGIN"].orEmpty()
    if (token.length < 32) {
        throw GradleException("ADMIN_MOBILE_TOKEN must be at least 32 characters")
    }
    val parsedOrigin = runCatching { URI(origin) }.getOrNull()
    if (parsedOrigin?.scheme != "https" ||
        parsedOrigin.host.isNullOrBlank() ||
        !parsedOrigin.rawPath.isNullOrEmpty() ||
        parsedOrigin.rawQuery != null ||
        parsedOrigin.rawFragment != null ||
        parsedOrigin.userInfo != null
    ) {
        throw GradleException("ADMIN_MOBILE_API_ORIGIN must be an HTTPS origin without a trailing slash")
    }
}
fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
val hasReleaseKeystore =
    keystorePropertiesFile.exists() &&
        keystoreProperties["storeFile"] != null &&
        keystoreProperties["keyAlias"] != null &&
        keystoreProperties["storePassword"] != null &&
        keystoreProperties["keyPassword"] != null

android {
    namespace = "dk.betterlectio.android"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dk.betterlectio.android"
        minSdk = 29
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Exposed via BuildConfig for debug-only tools later.
        buildConfigField("String", "LECTIO_BASE_HOST", "\"www.lectio.dk\"")
        // iOS parity (Info.plist): same public project URL + publishable key as defaults.
        // Override via local.properties / env: SUPABASE_URL, SUPABASE_ANON_KEY or SUPABASE_PUBLISHABLE_KEY.
        val defaultSupabaseUrl = "https://uyyqxwlojqvbpukxgxem.supabase.co"
        val defaultSupabaseKey = "sb_publishable_yGKR4-hB3Pp3Zr7QkU3UNg_5S3i-II3"
        val supabaseUrl = (project.findProperty("SUPABASE_URL") as String?)
            ?: System.getenv("SUPABASE_URL")
            ?: defaultSupabaseUrl
        val supabaseKey = (project.findProperty("SUPABASE_ANON_KEY") as String?)
            ?: (project.findProperty("SUPABASE_PUBLISHABLE_KEY") as String?)
            ?: System.getenv("SUPABASE_ANON_KEY")
            ?: System.getenv("SUPABASE_PUBLISHABLE_KEY")
            ?: defaultSupabaseKey
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.replace("\"", "\\\"")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseKey.replace("\"", "\\\"")}\"")

        val posthogApiKey = localProperties.getProperty("posthog.apiKey")
            ?: (project.findProperty("posthog.apiKey") as String?)
            ?: System.getenv("POSTHOG_API_KEY")
            ?: ""
        val posthogHost = localProperties.getProperty("posthog.host")
            ?: (project.findProperty("posthog.host") as String?)
            ?: System.getenv("POSTHOG_HOST")
            ?: "https://eu.i.posthog.com"
        buildConfigField("String", "POSTHOG_API_KEY", "\"${posthogApiKey.replace("\"", "\\\"")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${posthogHost.replace("\"", "\\\"")}\"")
        // Empty in every ordinary artifact. The admin build type overrides these values.
        buildConfigField("boolean", "ADMIN_BUILD", "false")
        buildConfigField("String", "ADMIN_MOBILE_TOKEN", "\"\"")
        buildConfigField("String", "ADMIN_MOBILE_API_ORIGIN", "\"\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storePassword = keystoreProperties["storePassword"] as String
                // Paths in key.properties are relative to the android/ project root.
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
            }
        }
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: avoid a second launcher icon next to a stale
            // "Hello Android" install from the initial Studio template (same package).
            versionNameSuffix = "-debug"
        }
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 code shrinking/obfuscation + unused resource removal (Play "optimized" builds).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        create("admin") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".admin"
            versionNameSuffix = "-admin"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "ADMIN_BUILD", "true")
            buildConfigField("String", "POSTHOG_API_KEY", "\"\"")
            buildConfigField(
                "String",
                "ADMIN_MOBILE_TOKEN",
                buildConfigString(adminEnvironment["ADMIN_MOBILE_TOKEN"].orEmpty()),
            )
            buildConfigField(
                "String",
                "ADMIN_MOBILE_API_ORIGIN",
                buildConfigString(adminEnvironment["ADMIN_MOBILE_API_ORIGIN"].orEmpty()),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Backport java.time (and other Java 8+ APIs) to minSdk 29. Without this,
        // API-34-only factories like LocalDate.ofInstant / LocalDateTime.ofInstant
        // throw NoSuchMethodError on Android 13 and below.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("da", "en")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core:wear-model"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)
    implementation(libs.timber)
    implementation(libs.zxing.core)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.webkit)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Glance home-screen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Play In-App Updates (probe; graceful if unused)
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    // Play Install Referrer (classmate referral attribution)
    implementation(libs.play.install.referrer)

    implementation(libs.posthog.android)
    implementation(libs.play.services.wearable)

    // Supabase (iOS parity — optional enhancement layer; core Lectio works without it)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
