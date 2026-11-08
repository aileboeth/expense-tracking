plugins {
    id("com.android.application") version "9.5.1" apply false
    // Puts a newer Kotlin on the classpath than the one AGP ships with (needed by current AndroidX).
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
