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
package com.epam.drill.admin.config

import com.epam.drill.admin.config.repository.AppQualityGateRepository
import com.epam.drill.admin.config.repository.MilestoneRepository
import com.epam.drill.admin.config.repository.QualityGateTargetRepository
import com.epam.drill.admin.config.repository.ReleaseTrackRepository
import com.epam.drill.admin.config.repository.impl.AppQualityGateRepositoryImpl
import com.epam.drill.admin.config.repository.impl.MilestoneRepositoryImpl
import com.epam.drill.admin.config.repository.impl.QualityGateTargetRepositoryImpl
import com.epam.drill.admin.config.repository.impl.ReleaseTrackRepositoryImpl
import com.epam.drill.admin.config.service.ConfigService
import com.epam.drill.admin.config.service.impl.ConfigServiceImpl
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

val configDIModule
    get() = DI.Module("config") {
        bind<ReleaseTrackRepository>() with singleton { ReleaseTrackRepositoryImpl() }
        bind<MilestoneRepository>() with singleton { MilestoneRepositoryImpl() }
        bind<QualityGateTargetRepository>() with singleton { QualityGateTargetRepositoryImpl() }
        bind<AppQualityGateRepository>() with singleton { AppQualityGateRepositoryImpl() }
        bind<ConfigService>() with singleton {
            ConfigServiceImpl(
                releaseTrackRepository = instance(),
                milestoneRepository = instance(),
                qualityGateTargetRepository = instance(),
                appQualityGateRepository = instance()
            )
        }
    }
