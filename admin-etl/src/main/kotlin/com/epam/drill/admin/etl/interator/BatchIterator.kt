package com.epam.drill.admin.etl.interator

abstract class BatchIterator<T>(
    private val batchSize: Int,
    initData: List<T> = emptyList()
) : Iterator<T> {
    private var currentBatch: List<T> = emptyList()
    private var currentIndex = 0
    private var offset = 0
    private var exhausted = false

    init {
        if (initData.isNotEmpty()) {
            currentBatch = initData
            offset += initData.size
            if (initData.size < batchSize) {
                exhausted = true
            }
        }
    }

    abstract fun fetchBatch(
        offset: Int,
        batchSize: Int
    ): List<T>

    override fun hasNext(): Boolean {
        // If we have items in current batch, return true
        if (currentIndex < currentBatch.size) {
            return true
        }

        // If we've exhausted all batches, return false
        if (exhausted) {
            return false
        }

        // Try to load next batch
        loadNextBatch()

        return currentBatch.isNotEmpty()
    }

    override fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException("No more elements in SqlSequence")
        }

        val item = currentBatch[currentIndex]
        currentIndex++
        return item
    }

    private fun loadNextBatch() {
        currentBatch = fetchBatch(offset, batchSize)

        if (currentBatch.isEmpty() || currentBatch.size < batchSize) {
            exhausted = true
        }

        currentIndex = 0
        offset += currentBatch.size
    }
}