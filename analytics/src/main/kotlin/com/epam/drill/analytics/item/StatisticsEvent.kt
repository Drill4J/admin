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
package com.epam.drill.analytics.item

import com.epam.drill.analytics.*

class StatisticsEvent private constructor(val payload: StatisticsItems) {

    class StatisticsEventBuilder {
        private val eventType = "t" to "event"

        private val params = mutableMapOf(eventType)

        /**
         * Required property
         */
        fun withCategory(category: String): StatisticsEventBuilder {
            params["ec"] = category
            return this
        }

        /**
         * Required property
         */
        fun withAction(action: String): StatisticsEventBuilder {
            params["ea"] = action
            return this
        }

        /**
         * Optional property
         */
        fun withLabel(label: String): StatisticsEventBuilder {
            params["el"] = label
            return this
        }

        //TODO add `uid` to statistic params after EPMDJ-9597
        /**
         * Optional property
         */
        fun withUserUID(userUID: String): StatisticsEventBuilder {
            params["uid"] = userUID
            return this
        }

        fun build(): StatisticsEvent {
            return StatisticsEvent(params)
        }
    }
}
