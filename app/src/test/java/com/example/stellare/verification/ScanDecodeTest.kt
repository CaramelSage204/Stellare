package com.example.stellare.verification

import com.example.stellare.ui.verification.ScanDecode
import com.example.stellare.ui.verification.ScanSize
import com.example.stellare.ui.verification.allowsApproval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScanDecode.of] jest sparametryzowane obrazem wlasnie po to, zeby dalo sie je sprawdzic bez
 * android.graphics.Bitmap - w tescie jednostkowym Bitmapy nie da sie zbudowac, a to wlasnie ta
 * klasyfikacja decyduje, czy ekran pokaze wskaznik postepu, czy zdanie o stanie.
 *
 * Funkcja mierzaca w [classify] zwraca to samo, co podaje produkcja: szerokosc i wysokosc
 * zdekodowanego obrazu (VerificationRequestCard przekazuje bitmap.width i bitmap.height).
 */
class ScanDecodeTest {

    private data class TestScan(val widthPx: Int, val heightPx: Int)

    private fun classify(source: String, decoded: TestScan?): ScanDecode<TestScan> =
        ScanDecode.of(source, decoded) { scan -> ScanSize(scan.widthPx, scan.heightPx) }

    private fun czytelnySkan(
        widthPx: Int = ScanDecode.MIN_READABLE_SCAN_EDGE_PX * 4,
        heightPx: Int = ScanDecode.MIN_READABLE_SCAN_EDGE_PX * 5
    ) = TestScan(widthPx = widthPx, heightPx = heightPx)

    @Test
    fun `zgloszenie bez skanu nie jest w trakcie dekodowania`() {
        assertEquals(
            "pusty skan nigdy sie nie pojawi, a wskaznik postepu obiecuje, ze cos nadejdzie",
            ScanDecode.Missing,
            classify("", null)
        )
    }

    @Test
    fun `nieodczytany skan nie jest w trakcie dekodowania`() {
        assertEquals(
            "dekodowanie sie skonczylo i nic z niego nie wyszlo: wskaznik postepu krecilby sie bez konca, " +
                "a administrator i tak widzi pod nim przycisk zatwierdzenia dokumentu, ktorego nie zobaczy",
            ScanDecode.Unreadable,
            classify("to-nie-jest-obrazek", null)
        )
    }

    @Test
    fun `odczytany skan niesie obraz`() {
        val skan = czytelnySkan()

        assertEquals(
            ScanDecode.Decoded(skan),
            classify("ZGFuZQ==", skan)
        )
    }

    @Test
    fun `pusty skan jest brakiem skanu, a nie bledem odczytu`() {
        assertEquals(
            "te dwa stany maja rozne zdania na ekranie: brak zalacznika to nie to samo co uszkodzony zalacznik",
            ScanDecode.Missing,
            classify("   ", null)
        )
    }

    @Test
    fun `skan jednopikselowy nie jest dokumentem do obejrzenia`() {
        assertEquals(
            "obraz 1x1 piksela przechodzi klasyfikacje jako odczytany skan, wiec administrator zatwierdza " +
                "weryfikacje bez obejrzenia dokumentu",
            ScanDecode.Unreadable,
            classify(JEDNOPIKSELOWY_PNG_BASE64, TestScan(widthPx = 1, heightPx = 1))
        )
    }

    @Test
    fun `skan tuz ponizej progu czytelnosci nie jest dokumentem do obejrzenia`() {
        val zaMaly = ScanDecode.MIN_READABLE_SCAN_EDGE_PX - 1

        assertEquals(
            "krotsza krawedz $zaMaly px nie daje wysokosci litery, ktora czlowiek odczyta, " +
                "a administrator zatwierdza weryfikacje bez obejrzenia dokumentu",
            ScanDecode.Unreadable,
            classify("ZGFuZQ==", TestScan(widthPx = zaMaly, heightPx = 2000))
        )
    }

    @Test
    fun `skan dokladnie na progu czytelnosci jest dokumentem do obejrzenia`() {
        val naProgu = czytelnySkan(widthPx = ScanDecode.MIN_READABLE_SCAN_EDGE_PX, heightPx = 2000)

        assertEquals(
            "prog jest granica czytelnosci, nie granica odrzucenia: skan dokladnie na nim administrator " +
                "jeszcze przeczyta, a zablokowane zatwierdzenie zostawiloby poprawne zgloszenie bez decyzji",
            ScanDecode.Decoded(naProgu),
            classify("ZGFuZQ==", naProgu)
        )
    }

    @Test
    fun `skan waski jak pasek nie jest dokumentem do obejrzenia`() {
        assertEquals(
            "20 000 pikseli powierzchni nie czyni dokumentu: pasek 2000x10 nie ma na czym pomiescic linii " +
                "tekstu, a administrator zatwierdza weryfikacje bez obejrzenia dokumentu",
            ScanDecode.Unreadable,
            classify("ZGFuZQ==", TestScan(widthPx = 2000, heightPx = 10))
        )
    }

    @Test
    fun `prog czytelnosci skanu jest przypiety liczba`() {
        assertEquals(
            "prog wyliczony z czytelnosci druku na legitymacji ID-1: 54 mm krotszej krawedzi, litera 1,5 mm, " +
                "dziewiec pikseli na litere, czyli 36 x 9 px = 152 dpi; obnizenie go wpuszcza skany, " +
                "ktorych administrator nie przeczyta, a podniesienie blokuje poprawne zgloszenia",
            324,
            ScanDecode.MIN_READABLE_SCAN_EDGE_PX
        )
    }

    @Test
    fun `zatwierdzenie jest czynne, gdy administrator ma dokument na ekranie`() {
        val skan = czytelnySkan()

        assertTrue(
            "przy czytelnym skanie administrator nie ma czym zatwierdzic poprawnego zgloszenia",
            classify("ZGFuZQ==", skan).allowsApproval()
        )
    }

    @Test
    fun `zatwierdzenie jest wylaczone, dopoki administrator nie widzi dokumentu`() {
        listOf(
            ScanDecode.Decoding to "dekodowanie jeszcze trwa",
            ScanDecode.Missing to "zgloszenie nie zawiera skanu",
            ScanDecode.Unreadable to "skanu nie udalo sie odczytac"
        ).forEach { (stan, powod) ->
            assertFalse(
                "$powod, a administrator zatwierdza weryfikacje bez obejrzenia dokumentu: psycholog bez " +
                    "waznego dokumentu dostaje odznake zweryfikowanego konta",
                stan.allowsApproval()
            )
        }
    }

    @Test
    fun `skan jednopikselowy nie otwiera zatwierdzenia`() {
        assertFalse(
            "base64 obrazu 1x1 piksela omija blokade przycisku i administrator zatwierdza weryfikacje " +
                "bez obejrzenia dokumentu",
            classify(JEDNOPIKSELOWY_PNG_BASE64, TestScan(widthPx = 1, heightPx = 1)).allowsApproval()
        )
    }
}

private const val JEDNOPIKSELOWY_PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR42mNoAAAAggCB2kUIOwAAAABJRU5ErkJggg=="
