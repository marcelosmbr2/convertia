package com.convertia.app.data.engine.pdf

import java.io.File

/** Lê um PDF no [PdfModel]; as imagens vão como PNG para [imagesDir]. */
interface PdfExtractor {
    fun extract(pdf: File, imagesDir: File): PdfModel
}
