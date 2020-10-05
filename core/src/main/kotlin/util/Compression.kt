package com.epam.drill.admin.util

import io.airlift.compress.zstd.*

object Zstd {
    private val zstdCompressor = ZstdCompressor()
    private val zstdDecompressor = ZstdDecompressor()

    fun compress(input: ByteArray): ByteArray {
        val maxCompressedLength = zstdCompressor.maxCompressedLength(input.size)
        val byteArray = ByteArray(maxCompressedLength)
        val compress = zstdCompressor.compress(input, 0, input.size, byteArray, 0, maxCompressedLength)
        return byteArray.copyOf(compress)
    }

    fun decompress(input: ByteArray): ByteArray {
        val decompressedSize = ZstdDecompressor.getDecompressedSize(input, 0, input.size)
        val byteArray = ByteArray(decompressedSize.toInt())
        zstdDecompressor.decompress(input, 0, input.size, byteArray, 0, decompressedSize.toInt())
        return byteArray
    }
}
