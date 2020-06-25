package com.epam.drill.admin.build

import com.epam.drill.common.*

fun AgentBuild.toBuildSummaryDto() = info.run {
    BuildSummaryDto(
        buildVersion = version,
        detectedAt = detectedAt,
        totalMethods = javaMethods.values.sumBy { it.count() },
        newMethods = methodChanges.count(DiffType.NEW),
        modifiedMethods = methodChanges.count(
            DiffType.MODIFIED_NAME,
            DiffType.MODIFIED_BODY,
            DiffType.MODIFIED_DESC
        ),
        unaffectedMethods = methodChanges.count(DiffType.UNAFFECTED),
        deletedMethods = methodChanges.count(DiffType.DELETED)
    )
}

private fun MethodChanges.count(vararg types: DiffType): Int = types.mapNotNull(map::get).sumBy { it.count() }
