package com.stormpanda.betterdualscreenassistant.system

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Direct-Boot-safe [SharedPreferences] accessor for `android:directBootAware="true"` components
 * (`BetterDualScreenApp`, `BootReceiver` on `ACTION_LOCKED_BOOT_COMPLETED`, `AssistantForegroundService`).
 *
 * - While the user is **unlocked** (`UserManager.isUserUnlocked == true`), reads and writes go
 *   directly to credential-encrypted storage (`context.getSharedPreferences`) while mirroring
 *   all mutations to device-protected storage (`context.createDeviceProtectedStorageContext()`).
 * - During **Direct Boot** (before first PIN/pattern/password unlock, where credential-encrypted
 *   `getSharedPreferences` throws `IllegalStateException`), reads and writes transparently fall back
 *   to the mirrored device-protected storage and seamlessly transition to credential-encrypted
 *   storage once the user unlocks.
 */
object DirectBootPrefs {

    private const val KEY_DIRTY_DURING_DIRECT_BOOT = "__bdsa_direct_boot_dirty__"

    fun isUserUnlocked(context: Context?): Boolean {
        if (context == null) return false
        return try {
            val um = context.getSystemService(UserManager::class.java)
            um?.isUserUnlocked != false
        } catch (_: Throwable) {
            true
        }
    }

    fun open(context: Context, name: String): SharedPreferences {
        val baseContext = context.applicationContext ?: context
        return MirroringDirectBootSharedPreferences(baseContext, name)
    }

    private class MirroringDirectBootSharedPreferences(
        private val context: Context,
        private val name: String
    ) : SharedPreferences {

        private val lock = Any()

        @Volatile
        private var unlockedSynced = false

        @Volatile
        private var cachedCePrefs: SharedPreferences? = null

        private val fallbackMemoryPrefs by lazy { InMemorySharedPreferences() }
        private val registeredListeners =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>()))

        private fun openDeviceProtectedOrNull(): SharedPreferences? {
            return try {
                val deCtx = if (context.isDeviceProtectedStorage) {
                    context
                } else {
                    context.createDeviceProtectedStorageContext() ?: return null
                }
                deCtx.getSharedPreferences(name, Context.MODE_PRIVATE)
            } catch (_: Throwable) {
                null
            }
        }

        private data class StoreResolution(
            val primary: SharedPreferences,
            val mirror: SharedPreferences?,
            val writingWhileLocked: Boolean
        )

        private fun resolveStores(): StoreResolution {
            if (isUserUnlocked(context)) {
                try {
                    val ce = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    val de = if (context.isDeviceProtectedStorage) null else openDeviceProtectedOrNull()
                    if (!unlockedSynced) {
                        synchronized(lock) {
                            if (!unlockedSynced) {
                                if (de != null && de !== ce) {
                                    val dirtyFromLockedBoot = de.getBoolean(KEY_DIRTY_DURING_DIRECT_BOOT, false)
                                    if (dirtyFromLockedBoot) {
                                        val deMap = HashMap(de.all).apply {
                                            remove(KEY_DIRTY_DURING_DIRECT_BOOT)
                                        }
                                        copyMapToPrefs(deMap, ce, clearFirst = false)
                                        de.edit().remove(KEY_DIRTY_DURING_DIRECT_BOOT).apply()
                                    }
                                    val ceMap = HashMap(ce.all).apply {
                                        remove(KEY_DIRTY_DURING_DIRECT_BOOT)
                                    }
                                    copyMapToPrefs(ceMap, de, clearFirst = true)
                                }
                                if (cachedCePrefs !== ce) {
                                    cachedCePrefs = ce
                                    synchronized(registeredListeners) {
                                        for (listener in registeredListeners) {
                                            ce.registerOnSharedPreferenceChangeListener(listener)
                                        }
                                    }
                                }
                                unlockedSynced = true
                            }
                        }
                    }
                    return StoreResolution(
                        primary = ce,
                        mirror = if (de !== ce) de else null,
                        writingWhileLocked = false
                    )
                } catch (_: IllegalStateException) {
                    // Fall through to device-protected storage while CE storage is still locked.
                } catch (_: Throwable) {
                }
            }
            val de = openDeviceProtectedOrNull()
            return StoreResolution(
                primary = de ?: fallbackMemoryPrefs,
                mirror = null,
                writingWhileLocked = de != null
            )
        }

        override fun getAll(): MutableMap<String, *> =
            HashMap(resolveStores().primary.all).apply { remove(KEY_DIRTY_DURING_DIRECT_BOOT) }

        override fun getString(key: String?, defValue: String?): String? =
            resolveStores().primary.getString(key, defValue)

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            resolveStores().primary.getStringSet(key, defValues)

        override fun getInt(key: String?, defValue: Int): Int =
            resolveStores().primary.getInt(key, defValue)

        override fun getLong(key: String?, defValue: Long): Long =
            resolveStores().primary.getLong(key, defValue)

        override fun getFloat(key: String?, defValue: Float): Float =
            resolveStores().primary.getFloat(key, defValue)

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            resolveStores().primary.getBoolean(key, defValue)

        override fun contains(key: String?): Boolean =
            key != KEY_DIRTY_DURING_DIRECT_BOOT && resolveStores().primary.contains(key)

        override fun edit(): SharedPreferences.Editor {
            val resolved = resolveStores()
            return MirroringEditor(
                primary = resolved.primary.edit(),
                mirror = resolved.mirror?.edit(),
                markLockedDirty = resolved.writingWhileLocked
            )
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener == null) return
            registeredListeners.add(listener)
            val resolved = resolveStores()
            resolved.primary.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener == null) return
            registeredListeners.remove(listener)
            cachedCePrefs?.unregisterOnSharedPreferenceChangeListener(listener)
            openDeviceProtectedOrNull()?.unregisterOnSharedPreferenceChangeListener(listener)
        }

        private fun copyMapToPrefs(
            source: Map<String, *>,
            target: SharedPreferences,
            clearFirst: Boolean
        ) {
            try {
                val editor = target.edit()
                if (clearFirst) editor.clear()
                for ((k, v) in source) {
                    when (v) {
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Float -> editor.putFloat(k, v)
                        is String -> editor.putString(k, v)
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            editor.putStringSet(k, v as Set<String>)
                        }
                    }
                }
                editor.apply()
            } catch (_: Throwable) {
            }
        }
    }

    private class MirroringEditor(
        private val primary: SharedPreferences.Editor,
        private val mirror: SharedPreferences.Editor?,
        private val markLockedDirty: Boolean
    ) : SharedPreferences.Editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            primary.putString(key, value)
            mirror?.putString(key, value)
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            primary.putStringSet(key, values)
            mirror?.putStringSet(key, values)
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            primary.putInt(key, value)
            mirror?.putInt(key, value)
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            primary.putLong(key, value)
            mirror?.putLong(key, value)
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            primary.putFloat(key, value)
            mirror?.putFloat(key, value)
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            primary.putBoolean(key, value)
            mirror?.putBoolean(key, value)
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            primary.remove(key)
            mirror?.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            primary.clear()
            mirror?.clear()
            return this
        }

        override fun commit(): Boolean {
            if (markLockedDirty) {
                primary.putBoolean(KEY_DIRTY_DURING_DIRECT_BOOT, true)
            }
            val ok = primary.commit()
            mirror?.apply()
            return ok
        }

        override fun apply() {
            if (markLockedDirty) {
                primary.putBoolean(KEY_DIRTY_DURING_DIRECT_BOOT, true)
            }
            primary.apply()
            mirror?.apply()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = ConcurrentHashMap<String, Any>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? =
            if (key != null) map[key] as? String ?: defValue else defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            if (key != null) (map[key] as? Set<String>)?.toMutableSet() ?: defValues else defValues

        override fun getInt(key: String?, defValue: Int): Int =
            if (key != null) map[key] as? Int ?: defValue else defValue

        override fun getLong(key: String?, defValue: Long): Long =
            if (key != null) map[key] as? Long ?: defValue else defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            if (key != null) map[key] as? Float ?: defValue else defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            if (key != null) map[key] as? Boolean ?: defValue else defValue

        override fun contains(key: String?): Boolean = key != null && map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val staged = HashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) staged[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply {
                if (key != null) staged[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) staged[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) staged[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) staged[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) staged[key] = value }
            override fun remove(key: String?) = apply { if (key != null) staged[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                for ((k, v) in staged) {
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }
}
