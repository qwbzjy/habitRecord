package com.wenbo.habitrecord.di

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.wenbo.habitrecord.data.backup.BackupManager
import com.wenbo.habitrecord.data.db.AppDatabase
import com.wenbo.habitrecord.data.repository.CheckInService
import java.io.InputStream
import java.io.OutputStream

/** 手动 DI 容器：依赖是少量封闭集合，全部单例，在 Application 中构造一次。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.create(appContext)
    val checkInService = CheckInService(database)
    val backupManager = BackupManager(database)

    /** SAF 流收口：备份读写的 uri 授权在会话内一次性消费，不做持久化。 */
    fun openBackupInput(uri: Uri): InputStream? = appContext.contentResolver.openInputStream(uri)

    fun openBackupOutput(uri: Uri): OutputStream? =
        appContext.contentResolver.openOutputStream(uri, "wt")
}

/** 在 MainActivity setContent 时提供，Composable 层通过它获取容器。 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

/** 备份文件 MIME 类型。 */
const val BACKUP_MIME = "application/json"
