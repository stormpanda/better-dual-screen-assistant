package com.stormpanda.betterdualscreenassistant.system

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.stormpanda.betterdualscreenassistant.R
import java.util.Locale

/**
 * Supported in-app language overrides backed by Android 13+ [LocaleManager] per-app locales.
 *
 * Add new entries here alongside `app/src/main/res/values-<qualifier>/strings.xml` and
 * `app/src/main/res/xml/locales_config.xml`. Once more than one concrete locale is registered,
 * [hasSelectableTranslations] becomes `true` and the in-app Language selector appears
 * automatically in General Settings.
 */
enum class AppLanguage(
    val tag: String?,
    val nativeLabel: String
) {
    SYSTEM(tag = null, nativeLabel = ""),
    ENGLISH(tag = "en", nativeLabel = "English");

    fun displayLabel(context: Context): String =
        if (this == SYSTEM) context.getString(R.string.language_system_default) else nativeLabel

    companion object {
        /**
         * True when at least two concrete languages are bundled so an in-app switcher is useful.
         */
        val hasSelectableTranslations: Boolean
            get() = entries.count { it != SYSTEM } > 1

        fun fromLocaleList(locales: LocaleList?): AppLanguage {
            if (locales == null || locales.isEmpty) return SYSTEM
            val primary = locales.get(0) ?: return SYSTEM
            return matchLocale(primary)
        }

        internal fun matchLocale(
            locale: Locale,
            candidates: List<AppLanguage> = entries
        ): AppLanguage {
            val taggedCandidates = candidates.filter { it.tag != null }
            val matchedTag = matchLocaleTag(locale, taggedCandidates.mapNotNull { it.tag })
                ?: return SYSTEM
            return taggedCandidates.firstOrNull { it.tag.equals(matchedTag, ignoreCase = true) }
                ?: SYSTEM
        }

        /**
         * Matches [locale] against [candidateTags] using exact BCP-47 tag, language+region,
         * and language+script precedence before falling back to a regionless language tag.
         *
         * Prevents regional variants such as `zh-TW` and `zh-CN` from colliding under a
         * naive `startsWith("zh")` prefix check.
         */
        internal fun matchLocaleTag(
            locale: Locale,
            candidateTags: Collection<String>
        ): String? {
            if (candidateTags.isEmpty()) return null
            val requestedTag = locale.toLanguageTag()
            // Android ART still returns legacy ISO-639 codes ("iw", "in", "ji") from locale.language,
            // while toLanguageTag() normalizes them to modern BCP-47 primary subtags ("he", "id", "yi").
            val normalizedLanguage = requestedTag
                .substringBefore('-')
                .takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }
                ?: locale.language

            // 1. Exact BCP-47 tag match (e.g., "zh-TW" == "zh-TW", "en" == "en")
            candidateTags.firstOrNull { it.equals(requestedTag, ignoreCase = true) }?.let { return it }

            // 2. Language + Country match (e.g., "zh-Hant-TW" -> "zh-TW", "zh-Hans-CN" -> "zh-CN")
            if (locale.country.isNotEmpty()) {
                val langCountry = "$normalizedLanguage-${locale.country}"
                candidateTags.firstOrNull { it.equals(langCountry, ignoreCase = true) }?.let { return it }
            }

            // 3. Language + Script match (e.g., "zh-Hant" -> "zh-Hant" or "zh-TW")
            if (locale.script.isNotEmpty()) {
                val langScript = "$normalizedLanguage-${locale.script}"
                candidateTags.firstOrNull { it.equals(langScript, ignoreCase = true) }?.let { return it }
                if (normalizedLanguage.equals("zh", ignoreCase = true)) {
                    when {
                        locale.script.equals("Hant", ignoreCase = true) -> {
                            candidateTags.firstOrNull {
                                it.equals("zh-TW", ignoreCase = true) || it.equals("zh-HK", ignoreCase = true)
                            }?.let { return it }
                        }
                        locale.script.equals("Hans", ignoreCase = true) -> {
                            candidateTags.firstOrNull {
                                it.equals("zh-CN", ignoreCase = true) || it.equals("zh-SG", ignoreCase = true)
                            }?.let { return it }
                        }
                    }
                }
            }

            // 4. Bare language fallback ONLY against regionless/scriptless tags (e.g., "en-US" -> "en")
            return candidateTags.firstOrNull { candidate ->
                !candidate.contains('-') && candidate.equals(normalizedLanguage, ignoreCase = true)
            }
        }
    }
}

object LocaleHelper {
    fun currentLanguage(context: Context): AppLanguage {
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return AppLanguage.SYSTEM
        return AppLanguage.fromLocaleList(localeManager.applicationLocales)
    }

    fun applyLanguage(context: Context, language: AppLanguage) {
        val localeManager = context.getSystemService(LocaleManager::class.java) ?: return
        localeManager.applicationLocales = if (language.tag == null) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language.tag)
        }
    }
}
