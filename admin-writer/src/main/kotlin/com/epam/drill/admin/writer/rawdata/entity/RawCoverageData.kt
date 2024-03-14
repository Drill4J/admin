package com.epam.drill.admin.writer.rawdata.entity

import com.epam.drill.plugins.test2code.common.api.Probes

data class RawCoverageData(
    val instanceId: String,
    val className: String,
    val testId: String,
    val probes: Probes
)