package com.epam.drill.storage

import com.epam.drill.endpoints.*
import com.epam.drill.endpoints.agent.*

typealias AgentStorage = ObservableMapStorage<String, AgentEntry, MutableSet<AgentWsSession>>
