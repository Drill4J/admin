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

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.singleton

val monitoringDIModule = DI.Module("monitoring") {
    bind<MeterRegistry>() with singleton {
        prometheusRegistry
    }
    bind<PrometheusMeterRegistry>() with singleton {
        prometheusRegistry
    }
}

private val prometheusRegistry: PrometheusMeterRegistry by lazy {
    PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT,
        CollectorRegistry.defaultRegistry,
        Clock.SYSTEM
    ).also { registry ->
        if (Metrics.globalRegistry.registries.none { it === registry }) {
            Metrics.addRegistry(registry)
        }
        ClassLoaderMetrics().bindTo(registry)
        JvmMemoryMetrics().bindTo(registry)
        JvmGcMetrics().bindTo(registry)
        JvmThreadMetrics().bindTo(registry)
        ProcessorMetrics().bindTo(registry)
        FileDescriptorMetrics().bindTo(registry)
        UptimeMetrics().bindTo(registry)
    }
}
