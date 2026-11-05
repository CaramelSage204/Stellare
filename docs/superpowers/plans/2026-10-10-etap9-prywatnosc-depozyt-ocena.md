# Etap 9: prywatność, depozyt, ocena — plan wdrożenia

> **Dla wykonawców:** WYMAGANA SUB-SKILL: superpowers:subagent-driven-development. Kroki mają składnię checkboxów (`- [ ]`).
> **Zakaz commitowania obowiązuje.** Żaden krok tego planu nie zawiera `git commit` ani `git push` — gdyby skill tego wymagał, krok POMIJAMY. Dowodem wykonania jest zielony zestaw testów i raport, nie commit.

**Cel:** zamknąć trzy dziury przeniesione z etapów 5–8 (odczyt cudzego profilu, zwrot monet zależny od psychologa, zerowanie własnej oceny), domknąć objaw „pieniądze przeszły, a interfejs mówi, że nie”, spłacić drobny dług i potwierdzić całość na emulatorze.

**Architektura:** trzy niezależne zmiany modelu danych, każda najpierw w regułach Firestore z testami regułowymi, potem w warstwie danych Androida, potem w UI. Żadna nie wymaga planu Blaze — wszystkie są zmianą kształtu danych, nie usługi.

**Stos:** Kotlin 2.2.10, Jetpack Compose, Material 3, AGP 9.1.1, Gradle 9.3.1, Firebase Firestore + Auth, `@firebase/rules-unit-testing` v3 + Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`

## Global Constraints

- **minSdk 24 BEZ desugaringu.** Daty i czas wyłącznie przez `java.util.Calendar`. `java.time` jest zakazane — nie skompiluje się na tym minSdk.
- **Plan Spark.** Brak Cloud Functions, brak Firebase Storage. Żadne zadanie nie wolno „domknąć” kodem serwerowym.
- **Reguły Firestore nie potrafią odpytywać kolekcji.** Dozwolony jest wyłącznie odczyt po ścieżce: `get()`, `getAfter()`, `exists()`, `existsAfter()`. Jeśli jakiś warunek wymaga przejrzenia kolekcji, nie da się go wyrazić — trzeba przeprojektować dane.
- **Limit dostępów do dokumentów w regule.** Reguła jednodokumentowa ma limit 10 wywołań dostępu. Każdą ścieżkę wiązać przez `let` i nie powtarzać `get()` na tej samej ścieżce więcej niż trzeba.
- **Zero literałów koloru poza `app/src/main/java/com/example/stellare/ui/theme/`.** Każdy nowy kolor to nazwany token w `Color.kt` z wariantem jasnym i ciemnym.
- **Każdy przycisk mający stan `enabled` używa `stellareButtonColors()`** z `ui/theme/ButtonColors.kt`. Domyślne kolory PrimeNG/Material dają 1.35:1 na wyłączonym przycisku i są niedopuszczalne.
- **Stan wpisany przez użytkownika przetrwa obrót ekranu** — `rememberSaveable`, nie `remember`. Uwaga na `LaunchedEffect`, który po odtworzeniu nadpisuje przywrócone pole.
- **Zero komentarzy w kodzie Kotlin, które powtarzają kod.** Wyjątek: blok wyjaśniający nieoczywisty niezmiennik bezpieczeństwa (dlaczego dwa zapisy są jedną partią, dlaczego nie wolno czegoś przywrócić). Komentarze w `firestore.rules` zostają — kodują powody, bez których reguła zostanie zepsuta przy następnej edycji.
- **Testy jednostkowe:** `./gradlew testDebugUnitTest`. **Testy reguł:** z katalogu `firebase`: `npx vitest run`. **Nigdy** `firebase emulators:exec`, gdy emulatory już działają — zamiast tego `npx vitest run` przy żywych emulatorach.
- **Firebase CLI nie jest w PATH.** Wywołanie to `node_modules/.bin/firebase.cmd`.
- **Dowodem działania zabezpieczenia jest test mutacyjny.** Po napisaniu testu zepsuj regułę/kod tak, by atak przeszedł, i pokaż, że test padł z komunikatem mówiącym o faktycznej szkodzie. Test, który przechodzi na zepsutej regule, nie jest dowodem i nie liczy się jako wykonanie kroku.
- **Cztery rodzaje testu bez wartości dowodowej** — żaden nie jest wykonaniem kroku: (a) test stwierdzający błąd jako oczekiwany; (b) test przechodzący z innego powodu niż bada; (c) test padający lub pomijany w przygotowaniu, nie na asercji (`37 skipped` to NIE `37 passed` — sprawdzaj liczbę pominiętych); (d) test sprawdzający fikcję atrapy, która rozjechała się z produkcją.
- **Zakaz zmieniania istniejących asercji, by test przeszedł.** Wolno zmienić liczbę argumentów konstruktora atrapy, gdy zmienia się interfejs. Nie wolno osłabić ani usunąć asercji.
- **Skrypty pomocnicze czytają cały plik, przekształcają w pamięci i zapisują dopiero gotowy wynik.** Otwarcie pliku w trybie `'w'` przed przekształceniem skasowało w tym projekcie `AuthViewModel.kt` z niezacommitowanymi zmianami. Przed uruchomieniem skryptu na pliku zrób jego kopię w katalogu scratchpad.
- **Diakrytyki.** Nie przekazuj polskiego tekstu przez heredoc powłoki — zapisz skrypt lub plik narzędziem plikowym w UTF-8 i uruchom go ze ścieżki.

---

## Struktura plików

**Nowe:**
- `app/src/main/java/com/example/stellare/data/model/RatingModel.kt` — agregat oceny poza dokumentem właściciela
- `app/src/main/java/com/example/stellare/data/remote/FirestoreRatingDataSource.kt` — odczyt kolekcji `ratings`
- `app/src/main/java/com/example/stellare/data/model/EscrowModel.kt` — depozyt wizyty
- `app/src/main/java/com/example/stellare/data/model/EscrowSettlement.kt` — decyzja, co zrobić z depozytem (czysta funkcja)
- `app/src/main/java/com/example/stellare/data/remote/FirestoreEscrowDataSource.kt`
- `app/src/main/java/com/example/stellare/data/model/PrivateProfile.kt` — telefon, e‑mail, saldo
- `app/src/main/java/com/example/stellare/data/remote/FirestorePrivateProfileDataSource.kt`
- `app/src/main/java/com/example/stellare/data/model/WriteReconciliation.kt` — uzgodnienie stanu po błędzie zapisu
- `firebase/test/ratings.test.js`, `firebase/test/escrow.test.js`, `firebase/test/private-profile.test.js`

**Modyfikowane (główne):**
- `firebase/firestore.rules` — wszystkie trzy zmiany
- `app/src/main/java/com/example/stellare/data/model/UserModel.kt` — ubywa `ratingSum`, `ratingCount`, `phone`, `email`, `coinsBalance`, `lastTransferTo`, `lastTransferAmount`
- `app/src/main/java/com/example/stellare/data/remote/FirestoreAppointmentDataSource.kt` — rezerwacja i zwrot przez depozyt
- `app/src/main/java/com/example/stellare/data/remote/DataSources.kt` — nowe interfejsy, usunięcie pięciu domyślnych ciał rzucających wyjątek
- `app/src/main/java/com/example/stellare/data/model/RefundPolicy.kt` — polityka wyrażona przez stan depozytu
- `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt` — atrapy dla nowych źródeł

**Usuwane:**
- `AppointmentTiming.PatientLists` i `AppointmentTiming.patientLists` — nieosiągalne, piętnasty taki przypadek w projekcie
- `refundPending` z `AppointmentModel` i cała maszyneria „czeka na zwrot” w UI
- `firebase/test/refund-pending.test.js` — testuje maszynerię, która przestaje istnieć

---

## Kolejność i uzasadnienie

Trzy zmiany dotykają tego samego bloku `match /users/{uid}` i tego samego modelu, więc **są bezwzględnie sekwencyjne**. Kolejność wybrana tak, by nie pisać dwa razy tej samej funkcji:

1. **Ocena** (zadania 1–2) — niezależna od pozostałych, najmniejsza.
2. **Depozyt** (zadania 3–5) — usuwa `receivesPaidTransfer()`, `lastTransferTo/Amount` i `refundPending`.
3. **Prywatność** (zadania 6–7) — przenosi saldo do dokumentu prywatnego. Idzie PO depozycie, bo inaczej trzeba by przepisać na nową ścieżkę funkcje, które depozyt zaraz usuwa.
4. Uzgadnianie po błędzie (8), dług (9), UI i wydajność (10), weryfikacja na emulatorze (11).

---

### Zadanie 1: ocena poza dokumentem właściciela — reguły

**Problem:** `ratingSum`/`ratingCount` leżą w `users/{uid}`, a właściciel ma `allow delete: if isSignedIn() && request.auth.uid == uid` i `allow create` wymagający `ratingSum == 0`. Usunięcie i odtworzenie własnego profilu zeruje ocenę. Reguły nie mogą tego wykryć, bo nie potrafią policzyć recenzji w kolekcji. Jedyne wyjście: agregat nie może leżeć w dokumencie, który właściciel zapisuje i usuwa.

**Files:**
- Modify: `firebase/firestore.rules` (blok `match /users/{uid}`: usunąć gałąź podbicia oceny i warunki na `ratingSum`/`ratingCount`; dodać blok `match /ratings/{uid}`)
- Test: `firebase/test/ratings.test.js` (nowy)
- Modify: `firebase/test/users.test.js` (przypadki dotyczące oceny przenieść, nie duplikować)

**Interfaces:**
- Produces: kolekcja `ratings/{psychologistId}` o polach `{ sum: int, count: int }`, czytelna dla każdego zalogowanego, zapisywalna wyłącznie przez recenzenta w tym samym zatwierdzeniu co recenzja, nieusuwalna.

- [ ] **Krok 1: napisz testy regułowe (mają paść)**

W `firebase/test/ratings.test.js`, wzorując przygotowanie środowiska na istniejącym `firebase/test/users.test.js` (ten sam `setupEnv()`, ten sam projekt emulatora). Przypadki:

1. każdy zalogowany czyta `ratings/{id}`
2. niezalogowany nie czyta
3. recenzent tworzy `ratings/psych` z `sum` równym ocenie i `count: 1`, gdy w tej samej partii powstaje `reviews/psych_recenzent` — przechodzi
4. recenzent podbija istniejący agregat o swoją ocenę — przechodzi
5. recenzent podaje `sum` większe niż ocena w recenzji — odmowa
6. recenzent podaje `count` zwiększone o 2 — odmowa
7. zapis agregatu bez recenzji w tej samej partii — odmowa
8. zapis agregatu, gdy recenzja JUŻ ISTNIAŁA przed partią (edycja własnej recenzji) — odmowa
9. oceniany podbija własny agregat — odmowa
10. oceniany usuwa własny agregat — odmowa
11. oceniany usuwa swój dokument `users/{uid}` i tworzy go ponownie — agregat w `ratings` zostaje nietknięty i nadal ma starą wartość
12. obcy dopisuje do agregatu pole poza `['sum','count']` — odmowa
13. recenzja z oceną 0 i z oceną 6 — odmowa podbicia
14. `sum`/`count` nie są liczbami całkowitymi — odmowa

Przypadek 11 jest sednem zadania: to on dowodzi, że dziura jest zamknięta.

- [ ] **Krok 2: uruchom testy i potwierdź, że padają z właściwego powodu**

Z katalogu `firebase`: `npx vitest run test/ratings.test.js`
Oczekiwane: komplet padnięć na asercjach, **zero pominiętych**. Jeśli widzisz `skipped`, reguły się nie wczytały — najpewniej błąd składni, nie brak funkcjonalności. Napraw składnię przed dalszą pracą.

- [ ] **Krok 3: dodaj blok reguł**

W `firebase/firestore.rules`, przed `match /reviews/{id}`:

```
    // Agregat oceny leży POZA dokumentem ocenianego i jest nieusuwalny. Gdy leżał
    // w users/{uid}, właściciel zerował sobie ocenę usuwając i odtwarzając profil
    // (allow create wymaga ratingSum == 0). Reguły nie potrafią policzyć recenzji
    // w kolekcji, więc nie da się tego wykryć po fakcie — agregat musiał się
    // wyprowadzić. NIE przywracać ratingSum/ratingCount do users.
    match /ratings/{uid} {
      allow read: if isSignedIn();

      function recenzja() {
        return /databases/$(database)/documents/reviews/$(uid + '_' + request.auth.uid);
      }

      // Para !exists() + existsAfter() wymusza, by recenzja POWSTAŁA w tym
      // zatwierdzeniu. Samo getAfter() spełnia też recenzja istniejąca od dawna,
      // co pozwalało podbijać ocenę w nieskończoność kolejnymi edycjami własnej.
      function recenzjaZTegoZatwierdzenia() {
        let r = recenzja();
        let po = getAfter(r).data;
        return !exists(r)
          && existsAfter(r)
          && po.reviewerId == request.auth.uid
          && po.rating is int
          && po.rating >= 1
          && po.rating <= 5;
      }

      function ocena() {
        return getAfter(recenzja()).data.rating;
      }

      allow create: if isSignedIn()
        && request.auth.uid != uid
        && request.resource.data.keys().hasOnly(['sum', 'count'])
        && request.resource.data.count == 1
        && request.resource.data.sum == ocena()
        && recenzjaZTegoZatwierdzenia();

      allow update: if isSignedIn()
        && request.auth.uid != uid
        && onlyChanged(['sum', 'count'])
        && request.resource.data.count is int
        && request.resource.data.count == resource.data.count + 1
        && request.resource.data.sum == resource.data.sum + ocena()
        && recenzjaZTegoZatwierdzenia();

      // Usunięcie przywróciłoby !exists() w regule recenzji i pozwoliło
      // policzyć tę samą recenzję po raz drugi.
      allow delete: if false;
    }
```

W bloku `match /users/{uid}` usuń: warunki `ratingSum`/`ratingCount` z `allow create` (linie ~129–130), warunki `ratingSum`/`ratingCount` z gałęzi właściciela (~144–145) i całą gałąź podbicia oceny (~166–183).

- [ ] **Krok 4: uruchom cały zestaw reguł**

`npx vitest run` — wszystkie pliki. Oczekiwane: wszystkie przechodzą, zero pominiętych. Jeśli `users.test.js` ma przypadki na podbijanie oceny przez `users`, przenieś je do `ratings.test.js` zamiast usuwać — zachowaj asercje, zmień tylko ścieżkę dokumentu.

- [ ] **Krok 5: dowód mutacyjny**

Dla każdej z poniższych mutacji zepsuj regułę, uruchom testy, zapisz w raporcie nazwę testu i komunikat, zatem przywróć regułę:
1. usuń `request.auth.uid != uid` z `allow update` → musi paść przypadek 9
2. usuń `!exists(r)` z `recenzjaZTegoZatwierdzenia()` → musi paść przypadek 8
3. zamień `== resource.data.sum + ocena()` na `>= resource.data.sum` → musi paść przypadek 5
4. zmień `allow delete: if false` na `if request.auth.uid == uid` → musi paść przypadek 10

Mutacja, której żaden test nie zabija, oznacza brakujący test — dopisz go. Mutację niewykrywalną w tym środowisku albo wzajemnie dublującą się z inną opisz w raporcie z uzasadnieniem, nie przemilczaj.

---

### Zadanie 2: ocena poza dokumentem właściciela — warstwa danych i UI

**Files:**
- Create: `app/src/main/java/com/example/stellare/data/model/RatingModel.kt`
- Create: `app/src/main/java/com/example/stellare/data/remote/FirestoreRatingDataSource.kt`
- Modify: `app/src/main/java/com/example/stellare/data/model/UserModel.kt` (usuń `ratingSum`, `ratingCount`)
- Modify: `app/src/main/java/com/example/stellare/data/remote/DataSources.kt` (interfejs `RatingDataSource`)
- Modify: `app/src/main/java/com/example/stellare/data/remote/FirestoreReviewDataSource.kt` (partia: recenzja + agregat w `ratings`)
- Modify: `app/src/main/java/com/example/stellare/data/repository/ReviewRepository.kt`, `UserRepository.kt`
- Modify: `app/src/main/java/com/example/stellare/ui/dashboard/PostCardPresentation.kt`, `ui/feed/FeedFilters.kt`, `ui/feed/FeedViewModel.kt`, `ui/reviews/ReviewScreens.kt`, `ui/profile/ProfileViewModel.kt`, `ui/psychologistdetail/` (gdzie pokazywana jest ocena)
- Modify: `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`
- Test: `app/src/test/java/com/example/stellare/model/RatingModelTest.kt`, oraz istniejące `feed/FeedViewModelTest.kt`, `profile/ProfileViewModelReviewFlowTest.kt`, `dashboard/PostCardPresentationTest.kt`

**Interfaces:**
- Consumes: kolekcja `ratings/{id}` z zadania 1.
- Produces:
  - `data class RatingModel(val sum: Int = 0, val count: Int = 0)` z `val average: Double`
  - `interface RatingDataSource { fun getRatings(): Flow<Map<String, RatingModel>>; fun getRating(psychologistId: String): Flow<RatingModel> }`
  - `UserRepository.getRatingsFlow(): Flow<Map<String, RatingModel>>`
  - `ratingBadgeText(rating: RatingModel): String?` — zwraca `null` dla `count == 0`
  - `FeedFilters.matches(user: UserModel, rating: RatingModel, filters: FeedFilterState): Boolean`

- [ ] **Krok 1: napisz test agregatu (ma paść)**

`RatingModelTest.kt`: `average` dla `count == 0` wynosi `0.0` (nie `NaN` — dzielenie przez zero w `Double` daje `NaN`, a `NaN` przechodzi przez `%.1f` jako „NaN” i pokazuje się użytkownikowi); `RatingModel(12, 3).average == 4.0`; `RatingModel(7, 2).average == 3.5`.

- [ ] **Krok 2: uruchom i potwierdź padnięcie**

`./gradlew testDebugUnitTest --tests "*RatingModelTest*"` — oczekiwane: nie kompiluje się (brak klasy). To jest właściwe padnięcie dla nowego typu.

- [ ] **Krok 3: dodaj model**

```kotlin
package com.example.stellare.data.model

data class RatingModel(
    val sum: Int = 0,
    val count: Int = 0
) {
    val average: Double get() = averageRating(sum, count)
}
```

Zachowaj istniejącą funkcję `averageRating` z `Rating.kt` — ma własne testy i jest używana w kilku miejscach.

- [ ] **Krok 4: źródło danych i zapis w jednej partii**

`FirestoreRatingDataSource` czyta całą kolekcję `ratings` jednym nasłuchem i wystawia `Map<String, RatingModel>`. Jedno zapytanie na listę, nie jedno na psychologa — lista psychologów ma inaczej N+1 odczytów.

W `FirestoreReviewDataSource.saveReviewWithRating` zapis idzie jedną partią (`db.batch()`): `reviews/{psych}_{reviewer}` oraz `ratings/{psych}` (`set` z `merge` dla pierwszej oceny, `update` z inkrementem dla kolejnych — wartości licz w Kotlinie na podstawie odczytanego agregatu, nie `FieldValue.increment`, bo reguła wymaga dokładnej wartości `resource.data.sum + ocena`). Dwa osobne zapisy zostawiają przy awarii recenzję bez oceny albo odwrotnie.

- [ ] **Krok 5: przełącz UI na nowe źródło**

Każde miejsce czytające `user.ratingSum`/`user.ratingCount` bierze `RatingModel` z mapy. `FeedViewModel` łączy strumień użytkowników ze strumieniem ocen (`combine`). Brak wpisu w mapie znaczy „brak ocen” — `RatingModel()`, nigdy wyjątek.

- [ ] **Krok 6: zaktualizuj atrapy i testy wywołań**

W `FakeDataSources.kt` dodaj atrapę `RatingDataSource` trzymającą mapę w pamięci. W testach wywołań zmień konstruowanie `UserModel(ratingSum = …, ratingCount = …)` na przekazanie `RatingModel` — **nie osłabiaj asercji**, zmienia się tylko sposób podania danych. `ProfileViewModelReviewFlowTest` nadal musi sprawdzać, że po wystawieniu oceny 5 przy stanie `(8, 2)` agregat wynosi `(13, 3)`.

- [ ] **Krok 7: cały zestaw testów**

`./gradlew testDebugUnitTest`. Oczekiwane: zero padnięć. Policz testy i podaj liczbę w raporcie — spadek liczby testów bez wyjaśnienia jest sygnałem, że coś zostało usunięte zamiast przeniesione.

- [ ] **Krok 8: dowód mutacyjny**

1. w `RatingModel.average` zamień warunek `count == 0` na `count < 0` → musi paść test o `NaN`
2. w `saveReviewWithRating` zapisz recenzję i agregat osobno (dwa zapisy, nie partia) → dopisz test, że przy błędzie drugiego zapisu nie zostaje recenzja bez oceny
3. w `FeedViewModel` podstaw stałe `RatingModel()` zamiast mapy → musi paść test filtra po ocenie

---

### Zadanie 3: depozyt — reguły

**Problem:** dziś rezerwacja przelewa monety pacjenta wprost psychologowi. Zwrotu nie da się zrobić zapisem pacjenta (`receivesPaidTransfer` wymaga, by piszący tracił monety), więc odwołanie ponad 24 h przed terminem zostawia `refundPending: true`, a pacjent czeka, aż psycholog to rozliczy. Dodatkowo psycholog może w tym czasie wydać monety i zwrot nie przejdzie („Za mało monet w portfelu, by zwrócić pacjentowi opłatę”). Monety muszą trafiać do depozytu, z którego obie strony mają własną, niezależną ścieżkę.

**Files:**
- Modify: `firebase/firestore.rules`
- Test: `firebase/test/escrow.test.js` (nowy)
- Modify: `firebase/test/refunds.test.js`, `firebase/test/wallet.test.js`, `firebase/test/appointments.test.js`
- Delete: `firebase/test/refund-pending.test.js` (testuje maszynerię, która przestaje istnieć — przypadki o oknie 24 h PRZENIEŚ do `escrow.test.js`, nie usuwaj ich)

**Interfaces:**
- Produces: kolekcja `escrows/{appointmentId}` o polach `{ appointmentId, patientId, psychologistId, amount: int, status: 'HELD'|'RELEASED'|'REFUNDED' }`, nieusuwalna; pole `escrowSettlement` w dokumencie użytkownika jako deklaracja rozliczanego depozytu.
- Usuwa: `receivesPaidTransfer()`, `refundCounterpartyEntry()`, `refundFlagAllowed()`, `bookingCounterpartyEntry()` w dzisiejszej formie, pola `lastTransferTo`/`lastTransferAmount`, pole `refundPending`.

Niezmiennik monet po zmianie: **suma sald + suma depozytów `HELD` jest stała** przy każdej operacji poza doładowaniem. Każdy test ataku ma sprawdzać właśnie to, nie samo saldo pojedynczego konta.

- [ ] **Krok 1: napisz testy regułowe (mają paść)**

`firebase/test/escrow.test.js`. Przypadki w czterech grupach:

*Powstanie depozytu*
1. pacjent rezerwuje: partia tworzy `escrows/{id}` ze `status: 'HELD'`, przestawia wizytę `FREE → BOOKED` na siebie, zmniejsza własne saldo o `amount` — przechodzi
2. depozyt tworzony bez zmniejszenia salda pacjenta — odmowa (to bicie monet)
3. `amount` różne od `priceCoins` wizyty — odmowa
4. `amount` ujemne albo zero — odmowa
5. depozyt zakładany na wizytę, która nie przechodzi `FREE → BOOKED` (jest już `BOOKED`) — odmowa
6. depozyt zakładany przez kogoś, kto nie zostaje pacjentem wizyty — odmowa
7. `psychologistId` w depozycie różny od tego w wizycie — odmowa
8. pole poza dozwolonym zestawem kluczy — odmowa
9. `status` inny niż `HELD` przy tworzeniu — odmowa

*Wypłata psychologowi*
10. psycholog przestawia `HELD → RELEASED`, wizyta jest `COMPLETED`, jego saldo rośnie o `amount` — przechodzi
11. psycholog przestawia `HELD → RELEASED` przy wizycie `BOOKED` — odmowa
12. `RELEASED` bez wzrostu salda psychologa — odmowa (monety przestają istnieć)
13. `RELEASED` ze wzrostem salda psychologa o więcej niż `amount` — odmowa
14. pacjent przestawia `HELD → RELEASED` przy wizycie `CANCELLED` odwołanej PÓŹNIEJ niż 24 h przed terminem, saldo psychologa rośnie — przechodzi (późne odwołanie = opłata należy się psychologowi i pacjent rozlicza to sam, nic nie wisi)
15. pacjent przestawia `HELD → RELEASED` przy wizycie odwołanej WCZEŚNIEJ niż 24 h przed terminem — odmowa
16. obcy przestawia `HELD → RELEASED` — odmowa
17. `RELEASED → REFUNDED` (drugie rozliczenie tego samego depozytu) — odmowa
18. `RELEASED → HELD` — odmowa

*Zwrot pacjentowi*
19. pacjent przestawia `HELD → REFUNDED`, wizyta `CANCELLED`, odwołanie ponad 24 h przed terminem, saldo pacjenta rośnie o `amount` — przechodzi **bez udziału psychologa**
20. pacjent przestawia `HELD → REFUNDED` przy odwołaniu poniżej 24 h — odmowa
21. psycholog przestawia `HELD → REFUNDED` niezależnie od okna czasowego, saldo pacjenta rośnie — przechodzi
22. `REFUNDED` bez wzrostu salda pacjenta — odmowa
23. `REFUNDED` ze wzrostem salda psychologa zamiast pacjenta — odmowa
24. `REFUNDED` przy wizycie `BOOKED` — odmowa
25. obcy przestawia `HELD → REFUNDED` — odmowa

*Niezmiennik i trwałość*
26. nikt nie usuwa depozytu, także jego pacjent i psycholog — odmowa
27. próba zwiększenia salda z deklaracją `escrowSettlement` wskazującą depozyt, który NIE zmienia się w tej partii — odmowa
28. deklaracja `escrowSettlement` wskazująca depozyt należący do kogo innego — odmowa
29. doładowanie ponad limit nadal odrzucane (regresja na `topUpWithinLimit`)
30. własne saldo nadal wolno zmniejszać bez deklaracji

Przypadek 19 jest sednem zadania, 12 i 22 pilnują, by monety nie przepadały, a 2 — by nie powstawały z niczego.

- [ ] **Krok 2: uruchom i potwierdź padnięcia (zero pominiętych)**

`npx vitest run test/escrow.test.js`

- [ ] **Krok 3: dodaj blok depozytu**

```
    // Monety wizyty nie idą wprost do psychologa, tylko do depozytu. Dzięki temu
    // zwrot ma własną ścieżkę pacjenta: wcześniej odwołanie ponad 24 h przed
    // terminem zostawiało refundPending == true i pacjent czekał, aż psycholog
    // to rozliczy — a psycholog mógł w tym czasie wydać monety i zwrot nie
    // przechodził. Depozyt jest nieusuwalny i rozliczalny dokładnie raz.
    match /escrows/{appointmentId} {
      allow read: if isSignedIn();

      function wizytaPrzed() {
        return get(/databases/$(database)/documents/appointments/$(appointmentId)).data;
      }

      function wizytaPo() {
        return getAfter(/databases/$(database)/documents/appointments/$(appointmentId)).data;
      }

      function saldoPo(uid) {
        return getAfter(/databases/$(database)/documents/users/$(uid)).data.coinsBalance;
      }

      function saldoPrzed(uid) {
        return get(/databases/$(database)/documents/users/$(uid)).data.coinsBalance;
      }

      // Para get + getAfter z wymaganą RÓŻNICĄ dowodzi, że rezerwacja dzieje się
      // teraz. Samo getAfter().status == 'BOOKED' spełnia każdy termin już zajęty.
      allow create: if isSignedIn()
        && request.resource.data.keys().hasOnly(
             ['appointmentId', 'patientId', 'psychologistId', 'amount', 'status'])
        && request.resource.data.appointmentId == appointmentId
        && request.resource.data.patientId == request.auth.uid
        && request.resource.data.status == 'HELD'
        && request.resource.data.amount is int
        && request.resource.data.amount > 0
        && wizytaPrzed().status == 'FREE'
        && wizytaPo().status == 'BOOKED'
        && wizytaPo().patientId == request.auth.uid
        && request.resource.data.psychologistId == wizytaPrzed().psychologistId
        && request.resource.data.amount == wizytaPrzed().priceCoins
        && saldoPo(request.auth.uid) == saldoPrzed(request.auth.uid) - request.resource.data.amount;

      // Wypłata psychologowi: po odbytej wizycie (jego decyzja) albo po odwołaniu
      // później niż 24 h przed terminem (wtedy rozlicza to pacjent, by nic nie wisiało).
      allow update: if isSignedIn()
        && onlyChanged(['status'])
        && resource.data.status == 'HELD'
        && request.resource.data.status == 'RELEASED'
        && saldoPo(resource.data.psychologistId)
           == saldoPrzed(resource.data.psychologistId) + resource.data.amount
        && ((request.auth.uid == resource.data.psychologistId
             && wizytaPo().status == 'COMPLETED')
            || (request.auth.uid == resource.data.patientId
                && wizytaPo().status == 'CANCELLED'
                && wizytaPrzed().startsAtMillis - request.time.toMillis() <= 86400000));

      // Zwrot pacjentowi: sam, gdy odwołał ponad 24 h przed terminem; psycholog
      // zawsze (wizyta się nie odbyła z jego strony).
      allow update: if isSignedIn()
        && onlyChanged(['status'])
        && resource.data.status == 'HELD'
        && request.resource.data.status == 'REFUNDED'
        && wizytaPo().status == 'CANCELLED'
        && saldoPo(resource.data.patientId)
           == saldoPrzed(resource.data.patientId) + resource.data.amount
        && ((request.auth.uid == resource.data.patientId
             && wizytaPrzed().startsAtMillis - request.time.toMillis() > 86400000)
            || request.auth.uid == resource.data.psychologistId);

      allow delete: if false;
    }
```

W `match /users/{uid}` zastąp gałęzie przelewu jedną gałęzią rozliczenia depozytu. Dostęp do monet ma wyłącznie ten, kto w tej samej partii przestawia depozyt, a KTO może go przestawić, pilnuje blok powyżej:

```
      // Saldo rośnie wyłącznie przez doładowanie albo rozliczenie depozytu.
      // Kto może przestawić depozyt, pilnuje match /escrows — tutaj zostaje
      // już tylko zgodność arytmetyczna.
      function rozliczenieDepozytu() {
        let kwota = request.resource.data.coinsBalance - resource.data.coinsBalance;
        let d = /databases/$(database)/documents/escrows/$(request.resource.data.get('escrowSettlement', ''));
        let przed = get(d).data;
        let po = getAfter(d).data;
        return kwota is int
          && kwota > 0
          && przed.status == 'HELD'
          && przed.amount == kwota
          && ((po.status == 'RELEASED' && przed.psychologistId == uid)
              || (po.status == 'REFUNDED' && przed.patientId == uid));
      }
```

a w gałęzi właściciela warunek salda przyjmuje postać:

```
        && (unchanged('coinsBalance')
            || (request.resource.data.coinsBalance is int
                && request.resource.data.coinsBalance >= 0
                && (request.resource.data.coinsBalance < resource.data.coinsBalance
                    || topUpWithinLimit()
                    || rozliczenieDepozytu())))
```

plus osobna gałąź dla zapisu obcego (psycholog zwracający monety pacjentowi):

```
      allow update: if isSignedIn()
        && request.auth.uid != uid
        && onlyChanged(['coinsBalance', 'escrowSettlement'])
        && rozliczenieDepozytu();
```

Usuń `receivesPaidTransfer()`, `bookingCounterpartyEntry()`, `refundCounterpartyEntry()`, `refundFlagAllowed()` oraz wszystkie warunki na `lastTransferTo`, `lastTransferAmount` i `refundPending`. Wpisy w księdze (`wallet_transactions`) przestaw tak, by dowodziły zmiany depozytu, nie zmiany statusu wizyty: wpis `BOOKING_PAYMENT` wymaga powstania depozytu w tej partii, `BOOKING_EARNING` — przejścia na `RELEASED`, `REFUND` — przejścia na `REFUNDED`.

- [ ] **Krok 4: cały zestaw reguł**

`npx vitest run`. Przypadki z `refund-pending.test.js` dotyczące okna 24 h muszą mieć odpowiednik w `escrow.test.js` **przed** usunięciem pliku. W raporcie podaj liczbę przypadków przed i po, z rozbiciem na przeniesione i usunięte jako bezprzedmiotowe.

- [ ] **Krok 5: dowód mutacyjny**

1. usuń z `allow create` warunek na spadek salda pacjenta → musi paść przypadek 2 z komunikatem o biciu monet
2. usuń z `RELEASED` warunek wzrostu salda psychologa → musi paść przypadek 12
3. zamień w zwrocie `> 86400000` na `>= 0` → musi paść przypadek 20
4. dopuść `allow delete: if request.auth.uid == resource.data.patientId` → musi paść przypadek 26
5. usuń `przed.status == 'HELD'` z `rozliczenieDepozytu()` → musi paść przypadek 17 (dwukrotne rozliczenie)
6. w `rozliczenieDepozytu()` zamień `przed.amount == kwota` na `przed.amount >= kwota` → dopisz przypadek: rozliczenie częściowe dwa razy

---

### Zadanie 4: depozyt — warstwa danych Androida

**Files:**
- Create: `app/src/main/java/com/example/stellare/data/model/EscrowModel.kt`, `EscrowSettlement.kt`
- Create: `app/src/main/java/com/example/stellare/data/remote/FirestoreEscrowDataSource.kt`
- Modify: `app/src/main/java/com/example/stellare/data/remote/FirestoreAppointmentDataSource.kt`
- Modify: `app/src/main/java/com/example/stellare/data/model/RefundPolicy.kt`, `AppointmentModel.kt` (usuń `refundPending`), `UserModel.kt` (usuń `lastTransferTo`, `lastTransferAmount`)
- Modify: `app/src/main/java/com/example/stellare/data/remote/DataSources.kt`, `data/repository/AppointmentRepository.kt`
- Modify: `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`
- Test: `app/src/test/java/com/example/stellare/model/EscrowSettlementTest.kt` (nowy) + istniejące testy rezerwacji i zwrotów

**Interfaces:**
- Consumes: kolekcja `escrows` i gałęzie z zadania 3.
- Produces:
  - `data class EscrowModel(val appointmentId: String = "", val patientId: String = "", val psychologistId: String = "", val amount: Int = 0, val status: String = "HELD")`
  - `EscrowSettlement.decide(escrow, appointment, settledBy, nowMillis): Settlement` gdzie `Settlement` to `ReleaseToPsychologist(amount)` / `RefundToPatient(amount)` / `NotSettleable(reason)`
  - `AppointmentRepository.cancelBooking(appointmentId, cancelledBy): CancelOutcome` — bez `RefundPending`
  - znika `AppointmentRepository.settleRefund` i `RefundPolicy.canSettle`

- [ ] **Krok 1: napisz testy decyzji (mają paść)**

`EscrowSettlementTest.kt` — czysta funkcja, bez Firestore. Przypadki: wizyta `COMPLETED` i rozlicza psycholog → wypłata; `COMPLETED` i rozlicza pacjent → nie da się rozliczyć; odwołana ponad 24 h przed terminem, rozlicza pacjent → zwrot; odwołana 23 h przed terminem, rozlicza pacjent → wypłata psychologowi; odwołana poniżej 24 h, rozlicza psycholog → zwrot (zawsze wolno); depozyt już `RELEASED` → nie da się rozliczyć; depozyt `REFUNDED` → nie da się rozliczyć; `amount <= 0` → nie da się rozliczyć; granica dokładnie 24 h (`startsAtMillis - now == 86400000`) → wypłata, nie zwrot, zgodnie z regułą `> 86400000`.

Ostatni przypadek pilnuje, by Kotlin i reguła nie rozjechały się na granicy: rozjazd daje operację, którą aplikacja pokazuje jako możliwą, a baza odrzuca.

- [ ] **Krok 2: potwierdź padnięcie, potem napisz `EscrowSettlement`**

- [ ] **Krok 3: przebuduj rezerwację**

W `bookAppointmentAtomically`, w jednej transakcji: wizyta `FREE → BOOKED`, `escrows/{appointmentId}` ze `status: 'HELD'`, saldo pacjenta `-priceCoins`, wpis `BOOKING_PAYMENT` w księdze pacjenta o **deterministycznym identyfikatorze** `"${appointmentId}_${patientId}"`. Saldo psychologa się NIE zmienia i nie powstaje wpis `BOOKING_EARNING` — psycholog zarabia, gdy wizyta się odbędzie. Deterministyczny identyfikator jest potrzebny zadaniu 8 (uzgadnianie po błędzie) i czyni operację idempotentną.

- [ ] **Krok 4: przebuduj odwołanie i zakończenie**

`cancelBooking`: wizyta `→ CANCELLED` i w tej samej transakcji rozliczenie depozytu zgodnie z `EscrowSettlement.decide` — zwrot pacjentowi albo wypłata psychologowi, z deklaracją `escrowSettlement` w zapisie salda. Nigdy nie zostawiaj depozytu `HELD` po odwołaniu. `completeAppointment`: wizyta `→ COMPLETED` i `HELD → RELEASED` plus wpis `BOOKING_EARNING`. Usuń `settleRefund` i całą ścieżkę `refundPending`.

- [ ] **Krok 5: atrapy i testy wywołań**

W `FakeDataSources.kt` atrapa depozytu musi **odwzorowywać regułę**, nie wygodę testu: odrzucać drugie rozliczenie tego samego depozytu i odrzucać rozliczenie bez zmiany salda. Atrapa, która na to pozwala, produkuje testy czwartego rodzaju — sprawdzające fikcję. Istniejące testy rezerwacji i zwrotów zachowują asercje o saldach; zmienia się tylko to, skąd monety przychodzą.

- [ ] **Krok 6: cały zestaw testów, podaj liczbę**

- [ ] **Krok 7: dowód mutacyjny**

1. w `bookAppointmentAtomically` dopisz z powrotem zwiększenie salda psychologa → musi paść test niezmiennika monet (suma sald + depozyty)
2. w `cancelBooking` pomiń rozliczenie depozytu → musi paść test, że po odwołaniu żaden depozyt nie zostaje `HELD`
3. w `EscrowSettlement` zamień `>` na `>=` na granicy 24 h → musi paść przypadek granicy
4. nadaj wpisowi księgi losowy identyfikator → musi paść test idempotencji z zadania 8 (jeśli zadanie 8 jeszcze nie weszło, zapisz tę mutację w raporcie jako do sprawdzenia tam)

---

### Zadanie 5: depozyt — UI

**Files:**
- Modify: `app/src/main/java/com/example/stellare/ui/calendar/AxisAppointments.kt`, `ConfirmationTarget.kt`, `SessionLabels.kt`, `CalendarViewModel.kt`, `components/SessionDetailDialog.kt`
- Delete: `app/src/main/java/com/example/stellare/ui/calendar/components/PendingRefundList.kt`
- Modify: `app/src/main/java/com/example/stellare/ui/wallet/` (nazwy wpisów księgi)
- Test: istniejące testy kalendarza i etykiet

**Interfaces:**
- Consumes: `CancelOutcome` bez `RefundPending` z zadania 4.

- [ ] **Krok 1: usuń stan „czeka na zwrot”**

`SessionTone.REFUND`, `REFUND_DUE`, `REFUND_WAITING` i lista oczekujących zwrotów przestają mieć przedmiot. Zamiast nich: odwołana wizyta pokazuje, **co się stało z monetami** — „Zwrócono N monet” albo „Opłata przepadła (odwołanie poniżej 24 h)”. `ConfirmationTarget.Operation.SETTLE_REFUND` znika razem z przyciskiem rozliczania.

- [ ] **Krok 2: komunikat przed odwołaniem mówi prawdę o pieniądzach**

W oknie potwierdzenia odwołania pacjent widzi skutek finansowy **przed** potwierdzeniem, policzony tą samą funkcją `EscrowSettlement.decide`, która wykona operację: ponad 24 h — „Monety wrócą do Twojego portfela”; poniżej — „Opłata nie zostanie zwrócona”. Nie pisz tego tekstu z drugiego źródła prawdy: rozjazd między zapowiedzią a skutkiem to dokładnie ten rodzaj UI, który kłamie, i etapy 5–6 usunęły kilkanaście takich obietnic.

- [ ] **Krok 3: testy etykiet i okna potwierdzenia**

Dla odwołania ponad i poniżej 24 h sprawdź, że tekst okna zgadza się z decyzją `EscrowSettlement.decide` dla tych samych danych. Test ma porównywać z decyzją, nie z zapisanym na sztywno napisem — wtedy rozjazd zostanie wykryty.

- [ ] **Krok 4: cały zestaw testów**

- [ ] **Krok 5: dowód mutacyjny**

1. w oknie potwierdzenia zamień progi okna 24 h → musi paść test zgodności tekstu z decyzją
2. przywróć przycisk rozliczania zwrotu → nie ma czego wywołać; w raporcie zapisz, że ścieżka zniknęła z UI razem z API (to usunięcie, nie regresja)

---

### Zadanie 6: prywatność — reguły

**Problem:** `match /users/{uid} { allow read: if isSignedIn(); }` daje każdemu zalogowanemu cały profil każdego innego — telefon, e‑mail i saldo. W aplikacji o zdrowiu psychicznym to najpoważniejsza z przeniesionych dziur.

**Rozstrzygnięcie zakresu (zmierzone, nie założone):** wiek i płeć **zostają publiczne**. Filtr wyszukiwania psychologa szuka po nich (`ui/feed/FeedFilters.kt:21-22`), karta szczegółów je pokazuje (`ui/psychologistdetail/components/DetailHeader.kt:84`), a ogłoszenia noszą je w migawce autora (`data/repository/PostRepository.kt:44-45`). To cecha produktu opisana w specyfikacji, nie przeciek. Prywatne są **telefon, e‑mail i saldo** — zmierzono, że wszystkie osiem miejsc czytających saldo w UI dotyczy `currentUser`, więc przeniesienie nie psuje żadnego ekranu.

**Files:**
- Modify: `firebase/firestore.rules`
- Test: `firebase/test/private-profile.test.js` (nowy)
- Modify: `firebase/test/users.test.js`, `firebase/test/phone.test.js`, `firebase/test/wallet.test.js`, `firebase/test/escrow.test.js`, `firebase/test/account-deletion.test.js`

**Interfaces:**
- Produces: dokument `users/{uid}/private/contact` o polach `{ phone, email, coinsBalance: int }`, czytelny i zapisywalny wyłącznie przez właściciela i administratora; wszystkie warunki monet i numeru telefonu przenoszą się na tę ścieżkę.

- [ ] **Krok 1: napisz testy regułowe (mają paść)**

`firebase/test/private-profile.test.js`:
1. właściciel czyta swój dokument prywatny
2. **obcy zalogowany NIE czyta cudzego dokumentu prywatnego** — sedno zadania
3. niezalogowany nie czyta
4. administrator czyta (potrzebne przy obsłudze zgłoszeń)
5. obcy nie zapisuje w cudzym dokumencie prywatnym poza ścieżką rozliczenia depozytu
6. właściciel nie ustawia salda z palca (nadal obowiązuje limit doładowania)
7. niepusty `phone` musi być zgodny z `request.auth.token.phone_number` — regresja na regule z etapu 7, teraz na nowej ścieżce
8. wyczyszczenie potwierdzonego numeru przechodzi tylko, gdy token numeru nie ma
9. publiczny `users/{uid}` **nie przyjmuje** pól `phone`, `email`, `coinsBalance` — inaczej dziura wróci bocznymi drzwiami
10. rozliczenie depozytu działa na nowej ścieżce (regresja na zadaniu 3)
11. usunięcie konta usuwa dokument prywatny (regresja na zadaniu z etapu 8)

Przypadek 9 jest tu równie ważny jak 2: bez niego nic nie broni przed ponownym dopisaniem telefonu do dokumentu publicznego.

- [ ] **Krok 2: potwierdź padnięcia (zero pominiętych)**

- [ ] **Krok 3: dodaj blok i przenieś warunki**

```
    // Telefon, e-mail i saldo leżą poza dokumentem publicznym, bo users/{uid}
    // czyta każdy zalogowany (imię, rola, specjalizacje, wiek i płeć są jawne
    // z założenia — szukanie psychologa po nich filtruje). Wcześniej ta sama
    // reguła wydawała obcym numer telefonu, adres e-mail i stan portfela.
    match /users/{uid}/private/contact {
      allow read: if isAdmin() || (isSignedIn() && request.auth.uid == uid);
      ...
    }
```

Przenieś na tę ścieżkę: `topUpWithinLimit()`, `rozliczenieDepozytu()`, `phoneMatchesVerifiedNumber()`, `phoneUpdateAllowed()` oraz warunki salda z gałęzi właściciela i obcego. W bloku publicznym `match /users/{uid}` **dodaj zakaz** pól prywatnych w `create` i `update`: `&& !request.resource.data.keys().hasAny(['phone', 'email', 'coinsBalance'])`. Ścieżki w `match /escrows` zmień z `users/$(uid)` na `users/$(uid)/private/contact`.

- [ ] **Krok 4: cały zestaw reguł**

`npx vitest run`. Spodziewaj się padnięć w `wallet.test.js`, `escrow.test.js` i `phone.test.js` — to te testy mają zmienić ścieżkę dokumentu, **zachowując asercje**.

- [ ] **Krok 5: dowód mutacyjny**

1. przywróć `allow read: if isSignedIn()` na dokumencie prywatnym → musi paść przypadek 2 z komunikatem o wydaniu numeru telefonu obcemu
2. usuń zakaz pól prywatnych w dokumencie publicznym → musi paść przypadek 9
3. usuń `phoneMatchesVerifiedNumber()` z nowej ścieżki → musi paść przypadek 7
4. usuń warunek admina z odczytu → sprawdź, czy któryś test tego pilnuje; jeśli nie, dopisz

---

### Zadanie 7: prywatność — warstwa danych i UI

**Files:**
- Create: `app/src/main/java/com/example/stellare/data/model/PrivateProfile.kt`, `data/remote/FirestorePrivateProfileDataSource.kt`
- Modify: `UserModel.kt` (usuń `phone`, `email`, `coinsBalance`), `DataSources.kt`, `UserRepository.kt`, `AccountDeleter.kt`
- Modify: `FirestoreAppointmentDataSource.kt`, `FirestoreWalletTransactionDataSource.kt` (ścieżka salda)
- Modify: `ui/wallet/WalletViewModel.kt`, `ui/shell/ProfileSheet.kt`, `ui/profile/ProfileEditHeader.kt`, `ui/calendar/CalendarScreen.kt`, `ui/psychologistdetail/PsychologistDetailScreen.kt`, `ui/settings/DeleteAccountScreen.kt`, `ui/auth/AuthViewModel.kt`
- Modify: `FakeDataSources.kt` i testy portfela, rezerwacji, usuwania konta

**Interfaces:**
- Produces:
  - `data class PrivateProfile(val phone: String = "", val email: String = "", val coinsBalance: Int = 0)`
  - `interface PrivateProfileDataSource { fun getPrivateProfile(uid: String): Flow<PrivateProfile?>; suspend fun savePhone(uid: String, phone: String); suspend fun saveEmail(uid: String, email: String); suspend fun deletePrivateProfile(uid: String) }`
  - `UserRepository.privateProfileFlow(uid: String): Flow<PrivateProfile?>`

- [ ] **Krok 1: test, że rejestracja zakłada oba dokumenty**

Rejestracja musi utworzyć dokument publiczny i prywatny. Jeśli powstanie tylko publiczny, konto nie ma salda i portfel pokazuje zero bez możliwości doładowania. Test: po `register(...)` atrapa ma oba dokumenty, a saldo wynosi 0.

- [ ] **Krok 2: potwierdź padnięcie, potem przenieś pola**

- [ ] **Krok 3: przełącz odczyty**

`WalletViewModel` czyta saldo z `privateProfileFlow`, nie z dokumentu użytkownika. Pamiętaj o nauce z etapu 5: **nieudany odczyt musi dać się ponowić**, inaczej raz zbuforowana porażka zostaje na ekranie na zawsze. `ProfileSheet` i `ProfileEditHeader` biorą telefon i e‑mail z dokumentu prywatnego.

- [ ] **Krok 4: usuwanie konta**

`AccountDeleter` usuwa dokument prywatny **przed** publicznym. Powodem NIE jest uprawnienie — reguła `users/{uid}/private/contact` opiera się na `request.auth.uid == uid` i nie zależy od istnienia dokumentu publicznego, więc odwrotna kolejność też by przeszła. Powodem jest okno przerwania: usuwanie to ciąg zapisów bez transakcji i jeśli przerwie je awaria sieci albo zabicie procesu po usunięciu dokumentu publicznego, w bazie zostaje sierocy dokument z numerem telefonu i adresem e‑mail, którego nikt już nie wyświetli ani nie posprząta. Prywatne idzie pierwsze, bo ono niesie dane osobowe.

Kolejność: notatki → wolne terminy → **dokument prywatny** → dokument użytkownika → ogłoszenia → zgłoszenie weryfikacyjne → konto w Auth. Pozycja ogłoszeń i zgłoszenia PO dokumencie użytkownika jest wymuszona regułą (`allow delete` tylko gdy `!exists(users/uid)`) — nie przestawiaj jej. Dopisz test kolejności.

- [ ] **Krok 5: ekran usuwania konta mówi prawdę**

`AccountDeletionDisclosure.removed` bierze saldo z dokumentu prywatnego. Zachowaj informację, że monety przepadają — to nadal prawda i nadal wymaga powiedzenia.

- [ ] **Krok 6: cały zestaw testów, podaj liczbę**

- [ ] **Krok 7: dowód mutacyjny**

1. w `AccountDeleter` przestaw usuwanie dokumentu prywatnego za publiczny → musi paść test kolejności z komunikatem o sierocym numerze telefonu
2. w rejestracji pomiń dokument prywatny → musi paść test z kroku 1
3. w `WalletViewModel` usuń ponawianie odczytu → musi paść test o zbuforowanej porażce

---

### Zadanie 8: uzgadnianie stanu po błędzie zapisu

**Problem:** gdy zapis dojdzie do bazy, ale odpowiedź nie wróci (sieć padła po zatwierdzeniu), aplikacja pokazuje porażkę operacji, która się udała. Bez serwera nie da się tego usunąć całkowicie, ale da się usunąć **kłamstwo**: po błędzie odczytać stan i powiedzieć, co się naprawdę stało.

**Files:**
- Create: `app/src/main/java/com/example/stellare/data/model/WriteReconciliation.kt`
- Modify: `FirestoreAppointmentDataSource.kt`, `FirestoreWalletTransactionDataSource.kt`
- Modify: `ui/calendar/CalendarViewModel.kt`, `ui/wallet/WalletViewModel.kt`
- Test: `app/src/test/java/com/example/stellare/model/WriteReconciliationTest.kt` + testy obu ViewModeli

**Interfaces:**
- Produces: `WriteReconciliation.outcome(error, landed): Outcome` gdzie `Outcome` to `Succeeded` / `Failed(message)`; każdy zapis pieniężny ma deterministyczny identyfikator, więc jest idempotentny i sprawdzalny.

- [ ] **Krok 1: testy (mają paść)**

1. zapis rzuca `FirebaseFirestoreException(UNAVAILABLE)`, a sprawdzenie pokazuje, że operacja doszła → wynik to sukces, użytkownik nie widzi błędu
2. zapis rzuca wyjątek i sprawdzenie pokazuje, że nie doszła → porażka z komunikatem
3. zapis rzuca `PERMISSION_DENIED` → porażka **bez** sprawdzania (odmowa reguły nie zostawia stanu; sprawdzanie to zbędny odczyt)
4. zapis rzuca `UserMessageException` („Za mało monet”) → komunikat bez zmiany, bez sprawdzania
5. samo sprawdzenie też rzuca → porażka z komunikatem o niepewności, nigdy fałszywy sukces
6. ponowna rezerwacja tego samego terminu przez tego samego pacjenta nie tworzy drugiego wpisu w księdze (idempotencja po deterministycznym identyfikatorze)

Przypadek 5 pilnuje granicy: kiedy nie wiemy, mówimy, że nie wiemy. Fałszywy sukces jest gorszy niż fałszywa porażka, bo pacjent nie wie, że musi coś zrobić.

- [ ] **Krok 2: potwierdź padnięcia, potem napisz `WriteReconciliation`**

- [ ] **Krok 3: podłącz do rezerwacji i doładowania**

Rezerwacja po błędzie odczytuje wizytę: `patientId == me && status == 'BOOKED'` znaczy, że się udało. Doładowanie odczytuje wpis księgi o deterministycznym identyfikatorze. Komunikat przy niepewności: „Nie wiemy, czy operacja się zapisała. Odśwież portfel po chwili.” — nigdy „Nie udało się”.

- [ ] **Krok 4: cały zestaw testów**

- [ ] **Krok 5: dowód mutacyjny**

1. niech sprawdzenie zawsze zwraca „doszło” → musi paść przypadek 2
2. niech wyjątek `PERMISSION_DENIED` też uruchamiał sprawdzenie → musi paść przypadek 3
3. niech błąd sprawdzenia dawał sukces → musi paść przypadek 5

---

### Zadanie 9: spłata drobnego długu

**Files:**
- Modify: `data/remote/DataSources.kt` (pięć domyślnych ciał `throw UnsupportedOperationException()` w liniach 25, 30, 40, 41, 111)
- Delete z `data/model/AppointmentTiming.kt`: `PatientLists` i `patientLists`
- Modify: `data/repository/ChatRepository.kt`, `UserRepository.kt`, `data/remote/FirestoreFavoriteDataSource.kt`, `ui/calendar/components/StellareCalendar.kt`, `WheelTimePicker.kt`, `ui/theme/Type.kt` (komentarze powtarzające kod)
- Modify: ekran z odczytem nazwy (przycisk ponowienia)
- Modify: `ui/shell/` (pastylka sesji przy skali czcionki 1.4 i największym rozmiarze aplikacji)

- [ ] **Krok 1: usuń domyślne ciała rzucające wyjątek**

Pięć metod w `DataSources.kt` ma domyślne implementacje rzucające `UnsupportedOperationException`, co ukrywa brak implementacji do czasu uruchomienia. Zamień na zwykłe metody abstrakcyjne i uzupełnij atrapy. Kompilator wskaże każde brakujące miejsce — to jest sens tej zmiany.

- [ ] **Krok 2: usuń nieosiągalny kod**

`AppointmentTiming.patientLists` i `PatientLists` nie mają konsumenta (piętnasty taki przypadek w projekcie — po usunięciu `refundPending` w zadaniu 4 ich jedyny powód istnienia też zniknął). Sprawdź `grep -rn "patientLists\|PatientLists" app/src`, usuń razem z testami, które je obsługują. Test nieosiągalnej funkcji nie jest pokryciem.

- [ ] **Krok 3: usuń komentarze powtarzające kod**

Dziewiętnaście komentarzy opisuje to, co widać w linii obok (`// Chats`, `// Hours column`, `// Days Grid`, `// Convert to 0=Mon…`). Tam, gdzie komentarz nosił znaczenie, **wydziel nazwaną funkcję albo stałą** zamiast kasować informację: `// Convert to 0=Mon, 1=Tue, ..., 6=Sun` → funkcja `fun mondayFirstIndex(calendarDayOfWeek: Int): Int`. Zostawiamy dwa bloki wyjaśniające niezmiennik bezpieczeństwa (`FirestoreVerificationDataSource`, `PostRepository`) — mówią, dlaczego dwa zapisy są jedną partią, czego nie da się wyrazić nazwą, i bez nich następna edycja rozdzieli partię. Komentarze w `firestore.rules` zostają w całości.

- [ ] **Krok 4: przycisk ponowienia przy odczycie nazwy**

Nieudany odczyt nazwy zostawia pusty ekran bez wyjścia. Dodaj przycisk ponowienia (wzorzec jak w portfelu po zadaniu 7) i test, że po nieudanym odczycie i kliknięciu ponowienia nazwa się pojawia.

- [ ] **Krok 5: pastylka sesji przy dużej czcionce**

Sprawdź pastylkę przy skali czcionki 1.4 i rozmiarze aplikacji „Największy” — przy tych ustawieniach tekst nie był sprawdzony. Napraw przycięcie, jeśli jest. Dowód: zrzut z emulatora w raporcie, nie wniosek z kodu.

- [ ] **Krok 6: cały zestaw testów, podaj liczbę**

---

### Zadanie 10: UI i wydajność

**Cel:** „wszystko ma śmigać, UI być profesjonalne”. Najpierw zmierz, potem zmieniaj — bez pomiaru to zgadywanie.

**Files:**
- Modify: `ui/session/LoginRegistrationScreens.kt` (586 linii), `ui/wallet/WalletScreens.kt` (528), `ui/auth/AuthViewModel.kt` (468), `ui/shell/ProfileSheet.kt` (389), `ui/verification/VerificationScreen.kt` (357), `ui/verification/AdminPanelScreen.kt` (323)

- [ ] **Krok 1: podziel pliki ponad 300 linii**

Każdy po wzorcu użytym w etapie 6 (ekran → prezentacja + komponenty + okna dialogowe). `LoginRegistrationScreens.kt` i `WalletScreens.kt` to dwa ostatnie pliki w projekcie łączące kilka ekranów w jednym pliku. Nie zmieniaj przy tym zachowania — podział ma być czysto strukturalny, a cały zestaw testów musi przejść bez zmian w asercjach.

- [ ] **Krok 2: klucze na listach**

Każdy `items(...)` w `LazyColumn`/`LazyRow` musi mieć `key = { … }` z **unikalnym** identyfikatorem. Bez klucza przewijana lista gubi stan elementów i przekomponowuje więcej, niż musi. Sprawdź `grep -rn "items(" app/src/main/java --include=*.kt` i wymień każdy przypadek bez klucza w raporcie.

- [ ] **Krok 3: niestabilne argumenty**

Szukaj wyrażeń tworzących nowy obiekt przy każdej rekompozycji w argumencie komponentu (lista budowana w locie, lambda bez `remember`, `Modifier` składany warunkowo). Każde takie miejsce unieważnia pominięcie rekompozycji. Napraw przez `remember`/`derivedStateOf`. **Nie stosuj funkcji w `[ngModel]`-podobnym miejscu**, czyli nie przekazuj wyniku wywołania funkcji tam, gdzie liczona jest tożsamość referencji — użyj wartości zapamiętanej.

- [ ] **Krok 4: zmierz na emulatorze**

Zrzuć liczniki rekompozycji albo czas pierwszego rysowania przed i po (`adb shell am start -W` dla czasu startu). Podaj obie liczby w raporcie. Jeśli po zmianie nie ma różnicy, napisz to wprost — zmiana bez poprawy jest dopuszczalna, gdy upraszcza kod, ale nie wolno jej przedstawiać jako przyspieszenia.

- [ ] **Krok 5: cały zestaw testów**

---

### Zadanie 11: weryfikacja na emulatorze

**Cel:** zobaczyć, nie wywnioskować z kodu. Trzy ekrany projektu nigdy nie zostały oglądnięte po globalnej zmianie marginesów z etapu 8.

Środowisko jest gotowe: emulator Androida odpowiada jako `emulator-5554`, emulatory Firebase nasłuchują na 8080. Firebase CLI wołaj przez `node_modules/.bin/firebase.cmd`.

- [ ] **Krok 1: zbuduj i zainstaluj**

`./gradlew installDebug`. Jeśli build padnie, to jest wynik tego zadania — zgłoś go, nie obchodź.

- [ ] **Krok 2: trzy nieoglądane ekrany**

Zrzut ekranu (`adb exec-out screencap -p > …`) dla: **ekranu weryfikacji kwalifikacji**, **panelu administratora**, **ekranu usuwania konta**. Dla każdego oceń: marginesy u góry i dołu (pasek statusu i nawigacji nie zasłaniają treści), kontrast, zachowanie przy skali czcionki 1.4. Zrzut w katalogu scratchpad, ścieżki w raporcie.

- [ ] **Krok 3: pełna ścieżka depozytu na żywych danych**

Na emulatorach Firebase przejdź klikając: rejestracja pacjenta → doładowanie → rezerwacja → sprawdzenie w bazie, że powstał `escrows/{id}` ze `status: 'HELD'` i saldo psychologa się NIE zmieniło → odwołanie ponad 24 h przed terminem → sprawdzenie, że monety wróciły do pacjenta **bez udziału psychologa** i depozyt ma `REFUNDED`. Zrzuty i stan dokumentów w raporcie.

To jest jedyny dowód, że zadania 3–5 działają razem. Testy regułowe i jednostkowe sprawdzają każdą warstwę osobno.

- [ ] **Krok 4: ścieżka zakończonej wizyty**

Rezerwacja → psycholog oznacza wizytę jako odbytą → depozyt `RELEASED`, saldo psychologa rośnie, w jego księdze jest wpis „Wpływ za wizytę”. Sprawdź, że wpis pojawia się TERAZ, nie w chwili rezerwacji.

- [ ] **Krok 5: prywatność na żywo**

Zaloguj się jako obcy użytkownik i spróbuj odczytać `users/{inny}/private/contact` z konsoli emulatora. Oczekiwane: odmowa. Dla porównania odczytaj `users/{inny}` — przechodzi i **nie zawiera** telefonu, e‑maila ani salda.

- [ ] **Krok 6: ocena po odtworzeniu profilu**

Jako psycholog z oceną: usuń konto, zarejestruj się ponownie na ten sam identyfikator (w emulatorze Auth da się odtworzyć uid), sprawdź, że `ratings/{uid}` nadal ma starą wartość. To praktyczny dowód zamknięcia trzeciej dziury.

- [ ] **Krok 7: raport końcowy**

Liczba testów jednostkowych i regułowych, lista zrzutów, stan dokumentów w kluczowych momentach, każda rozbieżność między zapowiedzią UI a stanem bazy. Rozbieżność zgłoś nawet wtedy, gdy wszystkie testy są zielone — to właśnie ta klasa błędu, którą zielone testy ukrywały w tym projekcie osiem razy.

---

---

### Zadanie 12: identyfikator dokumentu jedyną tożsamością (awaria z pętlą, ścieżki zapisu, reguły)

**Powód — zmierzony na urządzeniu w zadaniu 11, nie wywnioskowany.** Dwa dokumenty `posts` z tym samym polem `id` **wywracają aplikację w pętlę awarii**: log nazywa cel wprost (`Write failed at posts/duplikat` → `FATAL EXCEPTION: main`), trzy kolejne starty kończą się `DEAD`, użytkownik widzi „Stellare keeps stopping", a **usunięcie szkodliwych danych po stronie serwera nie pomaga**, bo odrzucony zapis siedzi w lokalnej kolejce Firestore — ratuje dopiero wyczyszczenie danych aplikacji. Konto jest trwale zablokowane.

Potwierdziłem oba ogniwa w kodzie: `FirestorePostDataSource.savePost` robi `collection.document(post.id).set(post)`, czyli celuje w dokument o identyfikatorze równym **polu**; a `PostViewModel.init` (`:38-44`) woła `syncOwnPostsWithAuthor` w `viewModelScope.launch` **bez `try/catch`** — podczas gdy `ProfileViewModel:137` to samo wywołanie ma osłonięte. Zadanie 10 naprawiło **rysowanie** listy; **ścieżki zapisu nadal ufają polu**.

**Files:**
- Modify: `app/src/main/java/com/example/stellare/data/remote/FirestorePostDataSource.kt` (odczyt i zapis)
- Modify: `app/src/main/java/com/example/stellare/ui/posts/PostViewModel.kt` (nieosłonięty zapis w `init`)
- Modify: `firebase/firestore.rules` — `match /posts/{id}`, `match /chats/{chatId}`, `match /messages/{id}`
- Test: pliki testowe ustal `grep`-em po nazwach symboli, **nie z pamięci** (w tym etapie lista z briefu dziewięć razy okazała się podpowiedzią, nie zakresem)

**Interfaces:**
- Consumes: `ui/ListKeys.kt` z zadania 10 (zostaje jako obrona w głąb)
- Produces: tożsamość `PostModel.id` pochodzącą z identyfikatora dokumentu; warunek `request.resource.data.<pole> == <id dokumentu>` na tworzeniu i edycji

- [ ] **Krok 1: tożsamość z identyfikatora dokumentu, nie z pola**

Wzorzec już istnieje w tym projekcie: `FirestoreVerificationDataSource.kt:47` robi `copy(uid = document.id)`. Zrób to samo dla ogłoszeń (i sprawdź czaty oraz wiadomości): mapuj `toObject(...)?.copy(id = document.id)` zamiast `toObjects(...)`. Po tej zmianie **duplikat klucza jest niemożliwy z konstrukcji**, a nie tylko zamaskowany.

- [ ] **Krok 2: żaden odrzucony zapis nie wywraca aplikacji**

`PostViewModel.init` dostaje tę samą osłonę co `ProfileViewModel` (`catch (CancellationException) { throw }` + `catch (Exception)` z logiem i stanem widocznym dla użytkownika, jeśli jest co pokazać). Potem **przeszukaj `app/src/main` za wszystkimi `viewModelScope.launch`, w których dzieje się zapis bez osłony** i wypisz wynik w raporcie — jeden nieosłonięty zapis wystarczył, żeby zablokować konto.

- [ ] **Krok 3: test awarii, który dziś pada**

Test na szwie danych: zapis zwraca wyjątek (odmowa reguł) → `PostViewModel` **nie propaguje go dalej**. Dziś taki test pada z nieprzechwyconym wyjątkiem. Dowód mutacyjny: zdejmij osłonę i pokaż padnięcie z komunikatem mówiącym, że wyjątek wychodzi z korutyny.

- [ ] **Krok 4: zmierz, kto pisze pola identyfikatora, PRZED zmianą reguł**

`grep` po `set(`/`add(`/`update(` dla ogłoszeń, czatów i wiadomości. Jeśli jakakolwiek ścieżka produkcyjna zapisuje pole **inne** niż identyfikator dokumentu, reguła ją zablokuje — wtedy najpierw popraw klienta. Wynik pomiaru w raporcie **przed** zmianą reguły.

- [ ] **Krok 5: testy reguł, które dziś przechodzą na kłamliwych danych**

Dla każdej z trzech kolekcji: test tworzący dokument, którego pole identyfikatora nie zgadza się z identyfikatorem dokumentu — dziś **przechodzi**. Po zmianie ma dostawać odmowę. Plus test, że zgodny dokument nadal przechodzi (inaczej dowodzisz tylko, że zablokowałeś wszystko).

- [ ] **Krok 6: reguła**

Warunek na `create` **i** `update`. `request.resource.data` to dokument **po scaleniu**, więc warunek obowiązuje też wtedy, gdy edycja tego pola nie dotyczy.

- [ ] **Krok 7: mutacje**

Zdejmij warunek z `create`, potem osobno z `update`; każda mutacja musi dać padnięcie **z nazwą testu i komunikatem o szkodzie** (dwa dokumenty twierdzące, że są tym samym obiektem). Jeśli po zdjęciu warunku z `update` nic nie pada, brakuje testu na edycję.

- [ ] **Krok 8: raport**

Liczba testów jednostkowych i regułowych przed i po, lista nieosłoniętych zapisów z kroku 2, treść każdej mutacji, ścieżki produkcyjne z kroku 4.

---

### Zadanie 14: warstwa danych przestaje połykać awarie (interfejs nie może kłamać)

**Powód:** zadanie 11 zmierzyło dwa przypadki tego samego błędu — dane nie dotarły, a interfejs twierdzi inaczej.

**R2:** `NameLookup.Unavailable` jest **nieosiągalny w produkcji**, bo `FirestoreUserDataSource.getUserOnce` (`:61-67`) przechwytuje **każdy** wyjątek i zwraca `null`; `Unavailable` powstaje tylko w `catch` wyżej, do którego nic nie dociera. Zadanie 11 doprowadziło odczyt do faktycznej porażki: w logu `Error getting user once …`, a w interfejsie neutralne „inny użytkownik" i **żadnego przycisku ponowienia**. Przycisk dodany w zadaniu 9 jest więc **martwym kodem** — i to moja pomyłka: kazałem go dodać na podstawie stanu, który nie występuje. Moja recepta na sprawdzenie („wpis wskazujący na nieistniejące konto") była **niewystarczająca** — daje `Absent`, nie `Unavailable`, co wykonawca zmierzył osobno.

**R8:** po unieważnieniu tokenu (zmiana hasła, wyłączenie konta — nie tylko restart emulatora) interfejs **rysuje pełny ekran danych przy całkowicie pustej bazie**, a odświeżanie nic nie zmienia: nasłuch loguje błąd i wychodzi, a ostatni dobry stan zostaje na ekranie jako aktualny.

**Files:**
- Modify: `data/remote/FirestoreUserDataSource.kt` (`getUserOnce`), miejsca tworzące `NameLookup`
- Modify: nasłuchy Firestore kończące się samym logiem — **ustal je `grep`-em** po `addSnapshotListener` i `catch` w `callbackFlow`
- Test: istniejące testy `NameLookup`/portfela, plus nowe

- [ ] **Krok 1: rozróżnij „nie ma dokumentu" od „odczyt padł"**

`getUserOnce` nie może zwracać `null` w obu przypadkach. Albo przepuszcza wyjątek, albo zwraca typ wynikowy. Wybór uzasadnij liczbą konsumentów (`grep`) — nie zmieniaj sygnatury, jeśli wystarczy przepuszczenie wyjątku tam, gdzie wywołujący już ma `catch`.

- [ ] **Krok 2: test, że przycisk ponowienia JEST osiągalny**

Test, w którym odczyt nazwy **rzuca**, a stan kończy się jako `Unavailable` i flaga `nameUnavailable` jest ustawiona. Dziś taki test pada (stan nieosiągalny). Mutacja: przywróć połykanie wyjątku i pokaż padnięcie z komunikatem, że przycisk ponowienia nigdy się nie pokaże.

- [ ] **Krok 3: nasłuch, który padł, musi być widoczny w stanie**

Błąd nasłuchu ma trafić do stanu ekranu, a nie tylko do logu. Minimum: dane przestają być przedstawiane jako aktualne i użytkownik ma co zrobić (ponowienie albo informacja o wylogowaniu). **Nie obiecuj, że dane wrócą** — komunikat mówi o stanie tu i teraz (ta sama zasada, która w zadaniu 7 zamieniła fałszywą obietnicę na najwęższe prawdziwe zdanie).

- [ ] **Krok 4: mutacje i raport**

Dla każdej zmiany mutacja z padnięciem **z nazwą testu i komunikatem o szkodzie**. W raporcie: liczba testów przed i po, lista nasłuchów kończących się samym logiem (z decyzją dla każdego).

---

### Zadanie 15: defekty interfejsu zobaczone na urządzeniu

**Powód:** sześć defektów i dwie niesprawdzone pozycje z zadania 11. **Każda poprawka wymaga zrzutu przed i po** — to zadanie powstało z obejrzenia, więc i dowód jest z obejrzenia.

- [ ] **Krok 1: R7 — treść pod paskiem statusu na ekranie „Uzupełnij profil"**

Wiersz „Pacjent" nachodzi na zegar systemowy, a podpowiedź „Podaj cenę sesji." jest **zdublowana**. Ekran leży na ścieżce rejestracji, więc widzi go każdy nowy użytkownik.

- [ ] **Krok 2: R5 — pasek dni rozpada się przy dużej czcionce**

Przy skali 1.4 i gęstości 546: „sob" łamie się na „so"+„b", kółko dnia 17 **zawija cyfry pionowo** („1" nad „7"), dzień 18 wypada z widoku. Napraw tak, żeby nie wymagało skracania nazw dni do jednej litery dla wszystkich — i obejrzyj wynik przy 1.0 oraz 1.4.

- [ ] **Krok 3: R6 — obramowanie `OutlinedButton`**

Zmierzone na pikselach: **1,33:1 włączone**, **1,00:1 wyłączone** (nic nie narysowane). Treść spełnia wymóg (3,99:1), ale obramowanie jest granicą kontrolki i wymaga 3:1. Dodaj jawny kolor obramowania w `ui/theme/` (oba warianty motywu) i **podaj zmierzone liczby po zmianie**.

- [ ] **Krok 4: R3 — zapis profilu offline bez komunikatu i bez limitu czasu**

„Zapisywanie…" wisiało **ponad 80 sekund** bez informacji. Rejestracja w tej samej sytuacji zachowuje się wzorowo — porównaj oba miejsca i zrób to samo. Limit czasu musi dawać komunikat **uczciwy**: nie „nie udało się", jeśli nie wiesz (wynik `Unknown` z zadania 8 już istnieje).

- [ ] **Krok 5: R4 — panel administratora z wiecznym wskaźnikiem**

Pusta lista daje animowany wskaźnik bez końca zamiast stanu pustego. Dodaj stan pusty (wzorzec `EmptyListPlaceholder` już jest w projekcie).

- [ ] **Krok 6: R9 — wpis księgi kłamie o przyczynie**

Zwrot po oknie 14 dni dostaje `title` „Zwrot za **odwołaną** wizytę", choć wizyta nie była odwołana (na ekranie etykieta liczona z `type`, więc widać neutralnie — ale w bazie zostaje nieprawda). Popraw tytuł dla tej ścieżki.

- [ ] **Krok 7: N1 i N2 — dwie pozycje, których zadanie 11 nie sprawdziło**

N1: zduplikowany `messageId` w czacie (zasiej przez REST emulatora z `Authorization: Bearer owner`, otwórz czat). N2: pastylka sesji przy **własnym** ustawieniu rozmiaru w aplikacji (nie przez `wm density`) — zadanie 11 podejrzewa tam powtórkę R5. Zrzuty obu.

- [ ] **Krok 8: raport**

Tabela „defekt → zrzut przed → co zmieniłem → zrzut po", liczby kontrastu dla R6, liczba testów po zmianach.

---
### Zadanie 13: pomiar wydajności (jedyne zadanie, w którym wolno powiedzieć „szybciej")

**Powód:** zadanie 10 zrobiło podział plików i klucze list, ale miało **zakaz** twierdzeń o szybkości bez liczby i zostawiło osiem hipotez. Żadnej z nich nie wolno „naprawić" przed pomiarem.

**Files:**
- Measure first, change second. Każda zmiana wymaga liczby **przed** i **po**, w tym samym przebiegu narzędzia.

**Interfaces:**
- Consumes: sekcja 8 raportu `briefs/task-10-report.md` (osiem hipotez)
- Produces: tabela „hipoteza → przyrząd → liczba przed → decyzja → liczba po"

- [ ] **Krok 1: przyrządy, które naprawdę są dostępne**

Zmierz, czym możesz mierzyć, i zapisz to **przed** pomiarami: raport stabilności kompilatora Compose (flagi metryk w Gradle — daje plik z listą klas niestabilnych, bez urządzenia), `adb shell am start -W` (TotalTime, urządzenie), prosty przebieg JVM dla czystych funkcji (`MessageGrouping.rowsFor` przy 200 i 2000 wiadomości). Layout Inspector wymaga Android Studio i **nie jest dla Ciebie dostępny** — nie udawaj, że policzyłeś rekompozycje, jeśli nie masz czym.

- [ ] **Krok 2: hipoteza „start aplikacji" jest już niemierzalna wstecz**

Podziału plików nie da się porównać z „przed", bo kod jest już podzielony. Podaj liczbę bezwzględną i napisz, że hipoteza „podział nic nie zmienia" **pozostaje niesprawdzona** — zamiast fabrykować porównanie.

- [ ] **Krok 3: stabilność klas**

Z raportu kompilatora wypisz, które z `WalletEntry`, `CoinPack`, `ChatRow`, `UserModel`, `PostModel` są niestabilne i **dlaczego** (pole `List`, pole `var`, typ z innego modułu). Dopiero dla niestabilnych rozważ poprawkę — i tylko tam, gdzie klasa trafia do argumentu composable.

- [ ] **Krok 4: czyste funkcje**

`rowsFor` przy 200 i 2000 wiadomości, `accentedPostId` przy 100 i 1000 ogłoszeniach. Podaj czas i **złożoność z lektury kodu** (liczba przejść po liście). Jeśli czas rośnie liniowo i jest w setkach mikrosekund — zapisz „bez zmian" i nie ruszaj algorytmu.

- [ ] **Krok 5: historia portfela**

`Column(verticalScroll)` komponuje wszystkie wpisy naraz. Zmierz czas pierwszego rysowania ekranu przy 50 i 500 wpisach (`am start -W` nie wystarczy — użyj `adb shell dumpsys gfxinfo` albo czasu od kliknięcia do narysowania z logu). Zamiana na `LazyColumn` tylko jeśli liczba to uzasadnia.

- [ ] **Krok 6: raport**

Tabela z liczbami i **lista hipotez odrzuconych** („zmierzone, nic nie zmieniam") — bo odrzucona hipoteza jest tu takim samym wynikiem jak poprawka.

---

### Zadanie 16: ostatni kawałek R8 — nasłuch tożsamości i nawigacja całej aplikacji

**Powód:** zadanie 14 naprawiło osiem z piętnastu milczących nasłuchów, ale **siódmy pozostawiony świadomie jest największy**: `FirestoreUserDataSource:39` (`getUser`) karmi `authStatus`, a `authStatus` steruje nawigacją całej aplikacji. Dołożenie tam `close(error)` bez obsługi u konsumenta pod `stateIn(viewModelScope)` daje **wywrotkę aplikacji** — czyli dokładnie awarię, którą zamknęło zadanie 12. Dlatego to zadanie jest osobne: wymaga przebudowy `authStatus`, grozi regresją logowania i nawigacji, i **musi skończyć się dymem na urządzeniu**, bo weryfikacja z zadania 11 odbyła się przed tą zmianą.

Objaw, który zostaje do zamknięcia (zmierzony w zadaniu 11, pozycja R8): po unieważnieniu tokenu — zmiana hasła, wyłączenie konta — interfejs **rysuje pełny ekran danych przy całkowicie pustej bazie**, a odświeżanie nic nie zmienia.

**Files:**
- Modify: `data/remote/FirestoreUserDataSource.kt` (nasłuch `getUser`), `authStatus` i jego konsumenci — **ustal ich `grep`-em**, nie z tej listy
- Test: testy nawigacji i stanu sesji — ustal `grep`-em

**Interfaces:**
- Consumes: `CurrentUserRead` i `StaleData` z zadania 14 (nie buduj trzeciego zestawu stanów)
- Produces: `authStatus`, który **odróżnia** „niezalogowany" od „nie wiem, bo odczyt padł"

- [ ] **Krok 1: policz konsumentów i narysuj, co się stanie przy każdym**

`authStatus` steruje nawigacją, więc **każdy** konsument musi mieć zdefiniowane zachowanie dla nowego stanu. Wypisz je **przed** zmianą. Jeśli któryś konsument nie ma sensownego zachowania, to jest sygnał, że stan trzeba inaczej nazwać — nie że można go pominąć.

- [ ] **Krok 2: trzeci stan, nie drugi**

„Niezalogowany" i „nie wiem" **nie mogą** być tą samą wartością: pierwsza wyrzuca na ekran logowania, druga nie ma prawa wyrzucić zalogowanego użytkownika z aplikacji przy chwilowej awarii odczytu. To ta sama zasada, która w zadaniu 8 dała trzy wyniki zapisu zamiast dwóch.

- [ ] **Krok 3: test, że chwilowa awaria NIE wylogowuje**

Odczyt tożsamości pada → użytkownik zostaje tam, gdzie jest, i widzi, że dane mogą być nieaktualne. Mutacja: zmapuj „nie wiem" na „niezalogowany" i pokaż padnięcie z komunikatem, że awaria odczytu wyrzuca zalogowanego użytkownika na ekran logowania.

- [ ] **Krok 4: test, że unieważnienie tokenu NIE pokazuje starych danych jako aktualnych**

To jest objaw R8. Dane przestają być przedstawiane jako aktualne, komunikat mówi o stanie **tu i teraz** (zasada z zadania 7: żadnych obietnic o przyszłości).

- [ ] **Krok 5: dym na urządzeniu**

`./gradlew installDebug`, emulator Androida, emulatory Firebase (Firestore 8080 + Auth 9099, projekt `stellare-a39ef`). Sprawdź klikając: logowanie, wylogowanie, ponowne logowanie, obrót ekranu, unieważnienie tokenu w trakcie sesji. **Zrzut każdego stanu** — zrzut, którego nie otworzyłeś, nie jest dowodem. Przed pierwszym kliknięciem potwierdź sondą, że emulator ma wczytane **dzisiejsze** reguły (odmowa cytuje numery linii wczytanego zbioru — porównaj z plikiem).

- [ ] **Krok 6: raport**

Tabela konsumentów z zachowaniem dla nowego stanu, treść każdej mutacji, lista zrzutów, liczby testów.

## Po wykonaniu

Specyfikacja `docs/superpowers/specs/2026-09-15-stellare-design.md` wymaga aktualizacji w sekcji 10: trzy dziury przestają być „NIE zaakceptowane” i stają się opisem działającego rozwiązania, a sekcja „Co wymaga planu Blaze” traci punkt o niemożności zamknięcia zwrotów bez serwera. Zostaje w niej: pobieranie płatności, Storage na skany, SMS‑y poza pulą.

Pozostaje świadomie otwarte i **musi zostać zapisane, nie przemilczane**:
- wizyta `BOOKED`, która minęła bez oznaczenia — monety czekają w depozycie, dopóki psycholog nie oznaczy wizyty jako odbytej albo odwołanej. Reguły nie potrafią sprawdzić, czy ktoś się pojawił.
- doładowanie bez potwierdzenia serwerowego — limit 1000 monet ogranicza skalę, zamknięcie wymaga planu Blaze.
- monety przepadają przy usunięciu konta — brak ścieżki wypłaty, bo wypłata to prawdziwe pieniądze.
