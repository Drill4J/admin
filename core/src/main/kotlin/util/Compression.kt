package com.epam.drill.admin.util

import io.airlift.compress.zstd.*

val Zstd: Encoder = object : Encoder {
    private val zstdCompressor = ZstdCompressor()
    private val zstdDecompressor = ZstdDecompressor()

    override fun encode(input: ByteArray): ByteArray {
        val maxCompressedLength = zstdCompressor.maxCompressedLength(input.size)
        val byteArray = ByteArray(maxCompressedLength)
        val compress = zstdCompressor.compress(input, 0, input.size, byteArray, 0, maxCompressedLength)
        return byteArray.copyOf(compress)
    }

    override fun decode(input: ByteArray): ByteArray {
        val decompressedSize = ZstdDecompressor.getDecompressedSize(input, 0, input.size)
        val byteArray = ByteArray(decompressedSize.toInt())
        zstdDecompressor.decompress(input, 0, input.size, byteArray, 0, decompressedSize.toInt())
        return byteArray
    }
}

interface Encoder {
    fun decode(input: ByteArray): ByteArray
    fun encode(input: ByteArray): ByteArray
}