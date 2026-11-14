package dev.pocketagent.setup

class SetupSettings(private val store: KeyValueStore) {
    var noticeAccepted: Boolean
        get() = store.getBoolean(NOTICE, false)
        set(value) = store.putBoolean(NOTICE, value)

    var acceptedCustomHost: String?
        get() = store.getString(CUSTOM_HOST)
        set(value) = store.putString(CUSTOM_HOST, value)

    var microphoneAsked: Boolean
        get() = store.getBoolean(MIC_ASKED, false)
        set(value) = store.putBoolean(MIC_ASKED, value)

    var bubbleOn: Boolean
        get() = store.getBoolean(BUBBLE, false)
        set(value) = store.putBoolean(BUBBLE, value)

    /** Speak and listen when a task is started with the mic. On unless the user turns it off. */
    var talkBack: Boolean
        get() = store.getBoolean(TALK_BACK, true)
        set(value) = store.putBoolean(TALK_BACK, value)

    /** Send the model only what changed on the screen. Off unless the user turns it on: it saves tokens, but small models may cope less well. */
    var shortScreens: Boolean
        get() = store.getBoolean(SHORT_SCREENS, false)
        set(value) = store.putBoolean(SHORT_SCREENS, value)

    /** Lets the user's own computer send tasks to this phone over the local network. Off until the user turns it on. */
    var computerLink: Boolean
        get() = store.getBoolean(COMPUTER_LINK, false)
        set(value) = store.putBoolean(COMPUTER_LINK, value)

    /** The pairing token for the computer link, or null until the link is first turned on. */
    var linkToken: String?
        get() = store.getString(LINK_TOKEN)
        set(value) = store.putString(LINK_TOKEN, value)

    /** "system", "light" or "dark". */
    var theme: String
        get() = store.getString(THEME)?.takeIf { it in setOf("system", "light", "dark") } ?: "system"
        set(value) = store.putString(THEME, value)

    /** The user's saved shortcut texts, or null until they have changed them (the app then shows its defaults). */
    var shortcuts: List<String>?
        get() = store.getString(SHORTCUTS)?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
        set(value) = store.putString(SHORTCUTS, value?.joinToString("\n"))

    private companion object {
        const val NOTICE = "settings.notice"
        const val CUSTOM_HOST = "settings.customHost"
        const val MIC_ASKED = "settings.micAsked"
        const val BUBBLE = "settings.bubble"
        const val TALK_BACK = "settings.talkBack"
        const val SHORT_SCREENS = "settings.shortScreens"
        const val COMPUTER_LINK = "settings.computerLink"
        const val LINK_TOKEN = "settings.linkToken"
        const val THEME = "settings.theme"
        const val SHORTCUTS = "settings.shortcuts"
    }
}
