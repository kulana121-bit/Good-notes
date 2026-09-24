package com.example.data.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.drive.DriveAuthState
import com.example.data.remote.drive.DriveFolderStructure
import com.example.data.remote.drive.DriveStorageInfo
import com.example.data.remote.drive.DriveTestReport
import com.example.data.remote.drive.GoogleDriveAuthManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveServiceTest {

    private lateinit var context: Context
    private lateinit var authManager: GoogleDriveAuthManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        authManager = GoogleDriveAuthManager(context)
    }

    @Test
    fun testDriveStorageInfo_formatting() {
        val storage = DriveStorageInfo(
            usageBytes = 1024L * 1024L * 500L, // 500 MB
            limitBytes = 1024L * 1024L * 1024L * 15L, // 15 GB
            availableBytes = (1024L * 1024L * 1024L * 15L) - (1024L * 1024L * 500L),
            userDisplayName = "Test User",
            userEmail = "test@example.com"
        )

        assertEquals("500.0 MB", storage.usageFormatted)
        assertEquals("15.00 GB", storage.limitFormatted)
        assertEquals("test@example.com", storage.userEmail)
        assertEquals("Test User", storage.userDisplayName)
        assertNotNull(storage.usagePercentage)
        assertTrue(storage.usagePercentage!! > 0f)
        assertTrue(storage.usagePercentage!! < 0.1f) // ~3.3%
    }

    @Test
    fun testDriveStorageInfo_unlimitedAccount() {
        val unlimitedStorage = DriveStorageInfo(
            usageBytes = 2048L,
            limitBytes = null,
            availableBytes = null,
            userDisplayName = "Enterprise User",
            userEmail = "enterprise@example.com"
        )

        assertEquals("2.0 KB", unlimitedStorage.usageFormatted)
        assertEquals("Unlimited", unlimitedStorage.limitFormatted)
        assertEquals("Available", unlimitedStorage.availableFormatted)
        assertNull(unlimitedStorage.usagePercentage)
    }

    @Test
    fun testDriveFolderStructure_properties() {
        val structure = DriveFolderStructure(
            rootFolderId = "root_notes_123",
            backupFolderId = "backup_456",
            documentsFolderId = "docs_789"
        )

        assertEquals("root_notes_123", structure.rootFolderId)
        assertEquals("backup_456", structure.backupFolderId)
        assertEquals("docs_789", structure.documentsFolderId)
    }

    @Test
    fun testDriveAuthState_transitions() {
        val disconnected: DriveAuthState = DriveAuthState.Disconnected
        assertTrue(disconnected is DriveAuthState.Disconnected)

        val connected: DriveAuthState = DriveAuthState.Connected(
            accountEmail = "user@gmail.com",
            accountName = "Sample User"
        )
        assertTrue(connected is DriveAuthState.Connected)
        assertEquals("user@gmail.com", (connected as DriveAuthState.Connected).accountEmail)

        val error: DriveAuthState = DriveAuthState.Error("Network failure")
        assertTrue(error is DriveAuthState.Error)
        assertEquals("Network failure", (error as DriveAuthState.Error).message)
    }

    @Test
    fun testDriveTestReport_successAndFailure() {
        val successReport = DriveTestReport(
            success = true,
            rootFolderId = "root_id",
            backupFolderId = "backup_id",
            documentsFolderId = "docs_id",
            storageInfo = DriveStorageInfo(100L, 1000L, 900L),
            testFileUploaded = true,
            testFileDownloaded = true,
            testFileDeleted = true,
            message = "Integration test passed"
        )

        assertTrue(successReport.success)
        assertTrue(successReport.testFileUploaded)
        assertTrue(successReport.testFileDownloaded)
        assertTrue(successReport.testFileDeleted)
        assertEquals("root_id", successReport.rootFolderId)

        val failedReport = DriveTestReport(
            success = false,
            message = "Authentication required"
        )
        assertFalse(failedReport.success)
        assertFalse(failedReport.testFileUploaded)
    }

    @Test
    fun testAuthManager_checkAuthorizationInitialState() {
        val state = authManager.checkDriveAuthorization()
        // In local test context without prior sign-in, it should be Disconnected
        assertEquals(DriveAuthState.Disconnected, state)
    }

    @Test
    fun testAuthManager_getAuthorizationIntent() {
        val intent = authManager.getAuthorizationIntent("testuser@gmail.com")
        assertNotNull(intent)
    }

    @Test
    fun testAuthManager_handleAuthorizationResult_nullData() {
        val result = authManager.handleAuthorizationResult(null)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull()?.message)
    }
}
