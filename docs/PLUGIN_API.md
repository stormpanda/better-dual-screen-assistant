# BDSA Third-Party Plugin API (v1)

Third-party Android apps can add quick toggles directly to Better Dual Screen Assistant (BDSA) with **no SDK dependency** and **no background service required**.

---

## How It Works

1. Your APK declares an exported `<receiver>` with `<action android:name="com.stormpanda.betterdualscreenassistant.action.PLUGIN_TILE" />` and metadata describing your plugin and its tile(s).
2. BDSA discovers installed plugins automatically. Users see your plugin under **Settings → Plugins** and opt into individual tiles under **Quick Toggles → Enable / Disable Toggles → Third-Party Plugins**.
3. When the user interacts with a tile (or opens the overlay), BDSA sends an explicit broadcast to your receiver.
4. Your app pushes tile state, native in-overlay picker dialogs, toasts, or dismiss requests back via BDSA's `ContentProvider`:
   `content://com.stormpanda.betterdualscreenassistant.plugin`

> **Android 11+ Package Visibility**: Add `<package android:name="com.stormpanda.betterdualscreenassistant" />` inside `<queries>` in your `AndroidManifest.xml` so `contentResolver.call(...)` can reach BDSA.

---

## Quick Start (Single-Tile Plugin)

### 1. `AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <queries>
        <package android:name="com.stormpanda.betterdualscreenassistant" />
    </queries>

    <application ...>
        <receiver
            android:name=".MyBdsaTileReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.stormpanda.betterdualscreenassistant.action.PLUGIN_TILE" />
            </intent-filter>

            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.API_VERSION" android:value="1" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.AUTHOR" android:value="Your Name" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.DESCRIPTION" android:value="@string/plugin_desc" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.TILE_ID" android:value="boost_mode" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.TILE_TITLE" android:value="@string/tile_boost_title" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.TILE_DESCRIPTION" android:value="@string/tile_boost_desc" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.TILE_ICON" android:resource="@drawable/ic_tile_boost" />
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.SUPPORTS_LONG_PRESS" android:value="true" />
            <!-- Optional -->
            <meta-data android:name="com.stormpanda.betterdualscreenassistant.plugin.SETTINGS_ACTIVITY" android:value=".SettingsActivity" />
        </receiver>
    </application>
</manifest>
```

### 2. `BroadcastReceiver` (Kotlin)

```kotlin
class MyBdsaTileReceiver : BroadcastReceiver() {
    companion object {
        private val BDSA_URI = Uri.parse("content://com.stormpanda.betterdualscreenassistant.plugin")
        private const val ACTION_CLICK = "com.stormpanda.betterdualscreenassistant.action.TILE_CLICK"
        private const val ACTION_LONG_CLICK = "com.stormpanda.betterdualscreenassistant.action.TILE_LONG_CLICK"
        private const val ACTION_OPTION_SELECTED = "com.stormpanda.betterdualscreenassistant.action.TILE_OPTION_SELECTED"
        private const val ACTION_REFRESH = "com.stormpanda.betterdualscreenassistant.action.TILE_REFRESH"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val tileId = intent.getStringExtra("tile_id") ?: return
        val prefs = context.getSharedPreferences("demo", Context.MODE_PRIVATE)

        when (intent.action) {
            ACTION_CLICK -> {
                val next = !prefs.getBoolean("boost_on", false)
                prefs.edit().putBoolean("boost_on", next).apply()
                pushState(context, tileId, selected = next, toast = if (next) "Boost ON" else "Boost OFF")
            }
            ACTION_LONG_CLICK -> {
                val current = prefs.getString("profile", "balanced")
                context.contentResolver.call(BDSA_URI, "show_picker", null, Bundle().apply {
                    putString("tile_id", tileId)
                    putString("dialog_title", "Boost Profile")
                    putStringArrayList("option_ids", arrayListOf("eco", "balanced", "max"))
                    putStringArrayList("option_labels", arrayListOf("Eco", "Balanced", "Max"))
                    putString("selected_option_id", current)
                })
            }
            ACTION_OPTION_SELECTED -> {
                val chosen = intent.getStringExtra("option_id") ?: return
                prefs.edit().putString("profile", chosen).apply()
                pushState(context, tileId, label = "Boost: $chosen", selected = chosen != "eco")
            }
            ACTION_REFRESH -> {
                val isOn = prefs.getBoolean("boost_on", false)
                pushState(context, tileId, selected = isOn)
            }
        }
    }

    private fun pushState(
        context: Context,
        tileId: String,
        label: String? = null,
        selected: Boolean,
        toast: String? = null
    ) {
        context.contentResolver.call(BDSA_URI, "update_tile", null, Bundle().apply {
            putString("tile_id", tileId)
            if (label != null) putString("label", label)
            putBoolean("selected", selected)
            if (toast != null) putString("toast", toast)
        })
    }
}
```

---

## Multi-Tile XML Configuration

To expose multiple tiles from one APK (up to **16 tiles**), point `META_CONFIG` to an XML resource instead of flat `<meta-data>` entries:

```xml
<receiver android:name=".MyBdsaTileReceiver" android:exported="true">
    <intent-filter>
        <action android:name="com.stormpanda.betterdualscreenassistant.action.PLUGIN_TILE" />
    </intent-filter>
    <meta-data
        android:name="com.stormpanda.betterdualscreenassistant.plugin.CONFIG"
        android:resource="@xml/bdsa_plugin" />
</receiver>
```

`res/xml/bdsa_plugin.xml`:

```xml
<bdsa-plugin
    apiVersion="1"
    author="Your Name"
    description="@string/plugin_desc"
    settingsActivity=".SettingsActivity">

    <tile
        id="boost_mode"
        title="@string/tile_boost_title"
        description="@string/tile_boost_desc"
        icon="@drawable/ic_tile_boost"
        pixelIcon="@drawable/ic_tile_boost_pixel"
        supportsLongPress="true"
        dismissOnClick="false"
        dismissOnLongClick="false"
        supportedDevices="all" />

    <tile
        id="open_tool"
        title="My Tool"
        description="Launch companion UI on top screen"
        icon="@drawable/ic_tile_tool"
        dismissOnClick="true"
        supportedDevices="ayn_thor,retroid_pocket_duo" />
</bdsa-plugin>
```

### Metadata Reference

| Attribute / Flat `<meta-data>` suffix | Type | Required | Notes |
| :--- | :--- | :--- | :--- |
| `apiVersion` / `API_VERSION` | `int` | Recommended | Must be `1`. Defaults to `1` if omitted. |
| `author` / `AUTHOR` | `string` / `@string` | No | Shown in **Settings → Plugins** (max 80 chars). |
| `description` / `DESCRIPTION` | `string` / `@string` | No | Plugin description shown in **Settings → Plugins** (max 300 chars). |
| `settingsActivity` / `SETTINGS_ACTIVITY` | `string` | No | Exported activity class (e.g. `.SettingsActivity`). Adds a **Configure** button in **Settings → Plugins**. |
| `id` / `TILE_ID` | `string` | **Yes** | Unique per package. Matches `^[a-zA-Z0-9._-]{1,64}$`. |
| `title` / `TILE_TITLE` | `string` / `@string` | **Yes** | Tile label (max 48 chars). Supports `\n` for explicit 2-line grid wrapping. |
| `description` / `TILE_DESCRIPTION` | `string` / `@string` | No | Shown in **Quick Toggles → Enable / Disable** (max 300 chars). |
| `icon` / `TILE_ICON` | `@drawable` | Recommended | Monochrome vector drawable (or alpha PNG). Falls back to default plugin icon if omitted. |
| `pixelIcon` / `TILE_PIXEL_ICON` | `@drawable` | No | Optional pixel-art monochrome icon used when BDSA's Retroid Pixel look is active. |
| `supportsLongPress` / `SUPPORTS_LONG_PRESS` | `boolean` | No | Set `true` to receive `TILE_LONG_CLICK` broadcasts. Default `false`. |
| `dismissOnClick` / `DISMISS_ON_CLICK` | `boolean` | No | Auto-close BDSA overlay immediately on tap. Default `false`. |
| `dismissOnLongClick` / `DISMISS_ON_LONG_CLICK` | `boolean` | No | Auto-close BDSA overlay immediately on long-press. Default `false`. |
| `supportedDevices` / `SUPPORTED_DEVICES` | `string` | No | Comma-separated: `all` (default), `ayn_thor`, `retroid_pocket_duo`. |

---

## Broadcasts (BDSA → Plugin)

BDSA sends explicit component broadcasts with `FLAG_INCLUDE_STOPPED_PACKAGES` (so headless plugins without a launcher icon work immediately after install).

| Action | Trigger |
| :--- | :--- |
| `com.stormpanda.betterdualscreenassistant.action.TILE_CLICK` | Short press (touch or Gamepad A). |
| `com.stormpanda.betterdualscreenassistant.action.TILE_LONG_CLICK` | Long press (only if `supportsLongPress="true"`). |
| `com.stormpanda.betterdualscreenassistant.action.TILE_OPTION_SELECTED` | User selected an option in a picker dialog. Includes `option_id`. |
| `com.stormpanda.betterdualscreenassistant.action.TILE_REFRESH` | Overlay opened or tile enabled. Refresh state via `update_tile`. |

### Standard Extras Included in Every Broadcast

| Extra | Type | Value |
| :--- | :--- | :--- |
| `api_version` | `Int` | `1` |
| `tile_id` | `String` | The tile's declared `id` |
| `option_id` | `String` | Only on `TILE_OPTION_SELECTED` |
| `device_id` | `String` | `"ayn_thor"` or `"retroid_pocket_duo"` |
| `top_display_id` | `Int` | Top display ID (`0`) |
| `bottom_display_id` | `Int` | Bottom display ID (`4` on AYN Thor, `2` on Retroid Pocket Duo) |
| `is_dark_theme` | `Boolean` | Whether BDSA's current overlay palette is dark |

---

## ContentProvider Calls (Plugin → BDSA)

Authority URI: `content://com.stormpanda.betterdualscreenassistant.plugin`

Every call returns a `Bundle` containing `ok: Boolean` (and `error: String` when `ok == false`). BDSA verifies `callingPackage` owns the target `tile_id` on every mutation.

### `update_tile`
Updates cached tile appearance and state (persisted locally so the overlay opens instantaneously next time). Any omitted field keeps its previous value.

| Extra | Type | Notes |
| :--- | :--- | :--- |
| `tile_id` | `String` | **Required.** |
| `label` | `String` | Optional dynamic label override (max 48 chars; pass `""` to reset to manifest title). |
| `selected` | `Boolean` | Optional active highlight state. |
| `enabled` | `Boolean` | Optional clickable state (`false` dims and disables the tile). |
| `icon_res` | `Int` | Optional `@drawable` res ID in your package (`0` resets to manifest icon). |
| `pixel_icon_res` | `Int` | Optional Retroid Pixel `@drawable` res ID (`0` resets). |
| `toast` | `String` | Optional short feedback toast on the bottom overlay (max 160 chars). |
| `dismiss_overlay` | `Boolean` | Optional; if `true`, closes the overlay immediately. |

### `show_picker`
Opens a native BDSA-styled modal option dialog inside the overlay (matching AYN Smooth / Retroid Pixel and D-pad navigation). Only accepted while the overlay is open and the tile is enabled.

| Extra | Type | Notes |
| :--- | :--- | :--- |
| `tile_id` | `String` | **Required.** |
| `dialog_title` | `String` | Optional header title (max 64 chars; defaults to tile title). |
| `option_ids` | `ArrayList<String>` | **Required.** Unique option IDs (1–12 items). |
| `option_labels` | `ArrayList<String>` | **Required.** Same length as `option_ids` (max 48 chars each). |
| `option_subtitles` | `ArrayList<String>` | Optional secondary line per option (max 80 chars each). |
| `option_icon_res` | `IntArray` | Optional monochrome `@drawable` res ID per option (`0` for no icon). |
| `selected_option_id` | `String` | Optional ID of the currently checked option. |
| `dismiss_overlay_on_select` | `Boolean` | If `true`, closes the entire overlay after an option is picked. Default `false`. |

### `show_toast`
Displays a short toast on the bottom screen (only accepted while the overlay is open or within 5 seconds of a tile click).
- `message: String` (max 160 chars)

### `dismiss_overlay`
Closes the BDSA overlay if open. No extras required.

### `get_host_info`
Read-only query returning:
- `ok: Boolean`, `api_version: Int`, `bdsa_version: String`, `device_id: String`, `top_display_id: Int`, `bottom_display_id: Int`, `is_plugin_enabled: Boolean`, `enabled_tile_ids: ArrayList<String>`.

---

## Icon, Label & Security Guidelines

- **Icons**: Supply **monochrome vector drawables** (`24dp × 24dp`, solid single color on transparent background) or alpha-masked PNGs. BDSA tints all tile and picker icons dynamically for Light, Dark, Retroid Pixel, and custom Megingiard accent palettes. External bitmaps are capped at `128×128 px` to protect overlay memory.
- **Labels**: Keep tile titles concise (1–2 short words fit cleanly inside the 2×4 quick-toggle grid). Use `@string/...` resources in your manifest/XML so BDSA resolves them in the user's selected language automatically.
- **Display targeting**: To launch an Activity on the top screen, use `ActivityOptions.makeBasic().setLaunchDisplayId(topDisplayId)` from the broadcast extras.
- **Rate limits & isolation**: Provider calls are capped at 20 calls/second per package. Plugins run strictly in their own Android sandbox and never inherit BDSA's privileged/root access.

---

## Testing via ADB

Install your plugin APK, enable its tile in **BDSA Settings → Quick Toggles → Enable / Disable Toggles**, or test your receiver directly:

```bash
adb shell am broadcast \
  -a com.stormpanda.betterdualscreenassistant.action.TILE_CLICK \
  -n com.example.myplugin/.MyBdsaTileReceiver \
  --es tile_id boost_mode \
  --ei api_version 1 \
  --es device_id ayn_thor \
  --ei top_display_id 0 \
  --ei bottom_display_id 4
```
