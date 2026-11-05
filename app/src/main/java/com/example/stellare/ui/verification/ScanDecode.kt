package com.example.stellare.ui.verification

/**
 * Wskaźnik postępu obiecuje, że coś jeszcze nadejdzie. Dekodowanie skanu kończyło się wartością
 * `null` zarówno dla zgłoszenia bez skanu, jak i dla danych, których nie da się odczytać, a ekran
 * rysował dla `null` ten sam wskaźnik co przed dekodowaniem — więc kręcił się bez końca, a
 * administrator i tak widział pod nim przycisk zatwierdzenia dokumentu, którego nigdy nie zobaczy.
 *
 * Klasyfikacja po samym "niepusty obraz" przepuszczała base64 obrazu 1x1 piksela jako odczytany
 * skan, więc blokada przycisku zatwierdzenia postawiona tylko na [Missing] i [Unreadable] dałaby się
 * obejść takim zgłoszeniem. Granicą jest [MIN_READABLE_SCAN_EDGE_PX]: skan poniżej niej nie jest
 * dokumentem do obejrzenia, więc jest tym samym stanem co skan, którego nie udało się odczytać.
 *
 * Typ jest sparametryzowany obrazem, żeby [of] dało się sprawdzić bez `android.graphics.Bitmap`.
 * Dlatego wymiary podaje wywołujący — funkcją mierzącą przekazaną do [of], a nie sam [ScanDecode].
 */
sealed interface ScanDecode<out T> {
    data object Decoding : ScanDecode<Nothing>
    data class Decoded<T>(val image: T) : ScanDecode<T>
    data object Missing : ScanDecode<Nothing>
    data object Unreadable : ScanDecode<Nothing>

    companion object {
        const val MISSING_MESSAGE = "Zgłoszenie nie zawiera skanu dokumentu."
        const val UNREADABLE_MESSAGE = "Nie udało się odczytać skanu dokumentu."

        /**
         * Najkrótsza krawędź skanu, przy której człowiek jeszcze przeczyta dokument.
         *
         * Najmniejszy przyjmowany dokument to legitymacja w formacie ID-1 (85,6 x 54 mm), czyli
         * krótsza krawędź 54 mm. Nazwisko i numer dokumentu są na niej drukowane literami o wysokości
         * około 1,5 mm, a litera przestaje być literą poniżej dziewięciu pikseli wysokości: przy
         * mniejszej liczbie wierszy zlewa się prześwit w "e" i "c" oraz talia w "3", "8" i "B".
         * 54 mm / 1,5 mm = 36 wysokości litery na krótszej krawędzi, 36 x 9 px = 324 px — co wypada
         * na 152 dpi, ledwie powyżej 150 dpi uznawanego za dolną granicę czytelności tekstu w skanie.
         *
         * Progu nie dotknie poprawne zgłoszenie: [compressDocumentScanToBase64] zmniejsza wymiary
         * dopóty, dopóki obraz ma więcej niż [MAX_DECODE_PIXELS] pikseli, więc zdjęcie 4000 x 3000
         * z telefonu trafia tu jako 2000 x 1500, a najmniejsza spotykana klatka VGA jako 640 x 480.
         */
        const val MIN_READABLE_SCAN_EDGE_PX = 324

        fun <T> of(source: String, decoded: T?, sizeOf: (T) -> ScanSize): ScanDecode<T> = when {
            source.isBlank() -> Missing
            decoded == null -> Unreadable
            sizeOf(decoded).shorterEdgePx < MIN_READABLE_SCAN_EDGE_PX -> Unreadable
            else -> Decoded(decoded)
        }
    }
}

data class ScanSize(val widthPx: Int, val heightPx: Int) {
    val shorterEdgePx: Int get() = minOf(widthPx, heightPx)
}

/**
 * Zatwierdzenie weryfikacji nadaje odznakę zweryfikowanego psychologa, więc wolno je nacisnąć
 * wyłącznie wtedy, gdy administrator ma dokument na ekranie. Odrzucenie zostaje czynne zawsze:
 * brak czytelnego skanu jest najczęstszym powodem odrzucenia zgłoszenia.
 */
fun ScanDecode<*>.allowsApproval(): Boolean = this is ScanDecode.Decoded<*>
