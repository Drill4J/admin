package com.epam.drill.admin.util

import kotlin.random.*
import kotlin.test.*

class ZstdTest {

    @Test
    fun `compress-decompress`() {
        val input = Random.nextBytes(Random.nextInt(1, 1024))
        val decompressed = input.compressDecompress()
        assertTrue { input.contentEquals(decompressed) }
    }

    @Test
    fun `compress-decompress repeated`() {
        repeat(10) {
            val input = Random.nextBytes(Random.nextInt(1, 1024))
            val decompressed = input.compressDecompress()
            assertTrue { input.contentEquals(decompressed) }
        }
    }
}

private fun ByteArray.compressDecompress(): ByteArray = Zstd.compress(this).let {
    Zstd.decompress(it)
}
