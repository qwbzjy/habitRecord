package com.wenbo.habitrecord.domain.model

/** 习惯打卡频率。 */
enum class FrequencyType {
    /** 每天打卡 */
    DAILY,

    /** 每周固定星期几打卡，weekdays 存 "1,3,5"（1=周一 … 7=周日） */
    WEEKDAYS,

    /** 每周 N 次灵活打卡，任何一天都可以打，按自然周（周一~周日）统计达标情况 */
    TIMES_PER_WEEK,
}

/** 打卡状态。 */
enum class CheckInStatus(val value: Int) {
    /** 当天尚未操作 */
    PENDING(0),

    /** 已完成 */
    DONE(1),

    /** 逾期未完成（由惰性结算产生） */
    MISS(2),

    /** 主动跳过（休息日，不扣分） */
    SKIP(3);

    companion object {
        fun from(value: Int): CheckInStatus = entries.first { it.value == value }
    }
}

/** 积分流水类型。 */
enum class LedgerReason {
    /** 打卡完成得分 */
    CHECKIN,

    /** 逾期/未达标扣分 */
    MISS,

    /** 兑换奖励扣分 */
    REDEEM,

    /** 手工校准（合并导入差额等） */
    ADJUST,
}
