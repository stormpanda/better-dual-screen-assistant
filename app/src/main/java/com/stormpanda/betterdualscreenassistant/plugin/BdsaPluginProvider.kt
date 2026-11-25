package com.stormpanda.betterdualscreenassistant.plugin

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Exported ContentProvider accepting state updates, picker dialog requests, toast messages,
 * overlay dismissal requests, and host-info queries from registered third-party plugin APKs.
 *
 * Strictly isolated from `PrivilegedBinderProvider`: exposes zero root/system privileges and
 * verifies [getCallingPackage] ownership for every mutation.
 */
class BdsaPluginProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: return Bundle().apply {
            putBoolean(BdsaPluginContract.EXTRA_OK, false)
            putString(BdsaPluginContract.EXTRA_ERROR, "no_context")
        }
        val registry = PluginRegistry.getInstance(ctx)
        return registry.handleProviderCall(
            callingPackage = callingPackage,
            method = method,
            extras = extras
        )
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
