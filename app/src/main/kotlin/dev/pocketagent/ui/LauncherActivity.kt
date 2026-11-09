package dev.pocketagent.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.pocketagent.R

/** The only exported component: one button that opens the accessibility settings. S4 replaces it with setup. */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Overlays.dp(this, 24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            setText(R.string.launcher_intro)
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        })
        root.addView(Button(this).apply {
            setText(R.string.launcher_open_settings)
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    // Some builds hide the screen; there is nothing else to open.
                }
            }
        })
        setContentView(root)
    }
}
