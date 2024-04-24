package com.epam.drill.admin.writer.rawdata.config

import com.epam.drill.admin.writer.rawdata.repository.*
import com.epam.drill.admin.writer.rawdata.repository.impl.*
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val metricsDIModule = DI.Module("metricsServices") {
    bind<MetricsRepository>() with singleton { MetricsRepositoryImpl() }
}