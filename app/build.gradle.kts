import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version: bump the last number for each release (0.1.0 -> 0.1.1).
val appVersion = "0.2.6"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

// Release key lives OUTSIDE the repository (../keys). Without it, release builds use the debug key.
val keyProps = Properties().apply {
    val f = rootProject.file("../keys/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    // Lets Android 13+ list the app's languages under system settings â†’ apps â†’ language.
    androidResources { generateLocaleConfig = true }
    namespace = "com.aileboeth.expensetracking"
    compileSdk = 37
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.aileboeth.expensetracking"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        create("release") {
            if (keyProps.isNotEmpty()) {
                storeFile = rootProject.file("../keys/" + keyProps.getProperty("storeFile"))
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keyProps.isNotEmpty()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Screenshot tests (Robolectric + Roborazzi) only run with -Pscreenshots=true.
                it.systemProperty("screenshots", project.findProperty("screenshots") ?: "false")
                it.systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/central")
                it.systemProperty("robolectric.dependency.repo.id", "aliyun")
                it.systemProperty("roborazzi.test.record", project.findProperty("screenshots") ?: "false")
                it.maxHeapSize = "2g"
                // Robolectric needs JDK <= 21: pass -PtestJavaHome=<jdk17/21 home> when Gradle runs on a newer JDK.
                (project.findProperty("testJavaHome") as String?)?.let { home ->
                    val exe = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
                    it.executable = File(home, "bin/$exe").absolutePath
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.78.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.78.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
