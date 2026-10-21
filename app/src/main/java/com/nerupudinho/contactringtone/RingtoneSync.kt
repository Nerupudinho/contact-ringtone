package com.nerupudinho.contactringtone

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.provider.ContactsContract.Contacts

/**
 * Writes the chosen ringtone onto contacts. Android then plays that tone for those
 * contacts on its own, so nothing has to run while a call is ringing.
 */
object RingtoneSync {
    const val PREFS = "settings"
    const val KEY_TONE = "tone_uri"
    const val KEY_LAST_APPLIED = "last_applied_uri"
    const val KEY_OVERWRITE = "overwrite_manual"
    const val KEY_AUTO = "auto_sync"

    data class Status(val total: Int, val withTone: Int)

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Gives every contact the chosen tone. Contacts with a tone set by hand are left
     * alone unless [overwriteManual] is true. Returns how many contacts changed.
     */
    fun apply(ctx: Context, overwriteManual: Boolean): Int {
        val p = prefs(ctx)
        val tone = p.getString(KEY_TONE, null) ?: return 0
        val last = p.getString(KEY_LAST_APPLIED, null)
        val ops = ArrayList<ContentProviderOperation>()

        ctx.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.CUSTOM_RINGTONE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val current = c.getString(1)
                if (current == tone) continue
                val setByThisApp = current.isNullOrEmpty() || current == last
                if (setByThisApp || overwriteManual) ops.add(setTone(id, tone))
            }
        }

        commit(ctx, ops)
        p.edit().putString(KEY_LAST_APPLIED, tone).apply()
        return ops.size
    }

    /** Clears the tone this app set. Tones set by hand are not touched. */
    fun removeAll(ctx: Context): Int {
        val p = prefs(ctx)
        val mine = setOfNotNull(
            p.getString(KEY_TONE, null),
            p.getString(KEY_LAST_APPLIED, null)
        )
        if (mine.isEmpty()) return 0
        val ops = ArrayList<ContentProviderOperation>()

        ctx.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.CUSTOM_RINGTONE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val current = c.getString(1)
                if (current != null && current in mine) ops.add(setTone(c.getLong(0), null))
            }
        }

        commit(ctx, ops)
        p.edit().remove(KEY_LAST_APPLIED).apply()
        return ops.size
    }

    fun status(ctx: Context): Status {
        val tone = prefs(ctx).getString(KEY_TONE, null)
        var total = 0
        var matching = 0
        ctx.contentResolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.CUSTOM_RINGTONE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                total++
                if (tone != null && c.getString(1) == tone) matching++
            }
        }
        return Status(total, matching)
    }

    private fun setTone(id: Long, tone: String?): ContentProviderOperation =
        ContentProviderOperation
            .newUpdate(ContentUris.withAppendedId(Contacts.CONTENT_URI, id))
            .withValue(Contacts.CUSTOM_RINGTONE, tone)
            .build()

    private fun commit(ctx: Context, ops: List<ContentProviderOperation>) {
        ops.chunked(200).forEach {
            ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(it))
        }
    }
}
