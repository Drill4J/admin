package com.epam.drill.admin.writer.rawdata.views

import kotlinx.serialization.Serializable

@Serializable
class MethodIgnoreRuleView (
    val id: Int,
    val groupId: String,
    val appId: String,
    val namePattern: String? = null,
    val classnamePattern: String? = null,
    val annotationsPattern: String? = null,
    val classAnnotationsPattern: String? = null
)
