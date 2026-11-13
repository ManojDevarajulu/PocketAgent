package dev.pocketagent.device

import dev.pocketagent.safety.BlockedApps
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppMatcherTest {
    private val matcher = AppMatcher(BlockedApps())
    private val maps = LauncherApp("Maps", "com.example.maps")
    private val googleMaps = LauncherApp("Google Maps", "com.google.android.apps.maps")
    private val clock = LauncherApp("Clock", "com.example.clock")

    @Test
    fun exactMatchLaunches() {
        assertEquals(AppResolution.Launch(clock), matcher.resolve("Clock", listOf(clock, maps)))
    }

    @Test
    fun caseInsensitiveMatchLaunches() {
        assertEquals(AppResolution.Launch(clock), matcher.resolve("  cLoCk ", listOf(clock, maps)))
    }

    @Test
    fun uniqueContainsMatchLaunches() {
        assertEquals(AppResolution.Launch(googleMaps), matcher.resolve("maps", listOf(googleMaps, clock)))
    }

    @Test
    fun exactMatchBeatsContains() {
        assertEquals(AppResolution.Launch(maps), matcher.resolve("Maps", listOf(googleMaps, maps)))
    }

    @Test
    fun ambiguousContainsRefusesAndListsLabels() {
        val apps = listOf(LauncherApp("Photos B", "p.b"), LauncherApp("Photos A", "p.a"), clock)
        val result = matcher.resolve("photos", apps)
        assertEquals(AppResolution.Refused("several apps match: Photos A, Photos B; ask the user which one"), result)
    }

    @Test
    fun ambiguousListIsCappedAtFive() {
        val apps = (1..7).map { LauncherApp("Game $it", "g.$it") }
        val result = matcher.resolve("game", apps)
        assertEquals(
            AppResolution.Refused("several apps match: Game 1, Game 2, Game 3, Game 4, Game 5; ask the user which one"),
            result,
        )
    }

    @Test
    fun twoAppsWithTheSameLabelAreAmbiguous() {
        val other = LauncherApp("Clock", "com.other.clock")
        val result = matcher.resolve("clock", listOf(clock, other))
        assertEquals(AppResolution.Refused("several apps match: Clock, Clock; ask the user which one"), result)
    }

    @Test
    fun noMatchRefuses() {
        assertEquals(AppResolution.Refused("no installed app named Camera"), matcher.resolve("Camera", listOf(clock)))
    }

    @Test
    fun blockedPackageIsRefused() {
        val renamed = LauncherApp("Quick", "com.phonepe.app")
        assertEquals(AppResolution.Refused("Quick is on the block list"), matcher.resolve("quick", listOf(renamed)))
    }

    @Test
    fun blockedLabelIsRefused() {
        val bank = LauncherApp("My Bank", "com.example.mb")
        assertEquals(AppResolution.Refused("My Bank is on the block list"), matcher.resolve("my bank", listOf(bank)))
    }

    @Test
    fun blankNameRefuses() {
        assertIs<AppResolution.Refused>(matcher.resolve("   ", listOf(clock, maps)))
    }

    @Test
    fun longerNameThatContainsTheLabelStillLaunches() {
        // The phone's app is labelled "Maps"; a model that asks for "Google Maps" must still reach it.
        val phoneMaps = LauncherApp("Maps", "com.google.android.apps.maps")
        assertEquals(AppResolution.Launch(phoneMaps), matcher.resolve("Google Maps", listOf(phoneMaps, clock)))
    }

    @Test
    fun longerNameWithOtherMapsAppInstalledStillPicksTheRightOne() {
        val phoneMaps = LauncherApp("Maps", "com.google.android.apps.maps")
        val organic = LauncherApp("Organic Maps", "app.organicmaps")
        assertEquals(AppResolution.Launch(phoneMaps), matcher.resolve("Google Maps", listOf(organic, phoneMaps)))
    }

    @Test
    fun labelOnlyMatchesAsWholeWordsInsideTheRequestedName() {
        val clockApp = LauncherApp("Clock", "com.example.clock")
        assertIs<AppResolution.Refused>(matcher.resolve("o'clockwork", listOf(clockApp)))
        assertEquals(AppResolution.Launch(clockApp), matcher.resolve("the Clock app", listOf(clockApp)))
    }

    @Test
    fun veryShortLabelsAreNeverMatchedInsideALongerName() {
        val go = LauncherApp("Go", "com.example.go")
        assertIs<AppResolution.Refused>(matcher.resolve("Google Maps", listOf(go)))
    }

    @Test
    fun twoLabelsInsideTheNameAreAmbiguous() {
        val a = LauncherApp("Maps", "com.a.maps")
        val b = LauncherApp("Google", "com.b.google")
        assertIs<AppResolution.Refused>(matcher.resolve("Google Maps", listOf(a, b)))
    }

    @Test
    fun aBlockedAppFoundByTheLongerNameIsStillRefused() {
        val bank = LauncherApp("Bank", "com.example.bank")
        assertEquals(AppResolution.Refused("Bank is on the block list"), matcher.resolve("my Bank app", listOf(bank)))
    }
}
