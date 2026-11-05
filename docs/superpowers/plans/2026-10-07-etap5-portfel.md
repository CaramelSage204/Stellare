# Etap 5 — Portfel, zwroty i domknięcie ocen — plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODUMIEJĘTNOŚĆ: użyj superpowers:subagent-driven-development do realizacji zadanie po zadaniu.

**Cel:** Domknąć ekonomię monet — cena z profilu psychologa zamiast stałej, zwroty przy odwołaniu, historia z typami operacji. Przy okazji zamknąć dwie dziury w ocenach odłożone z Etapu 3 i bloker logowania z Etapu 4.

**Architektura:** Wszystkie niezmienniki pieniężne wyrażone w regułach Firestore, bo nie ma serwera. Zwrot to przelew w drugą stronę, zakotwiczony w dokumencie wizyty — tak jak rezerwacja. Ocena przestaje być liczbą podawaną przez klienta i staje się sumą całkowitą dzieloną przy wyświetlaniu.

**Stos:** Kotlin 2.2.10, Compose, Firestore, reguły `rules_version = '2'`, testy reguł na `@firebase/rules-unit-testing` + Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcje 5.2, 5.3, 6 i 7.

## Ograniczenia globalne

- **Plan Spark.** Brak Cloud Functions i Firebase Storage. Każdy niezmiennik musi dać się wyrazić w regułach albo być świadomie zaakceptowany na piśmie.
- **Żaden ViewModel nie przekracza 200 linii.**
- **ViewModele nie znają Firebase.** Zależą wyłącznie od repozytoriów.
- **Testy jednostkowe działają bez emulatora i bez urządzenia.**
- **Kod i nazwy po angielsku, komunikaty dla użytkownika po polsku.**
- **Każda klauzula ma własny izolujący test — w regułach i w kodzie tak samo.** Test sprawdzający dwie rzeczy naraz maskuje brak ochrony jednej z nich; w tym projekcie zdarzyło się to pięć razy.
- **Mutacja ogólna i zawężająca to dwa różne narzędzia.** Podmiana na stałą sprawdza, czy cokolwiek testuje daną ścieżkę. Zawężenie warunku sprawdza, czy testuje ją w całości. Luki wychodziły głównie w drugiej kategorii.
- **Deklaracja o pokryciu bez mutacji nie znaczy nic.**
- **Listę dozwolonych pól wyprowadza się z realnego zapisu**, nie z pól uznanych za istotne. Dwa razy podana lista była niekompletna i odrzuciłaby każdy zapis z aplikacji.
- **Zależności wymagane, nigdy opcjonalne z domyślnym `null`.** Opcjonalna zależność znaczy, że połowa funkcji może po cichu nie istnieć.
- **Po zaostrzeniu reguły przejrzyj testy jednostkowe napisane przed nią.** Testy nie znają reguł i same się nie dowiedzą, że kontrakt się zmienił — w Etapie 3 jeden test bronił błędu przez cały etap.
- **Etap kończy się testem na urządzeniu.** W trzech etapach z rzędu znalazł błędy, których kilkaset testów nie złapało, bo siedziały między warstwami.
- **Bez commitów.**

---

## Rozstrzygnięcia wyprzedzające

Zapadły po przeczytaniu **kodu i reguł**, nie samej specyfikacji.

### Reguła 24 godzin jest dziś niewykonalna

Specyfikacja mówi: odwołanie przez pacjenta ponad 24 h przed sesją daje pełny zwrot, poniżej — nie daje nic. Ale `appointments` trzyma termin jako **dwa łańcuchy znaków**: `date` w postaci `2026-10-01` i `time` w postaci `10:00`.

Reguły Firestore nie parsują takich łańcuchów na znacznik czasu. Bez liczby nie da się porównać terminu z `request.time`, więc **reguła 24 godzin mogłaby istnieć wyłącznie w interfejsie** — a to znaczy, że pacjent odwołujący wizytę godzinę przed sesją dostanie zwrot, jeśli ominie aplikację.

To ta sama klasa błędu, którą zamykaliśmy przez cztery etapy: polityka w interfejsie, nie w regule.

**Rozstrzygnięcie:** `appointments` dostaje `startsAtMillis: Long` i to ono jest **jedynym źródłem prawdy** o terminie. Łańcuchy `date` i `time` zostają do wyświetlania, ale aplikacja **wyprowadza je z `startsAtMillis`**, więc nie mogą się rozjechać. Reguła przy tworzeniu terminu wymaga, by `startsAtMillis` wypadało w przyszłości.

### Ocena przestaje być liczbą podawaną przez klienta

Etap 3 ujawnił, że `rating` jest wartością, którą klient wpisuje, a reguła sprawdza tylko przedział 1–5 i przyrost licznika. Jedna recenzja przesuwa średnią z 1.0 na 5.0 niezależnie od historii.

Naprawa przez porównanie średniej w regule jest niewykonalna: równość na liczbach zmiennoprzecinkowych jest krucha, a przy najmniejszym rozjeździe **żadna recenzja by nie przeszła**.

**Rozstrzygnięcie:** `users` dostaje `ratingSum: Int`. Reguła wymaga `ratingSum == stary + ocena z recenzji` oraz `ratingCount == stary + 1` — arytmetyka całkowita, dokładna. Pole `rating` **znika z dokumentu**; średnią liczy aplikacja przy wyświetlaniu, jako `ratingSum / ratingCount`.

To dotyka filtra „minimalna ocena", kart ogłoszeń, profilu psychologa i zasiewu testowego. Zakres jest spory i dlatego ma własne zadanie.

### Recenzja wymaga odbytej wizyty, a reguły nie potrafią odpytywać kolekcji

Druga dziura z Etapu 3: recenzję można wystawić z drugiego darmowego konta, bo reguła nie sprawdza, czy recenzent kiedykolwiek był u ocenianego.

Reguły potrafią wyłącznie pobrać dokument po ścieżce — nie przeszukać kolekcji. A `appointments` ma identyfikatory losowe.

**Rozstrzygnięcie:** oznaczenie sesji jako odbytej tworzy dokument-znacznik `completions/{psychologistId}_{patientId}` o przewidywalnym identyfikatorze. Reguła recenzji wymaga jego istnienia. Znacznik powstaje w tej samej partii co zmiana statusu wizyty, więc nie da się go sfabrykować bez odbytej wizyty.

### Zwrot to przelew w drugą stronę, ale księga tego nie przewiduje

`bookingCounterpartyEntry()` pozwala pisać do cudzej księgi **wyłącznie przy rezerwacji** — wymaga przejścia wizyty z `FREE` na `BOOKED`. Zwrot wymaga wpisu w księdze drugiej strony przy przejściu na `CANCELLED`, czego ta funkcja nie obejmuje. Dlatego trzy testy zwrotów są pominięte od Etapu 1.

**Rozstrzygnięcie:** osobna funkcja `refundCounterpartyEntry()`, zakotwiczona w tym samym wzorcu: wizyta musi w tym samym zatwierdzeniu przejść na `CANCELLED`, a kwota zwrotu musi równać się `priceCoins` tej wizyty. **Nie luzujemy `receivesPaidTransfer()`** — ujemna cena wizyty jest już zablokowana osobną klauzulą z Etapu 3, ale warunek dodatniej kwoty w przelewie zostaje nietknięty.

---

## Struktura plików

### Nowe

- `app/src/main/java/com/example/stellare/data/model/CompletionModel.kt`
- `app/src/main/java/com/example/stellare/ui/wallet/TransferState.kt`
- `firebase/test/refunds.test.js`
- `firebase/test/completions.test.js`

### Zmieniane

- `firebase/firestore.rules` — `refundCounterpartyEntry()`, `ratingSum`, `completions`, `startsAtMillis`
- `firebase/test/users.test.js`, `wallet.test.js`, `appointments.test.js`
- `app/src/main/java/com/example/stellare/data/model/{UserModel,AppointmentModel}.kt`
- `app/src/main/java/com/example/stellare/data/remote/{DataSources,FirestoreAppointmentDataSource,FirestoreUserDataSource,FirestoreReviewDataSource}.kt`
- `app/src/main/java/com/example/stellare/data/repository/{AppointmentRepository,WalletRepository,ReviewRepository}.kt`
- `app/src/main/java/com/example/stellare/ui/calendar/CalendarViewModel.kt`
- `app/src/main/java/com/example/stellare/ui/wallet/WalletViewModel.kt`
- `app/src/main/java/com/example/stellare/ui/profile/ProfileViewModel.kt`
- `app/src/main/java/com/example/stellare/ui/feed/FeedFilters.kt`
- `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`

---

## Zadanie 1: Bloker — cykl życia nasłuchów przy wylogowaniu

Pierwsze zadanie, bo dotyczy wszystkiego, co potem napiszemy, i bo jest jedynym znanym błędem uniemożliwiającym wejście do aplikacji.

**Objawy, oba udokumentowane w Etapie 4:**
- przy **każdym** wylogowaniu w logach pojawia się seria `PERMISSION_DENIED` — nasłuchy na `posts`, `users` i `verificationRequests` nie odpinają się, zanim token wygaśnie
- **nowo zarejestrowany użytkownik czasem nie może się zalogować** — komunikat o braku danych w bazie, mimo że dokument istnieje; jeden przebieg dał 3 niepowodzenia na 3 próby, inny 6 sukcesów na 6, tą samą metodą

Hipoteza wiąże oba: wyścig między wygaśnięciem tokenu a odpięciem nasłuchów.

**Pliki:** `StellareNavHost.kt`, ViewModele, `FirestoreUserDataSource.kt`

- [ ] **Krok 1: Ustal, kiedy strumienie są zbierane po wylogowaniu**

Wszystkie ViewModele powstają ponad `NavHost` i żyją przez całą sesję procesu. Ich strumienie mają `SharingStarted.WhileSubscribed(5000)` — czyli **zbierają jeszcze pięć sekund po tym, jak ekran zniknął**.

Sprawdź, co faktycznie subskrybuje po wylogowaniu i jak długo. Dopisz tymczasowe logowanie, uruchom, przeczytaj logcat. **Nie zgaduj.**

- [ ] **Krok 2: Napraw przyczynę, nie objaw**

Strumienie zależne od zalogowanego użytkownika muszą **przestać pytać**, gdy użytkownik znika — nie po pięciu sekundach, lecz natychmiast. Wzorzec jest już w kodzie: `currentUser.flatMapLatest { if (it != null) repo... else flowOf(pusto) }`.

Sprawdź **każdy** strumień we wszystkich siedmiu ViewModelach. Wypisz w raporcie te, które nie miały tej bramki.

**Nie wyciszaj błędu w źródle danych.** Nasłuch bez prawa odczytu ma nie powstawać, a nie logować po cichu.

- [ ] **Krok 3: Weryfikacja na urządzeniu — obowiązkowa**

Zaloguj się, wyloguj, odczekaj dziesięć sekund, sprawdź `adb logcat -d | grep PERMISSION_DENIED`. Ma być pusto.

Potem **dziesięć razy** pod rząd: rejestracja nowego konta, wylogowanie, logowanie. Zanotuj, ile razy się udało. Przy błędzie niedeterministycznym jedna próba nic nie znaczy — potrzebna jest seria.

Jeśli po naprawie nasłuchów logowanie nadal czasem zawodzi, **przechwyć log z samej awarii** i opisz. Wtedy to osobny problem, nie ten.

---

## Zadanie 2: Ocena jako suma całkowita

**Pliki:** `firestore.rules`, `users.test.js`, `UserModel.kt`, `FirestoreReviewDataSource.kt`, `ProfileViewModel.kt`, `FeedFilters.kt`, `PostCard`, zasiew

- [ ] **Krok 1: Napisz testy reguł, zanim zmienisz regułę**

1. recenzja podnosi `ratingSum` o ocenę i `ratingCount` o jeden — przechodzi
2. `ratingSum` podniesione o więcej niż ocena w recenzji — odrzucone
3. `ratingSum` podniesione bez recenzji — odrzucone
4. `ratingCount` podniesione o dwa — odrzucone
5. ocena poza przedziałem 1–5 w recenzji — odrzucone
6. właściciel podnosi sobie `ratingSum` — odrzucone
7. tworzenie konta z `ratingSum` różnym od zera — odrzucone

- [ ] **Krok 2: Potwierdź, które padają**

```bash
cd firebase && npm test
```

- [ ] **Krok 3: Zmień regułę**

Zamień warunki na `rating` warunkami na `ratingSum`, z arytmetyką całkowitą:

```
&& request.resource.data.ratingSum == resource.data.ratingSum + getAfter(recenzja).data.rating
&& request.resource.data.ratingCount == resource.data.ratingCount + 1
&& getAfter(recenzja).data.rating >= 1
&& getAfter(recenzja).data.rating <= 5
```

`onlyChanged` obejmuje teraz `ratingSum` i `ratingCount`. **Pole `rating` wypada z dokumentu** — sprawdź, czy nie zostało w żadnej klauzuli.

- [ ] **Krok 4: Średnia po stronie aplikacji**

`UserModel` traci `rating`, dostaje `ratingSum: Int`. Dodaj wyliczanie średniej jako **czystą funkcję** — testowalną bez emulatora:

```kotlin
fun averageRating(ratingSum: Int, ratingCount: Int): Double =
    if (ratingCount == 0) 0.0 else ratingSum.toDouble() / ratingCount
```

Testy: zero głosów daje zero, trzy głosy po 4 dają 4.0, dzielenie nie jest całkowite (7 na 2 daje 3.5).

- [ ] **Krok 5: Przejrzyj wszystkie miejsca, które czytały `rating`**

Filtr „minimalna ocena" w `FeedFilters`, karta ogłoszenia, profil psychologa, zasiew testowy, testy reguł z fiksturami. `grep -rn "rating"` i wypisz każde trafienie z decyzją.

**To jest zadanie, w którym najłatwiej zostawić rozjazd** — pole znika z modelu, ale fikstura w teście może je nadal zapisywać, a reguła z `hasOnly` odrzuci wtedy cały zapis.

- [ ] **Krok 6: Mutacja per klauzula**

Osobno dla każdego nowego warunku. Plus mutacja zawężająca: usuń samą dolną granicę przedziału oceny.

---

## Zadanie 3: Recenzja wymaga odbytej wizyty

**Pliki:** `firestore.rules`, `completions.test.js`, `CompletionModel.kt`, `DataSources.kt`, `FirestoreAppointmentDataSource.kt`, `CalendarViewModel.kt`, atrapy

- [ ] **Krok 1: Testy reguł**

1. oznaczenie wizyty jako odbytej tworzy znacznik — przechodzi
2. znacznik utworzony bez zmiany statusu wizyty — odrzucone
3. znacznik o identyfikatorze niezgodnym z parą uczestników — odrzucone
4. recenzja bez istniejącego znacznika — odrzucone
5. recenzja ze znacznikiem — przechodzi
6. znacznik tworzony przez osobę trzecią — odrzucone
7. `delete` i `update` znacznika — odrzucone

- [ ] **Krok 2: Reguła**

```
match /completions/{id} {
  allow read: if isSignedIn();

  function wizyta() {
    return /databases/$(database)/documents/appointments/$(request.resource.data.appointmentId);
  }

  allow create: if isSignedIn()
    && id == request.resource.data.psychologistId + '_' + request.resource.data.patientId
    && get(wizyta()).data.status == 'BOOKED'
    && getAfter(wizyta()).data.status == 'COMPLETED'
    && get(wizyta()).data.psychologistId == request.resource.data.psychologistId
    && get(wizyta()).data.patientId == request.resource.data.patientId
    && request.resource.data.keys().hasOnly(
         ['psychologistId', 'patientId', 'appointmentId', 'completedAt']);

  allow update, delete: if false;
}
```

Do reguły recenzji dochodzi `exists(/databases/$(database)/documents/completions/$(uid + '_' + request.auth.uid))`.

**Uwaga:** znacznik ma przewidywalny identyfikator, więc druga wizyta tej samej pary **nie utworzy go ponownie** — `create` się nie powtórzy. To zamierzone: jeden znacznik na parę wystarcza, a `update` jest zablokowane. Sprawdź, że kod to obsługuje i nie traktuje odrzucenia jako awarii.

- [ ] **Krok 3: Zapis w aplikacji**

`completeAppointment` zapisuje **jedną partią**: status wizyty i znacznik. Przy powtórnym oznaczeniu tej samej pary partia się nie uda — obsłuż to, nie pokazując użytkownikowi błędu, bo nic złego się nie stało.

- [ ] **Krok 4: Mutacje** — osobno dla każdej klauzuli, w tym dla warunku na identyfikator.

---

## Zadanie 4: Termin jako liczba i cena z profilu

**Pliki:** `firestore.rules`, `appointments.test.js`, `AppointmentModel.kt`, `CalendarViewModel.kt`, źródło i repozytorium terminów, atrapy, zasiew

- [ ] **Krok 1: `startsAtMillis` jako jedyne źródło prawdy**

`AppointmentModel` dostaje `startsAtMillis: Long`. Aplikacja **wyprowadza** `date` i `time` z tej liczby, zamiast przyjmować je osobno — dzięki temu nie mogą się rozjechać.

Reguła przy tworzeniu terminu wymaga `startsAtMillis > request.time.toMillis()`.

- [ ] **Krok 2: Cena z profilu, stała 40 znika**

`grep -rn "40"` w kontekście monet i wypisz każde trafienie. Cena ma się brać z `pricePerSession` psychologa **w chwili tworzenia terminu** i być zapisana na dokumencie, żeby późniejsza zmiana cennika nie wpływała na zwrot.

Reguła już wymaga `priceCoins >= 0` — sprawdź, czy wiąże też cenę z profilem, i jeśli nie, zdecyduj, czy powinna. **Zgłoś mi tę decyzję**, nie podejmuj jej sam: wiązanie oznacza, że psycholog nie wystawi terminu promocyjnego.

- [ ] **Krok 3: Testy i mutacje**

Termin w przeszłości odrzucony, termin w przyszłości przyjęty, czysta funkcja przeliczająca `startsAtMillis` na datę i godzinę pokryta testami bez emulatora.

---

## Zadanie 5: Zwroty

To sedno etapu i najtrudniejsza reguła w całym projekcie.

**Pliki:** `firestore.rules`, `refunds.test.js`, `wallet.test.js` (trzy pominięte testy), `AppointmentRepository.kt`, `WalletRepository.kt`, `CalendarViewModel.kt`

- [ ] **Krok 1: Odblokuj trzy pominięte testy**

`firebase/test/wallet.test.js`, linie około 442, 476 i 503. Są pominięte od Etapu 1 z opisem, że zwroty nie są jeszcze obsłużone. **Zamień `it.skip` na `it`, uruchom i zanotuj, co dokładnie pada** — to jest specyfikacja tego zadania, napisana przed nim.

- [ ] **Krok 2: Testy okna 24 godzin**

1. pacjent odwołuje ponad 24 h przed terminem — pełny zwrot przechodzi
2. pacjent odwołuje poniżej 24 h — zwrot odrzucony
3. psycholog odwołuje poniżej 24 h — zwrot przechodzi
4. kwota zwrotu różna od `priceCoins` wizyty — odrzucone
5. zwrot bez przejścia wizyty na `CANCELLED` — odrzucone
6. dwa zwroty dla tej samej wizyty — drugi odrzucony

Punkt 6 jest najważniejszy: bez niego da się odwoływać i zwracać w kółko.

- [ ] **Krok 3: Reguła**

```
function refundCounterpartyEntry() {
  let wizyta = /databases/$(database)/documents/appointments/$(request.resource.data.appointmentId);
  return request.resource.data.type == 'REFUND'
    && get(wizyta).data.status == 'BOOKED'
    && getAfter(wizyta).data.status == 'CANCELLED'
    && request.resource.data.amount == get(wizyta).data.priceCoins
    && (request.auth.uid == get(wizyta).data.psychologistId
        || get(wizyta).data.startsAtMillis - request.time.toMillis() > 86400000);
}
```

Dopisz ją do `allow create` w `wallet_transactions`, obok `bookingCounterpartyEntry()`.

**Nie luzuj `receivesPaidTransfer()`.** Warunek dodatniej kwoty zostaje — zwrot idzie od psychologa do pacjenta, więc kwota po stronie odbiorcy jest dodatnia i warunek jest spełniony bez zmian.

- [ ] **Krok 4: Mutacja per klauzula, obowiązkowo**

Pięć warunków w tej funkcji. Usuń każdy osobno. Szczególnie **okno 24 godzin i warunek na rolę** — to one decydują o pieniądzach.

Dodatkowo mutacja zawężająca: zamień `>` na `>=` w oknie czasu i sprawdź, czy któryś test to łapie. Jeśli nie — dopisz test na granicy.

---

## Zadanie 6: Portfel w interfejsie

**Pliki:** `WalletViewModel.kt`, `TransferState.kt`, `WalletScreens.kt`, `CalendarScreen.kt`

- [ ] **Krok 1: Historia z typami**

Lista operacji pokazuje typ, kwotę ze znakiem i drugą stronę. `TOPUP`, `BOOKING_PAYMENT`, `BOOKING_EARNING` i `REFUND` mają różne etykiety po polsku i różne znaki.

- [ ] **Krok 2: Odwołanie wizyty z informacją o zwrocie**

Przed odwołaniem użytkownik ma wiedzieć, **czy dostanie zwrot**. Policz to z `startsAtMillis` i pokaż w potwierdzeniu: „Odwołanie teraz oznacza pełny zwrot" albo „Do sesji zostało mniej niż 24 godziny — monety nie wracają".

Bez tego pacjent odwołuje wizytę, traci monety i nie wie dlaczego.

- [ ] **Krok 3: Stan operacji jako typ, nie wyścig**

`TransferState` jako `sealed interface` wzorem `BookingState`. Metody zwracają wynik, **nie polegają na czasie** — `yield()` i opóźnienia są zakazane, w Etapie 3 taka konstrukcja trafiła do rundy poprawek.

---

## Zadanie 7: Atrapa terminów z wymaganymi zależnościami

**Pliki:** `FakeDataSources.kt` i testy, które ją konstruują

`FakeAppointmentDataSource` ma `users` i `wallet` jako **opcjonalne z domyślnym `null`**, udokumentowane jako „tryb zredukowany". Konstrukcja bez nich daje atrapę, która **po cichu pomija sprawdzanie salda i zapis do księgi**.

To ten sam wzorzec, który w Etapie 2 pozwolił testom przejść bez weryfikacji salda, i który świadomie odrzuciliśmy w dwóch nowszych atrapach.

- [ ] **Krok 1: Uczyń oba wymaganymi.** Dostosuj konstrukcje w testach — **wyłącznie budowę obiektu, nie asercje.**
- [ ] **Krok 2: Sprawdź, czy któryś test przechodził tylko dzięki trybowi zredukowanemu.** Jeśli po zmianie pada — to jest ustalenie, nie usterka. Opisz.

---

## Zadanie 8: Test na urządzeniu

- [ ] **Krok 1: Środowisko** — emulatory Firebase, zasiew, `pm clear`.

- [ ] **Krok 2: Ścieżka**

1. Logi po zimnym starcie i **po wylogowaniu** — żadnego `PERMISSION_DENIED`
2. **Dziesięć** rejestracji z logowaniem — ile się udało
3. Rezerwacja: pacjentowi ubywa cena z profilu psychologa, psychologowi przybywa tyle samo
4. Historia obu stron pokazuje właściwe typy i znaki
5. Odwołanie ponad 24 h przed sesją — monety wracają, widać wpis zwrotu
6. Odwołanie poniżej 24 h — monety **nie** wracają, a użytkownik był o tym uprzedzony **przed** potwierdzeniem
7. Oznaczenie sesji jako odbytej, potem recenzja — przechodzi
8. Recenzja bez odbytej sesji — zablokowana
9. Średnia oceny na karcie zgadza się z sumą podzieloną przez licznik

Punkty 5 i 6 są najważniejsze — dotyczą pieniędzy i są jedynym miejscem, gdzie zobaczymy regułę 24 godzin w działaniu.

- [ ] **Krok 3: Zrzuty ekranu jako dowód.** Opis bez zrzutu nie jest dowodem.

---

## Definicja ukończenia

- Bloker logowania zamknięty albo udokumentowany serią dziesięciu prób
- Ocena jako suma całkowita; nie da się jej podnieść bez recenzji ani recenzją bez odbytej wizyty
- Zwroty działają, okno 24 godzin wymuszone **regułą**, nie interfejsem
- Trzy pominięte testy zwrotów przechodzą
- Stała 40 monet zniknęła z kodu
- Atrapa terminów bez trybu zredukowanego
- Testy jednostkowe i reguł zielone, każda nowa klauzula z mutacją
- Test na urządzeniu przeszedł, ze zrzutami

## Czego ten plan świadomie nie obejmuje

- Prowizji platformy i wypłat monet na konto bankowe — poza zakresem projektu
- Zabezpieczenia doładowania portfela; bez serwera się nie da, opisane w specyfikacji
- Zwrotów częściowych innych niż pełny zwrot ceny wizyty
- Historii zmian cennika psychologa
- Powiadomień o zwrocie — użytkownik widzi wpis w portfelu
