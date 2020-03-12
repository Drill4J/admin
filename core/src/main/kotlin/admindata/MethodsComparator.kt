package com.epam.drill.admin.admindata

import com.epam.drill.common.*
import com.epam.drill.common.Method
import io.vavr.collection.*
import io.vavr.kotlin.*
import kotlinx.atomicfu.*
import org.apache.bcel.classfile.*
import org.jacoco.core.analysis.*
import java.io.*

//TODO Replace this with a descent lib

fun IBundleCoverage.compareClasses(
    oldClasses: Map<String, Methods>,
    newClasses: Map<String, Methods>
): MethodChanges = MethodsComparator(this).compareClasses(oldClasses, newClasses)

class MethodsComparator(
    private val bundle: IBundleCoverage? = null,
    private val result: IncrementalCache = IncrementalCache()
) {

    fun compareClasses(
        oldClasses: Map<String, Methods>,
        newClasses: Map<String, Methods>
    ): MethodChanges {
        val intersectedKeys = oldClasses.keys.intersect(newClasses.keys)
        result.subjoin(DiffType.DELETED, (oldClasses - intersectedKeys).values.flatten())
        result.subjoin(DiffType.NEW, (newClasses - intersectedKeys).values.flatten())
        intersectedKeys.forEach { className ->
            computeDiff(
                oldClasses[className] ?: emptyList(),
                newClasses[className] ?: emptyList()
            )
        }
        return result.filterMethods(bundle)
    }

    private fun computeDiff(old: Methods, new: Methods) {
        val (oldAffected, newAffected) = filterUnaffected(old, new)
        var (oldFiltered, newFiltered) = filterUnique(oldAffected, newAffected)

        newFiltered.forEach { method ->
            val (diff, modified) = identifyDiffType(oldFiltered, method)
            if (diff != null) {
                oldFiltered -= modified!!
                result.subjoin(diff, listOf(method))
            } else {
                result.subjoin(DiffType.NEW, listOf(method))
            }
        }
        result.subjoin(DiffType.DELETED, oldFiltered)
    }

    private fun filterUnaffected(old: Methods, new: Methods): Pair<Methods, Methods> {
        result.subjoin(DiffType.UNAFFECTED, new.intersect(old).toList())
        return old - new to new - old
    }

    private fun filterUnique(old: Methods, new: Methods): Pair<Methods, Methods> {
        val (oldUnique, oldMixed) = old.uniqueByEveryField(new)
        val (newUnique, newMixed) = new.uniqueByEveryField(oldMixed)
        result.subjoin(DiffType.NEW, newUnique)
        result.subjoin(DiffType.DELETED, oldUnique)
        return oldMixed to newMixed
    }
}

fun identifyDiffType(
    list: Methods,
    method: Method
): Pair<DiffType?, Method?> {
    list.find { method.nameModified(it) }?.let { return DiffType.MODIFIED_NAME to it }
    list.find { method.descriptorModified(it) }?.let { return DiffType.MODIFIED_DESC to it }
    list.find { method.bodyModified(it) }?.let { return DiffType.MODIFIED_BODY to it }
    return null to null
}

fun Methods.splitToFields() = Triple(map { it.name }, map { it.desc }, map { it.hash })

fun Methods.uniqueByEveryField(other: Methods): Pair<Methods, Methods> {
    val (names, descs, hashes) = other.splitToFields()
    val unique = this.filter { !(it.name in names || it.desc in descs || it.hash in hashes) }
    return unique to this - unique
}

class IncrementalCache {

    private val _map = atomic(LinkedHashMap.empty<DiffType, Methods>())

    val map get() = _map.value!!

    operator fun get(key: DiffType): Methods = map.getOrNull(key) ?: emptyList()

    fun subjoin(key: DiffType, value: Methods) {
        _map.update { it.put(key, this[key] + value) }
    }

    fun filterMethods(bundle: IBundleCoverage?): MethodChanges = MethodChanges(
        (bundle?.toDataMap()?.run {
            map.map { diffType, methods ->
                if (diffType != DiffType.DELETED) {
                    tuple(diffType, methods!!.filter { this[it.ownerClass to it.sign] != null })
                } else tuple(diffType, methods)
            }
        } ?: map).toJavaMap()
    )
}


class BcelClassParser(
    bytes: ByteArray,
    private val className: String
) {
    private val methods = ClassParser(ByteArrayInputStream(bytes), className).parse().methods

    fun parseToJavaMethods() = methods
        .filter { !it.name.isNullOrEmpty() }
        .map { method ->
            Method(
                ownerClass = className,
                name = method.name,
                desc = method.signature,
                hash = computeHash(method.code)
            )
        }

    private fun computeHash(code: Code?) = code?.run {
        crc64(
            Utility.codeToString(
                code.code,
                code.constantPool,
                0,
                code.length,
                false
            )
        )
    }
}

private val Method.sign get() = "$name$desc"

private fun Method.nameModified(otherMethod: Method) = hash == otherMethod.hash && desc == otherMethod.desc

private fun Method.descriptorModified(otherMethod: Method) = name == otherMethod.name && hash == otherMethod.hash

private fun Method.bodyModified(otherMethod: Method) = name == otherMethod.name && desc == otherMethod.desc
