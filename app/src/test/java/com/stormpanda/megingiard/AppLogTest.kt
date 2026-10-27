package com.stormpanda.megingiard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLogTest {

    @After
    fun tearDown() {
        AppLog.level = AppLog.Level.WARN
    }

    @Test
    fun `AppLog Level enum values have expected priorities`() {
        assertEquals(2, AppLog.Level.VERBOSE.priority)
        assertEquals(3, AppLog.Level.DEBUG.priority)
        assertEquals(4, AppLog.Level.INFO.priority)
        assertEquals(5, AppLog.Level.WARN.priority)
        assertEquals(6, AppLog.Level.ERROR.priority)
        assertEquals(Int.MAX_VALUE, AppLog.Level.NONE.priority)
    }

    @Test
    fun `AppLog level updates levelFlow and executes log methods safely across all levels`() {
        val ex = RuntimeException("test exception")

        AppLog.level = AppLog.Level.ERROR
        assertEquals(AppLog.Level.ERROR, AppLog.levelFlow.value)
        AppLog.e("TestTag", "error msg")
        AppLog.e("TestTag", "error with throwable", ex)
        AppLog.w("TestTag", "filtered warn")
        AppLog.i("TestTag", "filtered info")
        AppLog.d("TestTag", "filtered debug")
        AppLog.v("TestTag", "filtered verbose")

        AppLog.level = AppLog.Level.VERBOSE
        assertEquals(AppLog.Level.VERBOSE, AppLog.levelFlow.value)
        AppLog.w("TestTag", "warn msg")
        AppLog.i("TestTag", "info msg")
        AppLog.d("TestTag", "debug msg")
        AppLog.v("TestTag", "verbose msg")

        AppLog.level = AppLog.Level.NONE
        assertEquals(AppLog.Level.NONE, AppLog.levelFlow.value)
        AppLog.e("TestTag", "suppressed error", ex)
    }
}
