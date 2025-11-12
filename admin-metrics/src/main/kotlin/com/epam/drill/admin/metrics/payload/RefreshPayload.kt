package com.epam.drill.admin.metrics.payload

import com.epam.drill.admin.metrics.models.MatViewScope
import kotlinx.serialization.Serializable

@Serializable
class RefreshPayload(val scopes: Set<MatViewScope> = emptySet())