package pub.mkm.timeup.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import pub.mkm.timeup.BuildConfig
import pub.mkm.timeup.db.Database
import pub.mkm.timeup.graph
import java.io.File
import java.time.LocalDate

/**
 * "Export database": checkpoint the WAL, copy the live SQLite file to the cache, hand it to
 * the share sheet. The copy is deleted on the next app start.
 */
object Exporter {
    private const val DIR = "export"

    fun export(context: Context): Intent {
        val g = context.graph
        g.repo.setMeta("exported_at", System.currentTimeMillis().toString())
        g.repo.setMeta("app_version", BuildConfig.VERSION_NAME)
        g.database.checkpoint()

        val src = context.getDatabasePath(Database.NAME)
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val dst = File(dir, "timeup-${LocalDate.now()}.sqlite")
        src.copyTo(dst, overwrite = true)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", dst)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, dst.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Export database").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun cleanup(context: Context) {
        File(context.cacheDir, DIR).deleteRecursively()
    }
}
