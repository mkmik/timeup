package pub.mkm.timeup.db

import android.content.Context
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import java.util.UUID

/** Owns the SQLite file `timeup.db`. The file itself is the export format. */
class Database private constructor(
    private val driver: AndroidSqliteDriver,
    val db: TimeUpDatabase,
) {
    val queries: TimeUpQueries get() = db.timeUpQueries

    /** Folds the WAL into the main file so a plain copy of `timeup.db` is complete. */
    fun checkpoint() {
        driver.executeQuery(null, "PRAGMA wal_checkpoint(TRUNCATE)", { QueryResult.Unit }, 0).value
    }

    fun getMeta(key: String): String? = queries.selectMeta(key).executeAsOneOrNull()

    fun setMeta(key: String, value: String) = queries.upsertMeta(key, value)

    /** Stable random id for this install; created on first run. */
    fun deviceId(): String {
        getMeta(META_DEVICE_ID)?.let { return it }
        val id = UUID.randomUUID().toString()
        db.transaction {
            setMeta(META_DEVICE_ID, id)
            queries.upsertDevice(id, "phone", "${Build.MANUFACTURER} ${Build.MODEL}")
        }
        return id
    }

    companion object {
        const val NAME = "timeup.db"
        const val META_DEVICE_ID = "device_id"

        fun open(context: Context): Database {
            val driver = AndroidSqliteDriver(
                schema = TimeUpDatabase.Schema,
                context = context,
                name = NAME,
                callback = object : AndroidSqliteDriver.Callback(TimeUpDatabase.Schema) {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.enableWriteAheadLogging()
                    }
                },
            )
            return Database(driver, TimeUpDatabase(driver))
        }
    }
}
