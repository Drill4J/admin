package com.epam.drill.methods

import com.epam.drill.admindata.*
import com.epam.drill.common.*
import com.epam.drill.methods.stubs.*
import kotlin.test.*

class MethodsComparatorTest {

    private val origin = getMethods(Baseline::class.java.readBytes())
    private val oneAdded = getMethods(OneMethodAdded::class.java.readBytes())
    private val oneDeletedOneModified = getMethods(OneMethodModifiedOneDeleted::class.java.readBytes())

    @Test
    fun `should detect 3 new methods`() {
        val res = MethodsComparator().compareClasses(emptyMap(), origin).map
        assertTrue { res[DiffType.UNAFFECTED].isNullOrEmpty() }
        assertTrue { res[DiffType.DELETED].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_NAME].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_DESC].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_BODY].isNullOrEmpty() }
        assertTrue { res[DiffType.NEW]!!.size == 3 }
    }

    @Test
    fun `should detect one new method`() {
        val res = MethodsComparator().compareClasses(origin, oneAdded).map
        assertTrue { res[DiffType.UNAFFECTED]!!.size == 3 }
        assertTrue { res[DiffType.DELETED].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_NAME].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_DESC].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_BODY].isNullOrEmpty() }
        assertTrue { res[DiffType.NEW]!!.size == 1 }
    }

    @Test
    fun `should detect 1 modified by body and 1 deleted methods`() {
        val res = MethodsComparator().compareClasses(origin, oneDeletedOneModified).map
        assertTrue { res[DiffType.UNAFFECTED]!!.size == 1 }
        assertTrue { res[DiffType.DELETED]!!.size == 1 }
        assertTrue { res[DiffType.MODIFIED_NAME].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_DESC].isNullOrEmpty() }
        assertTrue { res[DiffType.MODIFIED_BODY]!!.size == 1 }
        assertTrue { res[DiffType.NEW].isNullOrEmpty() }
    }

    private fun getMethods(bytes: ByteArray) = listOf(
        "abc.Rst" to BcelClassParser(bytes, "abc.Rst").parseToJavaMethods()
    ).toMap()
}

internal fun Class<*>.readBytes(): ByteArray = getResourceAsStream(
    "/${name.replace('.', '/')}.class"
).readBytes()
