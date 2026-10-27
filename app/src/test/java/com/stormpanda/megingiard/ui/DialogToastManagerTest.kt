package com.stormpanda.megingiard.ui

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogToastManagerTest {

    @Before
    fun setUp() {
        DialogToastManager.clear()
    }

    @After
    fun tearDown() {
        DialogToastManager.clear()
    }

    @Test
    fun `show sets currentToast with message and isError flag`() {
        assertNull(DialogToastManager.currentToast.value)

        DialogToastManager.show("Saved settings", durationMs = 2500L, isError = false)
        val first = DialogToastManager.currentToast.value
        assertNotNull(first)
        requireNotNull(first)
        assertEquals("Saved settings", first.message)
        assertFalse(first.isError)

        DialogToastManager.show("Failed to save", isError = true)
        val second = DialogToastManager.currentToast.value
        requireNotNull(second)
        assertEquals("Failed to save", second.message)
        assertTrue(second.isError)
    }

    @Test
    fun `showPersistent sets toast and clear resets currentToast to null`() {
        DialogToastManager.showPersistent("Applying overlay...", isError = false)
        val toast = DialogToastManager.currentToast.value
        requireNotNull(toast)
        assertEquals("Applying overlay...", toast.message)
        assertFalse(toast.isError)

        DialogToastManager.clear()
        assertNull(DialogToastManager.currentToast.value)
    }
}
