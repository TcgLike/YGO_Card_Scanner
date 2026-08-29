package com.ygocardscanner.data.scanner

/** Heuristically groups OCR blocks that occupy the same physical card column in a bulk photo. */
object ScanTextRegionGrouper {
    fun group(blocks: List<OcrTextBlock>): List<ScanTextRegion> {
        val remaining = blocks.filter { it.text.isNotBlank() }.sortedWith(compareBy(OcrTextBlock::top, OcrTextBlock::left)).toMutableList()
        val regions = mutableListOf<MutableList<OcrTextBlock>>()
        while (remaining.isNotEmpty()) {
            val region = mutableListOf(remaining.removeAt(0))
            var expanded: Boolean
            do {
                expanded = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    if (region.any { belongsWith(it, candidate) }) {
                        region += candidate
                        iterator.remove()
                        expanded = true
                    }
                }
            } while (expanded)
            regions += region
        }
        return regions
            .map { ScanTextRegion(it.sortedWith(compareBy(OcrTextBlock::top, OcrTextBlock::left))) }
            .sortedWith(compareBy({ it.blocks.minOf(OcrTextBlock::top) }, { it.blocks.minOf(OcrTextBlock::left) }))
    }

    private fun belongsWith(left: OcrTextBlock, right: OcrTextBlock): Boolean {
        val overlap = (minOf(left.right, right.right) - maxOf(left.left, right.left)).coerceAtLeast(0)
        val narrowestWidth = minOf(left.right - left.left, right.right - right.left).coerceAtLeast(1)
        val verticalGap = when {
            right.top > left.bottom -> right.top - left.bottom
            left.top > right.bottom -> left.top - right.bottom
            else -> 0
        }
        return overlap * 100 >= narrowestWidth * MIN_HORIZONTAL_OVERLAP_PERCENT && verticalGap <= MAX_VERTICAL_GAP_PIXELS
    }

    private const val MIN_HORIZONTAL_OVERLAP_PERCENT = 35
    private const val MAX_VERTICAL_GAP_PIXELS = 420
}