package com.epam.drill.admin.storage

import com.epam.drill.admin.endpoints.*
import com.epam.drill.admin.endpoints.agent.*

typealias AgentStorage = ObservableMapStorage<String, AgentEntry, MutableSet<AgentWsSession>>
