package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.TestSession
import com.epam.drill.admin.writer.rawdata.repository.TestSessionRepository
import com.epam.drill.admin.writer.rawdata.table.TestSessionTable
import org.jetbrains.exposed.sql.upsert

class TestSessionRepositoryImpl : TestSessionRepository {
    override fun create(session: TestSession) {
        TestSessionTable.upsert {
            it[id] = session.id
            it[groupId] = session.groupId
            it[testTaskId] = session.testTaskId
            it[startedAt] = session.startedAt
        }
    }
}