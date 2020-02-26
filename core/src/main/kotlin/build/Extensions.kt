package com.epam.drill.admin.build

import com.epam.drill.common.*

fun BuildInfo.toBuildSummaryDto() = methodChanges.run {
    val deleted = diffCount(DiffType.DELETED)
    BuildSummaryDto(
        buildVersion = buildVersion,
        totalMethods = map.values.flatten().count() - deleted,
        newMethods = diffCount(DiffType.NEW),
        modifiedMethods = diffCount(DiffType.MODIFIED_NAME, DiffType.MODIFIED_BODY, DiffType.MODIFIED_DESC),
        unaffectedMethods = diffCount(DiffType.UNAFFECTED),
        deletedMethods = deleted
    )
}

private fun MethodChanges.diffCount(vararg types: DiffType) = types.mapNotNull { map[it]?.count() }.sum()
