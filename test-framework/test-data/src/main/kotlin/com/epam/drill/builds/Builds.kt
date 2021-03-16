/**
 * Copyright 2020 EPAM Systems
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
package com.epam.drill.builds

object Build1 : Build {
    override lateinit var tests: Array<Class<Tst>>
    fun entryPoint() = Build1.tests.first().constructors.first().newInstance() as Test
    override val name: String = "build1"
    override val version: String = "0.1.0"


    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }

}

object Build2 : Build {
    override lateinit var tests: Array<Class<Tst>>
    override val name: String = "build2"
    override val version: String = "0.2.0"
    fun entryPoint() = Build2.tests.first().constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build3 : Build {
    override lateinit var tests: Array<Class<Tst>>
    override val name: String = "build3"
    override val version: String = "0.3.0"
    fun entryPoint() = Build3.tests.first().constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build4 : Build {
    override lateinit var tests: Array<Class<Tst>>
    override val name: String = "build4"
    override val version: String = "0.4.0"
    fun entryPoint() = Build4.tests.first().constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build5 : Build {
    override lateinit var tests: Array<Class<Tst>>
    override val name: String = "build5"
    override val version: String = "0.5.0"

    fun entryPoint() = Build5.tests.first().constructors.first().newInstance() as Test
    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object CustomBuild : Build {
    override lateinit var tests: Array<Class<Tst>>
    override val name: String = "bigBuild"
    override val version: String = "1.0.0"

    fun entryPoint() = CustomBuild.tests.first().constructors.first().newInstance() as Test
    interface Test : Tst
}

interface Build {
    var tests: Array<Class<Tst>>
    val name: String
    val version: String
}

interface Tst
