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
package com.epam.drill.admin.etl.table

import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.LongColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import java.time.Instant

/**
 * PostgreSQL `CURRENT_TIMESTAMP + (seconds * INTERVAL '1 second')` as an Exposed [Expression].
 *
 * Computing the lease expiration on the database side (instead of `Instant.now()` in application
 * code) avoids clock-skew issues between the application server and the database.
 */
class TimestampPlusSeconds(private val seconds: Long) : Expression<Instant>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = with(queryBuilder) {
        append("(")
        append(CurrentTimestamp)
        append(" + (")
        registerArgument(LongColumnType(), seconds)
        append(" * INTERVAL '1 second'))")
    }
}
