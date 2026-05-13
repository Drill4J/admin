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
package com.epam.drill.admin.test

import kotlinx.coroutines.runBlocking
import org.awaitility.Awaitility.await
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration

private val DEFAULT_DB_WAIT_TIMEOUT: Duration = Duration.ofSeconds(5)
private val DEFAULT_DB_POLL_INTERVAL: Duration = Duration.ofMillis(100)

fun waitUntilInTransaction(assertion: () -> Unit) {
    await()
        .atMost(DEFAULT_DB_WAIT_TIMEOUT)
        .pollInterval(DEFAULT_DB_POLL_INTERVAL)
        .untilAsserted {
            transaction {
                assertion()
            }
        }
}

fun waitUntilInBlocking(
    onAssertionFailed: suspend (AssertionError) -> Unit = {},
    assertion: suspend () -> Unit
) {
    await()
        .atMost(DEFAULT_DB_WAIT_TIMEOUT)
        .pollInterval(DEFAULT_DB_POLL_INTERVAL)
        .untilAsserted {
            runCatching {
                runBlocking {
                    assertion()
                }
            }.onFailure { e ->
                if (e is AssertionError) {
                    runCatching {
                        runBlocking {
                            onAssertionFailed(e)
                        }
                    }
                }
                throw e
            }
        }
}