package com.epam.drill.admin.build

import com.epam.drill.common.*

fun AgentBuild.toBuildSummaryDto() = info.methodChanges.let { methodChanges ->
    val deleted = methodChanges.diffCount(DiffType.DELETED)
    BuildSummaryDto(
        buildVersion = info.version,
        detectedAt = detectedAt,
        totalMethods = methodChanges.map.values.flatten().count() - deleted,
        newMethods = methodChanges.diffCount(DiffType.NEW),
        modifiedMethods = methodChanges.diffCount(
            DiffType.MODIFIED_NAME,
            DiffType.MODIFIED_BODY,
            DiffType.MODIFIED_DESC
        ),
        unaffectedMethods = methodChanges.diffCount(DiffType.UNAFFECTED),
        deletedMethods = deleted
    )
}

private fun MethodChanges.diffCount(vararg types: DiffType) = types.mapNotNull { map[it]?.count() }.sum()
