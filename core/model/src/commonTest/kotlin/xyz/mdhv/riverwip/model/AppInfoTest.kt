package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInfoTest {
    @Test
    fun packageBaseMatchesGradleProperty() {
        // The package base is centralized; this pins the runtime constant to the
        // final Play Console package so a partial rename is caught immediately.
        assertEquals("dev.asystemofcells.nooz", AppInfo.PACKAGE_BASE)
    }

    @Test
    fun workingNameIsRegisterPlaceholder() {
        assertTrue(AppInfo.WORKING_NAME.isNotBlank())
    }
}
