# Stellare — przekazanie po etapie 9

Dokument dla osób, które będą pisać ten kod dalej i **nie uczestniczyły** w etapie 9. Opisuje stan,
reguły gry, których złamanie boli najszybciej, i listę rzeczy do zrobienia z kosztem tam, gdzie go znamy.

Pełny zapis decyzji (109 rozstrzygnięć z uzasadnieniami), raporty per zadanie, raporty recenzji oraz
zrzuty ekranu z weryfikacji na urządzeniu **zostały świadomie zostawione poza repozytorium**, lokalnie
u właściciela projektu. Jeśli potrzebujesz uzasadnienia decyzji, której nie wyjaśnia ten plik ani
komentarz obok kodu — poproś go o wgląd. Powody, bez których łatwo zepsuć regułę, zostały przeniesione
do komentarzy w `firebase/firestore.rules`, żeby podróżowały razem z kodem.

## 1. Stan

| | przed etapem | po etapie |
|---|---|---|
| testy jednostkowe | 831 | **1215** (0 padnięć, 0 pominiętych) |
| testy reguł Firestore | 392 | **673** (+1 pominięty: istniejący `it.skip` w `wallet.test.js`) |
| `firebase/firestore.rules` | 666 linii | **879** |

Zamknięte trzy dziury bezpieczeństwa (szczegóły w specyfikacji, sekcja 10): dane kontaktowe i saldo
niewidoczne dla obcych; monety pacjenta na depozycie, zwracane **bez udziału psychologa**; suma ocen
poza dokumentem właściciela, nieusuwalna.

Zamknięte przy okazji, choć nikt ich nie szukał: awaria pozwalająca **jednym zapisem z zewnątrz
trwale zablokować cudze konto**; ścieżka, w której zalogowany użytkownik bez sieci dostawał
zaproszenie do **nadpisania własnego profilu**; przycisk pozwalający nadać odznakę weryfikacji
**bez obejrzenia dokumentu**; przycisk ponowienia, który był **martwym kodem**, bo warstwa danych
połykała awarie; pięć kontrolek filtrowania, które **niczego nie filtrowały**; interfejs rysujący
**pełny ekran danych przy pustej bazie**.

## 2. Reguły gry, których złamanie boli najszybciej

Każda z nich kosztowała w etapie 9 co najmniej jedną rundę poprawek. Nie są to preferencje stylistyczne.

### Firestore — reguły

- **`request.resource.data` to dokument PO SCALENIU, nie delta.** Dlatego `!keys().hasAny(['pole'])`
  na edycji blokuje zapisy, które tego pola **nie dotykają**, a `unchanged('pole')` blokuje także jego
  **usunięcie**. „Pole nie może się pojawić ani zmienić, ale wolno je usunąć" to
  `(!keys().hasAny(['pole']) || unchanged('pole'))`.
- **Reguły są oceniane PRZED istnieniem dokumentu.** Zapis do nieistniejącego dokumentu daje
  `permission-denied`, nie `NOT_FOUND`. Mapowanie `NOT_FOUND` na komunikat jest martwym kodem.
- **Reguły nie potrafią odpytywać kolekcji** — tylko odczyt po znanej ścieżce (`get`, `getAfter`,
  `exists`, `existsAfter`), limit **10 dostępów** w regule jednodokumentowej.
- **Pole tożsamości (`id`, `chatId`, `messageId`, `noteId`, `appointmentId`, `reviewId`) ma warunek
  ŁAGODNY:** `request.resource.data.get('pole', idDokumentu) == idDokumentu` — przepuszcza brak,
  odrzuca kłamstwo. Powód stoi w komentarzu przy `match /posts`: klient **nie czyta już tego pola**
  (tożsamość bierze z identyfikatora dokumentu), więc pole, którego nie ma, nie kłamie i nie szkodzi,
  a wariant ścisły dokładał trwały tryb awarii (dokument zapisany spoza aplikacji bez pola byłby
  nieedytowalny **na zawsze**; dla `users` to profil, którego administrator nigdy nie zweryfikuje).
  **Wyjątek:** `escrows` zostaje ścisłe, bo reguła rezerwacji **autoryzuje** na podstawie tego pola.
- **Niezmiennik monet, dokładnie:** suma sald plus suma depozytów `HELD` jest stała, **z wyjątkiem**
  doładowania (do limitu) i dobrowolnego zniszczenia własnych monet; doładowanie może zajść w tej
  samej partii co rozliczenie. Zdanie bez tych wyjątków jest fałszywe i testy to pokażą.
- `FieldValue.increment` jest **jedyną** wykonalną implementacją, gdy reguła wymaga dokładnej zmiany
  salda na dokumencie, którego zapisujący nie może odczytać.

### Firestore — klient

- **Tożsamość bierz z `document.id`, nigdy z pola.** `toObjects(X::class.java)` czyta pole, które
  pisze ktoś inny; dwa dokumenty mogą twierdzić, że są tym samym obiektem, a `LazyColumn` **rzuca
  wyjątek przy powtórzonym kluczu**. Wzorzec: `toObject(X::class.java)?.copy(id = document.id)`.
  Pilnuje tego `DocumentIdentityTest` — zapadka na kształcie źródła (bo `DocumentSnapshot` nie ma
  publicznego konstruktora). **Dodając nowe mapowanie, dopisz je do tej zapadki**, inaczej zrobisz
  w niej dziurę, której nie zobaczysz w liczbie testów.
- **„Serwer mówi, że nie ma" i „pamięć podręczna nic nie wie" to TA SAMA MIGAWKA.** `exists == false`
  ich nie odróżnia. Rozwiązanie w `UserDocumentWatch`: `MetadataChanges.INCLUDE` (przy domyślnym
  `EXCLUDE` stan „serwer potwierdził brak" jest **nieosiągalny**) plus flaga **per nasłuch** „serwer
  już odpowiedział". Bez tego nieudany odczyt rysuje się jako „nie masz profilu".
- **Błąd nasłuchu przekazuj WARTOŚCIĄ.** `close(error)` bez obsługi u konsumenta pod
  `stateIn(viewModelScope)` **wywraca aplikację**. Propagacja i obsługa to **jedna** zmiana, nie dwie.
- `catch (CancellationException) { throw e }` **przed** `catch (Exception)`; `try` **wewnątrz**
  `collect`, nie na zewnątrz (na zewnątrz przepływ umiera po pierwszej odmowie i nic się nie ponawia).
- **Trybu samolotowego nie da się użyć do wywołania błędu nasłuchu** — Firestore offline nie zgłasza
  błędu, serwuje pamięć podręczną. Do stanu „odczyt padł" dochodzi się unieważnieniem tokenu albo
  odmową reguł; do stanu „pusty cache bez sieci" — czystymi danymi aplikacji i pierwszym startem offline.
- **Odczyt rozstrzygający po błędzie zapisu musi pomijać pamięć podręczną** (`Source.SERVER` albo
  transakcja): cache zawiera **lokalnie zakolejkowany** zapis i potwierdziłby wpis, którego serwer
  nigdy nie przyjął.
- Jedna taksonomia błędów trwałych: `RetryWithBackoff` (`PERMISSION_DENIED`, `INVALID_ARGUMENT`,
  `UNIMPLEMENTED`) — **nie buduj drugiej**. Ponawianie odmowy trwałej to pętla, która nie może się udać.

### Interfejs

- **minSdk 24 BEZ desugaringu** — `java.time` się nie skompiluje. Daty przez `java.util.Calendar`.
- **Zero literałów koloru poza `ui/theme/`.** Każdy kolor to nazwany token z wariantem jasnym i ciemnym.
  **Kontrast mierz per motyw** — w etapie 9 obramowanie przycisku miało 1,33:1 w jasnym i poprawne
  3,80:1 w ciemnym; defekt istniał tylko w jednym.
- **Stan wyłączony: kontrast treści ≥ 3:1.** Dla przycisków wypełnionych `stellareButtonColors()`,
  dla `TextButton`/`OutlinedButton`/`IconButton` **rodzeństwo** tej funkcji — `stellareButtonColors()`
  tam nie zadziała (`IconButton` przyjmuje `IconButtonColors`, a przyciskom o przezroczystym tle
  domyślny `containerColor = primary` zamalowałby tło).
- **Jawny `tint`/`color` na dziecku kontrolki unieważnia stan wyłączony rodzica.** Gwiazdka oceny z
  jawnym `tint` wyglądała na aktywną, choć była wyłączona (zmierzone: 1,27:1).
- **`rememberSaveable` dla stanu wpisanego, Z WYJĄTKIEM HASŁA** — `savedInstanceState` trafia na dysk.
  Dwa komentarze o tym w `LoginScreen.kt` i `RegistrationScreen.kt` **mają zostać**.
- **Baner wstawiany do widoku jest zmianą układu, a układu nie dowodzi kompilacja.** Dwa razy w etapie
  baner z przyciskiem wylądował jako pierwsze dziecko `Box`, którego drugie dziecko wypełniało obszar
  od góry — i był **przykryty treścią**. Wstawiaj jako **rodzeństwo** treści.
- **Komunikaty mówią o stanie tu i teraz, nigdy nie obiecują przyszłości.** Zdanie „Monety wrócą po
  oknie zwrotu" było **nieprawdziwe** w scenariuszu, dla którego powstało. Najwęższe prawdziwe zdanie
  jest zawsze bezpieczniejsze od uprzejmej obietnicy.
- **Kontrolka, którą użytkownik naciska bez skutku, obiecuje najdobitniej ze wszystkiego** — bo reaguje.
  W etapie 9 usunięto z tego powodu pięć kontrolek filtrowania, które niczego nie filtrowały.
- Każdy `items(...)` ma `key` z unikalnym identyfikatorem — **chyba że** lista jest stała i o niezmiennej
  długości (wtedy domyślnym kluczem jest indeks i dopisywanie klucza nic nie wnosi).

### Testy i dowodzenie

- **Testów Compose nie ma, ale wszystkie zależności są zadeklarowane** w `libs.versions.toml`
  (`robolectric`, `roborazzi`, `compose-ui-test-junit4`, `-manifest`), a wtyczka Roborazzi jest
  zastosowana. Brakuje **trzech wpisów w `dependencies`** w `app/build.gradle.kts` oraz prawdopodobnie
  `robolectric.properties` z `sdk=35`. `isIncludeAndroidResources = true` już stoi.
- **Alarm „zero uruchomionych testów":** licz testy z `app/build/test-results`, **kasuj katalog przed
  przebiegiem**, używaj `--rerun-tasks`. Pierwszy przebieg po zmianach wielokrotnie wychodził
  `UP-TO-DATE` z zerem wykonanych testów i wyglądał na sukces.
- **Testy reguł: jeden przebieg naraz, nic w tle, nigdy `emulators:exec`.** Dwa równoległe przebiegi
  czyszczą sobie dane. **Zmienna liczba padnięć na niezmienionym kodzie to podpis wyścigu**, bo
  regresja jest deterministyczna.
- **Emulator Firestore trzyma OSOBNY zbiór reguł dla każdego identyfikatora projektu**, a biblioteka
  testowa wgrywa swój pod `stellare-test`, podczas gdy aplikacja pisze pod `stellare-a39ef`. **Zielone
  testy reguł nie mówią nic o regułach, na których działa aplikacja.** Przed weryfikacją na urządzeniu
  sprawdź sondą, co jest wczytane: odmowa z emulatora **cytuje numery linii wczytanego zbioru** —
  porównaj je z plikiem. Raz w etapie emulator trzymał reguły sprzed dwóch zadań, a wnioski wyglądałyby
  identycznie jak prawdziwe.
- **Zasiew danych:** REST emulatora z nagłówkiem `Authorization: Bearer owner` **pomija reguły**.
  Konto administratora wymaga dwóch rzeczy: `isAdmin()` sprawdza `exists(/admins/{uid})`, **nie** pole
  `users.isAdmin`.
- **Cztery rodzaje testu bez wartości dowodowej:** (a) stwierdzający błąd jako oczekiwany;
  (b) przechodzący z innego powodu niż bada; (c) padający lub pominięty w przygotowaniu, nie na
  asercji; (d) **sprawdzający fikcję atrapy rozjechanej z produkcją**. Kategoria (d) wystąpiła realnie
  trzy razy — raz atrapa nasłuchu **sama się naprawiała** po błędzie i przez to czyniła prawdziwy defekt
  **nieodtwarzalnym**. Pierwszym objawem jest zdanie „nie umiem napisać testu, który pada".
- **Atrapa, która liczy cokolwiek sama, jest kandydatem na fikcję.** Atrapa trzymająca **produkcyjną**
  implementację i podstawiająca tylko wejście nie ma jak się rozjechać — po takiej zamianie mutacja
  przywracająca defekt przeszła z 0 na 9 zabójców.
- **Zielony przebieg po zdjęciu osłony nie dowodzi, że osłona była zbędna — dowodzi, że nikt jej nie
  testował.**
- **Gdy mutant przeżyje, policz odwołania w bajtkodzie** (`javap`), żeby odróżnić „mutant nie wszedł"
  od „nikt tego nie testuje". I policz, czy wykonały się **wszystkie** zaplanowane mutacje — jedna
  wypadła z przebiegu po cichu z powodu nieaktualnego wzorca, a wykryła to dziura w numeracji logu.

## 3. Co zostało do zrobienia

### Wymaga planu Blaze (nie da się kodem)

1. **Pobieranie płatności za monety** — Cloud Functions. To jedyna droga do zamknięcia niezabezpieczonego
   doładowania; dziś reguły ograniczają kwotę i wymuszają wpis w księdze, ale użytkownik technicznie
   sprawny dopisze sobie monety.
2. **Firebase Storage na skany dokumentów** — dziś skan siedzi w Firestore jako base64, z limitem 1 MB.
3. **SMS-y poza bezpłatną pulą** (Spark: 10 na dobę na cały projekt).

### Dług techniczny z wyliczonym kosztem

4. **Wpięcie zależności testowych Compose** — trzy wiersze w `app/build.gradle.kts`, prawdopodobnie
   `robolectric.properties` z `sdk=35`, test ~20 wierszy z `createComposeRule()`. Zabija mutację, która
   dziś przeżywa: podmiana `enabled` przy przycisku zatwierdzania weryfikacji **nie zabija żadnego
   testu**, bo nie ma czym uruchomić `@Composable`. Jedyne ryzyko: zgodność Robolectric 4.16.1 z
   `compileSdk 36.1`. Szacunek: w granicach godziny.
5. **Szew dla nasłuchu `FirestoreUserDataSource.getUser`** — klasa sięga po `db` wprost, więc mutacja
   usuwająca sklejkę nasłuchu przeżywa. **Żadna zależność testowa tego nie naprawi** (Robolectric nie
   postawi działającego nasłuchu Firestore); potrzebny jest interfejs analogiczny do `UserDocumentReader`.
   Uwaga: to refaktor w **najwrażliwszym miejscu aplikacji** — po nim trzeba powtórzyć dym na urządzeniu.
6. **Kłamiący komentarz w `UserRepository:80-84`** — twierdzi, że `readCurrentUser()` rozróżnia
   „niezalogowany" od „brak dokumentu", a nie rozróżnia (i nie rozróżniał od dawna). Zachowanie
   zostawiono świadomie: zmiana wymaga nowych decyzji o treści komunikatów w sześciu miejscach zapisu.
   Komentarz **należy poprawić albo usunąć** — kłamiący komentarz jest gorszy od żadnego.
7. **Migracja historycznych ocen** do `ratings/{uid}` wymaga Admin SDK (reguły zabraniają zapisu przez
   właściciela). Konta sprzed etapu 9 mają sumę ocen tylko w nowym miejscu, jeśli dostały nową opinię.

### Decyzje produktowe (nie dług — ktoś musi wybrać)

8. **Brak kontrolki „Dezaktywuj ogłoszenie".** Zdolność istnieje w warstwie danych i w regułach
   (dezaktywacja zamiast usunięcia zostawia ślad nadużycia — `delete` kasuje dowód), ale **nie ma do
   niej żadnego wejścia w interfejsie**. Obsługa błędu jest już gotowa, więc po dodaniu przycisku nie
   będzie milczeć.
9. **Filtrowanie ogłoszeń po cechach autora.** Pięć usuniętych kontrolek filtrowania antycypowało tę
   funkcję; dane są dostępne (migawka autora w ogłoszeniu plus oceny osobno). Trzeba zbudować ekran
   albo filtr na istniejącym.
10. **Biała kartka w pełnym wymiarze przechodzi blokadę skanu.** Próg (324 px najkrótszej krawędzi,
    wyprowadzony z czytelności legitymacji ID-1) odrzuca obrazy, na których nic nie widać, ale nie
    odróżni pustej kartki od bladego skanu. Miary treści świadomie nie dodano: wariancja pikseli nie
    odróżni tych dwóch, a odrzucenie poprawnego zgłoszenia jest szkodą symetryczną — przy pełnym
    wymiarze administrator **widzi, co przysłano**, i ma czynne „Odrzuć".

### Niezmierzone (ktoś powinien obejrzeć)

11. `ChatRoomScreen` — strony dymków; `ProfileEditScreen` — puste pola formularza. Oba wyszły przy
    wpinaniu banera o nieaktualnych danych i nie zostały zmierzone.

## 4. Jak to uruchomić

```
# testy jednostkowe (punkt odniesienia: 1215, 0 padnięć, 0 pominiętych)
rm -rf app/build/test-results/testDebugUnitTest
./gradlew cleanTestDebugUnitTest testDebugUnitTest --console=plain --rerun-tasks

# testy reguł (punkt odniesienia: 673 przeszło, 1 pominięty) — z katalogu firebase, JEDEN przebieg
firebase/node_modules/.bin/firebase.cmd emulators:start --only firestore,auth --project stellare-a39ef
npx vitest run

# aplikacja na emulatorze (wskazuje na emulatory TYLKO w debug)
./gradlew installDebug
```

Firebase CLI **nie jest w PATH** — wołaj go z `firebase/node_modules/.bin/`. `adb` i `emulator` też
nie są w PATH. Zrzuty ekranu rób przez `adb shell screencap -p` + `adb pull`, **nie** przez
przekierowanie `exec-out` (na Windowsie psuje bajty) — i po każdym sprawdź rozmiar pliku.

Pisząc skrypty pomocnicze: czytaj cały plik, przekształcaj w pamięci, zapisuj **atomowo** (plik
tymczasowy + `os.replace`) — zabicie skryptu w trakcie zapisu **wyzerowało** raz plik produkcyjny.
I nie przepuszczaj polskiego tekstu ani odwrotnych apostrofów przez heredoc powłoki: ten jeden wzorzec
zepsuł w etapie 9 skrypty czterem osobom.
