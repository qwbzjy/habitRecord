package com.wenbo.habitrecord.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 备份 JSON 编解码（纯函数，便于单测）。 */
object BackupCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: BackupV1): String = json.encodeToString(backup)

    /** 解析失败（非法 JSON / 版本过新）返回 null。 */
    fun decode(text: String): BackupV1? = try {
        val parsed = json.decodeFromString<BackupV1>(text)
        if (parsed.version > BackupV1.BACKUP_VERSION) null else parsed
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
