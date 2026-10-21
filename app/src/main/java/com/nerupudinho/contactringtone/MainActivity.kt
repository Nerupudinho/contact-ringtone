package com.nerupudinho.contactringtone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy { RingtoneSync.prefs(this) }
    private lateinit var toneLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var overwriteSwitch: Switch
    private lateinit var autoSwitch: Switch
    private val actionButtons = mutableListOf<Button>()
    private var pendingAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        col.addView(label("Contact Ringtone", 26f, bold = true))
        col.addView(label(
            "Saved contacts get the ringtone you pick here. Unknown numbers keep the " +
                "SIM 1 / SIM 2 ringtones from your phone's sound settings.", 15f
        ))

        col.addView(label("1. Ringtone for saved contacts", 19f, bold = true, top = 28))
        toneLabel = label("", 15f)
        col.addView(toneLabel)
        col.addView(button("Choose ringtone") { pickTone() })

        overwriteSwitch = Switch(this).apply {
            text = "Also replace ringtones I set by hand on individual contacts"
            isChecked = prefs.getBoolean(RingtoneSync.KEY_OVERWRITE, false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(RingtoneSync.KEY_OVERWRITE, on).apply()
            }
            setPadding(0, dp(12), 0, dp(4))
        }
        col.addView(overwriteSwitch)
        col.addView(button("Apply to all contacts") { applyToAll() })

        autoSwitch = Switch(this).apply {
            text = "Automatically give new contacts this ringtone"
            isChecked = prefs.getBoolean(RingtoneSync.KEY_AUTO, true)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean(RingtoneSync.KEY_AUTO, on).apply()
                if (on) Scheduler.enable(this@MainActivity) else Scheduler.disable(this@MainActivity)
            }
            setPadding(0, dp(12), 0, dp(4))
        }
        col.addView(autoSwitch)
        statusLabel = label("", 14f, top = 8)
        col.addView(statusLabel)

        col.addView(label("2. Unknown callers and SIM 2", 19f, bold = true, top = 28))
        col.addView(label(
            "In your phone's ringtone settings, the SIM 1 ringtone plays for unknown " +
                "numbers calling SIM 1, and the SIM 2 ringtone plays for unknown numbers " +
                "calling SIM 2. Saved contacts get the contacts ringtone on either SIM.", 15f
        ))
        col.addView(button("Open sound settings") { openSoundSettings() })

        col.addView(label("Undo", 19f, bold = true, top = 28))
        col.addView(button("Remove this ringtone from all contacts") { removeAll() })

        val scroll = ScrollView(this).apply { addView(col) }
        // Android 15 draws apps edge to edge; keep content clear of the status and nav bars.
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(scroll)
        updateToneLabel()

        if (autoSwitch.isChecked && hasContacts() && prefs.getString(RingtoneSync.KEY_TONE, null) != null) {
            Scheduler.enable(this)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---- Ringtone picker ----

    private fun pickTone() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Ringtone for saved contacts")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            prefs.getString(RingtoneSync.KEY_TONE, null)?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
            }
        }
        try {
            startActivityForResult(intent, REQ_PICK)
        } catch (e: Exception) {
            toast("No ringtone picker found on this phone")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uri = pickedUri(data) ?: return
        prefs.edit().putString(RingtoneSync.KEY_TONE, uri.toString()).apply()
        updateToneLabel()
        refreshStatus()
        toast("Now tap \"Apply to all contacts\"")
    }

    @Suppress("DEPRECATION")
    private fun pickedUri(data: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }

    private fun updateToneLabel() {
        val saved = prefs.getString(RingtoneSync.KEY_TONE, null)
        toneLabel.text = if (saved == null) "No ringtone chosen yet." else "Chosen: " + toneTitle(Uri.parse(saved))
    }

    private fun toneTitle(uri: Uri): String =
        runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }.getOrNull()
            ?: uri.lastPathSegment ?: "Custom ringtone"

    // ---- Actions ----

    private fun applyToAll() {
        if (prefs.getString(RingtoneSync.KEY_TONE, null) == null) {
            toast("Choose a ringtone first")
            return
        }
        withContacts {
            val overwrite = overwriteSwitch.isChecked
            inBackground({ RingtoneSync.apply(this, overwrite) }) { n ->
                toast(if (n == 0) "All contacts already have this ringtone" else "Updated $n contacts")
                if (autoSwitch.isChecked) Scheduler.enable(this)
            }
        }
    }

    private fun removeAll() = withContacts {
        inBackground({ RingtoneSync.removeAll(this) }) { n -> toast("Removed from $n contacts") }
    }

    private fun openSoundSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun refreshStatus() {
        if (!hasContacts()) {
            statusLabel.text = "Contacts access not granted yet."
            return
        }
        val tone = prefs.getString(RingtoneSync.KEY_TONE, null)
        Thread {
            val s = runCatching { RingtoneSync.status(this) }.getOrNull()
            runOnUiThread {
                statusLabel.text = when {
                    s == null -> ""
                    tone == null -> "${s.total} contacts on this phone."
                    else -> "${s.withTone} of ${s.total} contacts use this ringtone."
                }
            }
        }.start()
    }

    private fun <T> inBackground(work: () -> T, done: (T) -> Unit) {
        setBusy(true)
        Thread {
            val result = kotlin.runCatching { work() }
            runOnUiThread {
                setBusy(false)
                result.onSuccess(done).onFailure { toast("Something went wrong: ${it.message}") }
                refreshStatus()
            }
        }.start()
    }

    private fun setBusy(busy: Boolean) = actionButtons.forEach { it.isEnabled = !busy }

    // ---- Permissions ----

    private fun hasContacts() = PERMS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun withContacts(action: () -> Unit) {
        if (hasContacts()) {
            action()
        } else {
            pendingAction = action
            requestPermissions(PERMS, REQ_PERMS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        val action = pendingAction
        pendingAction = null
        if (hasContacts()) action?.invoke() else toast("Contacts access is needed to set ringtones")
        refreshStatus()
    }

    // ---- UI helpers ----

    private fun label(s: String, size: Float, bold: Boolean = false, top: Int = 6) =
        TextView(this).apply {
            text = s
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(top), 0, dp(4))
        }

    private fun button(title: String, onClick: () -> Unit) =
        Button(this).apply {
            text = title
            setOnClickListener { onClick() }
        }.also { actionButtons.add(it) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_PICK = 1
        private const val REQ_PERMS = 2
        private val PERMS = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    }
}
