package com.epam.drill.admin.writer.rawdata.config

import org.jetbrains.exposed.sql.*
import org.postgresql.util.PGobject
import java.util.*

class BitSetColumnType : ColumnType() {
    override fun sqlType(): String = "VARBIT"

    override fun valueFromDB(value: Any): Any =
        when (value) {
            is String -> value.toBitSet()
            is PGobject -> (value.value ?: "").toBitSet()
            else -> value
        }

    override fun notNullValueToDB(value: Any): Any {
        if (value is BitSet) {
            return PGobject().apply {
                this.type = "VARBIT"
                this.value = value.toBitString()
            }
        }
        return super.notNullValueToDB(value)
    }
}

internal fun String.toBitSet(): BitSet {
    val bitSet = BitSet(this.length)
    for (i in this.indices) {
        if (this[i] == '1') {
            bitSet.set(i)
        }
    }
    return bitSet
}

internal fun BitSet.toBitString(): String {
    val builder = StringBuilder(length())
    for (i in 0 until length()) {
        builder.append(if (get(i)) '1' else '0')
    }
    return builder.toString()
}