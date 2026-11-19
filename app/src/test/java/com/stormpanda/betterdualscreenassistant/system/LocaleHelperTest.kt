package com.stormpanda.betterdualscreenassistant.system

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocaleHelperTest {

    @Test
    fun `matchLocaleTag distinguishes regional variants like zh-TW and zh-CN without prefix collisions`() {
        val bilingualChinese = listOf("en", "zh-TW", "zh-CN")

        assertEquals("en", AppLanguage.matchLocaleTag(Locale.forLanguageTag("en"), bilingualChinese))
        assertEquals("en", AppLanguage.matchLocaleTag(Locale.forLanguageTag("en-US"), bilingualChinese))
        assertEquals("zh-TW", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-TW"), bilingualChinese))
        assertEquals("zh-TW", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-Hant-TW"), bilingualChinese))
        assertEquals("zh-TW", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-Hant"), bilingualChinese))
        assertEquals("zh-CN", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-CN"), bilingualChinese))
        assertEquals("zh-CN", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-Hans-CN"), bilingualChinese))
        assertEquals("zh-CN", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-Hans"), bilingualChinese))

        // zh-CN must NOT spuriously match zh-TW when only zh-TW is bundled
        val onlyTraditionalChinese = listOf("en", "zh-TW")
        assertNull(AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-CN"), onlyTraditionalChinese))
        assertNull(AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-Hans"), onlyTraditionalChinese))
        assertEquals("zh-TW", AppLanguage.matchLocaleTag(Locale.forLanguageTag("zh-TW"), onlyTraditionalChinese))
        assertNull(AppLanguage.matchLocaleTag(Locale.forLanguageTag("de-DE"), onlyTraditionalChinese))
    }

    @Test
    fun `matchLocaleTag normalizes legacy Android ISO-639 codes like iw and in to modern BCP-47 tags`() {
        val candidates = listOf("en", "he", "id", "yi")
        @Suppress("DEPRECATION")
        val legacyHebrew = Locale("iw", "IL")
        @Suppress("DEPRECATION")
        val legacyIndonesian = Locale("in", "ID")
        @Suppress("DEPRECATION")
        val legacyYiddish = Locale("ji")

        assertEquals("he", AppLanguage.matchLocaleTag(legacyHebrew, candidates))
        assertEquals("id", AppLanguage.matchLocaleTag(legacyIndonesian, candidates))
        assertEquals("yi", AppLanguage.matchLocaleTag(legacyYiddish, candidates))
    }

    @Test
    fun `fromLocaleList resolves empty null English and unsupported tags cleanly`() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocaleList(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocaleList(LocaleList.getEmptyLocaleList()))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocaleList(LocaleList.forLanguageTags("en")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLocaleList(LocaleList.forLanguageTags("en-US")))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLocaleList(LocaleList.forLanguageTags("ja-JP")))
    }

    @Test
    fun `hasSelectableTranslations is false while only English is bundled`() {
        assertFalse(AppLanguage.hasSelectableTranslations)
    }

    @Test
    fun `LocaleHelper delegates currentLanguage and applyLanguage to Android 13 LocaleManager`() {
        val mockLocaleManager = mockk<LocaleManager>(relaxed = true)
        val mockContext = mockk<Context>()
        every { mockContext.getSystemService(LocaleManager::class.java) } returns mockLocaleManager
        every { mockLocaleManager.applicationLocales } returns LocaleList.forLanguageTags("en")

        assertEquals(AppLanguage.ENGLISH, LocaleHelper.currentLanguage(mockContext))

        LocaleHelper.applyLanguage(mockContext, AppLanguage.SYSTEM)
        verify(exactly = 1) {
            mockLocaleManager.applicationLocales = LocaleList.getEmptyLocaleList()
        }

        LocaleHelper.applyLanguage(mockContext, AppLanguage.ENGLISH)
        verify(exactly = 1) {
            mockLocaleManager.applicationLocales = LocaleList.forLanguageTags("en")
        }
    }
}
