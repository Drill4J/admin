package com.epam.drill.endpoints.agent

import com.epam.drill.endpoints.*
import java.util.concurrent.*

typealias SessionStorage = CopyOnWriteArraySet<DrillWsSession>