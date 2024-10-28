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

import com.epam.drill.admin.writer.rawdata.repository.*
import com.epam.drill.admin.writer.rawdata.repository.impl.*
import com.epam.drill.admin.writer.rawdata.service.RawDataWriter
import com.epam.drill.admin.writer.rawdata.service.impl.RawDataServiceImpl
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val rawDataWriterDIModule = DI.Module("rawDataWriterServices") {
    bind<InstanceRepository>() with singleton { InstanceRepositoryImpl() }
    bind<BuildRepository>() with singleton { BuildRepositoryImpl() }
    bind<MethodRepository>() with singleton { MethodRepositoryImpl() }
    bind<CoverageRepository>() with singleton { CoverageRepositoryImpl() }
    bind<TestMetadataRepository>() with singleton { TestMetadataRepositoryImpl() }
    bind<TestSessionRepository>() with singleton { TestSessionRepositoryImpl() }
    bind<MethodIgnoreRuleRepository>() with singleton { MethodIgnoreRuleRepositoryImpl() }
    bind<RawDataWriter>() with singleton { RawDataServiceImpl(
        instanceRepository = instance(),
        coverageRepository = instance(),
        testMetadataRepository = instance(),
        methodRepository = instance(),
        buildRepository = instance(),
        testSessionRepository = instance(),
        methodIgnoreRuleRepository = instance()
    ) }
}