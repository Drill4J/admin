package com.epam.drill.admin.writer.rawdata.route.payload

import kotlinx.serialization.Serializable

@Serializable
class CoveragePayload(
    val instanceId: String,
    val coverage: Array<SingleClassCoveragePayload>,
)

@Serializable
class SingleClassCoveragePayload(
    val classname: String,
    val testId: String,
    val probes: Array<Boolean>
)
