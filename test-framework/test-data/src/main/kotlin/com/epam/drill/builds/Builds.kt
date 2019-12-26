package com.epam.drill.builds

object Build1 : Build {
    override lateinit var test: Class<Tst>
    fun entryPoint() = test.constructors.first().newInstance() as Test
    override val name: String = "build1"


    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }

}

object Build2 : Build {
    override lateinit var test: Class<Tst>
    override val name: String = "build2"
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
}

interface Tst