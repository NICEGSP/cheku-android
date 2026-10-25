package com.cheku.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val databaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate14To15AddsRoadFeeColumn() {
        helper.createDatabase(databaseName, 14).close()

        helper.runMigrationsAndValidate(
            databaseName,
            15,
            true,
            *MIGRATIONS_TO_15
        ).use { database ->
            database.query(
                "SELECT name FROM pragma_table_info('mileage_records') WHERE name='roadFeeAmount'"
            ).use { cursor ->
                check(cursor.moveToFirst())
            }
        }
    }
}
