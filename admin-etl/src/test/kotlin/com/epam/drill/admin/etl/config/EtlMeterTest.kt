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
package com.epam.drill.admin.etl.config

import com.epam.drill.admin.etl.EtlContext
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EtlMeterTest {
    @Test
    fun `gauges share samples when contexts differ only by build`() {
        val registry = SimpleMeterRegistry()
        val metrics = EtlMeter(registry)

        metrics.lookupCacheUtilizationRatio("job", EtlContext("group", buildId = "build-1")).set(0.4)
        metrics.lookupCacheUtilizationRatio("job", EtlContext("group", buildId = "build-2")).set(0.8)

        val gauge = registry.get("etl_lookup_cache_utilization_ratio")
            .tag("jobName", "job")
            .tag("groupId", "group")
            .gauge()

        assertEquals(0.8, gauge!!.value())
    }
}
