package com.epam.drill.builds

object Build1 : Build {
    override lateinit var test: Class<Tst>
    fun entryPoint() = Build1.test.constructors.first().newInstance() as Test
    override val name: String = "build1"
    override val version: String = "0.1.0"


    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }

}

object Build2 : Build {
    override lateinit var test: Class<Tst>
    override val name: String = "build2"
    override val version: String = "0.2.0"
    fun entryPoint() = Build2.test.constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build3 : Build {
    override lateinit var test: Class<Tst>
    override val name: String = "build3"
    override val version: String = "0.3.0"
    fun entryPoint() = Build3.test.constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build4 : Build {
    override lateinit var test: Class<Tst>
    override val name: String = "build4"
    override val version: String = "0.4.0"
    fun entryPoint() = Build4.test.constructors.first().newInstance() as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build5 : Build {
    override lateinit var test: Class<Tst>
    override val name: String = "build5"
    override val version: String = "0.5.0"

    fun entryPoint() = Build5.test.constructors.first().newInstance() as Test
    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

interface Build {
    var test: Class<Tst>
    val name: String
    val version: String
}

interface Tst
