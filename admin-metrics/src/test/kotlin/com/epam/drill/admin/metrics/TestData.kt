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
package com.epam.drill.admin.metrics

import com.epam.drill.admin.writer.rawdata.route.payload.InstancePayload
import com.epam.drill.admin.writer.rawdata.route.payload.SessionPayload
import com.epam.drill.admin.writer.rawdata.route.payload.SingleMethodPayload
import com.epam.drill.admin.writer.rawdata.route.payload.TestDetails
import kotlinx.datetime.Clock

const val testGroup = "group-1"
const val testApp = "app-1"
const val testBranch = "main"
const val testEnv = "env-1"
const val testTask = "check"
const val testClass = "com.example.Class"
const val testPath = "com.example.Test"
val build1 = InstancePayload(
    groupId = testGroup,
    appId = testApp,
    instanceId = "instance-1",
    buildVersion = "1.0.0"
)
val build2 = InstancePayload(
    groupId = testGroup,
    appId = testApp,
    instanceId = "instance-2",
    buildVersion = "2.0.0"
)
val build3 = InstancePayload(
    groupId = testGroup,
    appId = testApp,
    instanceId = "instance-3",
    buildVersion = "3.0.0"
)
val method1 = SingleMethodPayload(
    classname = testClass,
    name = "method1",
    params = "()",
    returnType = "void",
    probesCount = 2,
    probesStartPos = 0,
    bodyChecksum = "100",
)
val method2 = SingleMethodPayload(
    classname = testClass,
    name = "method2",
    params = "()",
    returnType = "void",
    probesCount = 3,
    probesStartPos = 2,
    bodyChecksum = "200",
)
val method3 = SingleMethodPayload(
    classname = testClass,
    name = "method3",
    params = "()",
    returnType = "void",
    probesCount = 1,
    probesStartPos = 5,
    bodyChecksum = "300",
)
val test1 = TestDetails(
    runner = "junit",
    path = testPath,
    testName = "test1"
)
val test2 = TestDetails(
    runner = "junit",
    path = testPath,
    testName = "test2"
)
val test3 = TestDetails(
    runner = "junit",
    path = testPath,
    testName = "test3"
)
val session1 = SessionPayload(
    groupId = testGroup,
    id = "session-1",
    testTaskId = testTask,
    startedAt = Clock.System.now()
)
val session2 = SessionPayload(
    groupId = testGroup,
    id = "session-2",
    testTaskId = testTask,
    startedAt = Clock.System.now()
)
val session3 = SessionPayload(
    groupId = testGroup,
    id = "session-3",
    testTaskId = testTask,
    startedAt = Clock.System.now()
)

fun SingleMethodPayload.changed() = SingleMethodPayload(
    classname = classname,
    name = name,
    params = params,
    returnType = returnType,
    probesCount = probesCount,
    probesStartPos = probesStartPos,
    bodyChecksum = bodyChecksum + "0",
)

fun SingleMethodPayload.changeChecksum() = changed()

fun probesOf(vararg probes: Int): IntArray = probes

fun SessionPayload.testTaskId(testTaskId: String) = SessionPayload(
    groupId = this.groupId,
    id = this.id,
    testTaskId = testTaskId,
    startedAt = this.startedAt
)