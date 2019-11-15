package com.epam.drill.builds

object Build1 : Build {
    override lateinit var test: Tst
    fun entryPoint() = test as Test
    override val name: String = "build1"


    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }

}

object Build2 : Build {
    override lateinit var test: Tst
    override val name: String = "build2"
    fun entryPoint() = test as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build3 : Build {
    override lateinit var test: Tst
    override val name: String = "build3"
    fun entryPoint() = test as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build4 : Build {
    override lateinit var test: Tst
    override val name: String = "build4"
    fun entryPoint() = test as Test

    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

object Build5 : Build {
    override lateinit var test: Tst
    override val name: String = "build5"
    fun entryPoint() = test as Test
    interface Test : Tst {
        fun test1()
        fun test2()
        fun test3()
    }
}

interface Build {
    var test: Tst
    val name: String
}

interface Tst