# Stellare

Aplikacja Android łącząca psychologów i osoby szukające pomocy: ogłoszenia, kalendarz wizyt, czaty,
oceny, wirtualny portfel monet i weryfikacja kwalifikacji psychologa przez administratora.

Kotlin + Jetpack Compose + Firebase (Authentication, Firestore). **Brak własnego serwera** — cała
logika bezpieczeństwa siedzi w regułach Firestore.

> **Ten dokument jest dla osoby, która przejmuje projekt i nic w nim dotąd nie robiła.** Czytaj po
> kolei: sekcja 3 doprowadzi Cię do działającej aplikacji na emulatorach w kilkunastu minutach, bez
> żadnych kluczy i bez dostępu do Firebase. Sekcja 5 mówi, co trzeba dostarczyć, żeby działało na
> prawdziwym środowisku.

---

## 1. Stan projektu

| | |
|---|---|
| testy jednostkowe | **1215** (0 padnięć, 0 pominiętych) |
| testy reguł Firestore | **673** (+1 świadomie pominięty) |
| pliki źródłowe | 224 produkcyjne, 164 testowe (Kotlin) + 16 plików testów reguł (JS) |
| reguły Firestore | 879 linii |

### Co można zrobić w aplikacji

Aplikacja ma **23 trasy nawigacji** i trzy role: pacjent, psycholog i administrator. Rola jest wybierana
przy rejestracji i decyduje, co widać.

**Każdy, niezależnie od roli**

| Funkcja | Szczegóły |
|---|---|
| Rejestracja i logowanie | e-mailem z hasłem, kontem Google albo Facebookiem; reset hasła e-mailem |
| Powiązanie numeru telefonu (2FA SMS) | kod SMS; numer **nie jest** polem „zweryfikowany" w profilu — potwierdzenie żyje w tokenie uwierzytelnienia, więc nie da się go podrobić zapisem do bazy |
| Uzupełnienie i edycja profilu | imię, nazwisko, wiek, płeć, opis; psycholog dodatkowo cenę za sesję i specjalizacje |
| Ogłoszenia | przeglądanie listy, filtrowanie po tagach i szukanie tekstem, zakładanie własnego ogłoszenia („szukam psychologa" albo „oferuję terapię"), dezaktywacja własnego |
| Czaty | rozmowa z drugą stroną, wątek per para użytkowników; ostatnia wiadomość i czas widoczne na liście |
| Portfel | saldo, historia operacji z nazwami drugiej strony, doładowanie |
| Kalendarz | widok tygodnia, szczegóły wizyty, odwołanie |
| Ustawienia | motyw (jasny / ciemny / systemowy), rozmiar czcionki, kolor dymków czatu |
| FAQ | statyczne odpowiedzi w aplikacji |
| Usunięcie konta | z informacją, co przepada (patrz sekcja 8) |

**Pacjent**

| Funkcja | Szczegóły, dokładnie |
|---|---|
| Doładowanie portfela | pakiety **50 / 100 / 250 monet**; ręcznie **od 1 do 1000 monet** na raz. **Płatność jest symulowana** — nie ma operatora płatności (patrz sekcja 7) |
| Przeglądanie psychologa | profil, cena za sesję, średnia ocena i liczba opinii, status weryfikacji kwalifikacji, wolne terminy |
| Rezerwacja terminu | monety schodzą z salda **na depozyt wizyty**, nie na konto psychologa. Cena jest zapisywana na dokumencie terminu w chwili rezerwacji, więc późniejsza zmiana cennika nie wpływa na zwrot |
| Odwołanie **ponad 24 h** przed terminem | **pełny zwrot, samoobsługowy** — nie wymaga żadnego działania psychologa |
| Odwołanie **poniżej 24 h** przed terminem | bez zwrotu; monety pozostają w depozycie do rozliczenia |
| Termin, którego psycholog nigdy nie rozliczył | po **14 dniach** od minionego terminu pacjent odzyskuje monety sam. To zamyka przypadek psychologa, który przestał korzystać z aplikacji |
| Ocena i opinia | gwiazdki **1–5** plus komentarz, **tylko po wizycie oznaczonej jako odbyta**; jedna opinia na wizytę |

**Psycholog**

| Funkcja | Szczegóły, dokładnie |
|---|---|
| Zgłoszenie kwalifikacji do weryfikacji | typ dokumentu (dyplom / legitymacja / certyfikat) i zdjęcie skanu; skan idzie do Firestore jako base64, **limit 1 MB** |
| Odznaka „Zweryfikowany" | nadaje ją **wyłącznie administrator**; reguły nie pozwalają nadać jej sobie |
| Wystawianie wolnych terminów | w kalendarzu; cena bierze się z profilu |
| Oznaczenie wizyty jako odbytej | **dopiero wtedy** monety z depozytu trafiają na jego saldo i pojawia się wpis „Wpływ za wizytę" w księdze |
| Odwołanie wizyty | **zawsze** pełny zwrot dla pacjenta, niezależnie od terminu |
| „Moi klienci" | lista czatów z pacjentami, z notatkami prywatnymi do rozmowy |
| „Moje oceny" | własne opinie i średnia; **nie da się ich wyzerować**, usuwając i odtwarzając profil |

**Administrator**

| Funkcja | Szczegóły, dokładnie |
|---|---|
| Panel weryfikacji | kolejka zgłoszeń z podglądem skanu dokumentu |
| Zatwierdzenie | **możliwe tylko, gdy skan da się odczytać** — przy braku skanu albo obrazie poniżej progu czytelności przycisk jest nieaktywny |
| Odrzucenie | zawsze dostępne, z podaniem powodu (brak czytelnego skanu jest najczęstszym powodem odrzucenia) |
| Dostęp | wyłącznie przez dokument `admins/{uid}` w Firestore — **nie** przez pole w profilu |

Czego w aplikacji **nie ma**: prawdziwych płatności, wypłaty monet, powiadomień push, wideorozmów,
listy psychologów jako osobnego ekranu (wyszukiwanie idzie po ogłoszeniach) oraz kontrolki
dezaktywacji ogłoszenia, choć sama zdolność istnieje w danych i w regułach (sekcja 7, pozycja 8).

**Czego NIE ma:** własnego serwera, prawdziwych płatności (doładowanie portfela jest symulowane),
Firebase Storage (skany dokumentów idą do Firestore jako base64, limit 1 MB), testów interfejsu
(Compose) — szczegóły w sekcji 7.

---

## 2. Wymagania

| Narzędzie | Wersja | Uwagi |
|---|---|---|
| **JDK** | 17+ | projekt kompiluje do zgodności z **Java 11** (`sourceCompatibility = VERSION_11`) |
| **Android Studio** | nowsze niż AGP 9.1.1 | albo sam SDK + wrapper Gradle z repozytorium |
| **Android SDK** | `compileSdk 36.1`, `targetSdk 36`, **`minSdk 24`** | |
| **Node.js** | 18+ | tylko do emulatorów Firebase i testów reguł |
| Gradle | **9.3.1** | z wrappera, nie instaluj osobno |
| Kotlin | 2.2.10 | |
| Compose BOM | 2024.09.00 | |
| Firebase BOM | 34.17.0 | |

**`minSdk 24` BEZ desugaringu.** `java.time` się **nie skompiluje** — daty obsługuje
`java.util.Calendar`. To nie jest preferencja, to ograniczenie konfiguracji.

---

## 3. Szybki start — lokalnie, bez żadnych kluczy

Debugowy build celuje w **emulatory Firebase** (`BuildConfig.USE_FIREBASE_EMULATOR = true` w
`app/build.gradle.kts`), więc nie potrzebujesz dostępu do prawdziwego projektu Firebase, żeby
uruchomić aplikację i zobaczyć, jak działa.

```bash
# 1. zależności emulatorów Firebase (jednorazowo)
cd firebase
npm install

# 2. emulatory: Firestore na 8080, Auth na 9099
#    Firebase CLI NIE jest w PATH — wołaj go z node_modules
node_modules/.bin/firebase.cmd emulators:start --only firestore,auth --project stellare-a39ef
#    (Linux/macOS: node_modules/.bin/firebase)

# 3. w drugim terminalu: emulator Androida
#    adb i emulator też nie są w PATH — pełne ścieżki z $ANDROID_HOME/platform-tools i /emulator
emulator -avd <nazwa_twojego_avd> -no-boot-anim

# 4. instalacja aplikacji
cd ..
./gradlew installDebug
```

Aplikacja łączy się z emulatorami pod adresem `10.0.2.2` (tak emulator Androida widzi `localhost`
gospodarza). Pierwsze konto zarejestruj z ekranu rejestracji — emulator Auth przyjmie dowolny e-mail.

### Zasiew danych do testów

Emulator Firestore przyjmuje zapisy **z pominięciem reguł**, jeśli podasz nagłówek
`Authorization: Bearer owner`. To jedyna droga, żeby zasiać dane, których reguły nie przepuszczą:

```bash
curl -X PATCH \
  "http://127.0.0.1:8080/v1/projects/stellare-a39ef/databases/(default)/documents/admins/TWOJ_UID" \
  -H "Authorization: Bearer owner" -H "Content-Type: application/json" \
  -d '{"fields":{"granted":{"booleanValue":true}}}'
```

**Panel administratora wymaga dokumentu `admins/{uid}`, a nie pola `isAdmin` w profilu** —
`isAdmin()` w regułach sprawdza `exists(/admins/{uid})`. To najczęstsza pomyłka przy pierwszym
kontakcie z tym projektem.

---

## 4. Układ repozytorium

```
app/src/main/java/com/example/stellare/
  data/model/          modele domenowe (UserModel, PostModel, AppointmentModel, …)
  data/remote/         źródła danych Firestore/Auth — TU siedzą transakcje i depozyt monet
  data/repository/     repozytoria (warstwa między UI a źródłami)
  ui/<obszar>/         ekrany i modele widoku: auth, calendar, chat, dashboard, feed, posts,
                       profile, psychologistdetail, reviews, session, settings, shell,
                       verification, wallet
  ui/theme/            kolory, typografia, kolory przycisków — JEDYNE miejsce na literały koloru
app/src/test/          testy jednostkowe (JVM, bez Androida)
firebase/
  firestore.rules      reguły bezpieczeństwa — 879 linii, serce projektu
  firestore.indexes.json  7 indeksów złożonych
  test/                testy reguł (Vitest + @firebase/rules-unit-testing)
docs/
  PRZEKAZANIE-etap9.md    pułapki, które bolą najszybciej + lista rzeczy do zrobienia
  superpowers/specs/      specyfikacja projektu (sekcja 10: wymagania zewnętrzne i ograniczenia)
(dziennik decyzji — 109 rozstrzygnięć — NIE jest w repozytorium, patrz niżej)
```

---

## 5. Co trzeba dostarczyć, żeby działało na prawdziwym środowisku

`app/google-services.json` w repozytorium wskazuje na istniejący projekt **`stellare-a39ef`**
(numer `818324884549`). Masz dwie drogi: dostać dostęp do tego projektu albo **założyć własny**
i podmienić ten plik. Poniżej lista wszystkiego, czego brakuje w obu przypadkach.

### 5.1 Odciski certyfikatów — bez nich dwie funkcje są martwe

**W dołączonym `google-services.json` nie ma zarejestrowanego ani jednego odcisku SHA** (sprawdzone:
`certificate_hash` jest puste). Skutek: **logowanie Google i 2FA SMS nie zadziałają**, a komunikaty
błędów będą mylące.

```bash
# odcisk debugowy
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android
# odcisk release — z Twojego keystore (patrz 5.4)
keytool -list -v -alias upload -keystore <ścieżka>/my-upload-key.jks
```

SHA-1 **i** SHA-256 wklej w konsoli Firebase → Ustawienia projektu → Twoje aplikacje → dodaj odcisk.
Potem **pobierz `google-services.json` ponownie** i podmień plik w `app/`.

### 5.2 Klucze i konfiguracja per funkcja

| Funkcja | Co dostarczyć | Gdzie to wpisać | Co się stanie bez tego |
|---|---|---|---|
| **Logowanie e-mail + reset hasła** | nic | — | działa od razu |
| **Logowanie Google** | odcisk SHA-1 aplikacji; identyfikator klienta serwera pochodzi z `google-services.json` | konsola Firebase → Authentication → Google (włącz dostawcę) | przycisk zwraca błąd podpisu |
| **Logowanie Facebook** | **App ID i App Secret** z Meta for Developers | konsola Firebase → Authentication → Facebook; **oraz** adres przekierowania OAuth podany przez Firebase trzeba wkleić w aplikacji Meta | logowanie nie przechodzi. **Uwaga: w aplikacji NIE ma SDK Facebooka** — używany jest generyczny `OAuthProvider("facebook.com")`, więc klucze idą wyłącznie do konsoli Firebase, nie do zasobów aplikacji |
| **2FA SMS** | odciski SHA + włączone Phone Auth | konsola Firebase → Authentication → Phone | wysyłka kodu pada. **Plan Spark: 10 SMS na dobę na cały projekt** |
| **Panel administratora** | dokument `admins/{uid}` | ręcznie w Firestore albo przez REST emulatora | panel niedostępny dla nikogo |
| **Reguły i indeksy** | wdrożenie (patrz 5.3) | — | zapisy odrzucane albo zapytania padają na brak indeksu |

**Czego NIE trzeba, wbrew staremu README:** klucza Gemini ani pliku `.env`. Poprzedni `README.md` był
pozostawionym szablonem z AI Studio i mówił o `GEMINI_API_KEY` oraz o usunięciu linii
`signingConfig = signingConfigs.getByName("debugConfig")`, której w tym projekcie nie ma. Nie szukaj
tych rzeczy.

### 5.3 Wdrożenie reguł i indeksów

```bash
cd firebase
node_modules/.bin/firebase.cmd login
node_modules/.bin/firebase.cmd deploy --only firestore:rules,firestore:indexes --project <twój-projekt>
```

**Indeksów jest siedem i emulator ICH NIE EGZEKWUJE** — zapytanie, które lokalnie działa, w produkcji
padnie bez wdrożonego indeksu. Lista: `chats(participants, lastMessageTime)`,
`messages(chatId, timestamp)`, `notes(chatId, timestamp)`, `reviews(psychologistId, timestamp)`,
`wallet_transactions(userId, timestamp)`, `wallet_transactions(userId, transactionId)`,
`posts(isActive, type, createdAt)`.

### 5.4 Podpisywanie wydania

`app/build.gradle.kts` czyta konfigurację podpisu **ze zmiennych środowiskowych**:

```bash
export KEYSTORE_PATH=/pełna/ścieżka/my-upload-key.jks   # domyślnie <katalog projektu>/my-upload-key.jks
export STORE_PASSWORD=…
export KEY_PASSWORD=…
# alias klucza jest zakodowany na "upload"
./gradlew assembleRelease
```

Keystore **nie jest** w repozytorium i nie powinien być. Jeśli go nie masz, wygeneruj nowy — ale wtedy
odcisk SHA się zmieni i trzeba go dodać w konsoli Firebase (patrz 5.1).

**Build release celuje w prawdziwy Firebase**, nie w emulatory (`USE_FIREBASE_EMULATOR = false`).
To jedyna różnica między wariantami poza podpisem.

---

## 6. Testy

```bash
# jednostkowe — punkt odniesienia: 1215, 0 padnięć, 0 pominiętych
rm -rf app/build/test-results/testDebugUnitTest
./gradlew cleanTestDebugUnitTest testDebugUnitTest --console=plain --rerun-tasks

# reguły Firestore — punkt odniesienia: 673 przeszło, 1 pominięty
cd firebase
node_modules/.bin/firebase.cmd emulators:start --only firestore,auth --project stellare-a39ef
npx vitest run     # w drugim terminalu
```

Cztery rzeczy, na których w tym projekcie **już ktoś się przejechał** — przeczytaj je, zanim uznasz
wynik za dobry:

1. **Zielony przebieg Gradle nie znaczy, że testy się wykonały.** Pierwszy przebieg po zmianach
   wielokrotnie wychodził `UP-TO-DATE` z **zerem** uruchomionych testów i wyglądał na sukces. Dlatego
   w komendzie wyżej jest `rm -rf` na katalogu wyników i `--rerun-tasks`; liczbę testów czytaj z plików
   XML w `app/build/test-results/`, nie z podsumowania Gradle.
2. **Testy reguł: jeden przebieg naraz, nic w tle, nigdy `emulators:exec`.** Dwa równoległe przebiegi
   czyszczą sobie dane. **Zmienna liczba padnięć na niezmienionym kodzie to podpis wyścigu**, nie
   regresji — regresja jest deterministyczna.
3. **Emulator Firestore trzyma OSOBNY zbiór reguł dla każdego identyfikatora projektu.** Biblioteka
   testowa wgrywa swój pod `stellare-test`, a aplikacja pisze pod `stellare-a39ef` — **zielone testy
   reguł nie mówią nic o regułach, na których działa aplikacja**. Przed weryfikacją na urządzeniu
   sprawdź, co jest wczytane: odmowa z emulatora **cytuje numery linii wczytanego zbioru**, więc
   porównaj je z plikiem reguł. Raz emulator trzymał wersję sprzed dwóch zadań, a wszystkie wnioski
   wyglądałyby identycznie jak prawdziwe.
4. **Testów Compose nie ma**, choć wszystkie zależności są zadeklarowane w `gradle/libs.versions.toml`
   (`robolectric`, `roborazzi`, `compose-ui-test-junit4`). Brakuje trzech wpisów w `dependencies` —
   patrz pozycja 4 w sekcji 7. Dopóki ich nie wpiszesz, **wpięcia logiki w `@Composable` nie pokrywa
   żaden test** i trzeba je sprawdzać zrzutem ekranu.

---

## 7. Co zostało do zrobienia

Pełna lista z uzasadnieniami i kosztami: **`docs/PRZEKAZANIE-etap9.md`**. Skrót:

**Wymaga planu Blaze (karta płatnicza w Firebase) — nie da się kodem:**
1. **Pobieranie płatności za monety** (Cloud Functions). Dziś doładowanie jest **niezabezpieczone** —
   użytkownik technicznie sprawny dopisze sobie monety; reguły ograniczają kwotę i wymuszają wpis
   w księdze, ale to tylko podniesienie progu.
2. **Firebase Storage na skany dokumentów** — dziś skan siedzi w Firestore jako base64, limit 1 MB.
3. **SMS-y poza bezpłatną pulą.**

**Dług techniczny z wyliczonym kosztem:**
4. **Wpięcie zależności testowych Compose** — trzy wiersze w `app/build.gradle.kts`, prawdopodobnie
   `robolectric.properties` z `sdk=35`, test ~20 wierszy. Szacunek: w granicach godziny. Ryzyko:
   zgodność Robolectric 4.16.1 z `compileSdk 36.1`.
5. **Szew dla nasłuchu `FirestoreUserDataSource.getUser`** — klasa sięga po `db` wprost, więc jedna
   ścieżka nasłuchu nie jest pokryta testem. **Żadna zależność testowa tego nie naprawi.** To refaktor
   w najwrażliwszym miejscu aplikacji; po nim trzeba powtórzyć weryfikację na urządzeniu.
6. **Kłamiący komentarz w `UserRepository:80-84`** — twierdzi, że funkcja rozróżnia „niezalogowany"
   od „brak dokumentu", a nie rozróżnia. Popraw albo usuń; kłamiący komentarz jest gorszy od żadnego.
7. **Migracja historycznych ocen** do `ratings/{uid}` wymaga Admin SDK (reguły zabraniają zapisu przez
   właściciela).

**Decyzje produktowe — ktoś musi wybrać, to nie dług:**
8. **Brak kontrolki „Dezaktywuj ogłoszenie"** — zdolność istnieje w danych i w regułach, nie ma wejścia
   w interfejsie.
9. **Filtrowanie ogłoszeń po cechach autora** — dane są dostępne, ekranu nie ma.
10. **Biała kartka w pełnym wymiarze przechodzi blokadę skanu** — próg odrzuca obrazy, na których nic
    nie widać (324 px najkrótszej krawędzi, wyprowadzone z czytelności legitymacji), ale nie odróżni
    pustej kartki od bladego skanu.

**Niezmierzone:** strony dymków w `ChatRoomScreen`, puste pola w `ProfileEditScreen`.

---

## 8. Ograniczenia, które Cię zaskoczą (wszystkie przyjęte świadomie)

Spis z powodami jest w specyfikacji, sekcja 10 — tu najważniejsze:

- **Monety przepadają przy usunięciu własnego konta** — nie ma ścieżki wypłaty, bo wypłata to
  prawdziwe pieniądze.
- **Nowe konto nie założy profilu bez sieci.** To cena rozdzielenia stanów „nie ma profilu" i „nie
  wiem, bo odczyt padł"; alternatywą była ścieżka, w której nieudany odczyt **nadpisywał prawdziwy
  profil**.
- **Kontrahent depozytu może poznać saldo drugiej strony** liniowym zgadywaniem kwoty rozliczenia.
  Nie da się domknąć bez serwera, bo reguła musi porównać kwotę z saldem. Decyzja zapisana
  **w komentarzu reguły**.
- **Brak migracji historycznych sald** — odtworzenie salda wymagałoby użycia dziury niepotwierdzonego
  doładowania jako narzędzia. Konta sprzed przebudowy dostają dokument prywatny z saldem `0` przy
  pierwszym logowaniu, a **istniejące saldo nie jest ruszane** (pilnuje tego test).
- **Logowanie Facebook w trybie deweloperskim Meta działa tylko dla kont testowych**; publiczne
  wymaga weryfikacji biznesowej, która trwa tygodniami.

---

## 9. Zanim zmienisz pierwszą linię

Przeczytaj **`docs/PRZEKAZANIE-etap9.md`**, sekcję „reguły gry, których złamanie boli najszybciej".
Kryterium wejścia na tamtą listę było jedno: **każda pozycja kosztowała co najmniej jedną rundę
poprawek**. Są tam między innymi: dlaczego `request.resource.data` w regułach to dokument **po
scaleniu** (a nie delta), dlaczego tożsamość dokumentu bierze się z `document.id` **nigdy z pola**,
dlaczego błąd nasłuchu przekazuje się **wartością** (bo `close(error)` bez obsługi u konsumenta
wywraca aplikację), i dlaczego atrapa rozjechana z produkcją potrafi uczynić prawdziwy defekt
**nieodtwarzalnym**.

**Dziennik decyzji — 109 rozstrzygnięć z uzasadnieniami i kosztem każdego wyboru — nie jest częścią
repozytorium**: został świadomie zostawiony lokalnie u właściciela projektu. Jeśli zastanowi Cię,
dlaczego coś jest zrobione wbrew pozorom, a odpowiedzi nie ma ani w tym pliku, ani w
`docs/PRZEKAZANIE-etap9.md`, ani w komentarzu obok kodu — poproś właściciela o wgląd w dziennik,
zamiast zgadywać. Najważniejsze powody zostały przeniesione do komentarzy w `firestore.rules` i
w kodzie właśnie po to, żeby podróżowały razem z repozytorium.
