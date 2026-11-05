# Etap 4 — Weryfikacja kwalifikacji — plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODUMIEJĘTNOŚĆ: użyj superpowers:subagent-driven-development do realizacji zadanie po zadaniu.

**Cel:** Psycholog wysyła skan dyplomu, administrator go ogląda i zatwierdza albo odrzuca z powodem. Dopiero wtedy konto dostaje odznakę „Zweryfikowany".

**Architektura:** Skan trafia do Firestore jako base64 w `verificationRequests/{uid}` — klucz dokumentu równy identyfikatorowi zgłaszającego, więc jedno zgłoszenie na konto. Panel administratora czyta całą kolekcję; zwykły użytkownik wyłącznie własne zgłoszenie. `isVerified` przestawia wyłącznie administrator.

**Stos:** Kotlin 2.2.10, Compose, Firestore, reguły `rules_version = '2'`, testy reguł na `@firebase/rules-unit-testing` + Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcje 5.1, 6 i 8.

## Ograniczenia globalne

- **Plan Spark.** Brak Cloud Functions i Firebase Storage — stąd base64 w Firestore zamiast pliku.
- **Dokument Firestore ma limit 1 MiB.** Base64 powiększa dane o mniej więcej jedną trzecią, więc skan przed zapisem kompresujemy do około 500 KB.
- **Żaden ViewModel nie przekracza 200 linii.**
- **ViewModele nie znają Firebase.** Zależą wyłącznie od repozytoriów.
- **Testy jednostkowe działają bez emulatora i bez urządzenia.**
- **Kod i nazwy po angielsku, komunikaty dla użytkownika po polsku.**
- **Każda klauzula ma własny izolujący test — w regułach i w kodzie tak samo.** Test sprawdzający dwie rzeczy naraz maskuje brak ochrony jednej z nich; w tym projekcie zdarzyło się to już cztery razy.
- **Mutacja ogólna i zawężająca to dwa różne narzędzia.** Podmiana na stałą sprawdza, czy cokolwiek testuje daną ścieżkę. Zawężenie warunku sprawdza, czy testuje ją w całości.
- **Deklaracja o pokryciu bez mutacji nie znaczy nic.**
- **Zależności wymagane, nigdy opcjonalne z domyślnym `null`.** Opcjonalna zależność znaczy, że połowa funkcji może po cichu nie istnieć.
- **Etap kończy się testem na urządzeniu.**
- **Bez commitów.**

---

## Rozstrzygnięcia wyprzedzające

Zapadły po przeczytaniu istniejących reguł, nie samej specyfikacji — bo reguły `verificationRequests` powstały już w Etapie 1 i mają luki, których spec nie opisuje.

**Brak listy dozwolonych pól.** `allow create` sprawdza tylko autorstwo i status `PENDING`. Zgłaszający może dopisać dowolne pole — dokładnie ta sama dziura, którą zamknęliśmy w `posts` w Etapie 3. Najgroźniejszy wariant: dopisanie `reviewedBy` albo `reviewNote` przy tworzeniu, czyli sfabrykowanie śladu rozpatrzenia.

**Zgłoszenie może wysłać każdy, także pacjent.** Reguła nie sprawdza roli. Nieszkodliwe dla przywileju, bo `isVerified` i tak przestawia wyłącznie administrator, ale zaśmieca panel i myli obsługę. Wiążemy z rolą.

**`submittedAt` nie jest niczym ograniczone.** Panel sortuje zgłoszenia po dacie, więc bez okna czasu wystarczy wpisać rok 3000, żeby zawsze być pierwszym w kolejce. To ta sama dziura, którą w `posts` zamknęliśmy oknem czasu serwera.

**Zatwierdzenie to jeden zapis, nie dwa.** Administrator ustawia `status = 'APPROVED'` i `users/{uid}.isVerified = true`. Jeśli pójdą osobno, awaria między nimi zostawia zgłoszenie zatwierdzone przy koncie niezweryfikowanym albo odwrotnie. Idą jedną partią.

**Aparat bez CameraX i bez uprawnień.** Zależności `androidx.camera.*` i `accompanist-permissions` są w `app/build.gradle.kts` zakomentowane. Nie odkomentowujemy ich: `ActivityResultContracts.PickVisualMedia` nie wymaga żadnego uprawnienia, a `TakePicture` korzysta z aparatu systemowego i też go nie wymaga, dopóki aplikacja nie deklaruje `CAMERA` w manifeście. Mniej zależności, zero okien z prośbą o zgodę.

---

## Struktura plików

### Nowe

- `app/src/main/java/com/example/stellare/data/model/VerificationRequestModel.kt`
- `app/src/main/java/com/example/stellare/data/remote/FirestoreVerificationDataSource.kt`
- `app/src/main/java/com/example/stellare/data/repository/VerificationRepository.kt`
- `app/src/main/java/com/example/stellare/ui/verification/ImageCompression.kt` — czyste funkcje decyzyjne
- `app/src/main/java/com/example/stellare/ui/verification/VerificationViewModel.kt`
- `app/src/main/java/com/example/stellare/ui/verification/VerificationScreen.kt`
- `app/src/main/java/com/example/stellare/ui/verification/AdminPanelScreen.kt`
- `firebase/test/verification.test.js`

### Zmieniane

- `firebase/firestore.rules` — blok `match /verificationRequests/{uid}`
- `app/src/main/java/com/example/stellare/data/remote/DataSources.kt`
- `app/src/main/java/com/example/stellare/di/AppContainer.kt`
- `app/src/main/java/com/example/stellare/ui/navigation/Routes.kt`
- `app/src/main/java/com/example/stellare/ui/navigation/StellareNavHost.kt`
- `app/src/main/java/com/example/stellare/ui/shell/ProfileSheet.kt` — pozycja „Panel weryfikacji" ukryta dziś za stałą
- `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`

---

## Zadanie 1: Domknięcie reguł `verificationRequests`

Najpierw reguły. W Etapie 1 odwrotna kolejność kosztowała sześć dziur wykrytych po fakcie, a w Etapie 3 próba adwersaryjna znalazła dwie kolejne w regule uznanej za gotową.

**Pliki:**
- Zmodyfikuj: `firebase/firestore.rules`
- Utwórz: `firebase/test/verification.test.js`

- [ ] **Krok 1: Przeczytaj istniejący blok i wypisz, co już chroni**

Blok `match /verificationRequests/{uid}` istnieje od Etapu 1. **Nie pisz reguły od zera** — dopisujesz brakujące klauzule do istniejących. Wypisz w raporcie, co zastałeś.

- [ ] **Krok 2: Napisz testy, zanim dopiszesz klauzule**

Każdy jako osobny `it`, każdy sprawdzający **jedną** rzecz:

1. psycholog tworzy własne zgłoszenie ze statusem `PENDING` — przechodzi
2. tworzenie zgłoszenia pod cudzym identyfikatorem — odrzucone
3. tworzenie od razu ze statusem `APPROVED` — odrzucone
4. **pacjent tworzy zgłoszenie** — odrzucone
5. **student psychologii tworzy zgłoszenie** — przechodzi
6. **zgłoszenie z dopisanym `reviewedBy`** — odrzucone
7. **zgłoszenie z dopisanym polem spoza modelu** — odrzucone
8. **`submittedAt` w przyszłości o godzinę** — odrzucone
9. obcy czyta cudze zgłoszenie — odrzucone
10. administrator czyta cudze zgłoszenie — przechodzi
11. zgłaszający czyta własne — przechodzi
12. zgłaszający sam ustawia sobie `status = 'APPROVED'` — odrzucone
13. zgłaszający po odrzuceniu wysyła ponownie (`REJECTED` → `PENDING`) — przechodzi
14. administrator zatwierdza — przechodzi
15. obcy usuwa zgłoszenie — odrzucone

- [ ] **Krok 3: Uruchom i potwierdź, które padają**

```bash
cd firebase && npm test
```

Część przejdzie od razu — reguła istnieje i coś już chroni. **Wypisz, które padły i dlaczego** — to jest właściwa miara tego, czego brakowało.

- [ ] **Krok 4: Dopisz brakujące klauzule**

Do `allow create` dochodzi lista dozwolonych pól, wiązanie z rolą i okno czasu:

```
function zglaszajacy() {
  return get(/databases/$(database)/documents/users/$(request.auth.uid)).data;
}

allow create: if isSignedIn()
  && request.auth.uid == uid
  && request.resource.data.status == 'PENDING'
  && zglaszajacy().role in ['PSYCHOLOGIST', 'PSYCHOLOGY_STUDENT']
  && request.resource.data.keys().hasOnly(
       ['docImageBase64', 'docType', 'status', 'submittedAt'])
  && request.resource.data.submittedAt <= request.time.toMillis()
  && request.resource.data.submittedAt > request.time.toMillis() - 300000;
```

**Sprawdź listę pól względem modelu**, zanim uruchomisz testy. W Etapie 3 podana przeze mnie lista nie zawierała jednego pola i odrzuciłaby każdy zapis z aplikacji — testy reguł konstruujące dokumenty ręcznie tego nie pokazały.

Ścieżka ponownego zgłoszenia po odrzuceniu też potrzebuje listy pól — sprawdź, czy istniejące warunki wystarczą.

- [ ] **Krok 5: Mutacja per klauzula**

Usuń pojedynczo każdą nową klauzulę, uruchom `npm test`, zanotuj, który test się zaczerwienił. Klauzula, której usunięcie nie zapala żadnego testu, nie jest przetestowana — dopisz test izolujący i powtórz.

---

## Zadanie 2: Model, źródło danych, repozytorium, atrapa

**Pliki:** `VerificationRequestModel.kt`, `FirestoreVerificationDataSource.kt`, `VerificationRepository.kt`, `DataSources.kt`, `AppContainer.kt`, `FakeDataSources.kt`

**Interfejsy:**
- Produkuje: `VerificationRepository` z `myRequest(uid): Flow<VerificationRequestModel?>`, `pendingRequests(): Flow<List<VerificationRequestModel>>`, `submit(request)`, `decide(uid, approved, note)`

- [ ] **Krok 1: Model**

```kotlin
enum class VerificationStatus { PENDING, APPROVED, REJECTED }

data class VerificationRequestModel(
    val uid: String = "",
    val docImageBase64: String = "",
    val docType: String = "",
    val status: VerificationStatus = VerificationStatus.PENDING,
    val submittedAt: Long = 0L,
    val reviewedBy: String = "",
    val reviewedAt: Long = 0L,
    val reviewNote: String = ""
)
```

`uid` jest kluczem dokumentu, nie polem — **nie zapisuj go w dokumencie**, bo lista dozwolonych pól go nie obejmuje. Ustawiaj przy odczycie z identyfikatora dokumentu.

- [ ] **Krok 2: Zatwierdzenie jedną partią**

`decide(uid, approved, note)` zapisuje **w jednym `batch()`**: status zgłoszenia oraz `users/{uid}.isVerified`. Osobne zapisy zostawiają przy awarii konto zweryfikowane bez zgłoszenia albo odwrotnie.

Wzorzec masz w `FirestoreReviewDataSource.saveReviewWithRating` — ta sama konstrukcja, ten sam powód.

- [ ] **Krok 3: Atrapa**

`FakeVerificationDataSource` ma zachowywać się jak źródło: `decide` zmienia **obie** rzeczy, nie tylko status. Rozjazd atrapy ze źródłem ugryzł w tym projekcie pięć razy.

- [ ] **Krok 4: Test wierności**

Dopisz do `FakeDataSourcesFidelityTest`: `decide(approved = true)` ustawia status **i** odznakę użytkownika; `decide(approved = false)` ustawia status i notatkę, a odznaki **nie rusza**.

---

## Zadanie 3: Kompresja skanu

**Pliki:** `ui/verification/ImageCompression.kt`, `app/src/test/java/com/example/stellare/verification/ImageCompressionTest.kt`

Skan musi zejść poniżej około 500 KB, żeby po zamianie na base64 zmieścić się w limicie dokumentu.

- [ ] **Krok 1: Wydziel decyzje jako czyste funkcje**

Praca na bitmapie jest nietestowalna bez urządzenia, ale **decyzje już tak**:

```kotlin
fun sampleSizeFor(width: Int, height: Int, maxPixels: Int): Int
fun qualityFor(sizeBytes: Int, targetBytes: Int): Int
fun fitsInDocument(base64Length: Int): Boolean
```

Sama obróbka bitmapy to cienka warstwa wołająca te funkcje. Taki podział daje testy bez emulatora — inaczej ta część etapu nie ma żadnego pokrycia.

- [ ] **Krok 2: Testy czystych funkcji**

Minimum sześć, każdy na jedną rzecz:
- obraz mniejszy niż limit nie jest pomniejszany (`sampleSizeFor` zwraca 1)
- obraz czterokrotnie za duży daje współczynnik 2
- `qualityFor` zwraca niższą jakość dla większego pliku
- `qualityFor` nigdy nie schodzi poniżej rozsądnego minimum, np. 40
- `fitsInDocument` odrzuca długość powyżej limitu
- `fitsInDocument` przyjmuje długość tuż poniżej limitu

- [ ] **Krok 3: Zabezpieczenie przed wyczerpaniem pamięci**

Dekodowanie zdjęcia z aparatu wprost do bitmapy potrafi wyczerpać pamięć. Użyj `BitmapFactory.Options.inJustDecodeBounds` do odczytania wymiarów, potem `inSampleSize` z Kroku 1. **Nie dekoduj pełnego obrazu, żeby sprawdzić rozmiar.**

---

## Zadanie 4: `VerificationViewModel`

**Pliki:** `ui/verification/VerificationViewModel.kt`, test

**Interfejsy:**
- Konsumuje: `VerificationRepository`, `UserRepository`
- Produkuje: `myRequest: StateFlow<VerificationRequestModel?>`, `pendingRequests: StateFlow<List<VerificationRequestModel>>`, `submitError: StateFlow<String?>`, `suspend submit(imageBase64, docType): Boolean`, `suspend decide(uid, approved, note): Boolean`

- [ ] **Krok 1: Walidacja przed zapisem**

Reguła odrzuci zgłoszenie za duże albo o złym statusie, a odrzucony zapis bez komunikatu wygląda dla użytkownika jak **nic się nie stało**. Waliduj przed wysłaniem i wystaw `submitError`, wzorem `WalletViewModel.topUpError` i `PostViewModel.publishError`.

Komunikaty po polsku:
- brak wybranego pliku → `"Najpierw wybierz zdjęcie dokumentu."`
- za duży po kompresji → `"Zdjęcie jest zbyt duże. Spróbuj zrobić je ponownie w gorszym oświetleniu lub wybierz inne."`

- [ ] **Krok 2: Metody zwracają wynik, nie polegają na czasie**

`submit` i `decide` są `suspend` i zwracają `Boolean` — ekran nawiguje na `true`. Ten sam kształt co `ChatViewModel.openChatWith`, `WalletViewModel.spend` i `PostViewModel.publish`.

**Nie używaj `yield()` ani opóźnień do sprawdzenia, czy zapis się udał.** W Etapie 3 taka konstrukcja trafiła do rundy poprawek: działała przez zależność od czasu, nie przez kontrakt.

- [ ] **Krok 3: Nie odpytuj przed zalogowaniem**

Jeśli cokolwiek ładujesz w `init`, **poczekaj na zalogowanego użytkownika** (`currentUser.filterNotNull().first()`). Bezwarunkowe zapytanie w `init` dostaje odmowę uprawnień i trwale zamyka strumień — ten błąd wystąpił w tym projekcie dwa razy, w `ChatViewModel` i w `PostViewModel`.

- [ ] **Krok 4: Testy**

Minimum sześć, każdy na jedną rzecz: udane zgłoszenie zwraca `true` i zapisuje; brak pliku zwraca `false`, ustawia komunikat i **nic nie zapisuje**; za duży plik to samo; `decide(approved = true)` ustawia odznakę; `decide(approved = false)` zapisuje notatkę i **nie** ustawia odznaki; uzgadnianie nie rusza przed zalogowaniem.

- [ ] **Krok 5: Mutacje**

Po jednej: usuń walidację rozmiaru, usuń ustawienie odznaki w `decide`, usuń bramkę zalogowania. Każda musi zapalić test.

---

## Zadanie 5: Ekran weryfikacji

**Pliki:** `ui/verification/VerificationScreen.kt`, `Routes.kt`, `StellareNavHost.kt`, `ProfileSheet.kt`

- [ ] **Krok 1: Wybór zdjęcia bez uprawnień**

`ActivityResultContracts.PickVisualMedia` do galerii. Jeśli dokładasz aparat, użyj `TakePicture` z aparatem systemowym — **nie deklaruj uprawnienia `CAMERA` w manifeście**, bo wtedy trzeba by o nie prosić.

**Nie odkomentowuj zależności `androidx.camera.*` ani `accompanist-permissions`** z `app/build.gradle.kts`.

- [ ] **Krok 2: Ekran statusu**

Cztery stany, każdy z innym komunikatem: brak zgłoszenia, oczekuje, zatwierdzone, odrzucone wraz z powodem z `reviewNote`. Po odrzuceniu widoczny przycisk ponownego wysłania.

- [ ] **Krok 3: Wejście z arkusza profilu**

Pozycja „Weryfikacja kwalifikacji" widoczna **wyłącznie dla psychologa i studenta psychologii**. Pacjent jej nie widzi, bo nie ma czego weryfikować.

---

## Zadanie 6: Panel administratora

**Pliki:** `ui/verification/AdminPanelScreen.kt`, `Routes.kt`, `StellareNavHost.kt`, `ProfileSheet.kt`

- [ ] **Krok 1: Odsłoń pozycję w arkuszu**

`ProfileSheet` ma dziś pozycję „Panel weryfikacji" ukrytą za stałą `false` z komentarzem wskazującym ten etap. Zamień warunek na `currentUser.isAdmin`.

- [ ] **Krok 2: Lista i podgląd**

Lista zgłoszeń oczekujących, każde z imieniem zgłaszającego i datą. Podgląd skanu z base64 przez `BitmapFactory.decodeByteArray` na `Base64.decode`.

**Dekoduj w tle, nie w kompozycji** — dekodowanie obrazu pół megabajta w wątku głównym zatnie interfejs.

- [ ] **Krok 3: Zatwierdzenie i odrzucenie**

`Zatwierdź` woła `decide(uid, true, "")`. `Odrzuć` otwiera pole na powód i woła `decide(uid, false, powód)`.

**Odrzucenie bez powodu nie ma sensu** — zgłaszający zobaczy „odrzucone" i nie będzie wiedział, co poprawić. Wymagaj niepustej notatki i pokaż komunikat, gdy jej brak.

- [ ] **Krok 4: Panel jest niedostępny dla nie-administratora**

Sprawdź, że wejście na trasę panelu bez uprawnień nie pokazuje danych. Reguły i tak odmówią odczytu, ale ekran nie powinien wyglądać jak pusty panel — ma jasno powiedzieć, że nie ma dostępu.

---

## Zadanie 7: Test na urządzeniu

Bez tego etap nie jest zamknięty. W Etapie 2 trzy realne błędy przeszły przez 185 testów i dziesięć recenzji, a widać je było w pierwszej minucie używania.

- [ ] **Krok 1: Środowisko**

Emulatory Firebase, zasiew, emulator Androida, `installDebug`, `adb shell pm clear com.example.stellare`.

Konto administratora jest w zasiewie (`admin@stellare.test`) i ma wpis w `admins`.

- [ ] **Krok 2: Ścieżka**

1. Logi po zimnym starcie, **przed zalogowaniem** — żadnego `PERMISSION_DENIED`
2. Psycholog wybiera zdjęcie i wysyła — czy widzi status „oczekuje"?
3. Pacjent **nie widzi** pozycji „Weryfikacja kwalifikacji" w arkuszu
4. Administrator widzi panel i zgłoszenie na liście; skan się wyświetla
5. Odrzucenie **bez powodu** jest zablokowane
6. Odrzucenie z powodem — czy psycholog widzi ten powód?
7. Ponowne wysłanie po odrzuceniu działa
8. Zatwierdzenie — czy odznaka „Zweryfikowany" pojawia się **na karcie ogłoszenia** psychologa?
9. Zwykły użytkownik nie widzi pozycji „Panel weryfikacji"

Punkt 8 jest najważniejszy: łączy ten etap z Etapem 3 i sprawdza, czy uzgadnianie danych autora w ogłoszeniach faktycznie działa po zmianie odznaki przez administratora.

- [ ] **Krok 3: Zrzuty ekranu jako dowód**

Każdy punkt ze zrzutem. Opis bez zrzutu nie jest dowodem.

---

## Definicja ukończenia

- Reguły `verificationRequests` domknięte, **każda klauzula zweryfikowana osobną mutacją**
- Nie da się wysłać zgłoszenia pod cudzym identyfikatorem, ze sfabrykowanym śladem rozpatrzenia ani jako pacjent
- Odznakę przestawia wyłącznie administrator, jedną partią razem ze statusem zgłoszenia
- Testy jednostkowe i testy reguł zielone
- Test na urządzeniu przeszedł, ze zrzutami

## Czego ten plan świadomie nie obejmuje

- Kolejki zgłoszeń z priorytetami i przydzielaniem do konkretnego administratora
- Historii zgłoszeń — jedno konto ma jedno zgłoszenie, kolejne nadpisuje poprzednie
- Powiadomienia o rozpatrzeniu; użytkownik widzi wynik po wejściu na ekran weryfikacji
- Automatycznego rozpoznawania treści dokumentu — ocenia człowiek
- Firebase Storage, które zniosłoby limit rozmiaru; wymaga planu Blaze, patrz sekcja 10 specyfikacji
