package com.epam.drill.admin.build

import com.epam.drill.common.*

fun BuildInfo.toBuildSummaryDto() = run {
   val changes = methodChanges.map
   val deletedMethodsCount = changes[DiffType.DELETED]?.count() ?: 0
   BuildSummaryDto(
      buildVersion = buildVersion,
      totalMethods = changes.values.flatten().count() - deletedMethodsCount,
      newMethods = changes[DiffType.NEW]?.count() ?: 0,
      modifiedMethods = (changes[DiffType.MODIFIED_NAME]?.count() ?: 0) +
              (changes[DiffType.MODIFIED_BODY]?.count() ?: 0) +
              (changes[DiffType.MODIFIED_DESC]?.count() ?: 0),
      unaffectedMethods = changes[DiffType.UNAFFECTED]?.count() ?: 0,
      deletedMethods = deletedMethodsCount
   )
}
