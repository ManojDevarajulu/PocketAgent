package dev.pocketagent

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the rules in docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md that a compiler cannot enforce. */
class ArchitectureTest {
    private val corePackages = listOf("agent", "llm", "safety", "playbook", "device")
    private val mainRoot = File("src/main/kotlin/dev/pocketagent")

    private fun mainSources(): List<File> =
        File("src/main/kotlin").walk().filter { it.extension == "kt" }.toList()

    private fun codeLines(f: File): List<String> =
        f.readLines().map { it.trim() }.filterNot { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }

    @Test
    fun corePackagesDoNotImportAndroid() {
        val offenders = corePackages
            .flatMap { File(mainRoot, it).walk().filter { f -> f.extension == "kt" }.toList() }
            .filter { f -> f.readLines().any { it.startsWith("import android.") || it.startsWith("import androidx.") } }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun corePackagesExist() {
        corePackages.forEach {
            assertTrue(File(mainRoot, it).isDirectory, "missing package $it")
        }
    }

    @Test
    fun onlyLlmPackageUsesNetworkLibraries() {
        val network = Regex("""^import (okhttp3|java\.net\.http|java\.net\.HttpURLConnection|javax\.net)|\.openConnection\(""")
        val offenders = mainSources()
            .filterNot { it.invariantSeparatorsPath.contains("/dev/pocketagent/llm/") }
            .filter { f -> codeLines(f).any { network.containsMatchIn(it) } }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun noDirectLoggingOutsideAppLog() {
        val logging = Regex("""^import android\.util\.Log|\bLog\.[diwev]\(|\bprintln\(|\.printStackTrace\(""")
        val offenders = mainSources()
            .filterNot { it.invariantSeparatorsPath.endsWith("/dev/pocketagent/android/AppLog.kt") }
            .filter { f -> codeLines(f).any { logging.containsMatchIn(it) } }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun manifestMatchesAllowlist() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val allowedPermissions = setOf("android.permission.INTERNET", "android.permission.RECORD_AUDIO")
        val permissions = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet(), permissions - allowedPermissions, "unexpected permissions")
        assertTrue(manifest.contains("""android:allowBackup="false""""), "backups must be disabled")
        assertTrue(!manifest.contains("<receiver"), "no broadcast receivers")
        assertTrue(!manifest.contains("<provider"), "no content providers")
        val exportedBlocks = Regex("""<(activity|service)\b[^>]*android:exported="true"[^>]*>.*?</\1>""", RegexOption.DOT_MATCHES_ALL).findAll(manifest)
        exportedBlocks.forEach {
            assertTrue(it.value.startsWith("<activity") && it.value.contains("android.intent.category.LAUNCHER"), "only the launcher activity may be exported: ${it.value.take(80)}")
        }
    }

    @Test
    fun dependenciesMatchAllowlist() {
        val allowed = setOf(
            "org.jetbrains.kotlinx:kotlinx-coroutines-android",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
            "com.squareup.okhttp3:okhttp",
            "androidx.core:core-ktx",
            "junit:junit",
            "org.jetbrains.kotlin:kotlin-test",
            "org.jetbrains.kotlinx:kotlinx-coroutines-test",
        )
        val catalog = File("../gradle/libs.versions.toml").readText()
        val libraries = catalog.substringAfter("[libraries]").substringBefore("[plugins]")
        val modules = Regex("""module\s*=\s*"([^"]+)"""").findAll(libraries).map { it.groupValues[1] }.toSet()
        assertEquals(emptySet(), modules - allowed, "dependencies outside the allowlist")
    }
}
