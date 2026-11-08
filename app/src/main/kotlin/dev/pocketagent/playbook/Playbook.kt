package dev.pocketagent.playbook

/** Navigation tips for one app, written by contributors and reviewed like code before they ship inside the APK. */
data class Playbook(
    val app: String,
    val packageName: String,
    val testedVersion: String,
    val body: String,
)
