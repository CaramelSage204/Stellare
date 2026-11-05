# Etap 8 — Ustawienia i szlify: plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODSKILL: superpowers:subagent-driven-development. Kroki mają składnię checkboxów.

**Cel:** Ekran ustawień z motywem, rozmiarem czcionki i kolorem dymków czatu; usuwanie konta, które mówi prawdę o tym, co zostaje; oraz domknięcie długu zebranego w etapach 5–7.

**Architektura:** Motyw i rozmiar czcionki trzymane **na urządzeniu**, nie w dokumencie użytkownika — bo muszą działać przed zalogowaniem. Nakładki przestają trzymać własne kopie danych i wyszukują je po identyfikatorze w aktualnym stanie, wzorem istniejącego `SessionDetailDialog`.

**Stos:** Kotlin 2.2.10, Jetpack Compose, Material 3, DataStore Preferences, Firebase Auth + Firestore (Spark), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcja 8 („Ekrany nowe": Ustawienia) i sekcja 9 (etap 8).

**Stan wejściowy:** 678 testów jednostkowych, 357 testów reguł (1 pominięty), wszystkie zielone. Literałów koloru w ekranach: zero. Nic nie zacommitowane.

## Global Constraints

- minSdk 24 bez desugaringu: `java.util.Calendar`, nigdy `java.time`. Czas wstrzykiwany jako `() -> Long`.
- **Zero komentarzy w kodzie.** Teksty interfejsu po polsku.
- **Żadnych literałów koloru** — ani `Color(0xFF...)`, ani `Color.Black`, ani `Color.White`. Aplikacja ma ich **zero** i tak ma zostać. Kolory z `MaterialTheme.colorScheme` albo z tokenów (`CoinGold`, `RatingStar`, `OnScrim`).
- **Każdy przycisk z `enabled` używa `stellareButtonColors()`** — domyślne Material 3 dają kontrast 1,3:1 (zmierzone w etapie 6).
- **Hasła i kody jednorazowe nie trafiają do `rememberSaveable`** — ten stan idzie na dysk przy uśmierceniu procesu.
- **Nie modyfikuj istniejących testów.** Jeśli któryś wymaga zmiany — zatrzymaj się i napisz dlaczego.
- Każda nowa decyzja ma **test, czerwień przed implementacją i mutację** z komunikatem mówiącym, co zginęło (`assertTrue("co miało być", ...)`, nie goła asercja). Jeśli test tylko dokumentuje stan zastany — mutacji nie rób i napisz, że nie było czego zabić.
- **Jeśli mutacja nie ma czego zabić, bo decyzję liczy composable — wyciągnij decyzję do czystej funkcji.** To był najcenniejszy wynik mutacji w tym projekcie.
- Reguły Firestore: zmiana **tylko** tam, gdzie plan wprost zezwala (zadanie 6), zawsze z testami reguł i mutacją.
- Plan Spark: brak Cloud Functions i Firebase Storage.
- NIE commituj i NIE pushuj.
- Skrypt pomocniczy czyta cały plik, przekształca w pamięci, zapisuje dopiero gotowy wynik; kopia przed edycją. Repozytorium ma ponad 160 niezacommitowanych pozycji — git nie uratuje niczego.
- `cmd.exe` z Git Basha zawiesza się — PowerShell. Przebieg gradle 4–10 minut; jeśli skończy w kilka sekund, to cache — przelicz z plików XML i napisz o tym.

---

### Zadanie 1: Warunek wstępny — interfejs musi wytrzymać większą czcionkę

**To zadanie idzie pierwsze i nie wolno go pominąć.** Weryfikacja etapu 6 wykazała, że przy skali czcionki 1,5× **łamie się napis w dolnym pasku, a pole wyszukiwania jest ucięte**. Specyfikacja wymaga w tym etapie globalnego rozmiaru czcionki — więc dodanie suwaka przed naprawą układu dałoby użytkownikowi przełącznik, który **psuje aplikację**, i nazwałoby to funkcją.

**Pliki:** `ui/shell/` (dolny pasek), `ui/dashboard/MainDashboardScreen.kt` (pole wyszukiwania) oraz wszystko, co wyjdzie z przeglądu.

- [ ] **Krok 1: Przejdź aplikację przy skali 1,0 / 1,3 / 1,5 / 2,0** (`adb shell settings put system font_scale 1.5`) i **wypisz listę miejsc**, które się łamią, ucinają albo nachodzą. Zrzuty parami.
- [ ] **Krok 2: Napraw** — teksty skracane zamiast łamanych, wysokości `heightIn(min=)` zamiast stałych, ikony bez tekstu tam, gdzie tekst nie ma miejsca. Minimalny cel dotyku 48 dp zostaje.
- [ ] **Krok 3: Potwierdź na urządzeniu** przy 1,5× i 2,0×, zrzutami. **Testu jednostkowego tu nie będzie** — to układ, nie decyzja. Napisz to wprost w raporcie, zamiast dorabiać test, który niczego nie mierzy.
- [ ] **Krok 4: Przywróć `font_scale 1.0`** po testach.

---

### Zadanie 2: Ekran ustawień, motyw i rozmiar czcionki

**Odstępstwo od specyfikacji, zamierzone.** Sekcja 6 wymienia `users.settings` (rozmiar czcionki, kolor czatu, motyw). Motyw i rozmiar czcionki trzymamy **na urządzeniu** (DataStore), nie w dokumencie — bo:
- muszą działać **przed zalogowaniem**, a dokumentu wtedy nie ma;
- odczyt dokumentu jest asynchroniczny, więc aplikacja mignęłaby złym motywem przy każdym starcie;
- są to ustawienia **tego telefonu**, nie konta — ktoś może chcieć ciemnego motywu na swoim urządzeniu i jasnego na cudzym.

Kolor dymków czatu **może** iść do dokumentu, bo dotyczy treści, nie powłoki — ale jeśli to komplikuje zadanie, zostaw go też na urządzeniu i napisz dlaczego. Zadanie 7 poprawi specyfikację.

**Pliki:** nowe `data/settings/AppSettings.kt`, `data/settings/SettingsStore.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`; zmiany w `ui/theme/Theme.kt`, `MainActivity.kt`, `Routes.kt`, `StellareNavHost.kt`, `ui/shell/ProfileSheet.kt` (wejście).

**Interfejsy:**
- Produkuje: `AppSettings(theme: ThemeChoice, fontScale: FontScaleChoice)`, `ThemeChoice { SYSTEM, LIGHT, DARK }`, `FontScaleChoice { NORMAL, LARGE, LARGEST }`, `SettingsStore.settings: Flow<AppSettings>`, `SettingsStore.setTheme(...)`, `setFontScale(...)`

- [ ] **Krok 1: Testy czystych decyzji, czerwone przed implementacją**

Przypadki: `ThemeChoice.SYSTEM` z ciemnym motywem systemu daje ciemny; `LIGHT` daje jasny **niezależnie** od systemu; `DARK` analogicznie; mnożnik czcionki dla trzech wartości; nieznana wartość z magazynu daje **wartość domyślną, nie wyjątek** (dane z dysku mogą być z innej wersji).

- [ ] **Krok 2: Potwierdź czerwień** i podaj komunikat.
- [ ] **Krok 3: Implementacja.** `SettingsStore` na DataStore; motyw i skala czytane **przed** pierwszym złożeniem interfejsu, żeby nie było mignięcia. Skala czcionki przez `LocalDensity`/`fontScale`, nie przez mnożenie rozmiarów w każdym tekście.
- [ ] **Krok 4: Ekran ustawień** — wybór motywu, rozmiaru czcionki, koloru dymków, wejście do weryfikacji numeru (istnieje), FAQ (istnieje w `ui/faq/`), usunięcie konta (zadanie 6).
- [ ] **Krok 5: Mutacja** — spraw, by `LIGHT` zależał od motywu systemu, i pokaż czerwień. Podaj komunikat.
- [ ] **Krok 6: Pełny przebieg testów.**

---

### Zadanie 3: Kolor dymków czatu

Etap 6 przygotował to miejsce celowo: kolory dymków są **w jednym miejscu** (`bubbleColors`/`BubbleColors` w `ui/chat/MessageBubble.kt`), żeby ten etap miał co podmienić.

- [ ] **Krok 1: Testy** — wybrany kolor daje odpowiednią parę tło/tekst; **kontrast każdej pary nie schodzi poniżej 4,5:1**. Kontrast licz w teście, jak zrobiło to zadanie o wyłączonych przyciskach w etapie 6 — tam test na realnych tokenach wyłapał 1,13:1.
- [ ] **Krok 2: Czerwień, implementacja, mutacja** (para o zbyt niskim kontraście musi zczerwienić test).
- [ ] **Krok 3:** Kolory wyłącznie z palety albo tokenów. Żadnych literałów.

---

### Zadanie 4: Nakładki przestają trzymać nieaktualne dane

Inwentarz z etapu 7: **sześć dialogów** trzyma własne migawki `AppointmentModel` — cztery potwierdzenia w widoku psychologa (anulowanie, zakończenie, usunięcie, rozliczenie zwrotu), anulowanie w widoku pacjenta, potwierdzenie rezerwacji w detalu psychologa.

**Wzór poprawny jest już w kodzie**: `SessionDetailDialog` trzyma **identyfikator** i wyszukuje wizytę w aktualnej liście, więc znika sam, gdy wizyta zniknie. Nie projektuj nowego rozwiązania — rozciągnij to.

- [ ] **Krok 1: Testy czystej decyzji** „co pokazać dla danego identyfikatora i aktualnej listy": wizyta istnieje → dialog; wizyta zniknęła → brak dialogu; wizyta zmieniła status → dialog **widzi nowy status**, nie stary.
- [ ] **Krok 2: Czerwień, implementacja, mutacja** (trzymanie migawki zamiast wyszukiwania musi zczerwienić test „widzi nowy status").
- [ ] **Krok 3:** Panel profilu przy **zewnętrznym** wylogowaniu zostaje bez zmian — etap 7 rozstrzygnął to świadomie, bo odróżnienie od wylogowania z panelu psuje jego animację. Nie ruszaj.

---

### Zadanie 5: Osiem ekranów gubiących wpisane dane

Inwentarz z etapu 6: logowanie (e-mail), czat (treść wiadomości, tytuł i treść notatki, wybrana zakładka), recenzja (**gwiazdki i komentarz**), rezerwacja (uwagi), **weryfikacja kwalifikacji (wybrany obraz i typ dokumentu)**, panel administratora (osoba i notatka odrzucenia), dialog dodania terminu (data i godzina), kalendarz (widoczny miesiąc).

Priorytet: **wybrane zdjęcie w weryfikacji kwalifikacji** i **napisana recenzja** — tam utrata jest najbardziej kosztowna.

- [ ] **Krok 1:** Zamień na `rememberSaveable` to, co użytkownik **wpisał lub wybrał**. Stan ulotny (otwarty dialog, chwilowy komunikat) zostaje w `remember`. Wypisz w raporcie, co zamienione, a co świadomie ulotne.
- [ ] **Krok 2: Hasła i kody jednorazowe zostają poza stanem zapisywanym** — to rozstrzygnięcie z etapu 6 i jego skutek (utrata sześciu cyfr kodu przy obrocie) jest **świadomy**, nie do „naprawienia".
- [ ] **Krok 3: Uwaga na pułapkę z etapu 6:** samo `rememberSaveable` nie wystarczy, jeśli efekt wczytujący dane **nadpisuje** przywrócone pola. Sprawdź to w każdym ekranie, który wczytuje coś na wejściu, i jeśli trzeba — zrób jak `shouldHydrateProfileForm`: wypełniaj raz.
- [ ] **Krok 4: Potwierdź na urządzeniu** obrotem na każdym z ośmiu ekranów. Zrzuty.

---

### Zadanie 6: Usunięcie konta, które mówi prawdę

**Bez Cloud Functions nie da się usunąć wszystkiego**, a udawanie, że się da, byłoby trzynastym nieprawdziwym komunikatem w tym projekcie. Zadanie polega więc w równej mierze na usuwaniu i na **powiedzeniu użytkownikowi, co zostaje i dlaczego**.

**Masz zgodę na zmianę `firebase/firestore.rules` — tylko w tym zadaniu.**

Rulingi, od których zaczynasz (nie zgaduj ich od nowa):
- **znika**: dokument użytkownika, jego ogłoszenia, jego notatki terapeutyczne, konto w Firebase Auth;
- **zostaje**: wizyty (to także historia drugiej strony), wystawione opinie (reguły pozwalają zmienić oceny **tylko** razem z nową opinią, więc usunięcie opinii rozjechałoby oceny psychologa), wpisy w księdze portfela (reguły czynią je niezmiennymi);
- po usunięciu konta nierozwiązywalny identyfikator ma pokazywać **etykietę rodzajową** — i to jest wtedy **poprawne**, a nie defekt.

- [ ] **Krok 1: Ekran potwierdzenia mówi prawdę** — wymienia, co zniknie i co zostanie, zanim użytkownik potwierdzi. Bez „Twoje dane zostaną trwale usunięte", jeśli część zostaje.
- [ ] **Krok 2: Reguły** — właściciel może usunąć swój dokument i swoje ogłoszenia; psycholog swoje notatki. Testy reguł: właściciel może, obcy nie, wpisy księgi i opinie **nadal** nieusuwalne. **Mutacja** na każdym nowym warunku.
- [ ] **Krok 3: Kolejność i częściowa porażka.** Usuwaj dane **przed** kontem w Auth (po usunięciu konta token przestaje działać i reszta nie przejdzie). Jeśli któryś krok padnie — **powiedz to użytkownikowi** i nie twierdź, że konto usunięto.
- [ ] **Krok 4: `ERROR_REQUIRES_RECENT_LOGIN`** — Firebase wymaga świeżego logowania do usunięcia konta. Obsłuż to komunikatem po polsku i ścieżką ponownego logowania, a nie surowym błędem.
- [ ] **Krok 5: Testy** decyzji „co usuwamy i w jakiej kolejności" jako czystej funkcji, z mutacją. **Nie testuj tego, czego nie da się sprawdzić na atrapie** — napisz, czego nie da się sprawdzić.

---

### Zadanie 7: Dług zebrany w etapach 5–7

- [ ] **Krok 1: Nazwa w portfelu nie ma stanu wczytywania.** Po zimnym starcie historia pokazała „Od: inny użytkownik" zamiast nazwiska, bo odczyt nazwy połyka wyjątki i nie odróżnia „jeszcze nie wiem" od „nie ma". Rozdziel te stany, jak zrobiły to opinie (wczytywanie / pustka / niepowodzenie). Portfel ma **własny** odczyt nazw, zduplikowany wobec `UserRepository.namesOf` — przepnij go.
- [ ] **Krok 2: `showTutorialPrompt` nie jest zerowany w `logout()`** — nowy użytkownik mógłby zobaczyć „Dziękujemy za rejestrację". **Zgłoszone z czytania kodu, nieodtworzone** — najpierw potwierdź, czy to zachodzi, i napisz wynik. Jeśli nie zachodzi, nie zmieniaj niczego.
- [ ] **Krok 3: Zegar wołany wprost w czternastu miejscach** (ekrany: portfel, opinie, karty ogłoszeń, dialog terminu; ViewModele: portfel, profil, ogłoszenia, weryfikacja; źródła danych: wizyty ×3, weryfikacja). Wstrzyknij `() -> Long`, jak w pozostałych miejscach. To warunek, by cokolwiek zależne od czasu dało się przetestować.
- [ ] **Krok 4: `AppointmentOrder.past()` nie ma konsumenta** — powstało na mój wniosek dla ekranu historii, którego nie ma. Albo podłącz je do listy przeszłych wizyt (wtedy wolno zmienić test `patientLists`), albo usuń razem z testami. Wybierz i uzasadnij.
- [ ] **Krok 5: `MainDashboardScreen.kt` ma 352 linie** — jedyny plik powyżej trzystu. Rozbij, jeśli da się bez zmiany zachowania; jeśli nie, napisz dlaczego.
- [ ] **Krok 6: Popraw specyfikację** — sekcja 6 wymienia `users.settings` (rozmiar czcionki, kolor czatu, motyw). Zapisz, że motyw i rozmiar czcionki są **ustawieniami urządzenia**, nie konta, i dlaczego: muszą działać przed zalogowaniem. Dopisz też do sekcji 10, czego usunięcie konta **nie usuwa** i dlaczego — to informacja dla osoby prowadzącej projekt, bo dotyczy obowiązków wobec użytkownika.

---

## Weryfikacja końcowa etapu

Przebieg na emulatorze z lokalnymi emulatorami Firebase, w **obu motywach** i przy **skali czcionki 1,0 oraz 1,5×**:

1. **zadanie 1 jako pierwsze**: dolny pasek i pole wyszukiwania przy 1,5× i 2,0×,
2. motyw jasny, ciemny i systemowy — w tym **brak mignięcia złym motywem** przy starcie i działanie **przed zalogowaniem**,
3. rozmiar czcionki zmienia się w całej aplikacji, nie tylko na ekranie ustawień,
4. kolor dymków czatu, z czytelnym kontrastem w obu motywach,
5. **obrót na ośmiu ekranach z zadania 5** — wpisane dane i wybrane zdjęcie zostają,
6. **dialog potwierdzenia wizyty widzi nowy status**, gdy stan zmieni się pod nim,
7. **usunięcie konta**: ekran potwierdzenia wymienia, co zostaje; po usunięciu nie da się zalogować, dokument i ogłoszenia zniknęły, wizyty i opinie zostały, a nierozwiązywalny identyfikator pokazuje etykietę rodzajową,
8. **„wstecz" na ekranie uzupełnienia profilu pyta** zamiast gubić dane — to poprawka z etapu 7, której **nikt nie widział na urządzeniu**,
9. logcat bez `PERMISSION_DENIED` i bez `FATAL`.

Weryfikacja na urządzeniu znalazła poważny defekt w etapach 2–6, a w etapie 7 nie znalazła żadnego. Nie zakładaj, że to już reguła — ten etap dotyka motywu, czcionki i usuwania danych, czyli rzeczy, które psują się **globalnie**.
