package com.epam.drill.admin.writer.rawdata.repository

import com.epam.drill.admin.writer.rawdata.entity.TestSession

interface TestSessionRepository {
    fun create(session: TestSession)
}