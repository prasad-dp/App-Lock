package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(db.lockedAppDao(), db.intruderAlertDao())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("App Locker", appName)
    }

    @Test
    fun `test individual app lock and unlock lifecycle`() = runBlocking {
        // 1. Verify app is not locked initially
        assertFalse(repository.isAppLocked("com.instagram.android"))

        // 2. Lock the app
        repository.lockApp("com.instagram.android", "Instagram")

        // 3. Verify it is locked now
        assertTrue(repository.isAppLocked("com.instagram.android"))

        // 4. Trace the list of locked apps
        val lockedList = repository.allLockedAppsStateFlow.first()
        assertEquals(1, lockedList.size)
        assertEquals("com.instagram.android", lockedList[0].packageName)
        assertEquals("Instagram", lockedList[0].appName)

        // 5. Unlock the app
        repository.unlockApp("com.instagram.android")

        // 6. Verify it is unlocked
        assertFalse(repository.isAppLocked("com.instagram.android"))
    }
}
