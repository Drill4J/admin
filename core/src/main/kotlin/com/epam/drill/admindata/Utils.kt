package com.epam.drill.admindata

import org.jacoco.core.analysis.*
import org.jacoco.core.internal.data.*

fun crc64(source: String) = CRC64.classId(source.toByteArray()).toString(Character.MAX_RADIX)

fun IBundleCoverage.toDataMap() = packages
    .flatMap { it.classes }
    .flatMap { c -> c.methods.map { (c.name to it.sign()) to it } }.toMap()

fun IMethodCoverage.sign() = "$name$desc"