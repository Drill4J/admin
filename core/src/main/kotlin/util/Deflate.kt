package com.epam.drill.admin.util

import java.util.zip.*

object Deflate {
    private const val CHUNK = 1024
    private const val COMPRESS_RATIO = 15
    private const val MAX_ARRAY_SIZE =
        Int.MAX_VALUE - 5 // https://stackoverflow.com/questions/3038392/do-java-arrays-have-a-maximum-size

    fun decode(
        tempInput: ByteArray,
        size: Int =
            if (tempInput.size > CHUNK) {
                minOf(MAX_ARRAY_SIZE, tempInput.size * COMPRESS_RATIO)
            } else CHUNK
    ): ByteArray {
        var output = byteArrayOf()
        val inflater = Inflater(true)
        var hv = 0
        try {
            do {
                val temp = ByteArray(size)
                if (inflater.needsInput()) {
                    val read = tempInput.size
                    if (read <= 0) break
                    inflater.setInput(tempInput, 0, read)
                }
                val written = inflater.inflate(temp)
                hv += written
                output += temp
            } while (!inflater.finished())
        } finally {
            inflater.end()
        }
        return output.copyOf(hv)
    }
}
