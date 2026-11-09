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

    private companion object {
        const val NOTICE = "settings.notice"
        const val CUSTOM_HOST = "settings.customHost"
        const val MIC_ASKED = "settings.micAsked"
        const val BUBBLE = "settings.bubble"
    }
}
