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
