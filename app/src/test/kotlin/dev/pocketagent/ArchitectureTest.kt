package dev.pocketagent

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the rules in docs/superpowers/specs/2026-11-08-security-privacy-scenarios.md that a compiler cannot enforce. */
class ArchitectureTest {
    private val corePackages = listOf("agent", "llm", "safety", "playbook", "device", "setup")
    private val mainRoot = File("src/main/kotlin/dev/pocketagent")

    /** Every shipped source set (main, debug, release); test and androidTest sets are left out. */
    private val shippedRoots = listOf("src/main", "src/debug", "src/release").map { File(it) }

    private fun mainSources(): List<File> =
        shippedRoots.flatMap { root -> root.walk().filter { it.extension == "kt" }.toList() }

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

    @Test
    fun onlyOkHttpTransportImportsOkHttp() {
        val offenders = mainSources()
            .filterNot { it.invariantSeparatorsPath.endsWith("/dev/pocketagent/llm/OkHttpTransport.kt") }
            .filter { f -> codeLines(f).any { it.startsWith("import okhttp3") } }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    /**
     * Beyond the AppLog guard: in llm/ main sources the key must not be interpolated into any string except the
     * "Bearer" header value, and only the client, config and preset files may mention `apiKey` at all.
     */
    @Test
    fun noProviderCodeLogsOrPrintsTheKey() {
        val allowedFiles = setOf("AnthropicClient.kt", "OpenAiCompatClient.kt", "ProviderConfig.kt", "ProviderPresets.kt")
        val interpolated = Regex("""[$]\{[^}]*apiKey|[$]apiKey""")
        val llmSources = mainSources().filter { it.invariantSeparatorsPath.contains("/dev/pocketagent/llm/") }
        val mentions = llmSources.filter { f -> codeLines(f).any { it.contains("apiKey") } && f.name !in allowedFiles }
        assertEquals(emptyList(), mentions.map { it.path }, "unexpected files mention apiKey")
        val leaks = llmSources.flatMap { f ->
            codeLines(f).filter { interpolated.containsMatchIn(it) && !it.contains("Bearer \${config.apiKey}") }.map { "${f.name}: $it" }
        }
        assertEquals(emptyList(), leaks, "apiKey interpolated into a string")
    }

    @Test
    fun buildConfigHasNoKeyUrlOrModelFields() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(!gradle.contains("buildConfigField") && !gradle.contains("local.properties"), "build.gradle.kts feeds config at build time")
        val offenders = mainSources().filter { it.readText().contains("POCKETAGENT_") }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun noDevConfigRemains() {
        val found = File("src").walk().filter { it.name == "DevConfig.kt" }.toList()
        assertEquals(emptyList(), found.map { it.path })
    }

    @Test
    fun sourceSetsHaveNoKeyCode() {
        val offenders = listOf("src/debug", "src/release").map { File(it) }.filter { it.exists() }
            .flatMap { root -> root.walk().filter { it.extension == "kt" && it.readText().contains("apiKey") }.toList() }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    // ---- Manifest and accessibility service (S3 spec §3, §6 and §9) ----

    private fun parseXml(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        return factory.newDocumentBuilder().parse(File(path)).documentElement
    }

    private fun manifest(): Element = parseXml("src/main/AndroidManifest.xml")

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun components(): List<Element> =
        manifest().children("application").single().let { app ->
            listOf("activity", "activity-alias", "service", "receiver", "provider").flatMap { app.children(it) }
        }

    private fun component(name: String): Element =
        components().singleOrNull { it.getAttribute("android:name") == name } ?: error("$name is not in the manifest")

    @Test
    fun onlyTheLauncherIsExported() {
        val exported = components().filter { it.getAttribute("android:exported") == "true" }.map { it.getAttribute("android:name") }
        assertEquals(listOf(".ui.MainActivity"), exported)
        val launcher = component(".ui.MainActivity")
        val categories = launcher.children("category").map { it.getAttribute("android:name") }
        assertTrue("android.intent.category.LAUNCHER" in categories, "the launcher needs the LAUNCHER category")
        // Every other component states exported="false" explicitly.
        components().filter { it.getAttribute("android:name") != ".ui.MainActivity" }.forEach {
            assertEquals("false", it.getAttribute("android:exported"), "${it.getAttribute("android:name")} must not be exported")
        }
    }

    @Test
    fun serviceRequiresTheBindPermission() {
        val service = component(".android.PocketAccessibilityService")
        assertEquals("service", service.tagName)
        assertEquals("false", service.getAttribute("android:exported"))
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.getAttribute("android:permission"))
        val actions = service.children("action").map { it.getAttribute("android:name") }
        assertEquals(listOf("android.accessibilityservice.AccessibilityService"), actions)
        val meta = service.children("meta-data").single()
        assertEquals("android.accessibilityservice", meta.getAttribute("android:name"))
        assertEquals("@xml/accessibility_config", meta.getAttribute("android:resource"))
    }

    @Test
    fun serviceConfigRequestsNoEventsAndTheMinimumFlags() {
        val config = parseXml("src/main/res/xml/accessibility_config.xml")
        assertEquals("accessibility-service", config.tagName)
        assertTrue(!config.hasAttribute("android:accessibilityEventTypes"), "event types are switched on in code, never in the XML")
        assertEquals("flagRetrieveInteractiveWindows", config.getAttribute("android:accessibilityFlags"))
        listOf("canRetrieveWindowContent", "canPerformGestures", "canTakeScreenshot").forEach {
            assertEquals("true", config.getAttribute("android:$it"), "$it must be true")
        }
        assertTrue(!config.hasAttribute("android:canRequestFilterKeyEvents"), "key-event filtering is never requested")
        assertTrue(!config.hasAttribute("android:isAccessibilityTool"), "the service is not declared an accessibility tool")
        assertEquals("50", config.getAttribute("android:notificationTimeout"))
    }

    @Test
    fun manifestDeclaresLauncherQueriesOnly() {
        val root = manifest()
        val text = File("src/main/AndroidManifest.xml").readText()
        assertTrue(!text.contains("QUERY_ALL_PACKAGES"), "QUERY_ALL_PACKAGES is not used")
        val queries = root.children("queries").single()
        val intents = queries.children("intent")
        assertEquals(1, intents.size, "one launcher intent")
        assertTrue(queries.children("package").isEmpty() && queries.children("provider").isEmpty(), "launcher intent only")
        val intent = intents.single()
        assertEquals(listOf("android.intent.action.MAIN"), intent.children("action").map { it.getAttribute("android:name") })
        assertEquals(listOf("android.intent.category.LAUNCHER"), intent.children("category").map { it.getAttribute("android:name") })
    }

    @Test
    fun taskScreenHasItsOwnTask() {
        val screen = component(".ui.TaskInputActivity")
        assertEquals("activity", screen.tagName)
        assertTrue(screen.hasAttribute("android:taskAffinity"), "taskAffinity must be set")
        assertEquals("", screen.getAttribute("android:taskAffinity"))
        assertEquals("true", screen.getAttribute("android:excludeFromRecents"))
        assertEquals("false", screen.getAttribute("android:exported"))
    }

    /** Shipped sources are main, debug and release. Test sources are excluded because they hold fake keys. Matches `sk-...` and Anthropic's `sk-ant-...`. */
    /** Android's regex engine throws on this flag at runtime, while desktop Java (where the unit tests run) accepts it. */
    @Test
    fun noRegexFlagsThatAndroidRejects() {
        val unsupported = Regex("""UNICODE_CHARACTER_CLASS|\(\?[a-zA-Z]*U[a-zA-Z]*[:)]""")
        val offenders = mainSources().filter { f -> codeLines(f).any { unsupported.containsMatchIn(it) } }
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun noHardcodedKeysInShippedSources() {
        val key = Regex("""sk-[A-Za-z0-9_-]{20,}""")
        val offenders = shippedRoots.flatMap { it.walk().filter { f -> f.isFile }.toList() }.filter { key.containsMatchIn(it.readText()) }.toList()
        assertEquals(emptyList(), offenders.map { it.path })
    }

    @Test
    fun keystoreBoxUsesAesGcmInTheAndroidKeystore() {
        val text = File("src/main/kotlin/dev/pocketagent/android/KeystoreSecretBox.kt").readText()
        listOf("\"AndroidKeyStore\"", "\"AES/GCM/NoPadding\"", "setUserAuthenticationRequired(false)").forEach {
            assertTrue(text.contains(it), "missing $it")
        }
        listOf("getSharedPreferences", "putString", "Base64").forEach {
            assertTrue(!text.contains(it), "KeystoreSecretBox must handle bytes only, found $it")
        }
    }

    @Test
    fun onlyTheSetupStoresTouchTheSetupPreferences() {
        val users = mainSources().filter { it.readText().contains("\"pocketagent_setup\"") }
        assertEquals(1, users.size, users.joinToString { it.path })
    }

    @Test
    fun taskScreenNeverRequestsPermissions() {
        val text = File("src/main/kotlin/dev/pocketagent/ui/TaskInputActivity.kt").readText()
        assertTrue(!text.contains("requestPermissions"), "the task screen must not request permissions")
    }

    private fun setupActivityText() = File("src/main/kotlin/dev/pocketagent/ui/SetupActivity.kt").readText()

    @Test
    fun setupActivityIsSecure() = assertTrue(setupActivityText().contains("FLAG_SECURE"))

    @Test
    fun keyFieldIsAPasswordFieldWithAutofillOff() {
        val t = setupActivityText()
        assertTrue(t.contains("TYPE_TEXT_VARIATION_PASSWORD") && t.contains("IMPORTANT_FOR_AUTOFILL_NO"))
    }

    @Test
    fun noViewEverReceivesTheStoredKey() {
        val offenders = setupActivityText().lines().filter { it.contains("setText(") && it.contains("apiKey") }
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun onlySetupCodeRequestsRecordAudio() {
        val users = mainSources().filter { f -> codeLines(f).any { it.contains("requestPermissions(") } }
        assertEquals(listOf("SetupActivity.kt"), users.map { it.name })
    }

    @Test
    fun setupTestTaskTextIsCapped() {
        val t = setupActivityText()
        assertTrue(t.contains("TaskInputActivity.MAX_TEXT") || t.contains("2000") || t.contains("2_000"))
    }

    @Test
    fun bubbleSwitchWritesTheSetting() = assertTrue(setupActivityText().contains("bubbleOn ="))
}
