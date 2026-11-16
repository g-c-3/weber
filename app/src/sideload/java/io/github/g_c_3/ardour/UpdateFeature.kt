package io.github.g_c_3.ardour

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File

// Sideload flavor: the update button beside the version. The check runs only on its tap, never on open or in the
// background (DECISIONS.md 36). While a download runs, the line under the version row is the progress bar.
object UpdateFeature {
    fun attach(activity: Activity, slot: FrameLayout, divider: ProgressDivider, currentVersion: String) {
        val button = Ui.floatingPill(activity, "Update Check")
        slot.addView(button)
        val rows = UpdateRows(activity, currentVersion, button, divider, Pulse(button))
        button.setOnClickListener { rows.onTap() }
    }
}

// The button's words carry the state: Update Check, Checking, Up to date, Download 1.2.1, Installing 1.2.1.
private class UpdateRows(
    val activity: Activity,
    val currentVersion: String,
    val button: TextView,
    val divider: ProgressDivider,
    val pulse: Pulse,
) {
    private var newer: CheckResult.Newer? = null
    private var busy = false

    fun onTap() {
        if (busy) return
        val pending = newer
        if (pending == null) check() else install(pending)
    }

    private fun ui(block: () -> Unit) = activity.runOnUiThread { if (!activity.isDestroyed) block() }

    private fun setBusy(value: Boolean) {
        busy = value
        Ui.setBusy(button, value)
        // The light pulses only while an update waits to be installed and nothing is running.
        if (value) pulse.stop() else if (newer != null) pulse.start()
    }

    private fun check() {
        setBusy(true)
        button.text = "Checking…"
        divider.bar.value = 0f
        divider.bar.indeterminate = true
        divider.showProgress(true)
        divider.bar.indeterminate = true
        Thread {
            val result = UpdateCheck.check(currentVersion)
            ui {
                divider.showProgress(false)
                setBusy(false)
                show(result)
            }
        }.start()
    }

    private fun show(result: CheckResult) {
        when (result) {
            is CheckResult.Newer -> {
                newer = result
                button.text = "Download ${result.version}"
                pulse.start()
            }
            CheckResult.UpToDate -> button.text = "Up to date"
            CheckResult.RateLimited -> button.text = "Try again later"
            is CheckResult.Failed -> button.text = "Check failed"
        }
    }

    private fun install(update: CheckResult.Newer) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            // Android needs the user's permission once; come back and tap again.
            button.text = "Allow installs"
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
            )
            return
        }
        setBusy(true)
        button.text = "Installing ${update.version}"
        divider.bar.indeterminate = false
        divider.bar.value = 0f
        divider.showProgress(true)
        Thread {
            try {
                val file = UpdateCheck.download(update, File(activity.cacheDir, "update")) { pct ->
                    ui { divider.bar.value = pct / 100f }
                }
                ApkInstaller.install(activity, file)
                ui {
                    divider.showProgress(false)
                    setBusy(false)
                    // The Android installer takes over; if it is cancelled the button offers the download again.
                    button.text = "Download ${update.version}"
                }
            } catch (e: SecurityException) {
                ui {
                    divider.showProgress(false)
                    setBusy(false)
                    button.text = "Checksum failed"
                }
            } catch (e: Exception) {
                ui {
                    divider.showProgress(false)
                    setBusy(false)
                    button.text = "Update failed"
                }
            }
        }.start()
    }
}
