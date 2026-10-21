package com.stormpanda.betterdualscreenassistant.privileged

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log

/**
 * Receives the root [android.os.IBinder] from [PrivilegedHelperMain] when launched via PServerBinder.
 */
class PrivilegedBinderProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == PrivilegedHelperMain.METHOD_REGISTER_BINDER && extras != null) {
            val callingUid = Binder.getCallingUid()
            if (callingUid == 0 || callingUid == Process.SYSTEM_UID || callingUid == Process.myUid()) {
                val binder = extras.getBinder(PrivilegedHelperMain.EXTRA_BINDER)
                if (binder != null) {
                    Log.i("PrivilegedBinderProvider", "Received helper binder from uid=$callingUid")
                    PrivilegedBridge.onHelperBinderReceived(binder)
                }
            } else {
                Log.w("PrivilegedBinderProvider", "Rejected binder from unauthorized uid=$callingUid")
            }
        }
        return Bundle()
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
