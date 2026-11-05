# Etap 7 — Logowanie: plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODSKILL: superpowers:subagent-driven-development. Kroki mają składnię checkboxów.

**Cel:** Pierwszy raz logujący się przez Google dostaje gdzie wybrać rolę i uzupełnić profil; dochodzi reset hasła, prawdziwa weryfikacja numeru telefonu i logowanie przez Facebooka.

**Architektura:** Prawda o numerze telefonu mieszka w **tokenie uwierzytelnienia**, nie w polu dokumentu — dzięki temu reguły Firestore mogą ją sprawdzić, zamiast wierzyć klientowi. Uzupełnienie profilu po Google opiera się na stanie „zalogowany, dokumentu jeszcze nie ma", który w etapie 5 był źródłem wyścigu, a tutaj jest normalną ścieżką.

**Stos:** Kotlin 2.2.10, Jetpack Compose, Firebase Auth (Spark), Credential Manager, JUnit 4, kotlinx-coroutines-test.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcja 8 („Logowanie") i sekcja 9 (etap 7).

## Stan zastany, ustalony przed planem

- Jest e-mail z hasłem i Google (Credential Manager, `GoogleIdTokenCredential`, `signInWithCredential` w `FirebaseAuthDataSource`).
- **Nie ma**: Facebooka, `sendPasswordResetEmail`, `PhoneAuthProvider`.
- `AuthViewModel.authStatus` czyta **dokument użytkownika**, więc ktoś zalogowany bez dokumentu widziany jest jako wylogowany. To mechanizm, na którym stanie zadanie 1.
- `isSmsVerified` z rejestracji to **wyłącznie stan Compose** — nigdy nie trafia do Firestore i nie występuje w regułach (ustalenie etapu 5). Krok weryfikacji telefonu w rejestracji jest opcjonalny i uczciwie opisany jako testowy.
- `RegistrationRules` ma już walidację ceny (liczba całkowita ≥ 1) i liczbę kroków per rola — **używaj jej, nie pisz drugiej**.
- `UserFacingError` przepuszcza na ekran wyłącznie `UserMessageException`; każdy inny wyjątek daje polskie zdanie zastępcze.

## Global Constraints

- minSdk 24 bez desugaringu: `java.util.Calendar`, nigdy `java.time`. Czas wstrzykiwany jako `() -> Long`.
- **Zero komentarzy w kodzie.** Teksty interfejsu po polsku.
- **Żadnych literałów koloru** — ani `Color(0xFF...)`, ani `Color.Black`, ani `Color.White`. Aplikacja ma ich dziś **zero** i tak ma zostać. Kolory z `MaterialTheme.colorScheme` albo z tokenów w `ui/theme/Color.kt` (`CoinGold`, `RatingStar`, `OnScrim`).
- Plan Spark: brak Cloud Functions i Firebase Storage.
- **Nie modyfikuj istniejących testów.** Jeśli któryś wymaga zmiany — zatrzymaj się i napisz dlaczego.
- Każda nowa decyzja ma **test, czerwień przed implementacją i mutację** z komunikatem mówiącym, co zginęło (`assertTrue("co miało być", ...)`, nie goła asercja).
- Reguły Firestore są domknięte i sprawdzone mutacjami. **Zmiana tylko tam, gdzie plan wprost na to zezwala** (zadanie 3), i zawsze z testami reguł plus mutacją.
- **Żaden ekran nie twierdzi niczego, czego nie da się wskazać w kodzie albo w danych.** W etapach 5 i 6 zamknęliśmy kilkanaście takich obietnic; nie dodawaj trzynastej.
- `cmd.exe` z Git Basha zawiesza się — PowerShell. Przebieg gradle 4–10 minut.

---

### Zadanie 1: Uzupełnienie profilu po pierwszym logowaniu Google

**To jest jedyny defekt wymieniony w specyfikacji wprost**: *„użytkownik logujący się pierwszy raz przez Google nie ma gdzie wybrać roli ani uzupełnić profilu"*.

**Pliki:**
- Utwórz: `ui/session/ProfileCompletion.kt` (czysta decyzja), `ui/session/CompleteProfileScreen.kt`
- Modyfikuj: `ui/auth/AuthViewModel.kt`, `ui/navigation/StellareNavHost.kt`
- Test: `app/src/test/java/com/example/stellare/session/ProfileCompletionTest.kt`

**Interfejsy:**
- Konsumuje: `UserRepository.signedInUserIdFlow()`, `getCurrentUserFlow()`, `RegistrationRules`
- Produkuje: `ProfileCompletion.required(signedInUid: String?, document: UserModel?): Boolean`, `AuthViewModel.needsProfileCompletion: StateFlow<Boolean>`, `AuthViewModel.completeProfile(...)`

Stan „jest identyfikator z uwierzytelnienia, nie ma dokumentu" to **dokładnie** ten sam stan, który w etapie 5 powodował wyścig rejestracji. Tam był błędem do obejścia, tutaj jest **normalną ścieżką**: użytkownik Google ma konto, ale nie ma profilu.

- [ ] **Krok 1: Testy czystej decyzji, czerwone przed implementacją**

Przypadki: brak identyfikatora → nie wymaga uzupełnienia (jest wylogowany); identyfikator bez dokumentu → **wymaga**; identyfikator z dokumentem kompletnym → nie wymaga; identyfikator z dokumentem bez roli → **wymaga**. Ostatni przypadek pilnuje sytuacji, w której dokument powstał częściowo.

- [ ] **Krok 2: Potwierdź czerwień** i podaj komunikat.
- [ ] **Krok 3: Implementacja decyzji i ekranu.** Ekran zbiera rolę oraz pola wymagane dla tej roli; **cena psychologa przez `RegistrationRules`**, bo bez dodatniej ceny nie wystawi terminu. Zapis dokumentu przez tę samą ścieżkę co rejestracja, opakowaną `whileRegistering`, żeby nie wrócił wyścig z etapu 5.
- [ ] **Krok 4: Nawigacja.** Zalogowany bez profilu **nie widzi** ogłoszeń ani kalendarza — idzie na uzupełnienie profilu. Po zapisie wchodzi normalnie. Niezalogowany trafia na logowanie, jak dotąd.
- [ ] **Krok 5: Mutacja** — zamień warunek na „wymaga tylko, gdy brak identyfikatora" i pokaż, że czerwienieje przypadek „identyfikator bez dokumentu". Podaj komunikat.
- [ ] **Krok 6: Pełny przebieg testów.**

---

### Zadanie 2: Reset hasła

**Pliki:** modyfikuj `data/remote/FirebaseAuthDataSource.kt`, `ui/auth/AuthViewModel.kt`, `ui/session/LoginRegistrationScreens.kt`. Test: `app/src/test/java/com/example/stellare/auth/PasswordResetTest.kt`

- [ ] **Krok 1: Testy, czerwone przed implementacją** — stan wysyłania, powodzenie, niepowodzenie. Komunikat niepowodzenia **po polsku**, przez `UserFacingError`; nigdy surowy tekst Firebase.
- [ ] **Krok 2: Implementacja** — `sendPasswordResetEmail` w źródle danych, stan w ViewModelu, wejście z ekranu logowania.
- [ ] **Krok 3: Nie ujawniaj, czy konto istnieje.** Komunikat po wysłaniu jest **ten sam** niezależnie od tego, czy adres jest w bazie — tak samo jak scalone komunikaty logowania z etapu 5, które to domykały. Test pilnuje identyczności obu tekstów.
- [ ] **Krok 4: Mutacja** — rozdziel komunikaty i pokaż czerwień.
- [ ] **Krok 5:** Emulator Auth **nie wysyła poczty**, tylko wystawia link przez swoje API. Zweryfikuj ścieżkę na emulatorze i zapisz w raporcie, jak odczytałeś link.

---

### Zadanie 3: Prawdziwa weryfikacja numeru telefonu

**Tu jest decyzja architektoniczna, która odwraca poprzednie założenie.** Specyfikacja wymienia `phoneVerified` jako pole w `users`, ale **pole w dokumencie jest twierdzeniem klienta**: reguły nie mają jak sprawdzić, czy SMS doszedł, więc każdy mógłby je ustawić na `true`. Wstawilibyśmy więc do danych dokładnie taką obietnicę bez pokrycia, jakich kilkanaście usunęliśmy w etapach 5 i 6.

Zrób to inaczej: **powiąż numer z kontem w Firebase Auth** (`PhoneAuthProvider` i `linkWithCredential`). Wtedy numer siedzi w tokenie uwierzytelnienia, a reguły czytają `request.auth.token.phone_number` — wartość, której klient nie może podrobić.

**Pliki:** `data/remote/FirebaseAuthDataSource.kt`, `ui/auth/AuthViewModel.kt`, nowy `ui/session/PhoneVerificationScreen.kt`, `firebase/firestore.rules`, testy reguł w `firebase/test/`.

- [ ] **Krok 1: Testy decyzji** (stan weryfikacji, kod odrzucony, kod przyjęty), czerwone przed implementacją.
- [ ] **Krok 2: Implementacja powiązania numeru.** Emulator Auth obsługuje numery testowe z ustalonym kodem — użyj ich i zapisz w raporcie, których.
- [ ] **Krok 3: Reguły.** **Masz zgodę na zmianę `firestore.rules` wyłącznie w tym zadaniu.** Dodaj funkcję czytającą `request.auth.token.phone_number` i **nie dodawaj** pola `phoneVerified` do dokumentu. Testy reguł: zapis dostępny tylko dla tokenu z numerem; bez numeru odrzucony. **Mutacja** na tym warunku, z komunikatem.
- [ ] **Krok 4: Interfejs.** Weryfikacja jest **opcjonalna**, wejście z ustawień i z rejestracji. Ekran nie twierdzi, że numer jest potwierdzony, dopóki powiązanie się nie uda. Usuń z rejestracji kod pokazywany na ekranie — zastępuje go prawdziwa ścieżka.
- [ ] **Krok 5: Pełny przebieg oba zestawy** (`gradlew test` i `npx vitest run`) z liczbami.

---

### Zadanie 4: Logowanie przez Facebooka

**Pliki:** `data/remote/FirebaseAuthDataSource.kt`, `ui/auth/AuthViewModel.kt`, `ui/session/LoginRegistrationScreens.kt`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`.

Tego zadania **nie da się w pełni zweryfikować lokalnie**: wymaga aplikacji po stronie Facebooka, jej identyfikatora, tokenu klienta, odcisków certyfikatu podpisującego oraz włączenia dostawcy w konsoli Firebase. Żadnej z tych rzeczy nie wytworzysz z kodu.

- [ ] **Krok 1: Kod gotowy, ale nieaktywny bez konfiguracji.** Przycisk Facebooka **nie pojawia się**, dopóki nie ma identyfikatora aplikacji — nie pokazuj przycisku, który zawsze kończy się błędem. To ta sama zasada, z której w etapie 5 usunęliśmy udawane połączenie i fałszywą weryfikację dyplomu.
- [ ] **Krok 2: Test decyzji** „czy pokazać przycisk" jako czysta funkcja, z mutacją.
- [ ] **Krok 3: Ścieżka błędu** — anulowanie przez użytkownika i odmowa uprawnień dają polskie komunikaty przez `UserFacingError`, nie surowy tekst.
- [ ] **Krok 4: Wypisz w raporcie dokładną listę rzeczy do zrobienia poza kodem** — identyfikator aplikacji, token klienta, odciski SHA‑1 i SHA‑256, adres przekierowania, włączenie dostawcy w konsoli. Ta lista idzie do sekcji przekazania w specyfikacji.

---

### Zadanie 5: Sekcja przekazania w specyfikacji

**Pliki:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcja 10.

- [ ] **Krok 1:** Dopisz wymagania zewnętrzne dla Facebooka (z zadania 4) i dla weryfikacji telefonu: odciski certyfikatu w konsoli Firebase, Play Integrity albo reCAPTCHA, oraz **limity bezpłatnej puli SMS** — poza nią weryfikacja telefonu kosztuje.
- [ ] **Krok 2:** Zapisz, że numer telefonu jest prawdą **w tokenie uwierzytelnienia**, a nie polem w dokumencie, i dlaczego: pola w dokumencie reguły nie potrafią sprawdzić.
- [ ] **Krok 3:** Odnotuj, co z etapu 7 jest **niemożliwe do zweryfikowania lokalnie** i wymaga prawdziwego urządzenia z prawdziwym numerem.

---

## Weryfikacja końcowa etapu

Obowiązkowy przebieg na emulatorze z lokalnymi emulatorami Firebase, w **obu trybach kolorystycznych**:

1. **nowe konto Google bez profilu** trafia na uzupełnienie, a nie na ogłoszenia; po zapisie wchodzi normalnie i widzi portfel **bez ponownego logowania** (to ta sama ścieżka, którą domykał test rozstrzygający z etapu 5),
2. reset hasła: ten sam komunikat dla adresu istniejącego i nieistniejącego, link odczytany z emulatora Auth,
3. weryfikacja numeru na numerze testowym; po powiązaniu **reguła przepuszcza zapis, którego bez numeru nie przepuszczała**,
4. brak przycisku Facebooka bez konfiguracji — i **żadnego przycisku, który kończy się błędem**,
5. logcat bez `PERMISSION_DENIED` i bez `FATAL`.

W tym projekcie weryfikacja na urządzeniu znalazła poważny defekt **dziewięć razy z rzędu**, zawsze między warstwami. Załóż, że tak będzie i tutaj.
