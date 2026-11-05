package com.recast.app.data.engine.pdf

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Região retangular ocupada por uma tabela numa página, com as linhas de texto dentro dela. */
internal data class TableRegion(
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
    val rowBounds: List<Float>,
    val columnBounds: List<Float>,
    /** Célula por (linha, coluna) da grade; `null` nas posições cobertas por mesclagem. */
    val cells: Map<Pair<Int, Int>, CellSpan>,
    val bordered: Boolean,
) {
    data class CellSpan(val colSpan: Int, val rowSpan: Int)

    fun contains(x: Float, y: Float) = x >= left - 1f && x <= right + 1f && y >= top - 1f && y <= bottom + 1f

    val rows: Int get() = rowBounds.size - 1
    val columns: Int get() = columnBounds.size - 1

    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val col = columnBounds.indexOfLast { it <= x + 0.5f }.coerceIn(0, columns - 1)
        val row = rowBounds.indexOfLast { it <= y + 0.5f }.coerceIn(0, rows - 1)
        // Posição coberta por uma mesclagem: sobe/esquerda até a célula dona.
        var r = row
        var c = col
        while (r >= 0) {
            var cc = c
            while (cc >= 0) {
                cells[r to cc]?.let { span -> if (r + span.rowSpan > row && cc + span.colSpan > col) return r to cc }
                cc--
            }
            r--
        }
        return null
    }
}

/**
 * Tabelas pela grade desenhada: segmentos horizontais e verticais que se tocam formam
 * uma região; as interseções dão linhas e colunas; separadores ausentes dão mesclagens.
 */
internal object GridTableDetector {

    private const val TOLERANCE = 2.5f

    fun detect(strokes: List<Stroke>): List<TableRegion> {
        val horizontals = strokes.filter { it.isHorizontal }.map { Seg(min(it.x1, it.x2), max(it.x1, it.x2), (it.y1 + it.y2) / 2) }
        val verticals = strokes.filter { it.isVertical }.map { Seg(min(it.y1, it.y2), max(it.y1, it.y2), (it.x1 + it.x2) / 2) }
        if (horizontals.size < 2 || verticals.size < 2) return emptyList()
        val groups = connectedGroups(horizontals, verticals)
        return groups.mapNotNull { (hs, vs) -> region(hs, vs) }
    }

    /** Segmento: [a, b] ao longo do eixo, em [at] no eixo perpendicular. */
    private data class Seg(val a: Float, val b: Float, val at: Float)

    private fun connectedGroups(horizontals: List<Seg>, verticals: List<Seg>): List<Pair<List<Seg>, List<Seg>>> {
        val all = horizontals.map { it to true } + verticals.map { it to false }
        val parent = IntArray(all.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) x = parent[x].also { parent[x] = parent[parent[x]] }; return x }
        fun union(i: Int, j: Int) { parent[find(i)] = find(j) }
        for (i in all.indices) for (j in i + 1 until all.size) {
            val (s1, h1) = all[i]
            val (s2, h2) = all[j]
            val touch = when {
                h1 == h2 -> abs(s1.at - s2.at) < TOLERANCE && s1.a <= s2.b + TOLERANCE && s2.a <= s1.b + TOLERANCE
                h1 -> crosses(s1, s2)
                else -> crosses(s2, s1)
            }
            if (touch) union(i, j)
        }
        return all.indices.groupBy { find(it) }.values.map { members ->
            members.filter { all[it].second }.map { all[it].first } to members.filterNot { all[it].second }.map { all[it].first }
        }.filter { it.first.size >= 2 && it.second.size >= 2 }
    }

    /** Horizontal em y=h.at de h.a..h.b cruza a vertical em x=v.at de v.a..v.b. */
    private fun crosses(h: Seg, v: Seg) =
        v.at >= h.a - TOLERANCE && v.at <= h.b + TOLERANCE && h.at >= v.a - TOLERANCE && h.at <= v.b + TOLERANCE

    private fun region(hs: List<Seg>, vs: List<Seg>): TableRegion? {
        val left = min(hs.minOf { it.a }, vs.minOf { it.at })
        val right = max(hs.maxOf { it.b }, vs.maxOf { it.at })
        val top = min(vs.minOf { it.a }, hs.minOf { it.at })
        val bottom = max(vs.maxOf { it.b }, hs.maxOf { it.at })
        if (right - left < 20f || bottom - top < 8f) return null
        val rowBounds = cluster(hs.map { it.at })
        val columnBounds = cluster(vs.map { it.at })
        if (rowBounds.size < 2 || columnBounds.size < 2) return null
        // Uma caixa só (1×1) não é tabela: é uma moldura.
        if (rowBounds.size == 2 && columnBounds.size == 2) return null

        fun hasVertical(x: Float, y1: Float, y2: Float) =
            vs.any { abs(it.at - x) < TOLERANCE && it.a <= y1 + TOLERANCE && it.b >= y2 - TOLERANCE }
        fun hasHorizontal(y: Float, x1: Float, x2: Float) =
            hs.any { abs(it.at - y) < TOLERANCE && it.a <= x1 + TOLERANCE && it.b >= x2 - TOLERANCE }

        val rows = rowBounds.size - 1
        val cols = columnBounds.size - 1
        val covered = Array(rows) { BooleanArray(cols) }
        val cells = mutableMapOf<Pair<Int, Int>, TableRegion.CellSpan>()
        for (r in 0 until rows) for (c in 0 until cols) {
            if (covered[r][c]) continue
            var colSpan = 1
            while (c + colSpan < cols && !covered[r][c + colSpan] && !hasVertical(columnBounds[c + colSpan], rowBounds[r], rowBounds[r + 1])) colSpan++
            var rowSpan = 1
            while (r + rowSpan < rows && (0 until colSpan).none { k -> covered[r + rowSpan][c + k] } &&
                !hasHorizontal(rowBounds[r + rowSpan], columnBounds[c], columnBounds[c + colSpan])
            ) rowSpan++
            for (dr in 0 until rowSpan) for (dc in 0 until colSpan) covered[r + dr][c + dc] = true
            cells[r to c] = TableRegion.CellSpan(colSpan, rowSpan)
        }
        return TableRegion(top, bottom, left, right, rowBounds, columnBounds, cells, bordered = true)
    }

    /** Valores próximos (dentro da tolerância) viram um só, ordenados. */
    fun cluster(values: List<Float>, tolerance: Float = TOLERANCE): List<Float> {
        val sorted = values.sorted()
        val out = mutableListOf<MutableList<Float>>()
        for (v in sorted) {
            val last = out.lastOrNull()
            if (last != null && v - last.last() <= tolerance) last += v else out += mutableListOf(v)
        }
        return out.map { it.average().toFloat() }
    }
}

/**
 * Tabelas sem borda: três ou mais linhas seguidas, cada uma com dois ou mais trechos
 * curtos cujos inícios se alinham nas mesmas colunas.
 */
internal object BorderlessTableDetector {

    private const val ALIGN_TOLERANCE = 4f
    private const val MIN_ROWS = 3

    /** Devolve as faixas [primeira linha, última linha] (índices em [lines]) que formam tabelas. */
    fun detect(lines: List<Line>, textWidth: Float): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = 0
        while (start < lines.size) {
            val firstCells = TextLines.cellsOf(lines[start])
            if (!isRowLike(firstCells, textWidth)) { start++; continue }
            var end = start
            var columns = firstCells.map { it.x to it.right }
            while (end + 1 < lines.size) {
                val next = lines[end + 1]
                val cells = TextLines.cellsOf(next)
                if (!isRowLike(cells, textWidth)) break
                val gap = next.top - lines[end].bottom
                // Linhas de tabela têm o espaçamento das células, maior que o do texto corrido.
                if (gap > lines[end].fontSize * 2.0f) break
                val merged = mergeColumns(columns, cells.map { it.x to it.right }) ?: break
                columns = merged
                end++
            }
            if (end - start + 1 >= MIN_ROWS && columns.size >= 2) result += start..end
            start = end + 1
        }
        return result
    }

    /** Duas faixas horizontais se sobrepõem (com folga): mesma coluna. */
    private fun overlaps(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        a.first <= b.second + ALIGN_TOLERANCE && b.first <= a.second + ALIGN_TOLERANCE

    /** Une faixas sobrepostas em colunas, da esquerda para a direita. */
    fun unionColumns(spans: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val sorted = spans.sortedBy { it.first }
        val out = mutableListOf<Pair<Float, Float>>()
        for (span in sorted) {
            val last = out.lastOrNull()
            if (last != null && overlaps(last, span)) out[out.lastIndex] = min(last.first, span.first) to max(last.second, span.second)
            else out += span
        }
        return out
    }

    /** Duas ou mais células curtas, e não um item de lista (marcador + texto). */
    private fun isRowLike(cells: List<Chunk>, textWidth: Float): Boolean =
        cells.size >= 2 && cells.all { it.right - it.x < textWidth * 0.45f } &&
            ListMarkers.strip(cells.flatMap { it.words }) == null

    /**
     * Colunas em comum: quase toda célula da linha nova tem que cair numa coluna conhecida
     * (faixas horizontais que se sobrepõem), e a união não pode fundir duas colunas numa só.
     */
    private fun mergeColumns(columns: List<Pair<Float, Float>>, cells: List<Pair<Float, Float>>): List<Pair<Float, Float>>? {
        val matched = cells.count { c -> columns.any { overlaps(it, c) } }
        if (matched < min(2, cells.size)) return null
        if (matched < cells.size - 1) return null
        val merged = unionColumns(columns + cells)
        if (merged.size < columns.size) return null
        return merged
    }

    /** Monta as linhas da tabela: cada célula vai para a coluna cuja faixa ela ocupa. */
    fun build(lines: List<Line>, textRight: Float): Table {
        val cellsPerLine = lines.map { TextLines.cellsOf(it) }
        val columns = unionColumns(cellsPerLine.flatMap { cells -> cells.map { it.x to it.right } })
        val rows = cellsPerLine.map { lineCells ->
            val cells = MutableList(columns.size) { mutableListOf<Chunk>() }
            for (chunk in lineCells) {
                val index = columns.indices.firstOrNull { overlaps(columns[it], chunk.x to chunk.right) }
                    ?: columns.indices.minByOrNull { abs(columns[it].first - chunk.x) } ?: 0
                cells[index] += chunk
            }
            TableRow(cells.map { chunks -> Cell(if (chunks.isEmpty()) emptyList() else listOf(Paragraph(Runs.of(listOf(chunks.flatMap { it.words }))))) })
        }
        val widths = columns.indices.map { i -> (columns.getOrNull(i + 1)?.first ?: textRight) - columns[i].first }.map { max(it, 20f) }
        return Table(rows, widths, bordered = false)
    }
}

/** Texto em colunas: um vão vertical livre que divide as linhas em esquerda e direita. */
internal object ColumnSplitter {

    /** Região da página com as linhas em ordem de leitura. */
    data class Region(val top: Float, val bottom: Float, val left: Float, val right: Float, val lines: List<Line>)

    fun split(lines: List<Line>, textLeft: Float, textRight: Float, bodySize: Float): List<Region> {
        if (lines.size < 6) return listOf(whole(lines, textLeft, textRight))
        val width = textRight - textLeft
        val center = textLeft + width / 2
        var bestX = Float.NaN
        var bestCrossing = Int.MAX_VALUE
        var x = textLeft + width * 0.3f
        while (x <= textLeft + width * 0.7f) {
            val crossing = lines.count { spans(it, x, bodySize) }
            if (crossing < bestCrossing || (crossing == bestCrossing && abs(x - center) < abs(bestX - center))) { bestCrossing = crossing; bestX = x }
            x += 2f
        }
        if (bestX.isNaN()) return listOf(whole(lines, textLeft, textRight))
        val flowing = lines.filterNot { spans(it, bestX, bodySize) }
        val leftLines = flowing.filter { line -> line.words.any { it.right <= bestX } }
        val rightLines = flowing.filter { line -> line.words.any { it.x >= bestX } }
        fun longSide(side: List<Line>, left: Boolean) = side.count { l ->
            val words = l.words.filter { if (left) it.right <= bestX else it.x >= bestX }
            words.isNotEmpty() && words.last().right - words.first().x > width * 0.3f
        }
        val twoColumns = bestCrossing <= lines.size / 2 && leftLines.size >= 3 && rightLines.size >= 3 &&
            // Texto longo dos dois lados: é texto corrido em colunas, não uma tabela sem borda.
            longSide(leftLines, true) >= 3 && longSide(rightLines, false) >= 3
        if (!twoColumns) return listOf(whole(lines, textLeft, textRight))

        // Linhas que atravessam o vão separam faixas; dentro de cada faixa, esquerda antes da direita.
        val regions = mutableListOf<Region>()
        var band = mutableListOf<Line>()
        fun flushBand() {
            if (band.isEmpty()) return
            val left = mutableListOf<Line>()
            val right = mutableListOf<Line>()
            for (line in band) {
                val lw = line.words.filter { it.right <= bestX }
                val rw = line.words.filter { it.x >= bestX }
                if (lw.isNotEmpty()) left += line.copy(chunks = TextLines.rechunk(lw))
                if (rw.isNotEmpty()) right += line.copy(chunks = TextLines.rechunk(rw))
            }
            if (left.isNotEmpty()) regions += Region(left.minOf { it.top }, left.maxOf { it.bottom }, textLeft, bestX, left)
            if (right.isNotEmpty()) regions += Region(right.minOf { it.top }, right.maxOf { it.bottom }, bestX, textRight, right)
            band = mutableListOf()
        }
        for (line in lines.sortedBy { it.top }) {
            val spanning = spans(line, bestX, bodySize)
            if (spanning) {
                flushBand()
                regions += Region(line.top, line.bottom, textLeft, textRight, listOf(line))
            } else {
                band += line
            }
        }
        flushBand()
        return regions
    }

    /**
     * A linha atravessa o vão em [x]: uma palavra cruza, ou há palavras dos dois lados sem
     * mais que um espaço normal entre elas (texto corrido de largura inteira).
     */
    private fun spans(line: Line, x: Float, bodySize: Float): Boolean {
        val words = line.words
        if (words.any { it.x < x && it.right > x }) return true
        val leftMost = words.filter { it.right <= x }.maxOfOrNull { it.right } ?: return false
        val rightMost = words.filter { it.x >= x }.minOfOrNull { it.x } ?: return false
        return rightMost - leftMost < bodySize * 0.6f
    }

    private fun whole(lines: List<Line>, left: Float, right: Float) =
        Region(lines.minOfOrNull { it.top } ?: 0f, lines.maxOfOrNull { it.bottom } ?: 0f, left, right, lines)
}

/** Cabeçalho e rodapé: texto que se repete na mesma altura em várias páginas. */
internal object HeaderFooterDetector {

    data class Result(val header: List<Run>, val footer: List<Run>, val removed: Set<Pair<Int, Float>>)

    private val digits = Regex("\\d+")

    fun detect(pagesLines: Map<Int, List<Line>>, pageHeight: Float): Result {
        val pages = pagesLines.size
        if (pages < 2) return Result(emptyList(), emptyList(), emptySet())
        val topBand = pageHeight * 0.12f
        val bottomBand = pageHeight * 0.88f
        data class Candidate(val page: Int, val line: Line, val key: String, val header: Boolean)
        val candidates = pagesLines.flatMap { (page, lines) ->
            lines.filter { it.bottom < topBand || it.top > bottomBand }.map { line ->
                Candidate(page, line, digits.replace(line.text, "#").trim(), line.bottom < topBand)
            }
        }
        val needed = if (pages >= 3) (pages + 1) / 2 else 2
        val groups = candidates.groupBy { it.key to it.header }.values.filter { group ->
            group.map { it.page }.distinct().size >= needed && group.all { c -> abs(c.line.top - group.first().line.top) < 6f }
        }
        val header = groups.filter { it.first().header }.minByOrNull { it.first().line.top }?.first()?.line
        val footer = groups.filter { !it.first().header }.maxByOrNull { it.first().line.top }?.first()?.line
        val removed = groups.flatMap { g -> g.map { it.page to it.line.baseline } }.toSet()
        return Result(
            header = header?.let { Runs.of(listOf(it.words)) }.orEmpty(),
            footer = footer?.let { Runs.of(listOf(it.words)) }.orEmpty(),
            removed = removed,
        )
    }
}
