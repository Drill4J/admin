package com.epam.drill.admin.writer.rawdata.table

object GroupSettingsTable : StringIdTable("raw_data.group_settings", "group_id") {
    val retentionPeriodDays = integer("retention_period_days").nullable()
}
