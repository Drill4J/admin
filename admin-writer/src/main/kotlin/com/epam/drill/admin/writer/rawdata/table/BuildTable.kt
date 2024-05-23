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
package com.epam.drill.admin.writer.rawdata.table

object BuildTable : StringIdTable("raw_data.builds") {
    val groupId = varchar("group_id", SHORT_TEXT_LENGTH)
    val appId = varchar("app_id", SHORT_TEXT_LENGTH)
    val commitSha = varchar("commit_sha", SHORT_TEXT_LENGTH)
    val buildVersion = varchar("build_version", SHORT_TEXT_LENGTH)
    val instanceId = varchar("instance_id", SHORT_TEXT_LENGTH)
    val branch = varchar("branch", SHORT_TEXT_LENGTH)
    val commitDate = varchar("commit_date", SHORT_TEXT_LENGTH)
    val commitAuthor = varchar("commit_author", SHORT_TEXT_LENGTH)
    val commitTags = varchar("commit_tags", SHORT_TEXT_LENGTH)
}