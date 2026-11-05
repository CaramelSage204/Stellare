# Etap 6 — Redesign pozostałych ekranów: plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODSKILL: superpowers:subagent-driven-development. Kroki mają składnię checkboxów.

**Cel:** Kalendarz dostaje pasek dni i oś godzinową z blokami sesji, a czaty, profil i detal psychologa — wygląd z referencji graficznej. Trzy pliki powyżej 550 linii zostają rozbite.

**Architektura:** Cała matematyka czasu i układu bloków wychodzi z Compose do czystych funkcji (`DayTimeline`, `MessageGrouping`), które testujemy jednostkowo. Composable zostają cienkie i tylko rysują to, co policzyła czysta funkcja. Nazwy uczestników sesji dochodzą jako jedna mapa w ViewModelu, nie jako odczyt per karta.

**Stos:** Kotlin 2.2.10, Jetpack Compose, Material 3, minSdk 24, JUnit 4 + `org.junit.Assert`, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcje 8 i 9 (etap 6).

## Warunek wstępny

Dwie rundy poprawiania nieprawdziwych tekstów muszą być zamknięte — dotykają histogramu opinii, etykiet waluty, jednostki ceny i przycisku zatwierdzania terminu. Zadania 3, 5 i 6 budują na ich wyniku. Sprawdź, że istnieje `app/src/test/java/com/example/stellare/model/ReviewSummaryTest.kt`; jeśli nie, zatrzymaj się i zgłoś.

## Global Constraints

- minSdk 24 bez desugaringu: **`java.util.Calendar`, nigdy `java.time`**. Strefa czasowa wstrzykiwana jako `TimeZone`, czas jako `() -> Long`; żaden kod produkcyjny nie woła `System.currentTimeMillis()` wprost.
- **Zero komentarzy w kodzie.** Nazwy i krótkie funkcje zamiast komentarzy.
- Wszystkie teksty interfejsu po polsku.
- Plan Spark: brak Cloud Functions i brak Firebase Storage. **Żaden ekran nie obiecuje powiadomień.**
- Paleta wyłącznie z `ui/theme/Color.kt` i `MaterialTheme.colorScheme`. **Żadnych literałów koloru w ekranach — ani `Color(0xFF...)`, ani `Color.Black`, ani `Color.White`.** Tło białe, karty brzoskwiniowe.
- Przed tym etapem jest **piętnaście** wystąpień `Color.Black`/`Color.White` w ekranach kalendarza, czatu i detalu — dokładnie w plikach, które ten etap przepisuje. Każde z nich psuje tryb ciemny (czarny tekst na ciemnym tle). Policz je na starcie i na końcu: w przepisywanych plikach ma zostać **zero**.
- Pliki, które ten etap przepisuje, wychodzą z niego **bez komentarzy**. W kodzie jest ich dziś 114, w większości z pierwotnego repozytorium; nie róbcie osobnego sprzątania, ale z przepisywanego pliku komentarz znika razem z przepisaniem.
- Reguły Firestore są zamknięte w etapach 1–5. Ten etap **nie zmienia `firebase/firestore.rules`**; gdy zadanie wydaje się tego wymagać — zatrzymaj się i zgłoś.
- Żadne zadanie nie commituje ani nie pushuje.
- Każdy strumień danych w ViewModelu bramkowany po **identyfikatorze z uwierzytelnienia** (`signedInUserIdFlow`), nie po dokumencie użytkownika — chyba że potrzebuje roli. Wzorzec: `WalletViewModel`.
- **Dwa przebiegi gradle naraz blokują się na wspólnym zamku.** Jedno zadanie w danej chwili uruchamia testy.

## Struktura plików

| Plik | Odpowiedzialność |
| --- | --- |
| `ui/calendar/DayTimeline.kt` (nowy) | czysta matematyka: pasek siedmiu dni, umiejscowienie bloku na osi 08:00–20:00, kolumny przy nakładaniu |
| `ui/calendar/components/DayStrip.kt` (nowy) | pasek dni, dzień bieżący w kółku |
| `ui/calendar/components/HourAxis.kt` (nowy) | oś godzinowa z blokami |
| `ui/calendar/components/SessionBlock.kt` (nowy) | jeden blok: uczestnik + pigułka z zakresem godzin |
| `ui/calendar/CalendarScreen.kt` (zmiana) | składa pasek, oś i akcje; chudnie |
| `ui/calendar/CalendarViewModel.kt` (zmiana) | dochodzi mapa nazw uczestników i wybrany dzień |
| `ui/chat/MessageGrouping.kt` (nowy) | czysta funkcja: separatory dat i grupowanie wiadomości |
| `ui/chat/ChatRoomScreen.kt`, `MessageBubble.kt`, `MyClientsScreen.kt` | rozbicie `ChatScreens.kt` (651 linii) |
| `ui/psychologistdetail/components/DetailHeader.kt`, `ReviewsSection.kt`, `FreeSlotsSection.kt` | rozbicie `PsychologistDetailScreen.kt` (640 linii) |
| `ui/profile/ProfileEditScreen.kt`, `ui/faq/FAQScreen.kt` | rozbicie `ProfileScreens.kt` (572 linii); FAQ wychodzi z profilu, bo należy do etapu 8 |

---

### Zadanie 1: Matematyka osi godzinowej

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/calendar/DayTimeline.kt`
- Test: `app/src/test/java/com/example/stellare/calendar/DayTimelineTest.kt`

**Interfejsy:**
- Konsumuje: `AppointmentModel` (`appointmentId`, `startsAtMillis`, `status`, `priceCoins`), `AppointmentSchedule`
- Produkuje:
  - `DayTimeline.weekOf(todayMillis: Long, zone: TimeZone): List<TimelineDay>`
  - `DayTimeline.blocksFor(appointments: List<AppointmentModel>, dayMillis: Long, zone: TimeZone): List<TimelineBlock>`
  - `data class TimelineDay(val millis: Long, val label: String, val dayOfMonth: Int, val isToday: Boolean)`
  - `data class TimelineBlock(val appointmentId: String, val startMinuteOfDay: Int, val lengthMinutes: Int, val column: Int, val columns: Int, val clippedAbove: Boolean, val clippedBelow: Boolean)`

Oś obejmuje 08:00–20:00. Sesja trwa 60 minut — jedyna długość, jaką aplikacja zna; cena jest „za sesję" i nic nie przechowuje innego czasu trwania. Termin poza oknem widzialności **nie znika** — zostaje przycięty i oznaczony, żeby ekran mógł o nim powiedzieć. Terminy nakładające się dzielą szerokość: `column` z `columns`.

- [ ] **Krok 1: Napisz testy, które nie przechodzą**

```kotlin
package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.AppointmentSchedule
import com.example.stellare.ui.calendar.DayTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DayTimelineTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Europe/Warsaw")

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        AppointmentSchedule.startsAtMillis(date, hour, minute, zone)

    private fun slot(id: String, date: String, hour: Int, minute: Int = 0) =
        AppointmentModel(appointmentId = id, startsAtMillis = at(date, hour, minute))

    @Test
    fun `pasek ma siedem dni i zaznacza dokladnie jeden dzien biezacy`() {
        val week = DayTimeline.weekOf(at("2026-10-08", 12), zone)
        assertEquals(7, week.size)
        assertEquals(1, week.count { it.isToday })
        assertEquals(8, week.first { it.isToday }.dayOfMonth)
    }

    @Test
    fun `blok o dziewiatej ma pelna dlugosc i nie jest przyciety`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("a", "2026-10-08", 9)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertEquals(9 * 60, blocks[0].startMinuteOfDay)
        assertEquals(60, blocks[0].lengthMinutes)
        assertFalse(blocks[0].clippedAbove)
        assertFalse(blocks[0].clippedBelow)
    }

    @Test
    fun `termin przed oknem zostaje przyciety a nie wyrzucony`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("wczesny", "2026-10-08", 6)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].clippedAbove)
    }

    @Test
    fun `termin o dwudziestej trzeciej zostaje przyciety od dolu`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("pozny", "2026-10-08", 23)), at("2026-10-08", 0), zone)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0].clippedBelow)
    }

    @Test
    fun `terminy z innego dnia nie trafiaja na os`() {
        val blocks = DayTimeline.blocksFor(listOf(slot("jutro", "2026-10-09", 10)), at("2026-10-08", 0), zone)
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun `dwa terminy o tej samej godzinie dziela szerokosc`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("a", "2026-10-08", 10), slot("b", "2026-10-08", 10)),
            at("2026-10-08", 0),
            zone
        )
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it.columns == 2 })
        assertEquals(setOf(0, 1), blocks.map { it.column }.toSet())
    }

    @Test
    fun `terminy rozlaczne zajmuja pelna szerokosc`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("a", "2026-10-08", 10), slot("b", "2026-10-08", 12)),
            at("2026-10-08", 0),
            zone
        )
        assertTrue(blocks.all { it.columns == 1 && it.column == 0 })
    }

    @Test
    fun `bloki wracaja w kolejnosci rosnacej po czasie`() {
        val blocks = DayTimeline.blocksFor(
            listOf(slot("pozniej", "2026-10-08", 15), slot("wczesniej", "2026-10-08", 9)),
            at("2026-10-08", 0),
            zone
        )
        assertEquals(listOf("wczesniej", "pozniej"), blocks.map { it.appointmentId })
    }
}
```

- [ ] **Krok 2: Uruchom i potwierdź, że nie przechodzą**

`.\gradlew.bat testDebugUnitTest --tests "com.example.stellare.calendar.DayTimelineTest"` — oczekiwany błąd kompilacji, brak `DayTimeline`.

- [ ] **Krok 3: Napisz `DayTimeline.kt`**

Co testy wymuszają: `VISIBLE_FROM_MINUTE = 8 * 60`, `VISIBLE_TO_MINUTE = 20 * 60`, `SESSION_MINUTES = 60`, kolumny liczone po grupach zachodzących na siebie przedziałów, wynik sortowany po `startMinuteOfDay`. Przynależność do dnia ustalaj porównaniem `AppointmentSchedule.dateOf(...)` obu znaczników — **nie dzieleniem milisekund**, bo przestawienie czasu zepsułoby arytmetykę.

- [ ] **Krok 4: Testy zielone**

- [ ] **Krok 5: Mutacja dowodowa**

Zmień `VISIBLE_FROM_MINUTE` na `0` i potwierdź, że test o przycięciu od góry czerwienieje. Przywróć. Podaj w raporcie komunikat niepowodzenia. Test, którego mutacja nie zabija, nie jest dowodem — w tym projekcie zdarzyło się to pięć razy.

---

### Zadanie 2: Nazwy uczestników sesji

**Pliki:**
- Modyfikuj: `data/repository/UserRepository.kt`, `ui/calendar/CalendarViewModel.kt`
- Test: `app/src/test/java/com/example/stellare/calendar/ParticipantNamesTest.kt`

**Interfejsy:**
- Konsumuje: `UserRepository.signedInUserIdFlow()`, odczyt użytkownika po identyfikatorze
- Produkuje: `UserRepository.namesOf(ids: Set<String>): Map<String, String>`, `CalendarViewModel.participantNames: StateFlow<Map<String, String>>`, `CalendarViewModel.selectedDayMillis: StateFlow<Long>`, `CalendarViewModel.selectDay(millis: Long)`

Blok sesji w referencji pokazuje uczestnika, a dziś w kalendarzu **nie ma ani jednej nazwy** — tylko identyfikatory. Nazwa drugiej strony dochodzi jako jedna mapa w ViewModelu, zbudowana z różnych identyfikatorów widocznych terminów. Odczyt per karta dałby zapytanie na każde przerysowanie.

- [ ] **Krok 1: Napisz testy, które nie przechodzą**

Na atrapach z `fakes/FakeDataSources.kt`, z licznikiem odczytów:
1. dwa terminy z tym samym pacjentem → **jeden** odczyt, nie dwa,
2. mapa ma nazwę pacjenta po stronie psychologa i nazwę psychologa po stronie pacjenta,
3. niezalogowany → **zero** odczytów, a **zalogowany bez dokumentu użytkownika → nasłuch aktywny**.

Punkt 3 musi rozróżniać te dwa stany. Testy, które tego nie rozróżniały, w etapie 5 przepuściły niedokończoną poprawkę — przeszły zarówno przy bramce po dokumencie, jak i po uwierzytelnieniu.

- [ ] **Krok 2: Potwierdź, że nie przechodzą**
- [ ] **Krok 3: Implementacja** — `namesOf` czyta tylko brakujące identyfikatory i scala z tym, co ma. Mapa powstaje z `flatMapLatest` po liście terminów, nie w `collect` w composable.
- [ ] **Krok 4: Testy zielone**
- [ ] **Krok 5: Mutacja dowodowa** — usuń odsiewanie znanych identyfikatorów, potwierdź czerwień testu licznika; przywróć. Osobno: zmień bramkę na dokument i potwierdź czerwień testu z punktu 3.

---

### Zadanie 3: Kalendarz — pasek dni i oś godzinowa

**Pliki:**
- Utwórz: `ui/calendar/components/DayStrip.kt`, `HourAxis.kt`, `SessionBlock.kt`
- Modyfikuj: `ui/calendar/CalendarScreen.kt`, `views/PatientCalendarView.kt`, `views/PsychologistCalendarView.kt`

**Interfejsy:** konsumuje `DayTimeline` (Zadanie 1) oraz `participantNames` i `selectedDayMillis` (Zadanie 2). Produkuje ekran bez własnej arytmetyki czasu.

- [ ] **Krok 1: `DayStrip`** — siedem dni z `weekOf`, dzień bieżący w kółku, zaznaczenie dnia wybranego, klik wywołuje `selectDay`. Zero obliczeń daty w composable.
- [ ] **Krok 2: `SessionBlock`** — uczestnik z mapy nazw, pigułka z zakresem godzin, kolor z `colorScheme` zależny od statusu (wolny / zajęty / zwrot w toku).
- [ ] **Krok 3: `HourAxis`** — godziny po lewej, bloki z `TimelineBlock` (`startMinuteOfDay`, `column`, `columns`). Dla `clippedAbove` i `clippedBelow` **widoczna informacja**, że dzień ma terminy poza widokiem. Milczące zniknięcie terminu to ta sama klasa błędu, którą etap 5 zamykał dwa razy.
- [ ] **Krok 4: Złóż w `CalendarScreen`** dla obu ról. Miesięczna siatka `StellareCalendar` zostaje tylko tam, gdzie służy do wyboru daty (`AddSlotDialog`); jeśli nie ma żadnego wywołującego — usuń plik i napisz o tym.
- [ ] **Krok 5: Pełny przebieg testów jednostkowych.**

---

### Zadanie 4: Czaty — rozbicie i grupowanie wiadomości

**Pliki:**
- Utwórz: `ui/chat/MessageGrouping.kt`, `ChatRoomScreen.kt`, `MessageBubble.kt`, `MyClientsScreen.kt`
- Usuń: `ui/chat/ChatScreens.kt` (651 linii)
- Test: `app/src/test/java/com/example/stellare/chat/MessageGroupingTest.kt`

**Interfejsy:** produkuje `MessageGrouping.rowsFor(messages, myId, zone): List<ChatRow>` z `ChatRow.DateSeparator(label)` i `ChatRow.Bubble(message, isOutgoing, isFirstOfGroup, isLastOfGroup)`.

Rozbicie jest mechaniczne: trzy composable z jednego pliku do trzech plików, bez zmiany treści. Nowa jest tylko czysta funkcja układu listy i styl dymków z palety.

- [ ] **Krok 1: Testy `MessageGrouping`** — separator raz na dzień i przed pierwszą wiadomością; kolejne wiadomości tego samego autora tworzą grupę; zmiana autora kończy grupę; pusta lista daje pusty wynik.
- [ ] **Krok 2: Potwierdź, że nie przechodzą.**
- [ ] **Krok 3: Implementacja.** Dzień po `AppointmentSchedule.dateOf`.
- [ ] **Krok 4: Testy zielone.**
- [ ] **Krok 5: Przenieś composable** do trzech plików bez zmiany logiki, podepnij `rowsFor`, przestyluj dymki (`primaryContainer` wychodzące, `surfaceVariant` przychodzące). Kolor dymków będzie konfigurowalny w etapie 8 — **wyciągnij go do jednego miejsca**, nie zaszywaj w composable.
- [ ] **Krok 6: Notatki terapeutyczne** zostają widoczne wyłącznie dla psychologa. Logiki nie zmieniaj; potwierdź w raporcie, że po rozbiciu warunek nadal jest w kodzie. W etapie 2 ten warunek był wyłącznie w interfejsie i wyciekał dane.
- [ ] **Krok 7: Pełny przebieg testów.**

---

### Zadanie 5: Detal psychologa — rozbicie i wygląd

**Pliki:**
- Utwórz: `ui/psychologistdetail/components/DetailHeader.kt`, `ReviewsSection.kt`, `FreeSlotsSection.kt`
- Modyfikuj: `PsychologistDetailScreen.kt` (640 linii → szkielet składający sekcje)

- [ ] **Krok 1: `DetailHeader`** — awatar, imię, pigułka `Zweryfikowany`, cena w jednostce „WKT / sesja", średnia ocen z liczbą głosów. Liczby **wyłącznie z danych**; żadnych przykładowych.
- [ ] **Krok 2: `ReviewsSection`** — lista opinii plus `ReviewComposer` pod istniejącym warunkiem (znacznik odbytej wizyty i brak własnej opinii). Warunku nie zmieniaj; korzystaj z `ReviewSummary` z rundy tekstów.
- [ ] **Krok 3: `FreeSlotsSection`** — wolne terminy po filtrze przyszłości; slot bez ceny pozostaje niewybieralny z wyjaśnieniem (ustalenie z etapu 5: rezerwacja za 0 monet jest odrzucana i przez kod, i przez reguły).
- [ ] **Krok 4: Ekran główny** zostaje szkieletem bez logiki.
- [ ] **Krok 5: Pełny przebieg testów.** `ProfileViewModelReviewFlowTest` i `ReviewSummaryTest` muszą zostać zielone **bez modyfikacji**. Jeśli trzeba je zmienić — rozbicie zmieniło zachowanie; zatrzymaj się.

---

### Zadanie 6: Profil — rozbicie, wygląd, FAQ na zewnątrz

**Pliki:**
- Utwórz: `ui/profile/ProfileEditScreen.kt`, `ui/faq/FAQScreen.kt`
- Usuń: `ui/profile/ProfileScreens.kt` (572 linii)
- Modyfikuj: wywołania nawigacji do `FAQScreen`

- [ ] **Krok 1: Przenieś `FAQScreen` i `FAQItem`** do `ui/faq/`. FAQ należy do etapu 8 i nie ma powodu siedzieć w pliku profilu. Treści nie zmieniaj.
- [ ] **Krok 2: `ProfileEditScreen`** w osobnym pliku, przestylowany na paletę, nagłówek z awatarem i prawdziwą średnią ocen.
- [ ] **Krok 3: Popraw wszystkie wywołania** po przeniesieniu; potwierdź `.\gradlew.bat compileDebugKotlin`.
- [ ] **Krok 4: Pełny przebieg testów jednostkowych.**
- [ ] **Krok 5: Policz linie** wszystkich plików etapu i podaj w raporcie. Żaden nie powinien przekraczać ~300 linii; jeśli przekracza, napisz dlaczego.

---

### Zadanie 7: Trzy długi zebrane po etapie 5

**Pliki:** `ui/components/SharedComponents.kt`, `ui/dashboard/DashboardDialogs.kt`, `ui/calendar/CalendarViewModel.kt`, `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`

- [ ] **Krok 1: Scal bliźniacze `SpecializationChip`.** Repozytorium ma **dwa** komponenty o tej nazwie i różnych sygnaturach: w `ui/components` z parametrem `label`, w `ui/dashboard` z `text`. Ten pierwszy nadal maluje wybrany tekst `Color.Black` na `primary`. Zostaw jeden, z paletą, i popraw wszystkich wywołujących.
- [ ] **Krok 2: Terminy przeszłe malejąco.** Po etapie 5 wszystkie trzy listy sortują się rosnąco. Dla terminów **przyszłych** rosnąco jest poprawne (najbliższy pierwszy), dla **przeszłych** właściwe jest malejąco — historia czyta się od najnowszej. Rozdziel to w `AppointmentOrder` i dopisz testy. Wizyty bez `startsAtMillis` (stare dane) nie mogą trafiać na początek listy przyszłych.
- [ ] **Krok 0: Osiem literalow koloru w `ui/wallet/WalletScreens.kt`.** Zostaly z etapu 5, bo plik byl poza zakresem tamtych rund. Zamien na `MaterialTheme.colorScheme`.
- [ ] **Krok 0b: Etykieta „Zwrot do wypłaty" na karcie wizyty** (stan `refundPending` u psychologa) znaczy „zwrot do wykonania", a brzmi jak wypłata — a wypłata właśnie przestała istnieć. Przepisz.
- [ ] **Krok 0c: Token `coinGold` w `ui/theme/Color.kt`** zamiast `Color(0xFFFFD700)`. Złote monety to świadoma tożsamość wizualna, a złota nie ma w palecie Material — dlatego token w motywie, nie literał w ekranie. Użyj go w dialogu rezerwacji, `WalletScreens.kt` i `ProfileSheet.kt`; to usuwa literały także tam.
- [ ] **Krok 0d: Jawny lokal w formatowaniu oceny.** `"%.1f".format` bez lokalu daje „4,5" albo „4.5" zależnie od urządzenia, a test został osłabiony tak, by akceptować oba — czyli nie wyłapie regresu. Ustaw lokal polski i zacieśnij test do przecinka.
- [ ] **Krok 0e: Status weryfikacji pojawia się dwa razy** — pigułka w nagłówku detalu i zdanie w `AboutSection`. Zostaw pigułkę, usuń zdanie.
- [ ] **Krok 2b: `System.currentTimeMillis()` w `ChatRoomScreen` i `MyClientsScreen`.** Zostało z rozbicia czatu, bo to było przeniesienie bez zmiany logiki. Wstrzyknij zegar jak w pozostałych miejscach.
- [ ] **Krok 3: Atrapa portfela nadaje identyfikatory.** `FakeWalletDataSource` nadpisuje wpisy o pustym `transactionId`, więc dwie operacje w jednym teście kasują się wzajemnie — prawdziwy Firestore generuje identyfikator. To **atrapa kłamiąca o zachowaniu źródła**; w tym projekcie kategoria „test stwierdzający fikcję infrastruktury testowej" wystąpiła już raz. Niech atrapa nadaje identyfikatory jak Firestore, i dopisz to do `FakeDataSourcesFidelityTest`.

---

## Weryfikacja końcowa etapu

Testy jednostkowe nie dowodzą wyglądu. Po Zadaniu 6 obowiązkowy jest **przebieg na emulatorze Androida z lokalnymi emulatorami Firebase**, na danych z `seed.mjs`, i zrzuty ekranu: kalendarz w obu rolach (z terminem w oknie i poza nim), pokój czatu z separatorem dat, detal psychologa z opiniami, profil.

W każdym z pięciu poprzednich etapów test na urządzeniu znalazł defekt, którego nie złapały setki testów jednostkowych — zawsze **między warstwami**. Załóż, że tak będzie i tutaj, i szukaj rozbieżności między tym, co widać na ekranie, a tym, co jest w bazie.
