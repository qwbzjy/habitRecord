# 习惯打卡 (HabitRecord)

一款本地优先的 Android 习惯打卡应用：创建习惯、按自己的节奏打卡，用积分激励坚持，可兑换自定义奖励，并支持数据备份与恢复。

## 功能概览

- **今日**：当天待打卡习惯一览，一键完成 / 跳过，支持补打卡
- **习惯**：新建、编辑习惯，三种频率可选
  - 每天 (DAILY)
  - 每周固定星期几 (WEEKDAYS，如"周一三五")
  - 每周 N 次灵活打卡 (TIMES_PER_WEEK，按自然周统计达标)
- **奖励**：自定义奖励与所需积分，积分足够即可兑换
- **统计**：连续打卡天数 (Streak) 与达标情况
- **积分**：打卡得分、逾期/未达标扣分 (惰性结算)、兑换扣分、手工校准，全部记入积分流水，不重不漏
- **备份**：导出 JSON 备份文件；导入支持"整体替换"和"按名称合并"两种方式

## 技术栈

- 语言：Kotlin 2.3，UI 采用 Jetpack Compose + Material 3
- 架构：单模块分层 (ui / feature / domain / data / di)，手写依赖容器 (AppContainer)
- 持久化：Room (KSP 编译，schema 导出至 `app/schemas`)
- 导航：Navigation Compose，底部四个 Tab + 习惯编辑页
- 序列化：kotlinx.serialization (备份文件格式 BackupV1)
- 测试：JUnit 单元测试覆盖 FrequencyPolicy、StreakCalculator、备份编解码与合并

## 构建要求

- JDK 21 (构建前需 `export JAVA_HOME` 指向 JDK 21)
- Android SDK，compileSdk 36 / targetSdk 35 / minSdk 26
- AGP 8.13，Gradle Wrapper 自带

## 常用命令

```bash
# 单元测试
./gradlew test

# Debug 构建
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug
```

包名：`com.wenbo.habitrecord`
