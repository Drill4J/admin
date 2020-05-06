package com.epam.drill.admin.admindata

import org.jacoco.core.analysis.*
import org.jacoco.core.internal.data.*

internal fun String.crc64() = CRC64.classId(toByteArray()).toString(Character.MAX_RADIX)

fun IBundleCoverage.toDataMap() = packages
    .flatMap { it.classes }
    .flatMap { c -> c.methods.map { (c.name to it.sign()) to it } }.toMap()

fun IMethodCoverage.sign() = "$name$desc"
