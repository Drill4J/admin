package com.epam.drill.admin.metrics.models

open class MethodCriteria(
    val packageNamePattern: String? = null,
    val className: String? = null,
    val methodSignature: String? = null,
) {
    object NONE: MethodCriteria()
}