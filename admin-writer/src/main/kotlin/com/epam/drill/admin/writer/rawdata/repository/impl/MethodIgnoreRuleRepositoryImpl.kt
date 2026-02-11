/**
 * Copyright 2020 - 2022 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.drill.admin.writer.rawdata.repository.impl

import com.epam.drill.admin.writer.rawdata.entity.MethodIgnoreRule
import com.epam.drill.admin.writer.rawdata.repository.MethodIgnoreRuleRepository
import com.epam.drill.admin.writer.rawdata.table.MethodIgnoreRulesTable
import com.epam.drill.admin.writer.rawdata.views.MethodIgnoreRuleView
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll

class MethodIgnoreRuleRepositoryImpl : MethodIgnoreRuleRepository {
    override fun create(rule: MethodIgnoreRule) {
        MethodIgnoreRulesTable.insertAndGetId {
            it[groupId] = rule.groupId
            it[appId] = rule.appId
            it[namePattern] = rule.namePattern
            it[classnamePattern] = rule.classnamePattern
        }
    }

    override fun getAll(): List<MethodIgnoreRuleView> {
        return MethodIgnoreRulesTable.selectAll().map {
            MethodIgnoreRuleView(
                id = it[MethodIgnoreRulesTable.id].value,
                groupId = it[MethodIgnoreRulesTable.groupId],
                appId = it[MethodIgnoreRulesTable.appId],
                namePattern = it[MethodIgnoreRulesTable.namePattern],
                classnamePattern = it[MethodIgnoreRulesTable.classnamePattern],
            )
        }
    }

    override fun deleteById(ruleId: Int) {
        MethodIgnoreRulesTable.deleteWhere { MethodIgnoreRulesTable.id eq ruleId }
    }
}
