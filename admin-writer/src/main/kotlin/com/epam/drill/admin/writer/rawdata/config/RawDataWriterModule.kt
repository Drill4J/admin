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
package com.epam.drill.admin.writer.rawdata.config

import com.epam.drill.admin.writer.rawdata.repository.AgentConfigRepository
import com.epam.drill.admin.writer.rawdata.repository.AstMethodRepository
import com.epam.drill.admin.writer.rawdata.repository.ExecClassDataRepository
import com.epam.drill.admin.writer.rawdata.repository.TestMetadataRepository
import com.epam.drill.admin.writer.rawdata.repository.impl.AgentConfigRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.AstMethodRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.ExecClassDataRepositoryImpl
import com.epam.drill.admin.writer.rawdata.repository.impl.TestMetadataRepositoryImpl
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import com.epam.drill.admin.writer.rawdata.service.impl.RawDataServiceImpl
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val rawDataWriterDIModule = DI.Module("rawDataWriterServices") {
    bind<AgentConfigRepository>() with singleton { AgentConfigRepositoryImpl() }
    bind<AstMethodRepository>() with singleton { AstMethodRepositoryImpl() }
    bind<ExecClassDataRepository>() with singleton { ExecClassDataRepositoryImpl() }
    bind<TestMetadataRepository>() with singleton { TestMetadataRepositoryImpl() }
    bind<RawDataWriter>() with singleton { RawDataServiceImpl(
        agentConfigRepository = instance(),
        execClassDataRepository = instance(),
        testMetadataRepository = instance(),
        astMethodRepository = instance()
    ) }
}