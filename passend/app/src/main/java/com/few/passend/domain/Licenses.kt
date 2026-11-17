package com.few.passend.domain

import android.content.Context
import com.few.passend.R

data class LibraryLicense(
    val name: String,
    val version: String,
    val licenseName: String,
    val licenseRawId: Int,
    val url: String,
    val category: String
)

object Licenses {
    const val CAT_ANDROIDX = "AndroidX"
    const val CAT_GOOGLE = "Google"
    const val CAT_KOTLIN = "Kotlin"
    const val CAT_THIRDPARTY = "Third-party"
    const val CAT_BUILD = "Build Tools (bukan di APK)"

    val LIBRARIES: List<LibraryLicense> = listOf(

        // ============================================================
        // AndroidX (runtime)
        // ============================================================
        LibraryLicense(
            name = "Core KTX",
            version = "1.12.0",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/core",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "AppCompat",
            version = "1.6.1",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/appcompat",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "RecyclerView",
            version = "1.3.2",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/recyclerview",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "Lifecycle (ViewModel, Runtime, Process)",
            version = "2.7.0",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "Activity KTX",
            version = "1.8.2",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/activity",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "Room (Runtime, KTX, Compiler via KSP)",
            version = "2.6.1",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/room",
            category = CAT_ANDROIDX
        ),
        LibraryLicense(
            name = "Biometric",
            version = "1.1.0",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/jetpack/androidx/releases/biometric",
            category = CAT_ANDROIDX
        ),

        // ============================================================
        // Google (runtime)
        // ============================================================
        LibraryLicense(
            name = "Material Components",
            version = "1.11.0",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://github.com/material-components/material-components-android",
            category = CAT_GOOGLE
        ),
        LibraryLicense(
            name = "ZXing Core",
            version = "3.5.3",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://github.com/zxing/zxing",
            category = CAT_GOOGLE
        ),
        LibraryLicense(
            name = "ZXing Android Embedded",
            version = "4.3.0",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://github.com/journeyapps/zxing-android-embedded",
            category = CAT_GOOGLE
        ),

        // ============================================================
        // Kotlin (runtime)
        // ============================================================
        LibraryLicense(
            name = "Kotlin Standard Library",
            version = "1.9.24",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://kotlinlang.org/",
            category = CAT_KOTLIN
        ),
        LibraryLicense(
            name = "Kotlin Coroutines (Android)",
            version = "1.7.3",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            category = CAT_KOTLIN
        ),

        // ============================================================
        // Third-party (runtime)
        // ============================================================
        LibraryLicense(
            name = "NanoHTTPD",
            version = "2.3.1",
            licenseName = "BSD 3-Clause",
            licenseRawId = R.raw.bsd_3_clause,
            url = "https://github.com/NanoHttpd/nanohttpd",
            category = CAT_THIRDPARTY
        ),
        LibraryLicense(
            name = "NanoHTTPD WebSocket",
            version = "2.3.1",
            licenseName = "BSD 3-Clause",
            licenseRawId = R.raw.bsd_3_clause,
            url = "https://github.com/NanoHttpd/nanohttpd",
            category = CAT_THIRDPARTY
        ),

        // ============================================================
        // Build Tools (tidak termasuk dalam APK, hanya untuk transparency)
        // ============================================================
        LibraryLicense(
            name = "Android Gradle Plugin",
            version = "8.6.1",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://developer.android.com/build",
            category = CAT_BUILD
        ),
        LibraryLicense(
            name = "Kotlin Compiler",
            version = "1.9.24",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://kotlinlang.org/",
            category = CAT_BUILD
        ),
        LibraryLicense(
            name = "KSP (Kotlin Symbol Processing)",
            version = "1.9.24-1.0.20",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://github.com/google/ksp",
            category = CAT_BUILD
        ),
        LibraryLicense(
            name = "Gradle",
            version = "8.10.2",
            licenseName = "Apache 2.0",
            licenseRawId = R.raw.apache_2_0,
            url = "https://gradle.org/",
            category = CAT_BUILD
        )
    )

    fun grouped(): List<Pair<String, List<LibraryLicense>>> {
        val order = listOf(CAT_ANDROIDX, CAT_GOOGLE, CAT_KOTLIN, CAT_THIRDPARTY, CAT_BUILD)
        return order.mapNotNull { cat ->
            val items = LIBRARIES.filter { it.category == cat }
            if (items.isEmpty()) null else cat to items
        }
    }

    fun loadText(ctx: Context, rawId: Int): String {
        return try {
            ctx.resources.openRawResource(rawId).bufferedReader().use { it.readText() }
        } catch (e: Throwable) {
            "Teks lisensi tidak tersedia: ${e.message}"
        }
    }
}