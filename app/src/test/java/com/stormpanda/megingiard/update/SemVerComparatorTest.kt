package com.stormpanda.megingiard.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerComparatorTest {

    @Test
    fun `isUpdateAvailable returns true when remote major version is greater`() {
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0", "2.0.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("v1.9.9", "v2.0.0"))
    }

    @Test
    fun `isUpdateAvailable returns true when remote minor version is greater`() {
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0", "1.1.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.2.9", "1.3.0"))
    }

    @Test
    fun `isUpdateAvailable returns true when remote patch version is greater`() {
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.1"))
        assertTrue(SemVerComparator.isUpdateAvailable("0.2.0", "0.2.1"))
    }

    @Test
    fun `isUpdateAvailable returns false when versions are equal`() {
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("v1.2.3", "V1.2.3"))
        assertFalse(SemVerComparator.isUpdateAvailable("  v1.2.3  ", "1.2.3"))
    }

    @Test
    fun `isUpdateAvailable returns false when remote version is older`() {
        assertFalse(SemVerComparator.isUpdateAvailable("2.0.0", "1.9.9"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.2.0", "1.1.9"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.2", "1.0.1"))
    }

    @Test
    fun `isUpdateAvailable handles pre-release suffixes correctly`() {
        // Current is pre-release, remote is stable of the same numeric version -> update available
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0-SNAPSHOT", "1.0.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("v0.2.0-SNAPSHOT", "v0.2.0"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0-rc1", "1.0.0"))

        // Current is stable, remote is pre-release of the same numeric version -> no update
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.0-SNAPSHOT"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.0-beta.1"))

        // Both are pre-release of the same numeric version -> false
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0-SNAPSHOT", "1.0.0-SNAPSHOT"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0-alpha", "1.0.0-beta"))

        // Remote has higher numeric version even with pre-release suffix -> true
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0", "1.0.1-SNAPSHOT"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0-SNAPSHOT", "1.0.1-SNAPSHOT"))
    }

    @Test
    fun `isUpdateAvailable ignores build metadata after plus sign`() {
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0+20260101", "1.0.0+20261231"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0-SNAPSHOT+build1", "1.0.0+build2"))
        assertTrue(SemVerComparator.isUpdateAvailable("1.0.0+100", "1.0.1+1"))
    }

    @Test
    fun `isUpdateAvailable handles different segment lengths and non-numeric segments`() {
        assertTrue(SemVerComparator.isUpdateAvailable("1.0", "1.0.1"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0.0", "1.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("1.0", "1.0.0"))
        assertFalse(SemVerComparator.isUpdateAvailable("", ""))
        assertTrue(SemVerComparator.isUpdateAvailable("invalid", "0.0.1"))
    }
}
