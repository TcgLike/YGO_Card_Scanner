package com.ygocardscanner.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.entity.CatalogUpdateState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogUpdateStateDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertKeepsIndependentSourceStateAndClearsSafeErrorOnSuccess() = runBlocking {
        val dao = database.catalogUpdateStateDao()
        dao.upsert(
            CatalogUpdateState(
                sourceId = "development",
                phase = "failed",
                lastAttemptAtEpochMillis = 100L,
                lastSuccessAtEpochMillis = null,
                lastFailureAtEpochMillis = 101L,
                safeErrorText = "Catalog update unavailable",
            ),
        )
        dao.upsert(
            CatalogUpdateState(
                sourceId = "secondary",
                phase = "idle",
                lastAttemptAtEpochMillis = null,
                lastSuccessAtEpochMillis = null,
                lastFailureAtEpochMillis = null,
                safeErrorText = null,
            ),
        )
        dao.upsert(
            CatalogUpdateState(
                sourceId = "development",
                phase = "succeeded",
                lastAttemptAtEpochMillis = 200L,
                lastSuccessAtEpochMillis = 201L,
                lastFailureAtEpochMillis = 101L,
                safeErrorText = null,
            ),
        )

        val development = requireNotNull(dao.get("development"))
        assertEquals("succeeded", development.phase)
        assertEquals(200L, development.lastAttemptAtEpochMillis)
        assertEquals(201L, development.lastSuccessAtEpochMillis)
        assertEquals(101L, development.lastFailureAtEpochMillis)
        assertNull(development.safeErrorText)

        val secondary = requireNotNull(dao.get("secondary"))
        assertEquals("idle", secondary.phase)
        assertNull(secondary.lastAttemptAtEpochMillis)
    }
}

