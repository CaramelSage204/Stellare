package com.example.stellare.verification

import com.example.stellare.ui.verification.FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES
import com.example.stellare.ui.verification.MAX_JPEG_QUALITY
import com.example.stellare.ui.verification.MIN_JPEG_QUALITY
import com.example.stellare.ui.verification.fitsInDocument
import com.example.stellare.ui.verification.qualityFor
import com.example.stellare.ui.verification.sampleSizeFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressionTest {

    @Test
    fun `sampleSizeFor zwraca 1 gdy obraz miesci sie w limicie pikseli`() {
        val sampleSize = sampleSizeFor(width = 1000, height = 1000, maxPixels = 4_000_000)

        assertEquals(1, sampleSize)
    }

    @Test
    fun `sampleSizeFor zwraca 2 gdy obraz jest czterokrotnie za duzy`() {
        val sampleSize = sampleSizeFor(width = 2000, height = 2000, maxPixels = 1_000_000)

        assertEquals(2, sampleSize)
    }

    @Test
    fun `qualityFor zwraca nizsza jakosc dla wiekszego pliku`() {
        val targetBytes = 500_000
        val jakoscMniejszegoPliku = qualityFor(sizeBytes = 600_000, targetBytes = targetBytes)
        val jakoscWiekszegoPliku = qualityFor(sizeBytes = 1_000_000, targetBytes = targetBytes)

        assertTrue(jakoscMniejszegoPliku > jakoscWiekszegoPliku)
    }

    @Test
    fun `qualityFor nie schodzi ponizej minimalnej jakosci dla bardzo duzego pliku`() {
        val quality = qualityFor(sizeBytes = 10_000_000, targetBytes = 500_000)

        assertEquals(MIN_JPEG_QUALITY, quality)
    }

    @Test
    fun `qualityFor nie przekracza maksymalnej jakosci gdy plik jest juz mniejszy niz cel`() {
        val quality = qualityFor(sizeBytes = 100_000, targetBytes = 500_000)

        assertEquals(MAX_JPEG_QUALITY, quality)
    }

    @Test
    fun `fitsInDocument odrzuca dlugosc powyzej limitu`() {
        val fits = fitsInDocument(base64Length = FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES + 1)

        assertFalse(fits)
    }

    @Test
    fun `fitsInDocument przyjmuje dlugosc tuz ponizej limitu`() {
        val fits = fitsInDocument(base64Length = FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES - 1)

        assertTrue(fits)
    }

    @Test
    fun `fitsInDocument przyjmuje dlugosc dokladnie rowna limitowi`() {
        val fits = fitsInDocument(base64Length = FIRESTORE_DOCUMENT_SAFE_LIMIT_BYTES)

        assertTrue(fits)
    }
}
