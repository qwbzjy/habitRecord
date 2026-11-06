package com.wenbo.habitrecord.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 积分流水：账户余额 = SUM(amount)。
 * CHECKIN/MISS 行带 habitId + date（按日期幂等去重）；周结算行 date 存该周周一的 epochDay。
 */
@Entity(
    tableName = "points_ledger",
    indices = [Index("habitId"), Index("date"), Index("createdAt")],
)
data class PointsLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 正数=获得，负数=扣除 */
    val amount: Int,
    /** 取值见 [com.wenbo.habitrecord.domain.model.LedgerReason] */
    val reason: String,
    val habitId: Long? = null,
    val rewardId: Long? = null,
    /** 关联打卡日 epochDay；REDEEM/ADJUST 为 null */
    val date: Long? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
