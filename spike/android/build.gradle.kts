// Root build file. DISPOSABLE Phase-0 spike (OC-5).
// Versions are indicative of "AGP current LTS / Kotlin 2.x" (OC-8) at authoring
// time (Jun 2026); an Android developer may need to nudge them to whatever their
// installed Android Studio / SDK supports. Nothing here was compile-validated.
plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}
