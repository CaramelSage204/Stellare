# Etap 3 — Ogłoszenia — plan wdrożenia

> **Dla wykonawców:** WYMAGANA PODUMIEJĘTNOŚĆ: użyj superpowers:subagent-driven-development do realizacji zadanie po zadaniu.

**Cel:** Zastąpić listę profili listą ogłoszeń — z encją `posts`, formularzem „Napisz ogłoszenie", dwiema zakładkami zależnymi od roli i filtrami po tagach.

**Architektura:** Nowa kolekcja `posts` z regułami wiążącymi pola przywileju z dokumentem autora. `PostViewModel` zasila kanał ogłoszeń, a `FeedViewModel` zostaje przy filtrach i ulubionych. Formularz ustala `type` z roli autora; reguła to samo wymusza po stronie serwera.

**Stos:** Kotlin 2.2.10, Compose, Firestore, reguły `rules_version = '2'`, testy reguł na `@firebase/rules-unit-testing` + Vitest.

**Spec:** `docs/superpowers/specs/2026-09-15-stellare-design.md`, sekcje 6 i 8.

## Ograniczenia globalne

- **Plan Spark.** Brak Cloud Functions i Firebase Storage. Każdy niezmiennik musi dać się wyrazić w regułach albo być świadomie zaakceptowany na piśmie.
- **Żaden ViewModel nie przekracza 200 linii.**
- **ViewModele nie znają Firebase.** Zależą wyłącznie od repozytoriów.
- **Testy jednostkowe działają bez emulatora i bez urządzenia.**
- **Kod i nazwy po angielsku, komunikaty dla użytkownika po polsku.**
- **Każda klauzula ma własny izolujący test — w regułach i w kodzie tak samo.** Test sprawdzający dwie rzeczy naraz maskuje brak ochrony jednej z nich; w tym projekcie zdarzyło się to już cztery razy, po obu stronach.
- **Mutacja ogólna i zawężająca to dwa różne narzędzia.** Podmiana na stałą sprawdza, czy cokolwiek testuje daną ścieżkę. Zawężenie warunku — np. usunięcie jednej z dwóch dopuszczonych ról — sprawdza, czy testuje ją w całości. Luki w tym projekcie wychodziły głównie w tej drugiej kategorii.
- **Deklaracja o pokryciu bez mutacji nie znaczy nic.**
- **Etap kończy się testem na urządzeniu.** W Etapie 2 trzy realne błędy przeszły przez 185 testów i dziesięć recenzji, a widać je było w pierwszej minucie używania.
- **Bez commitów.**

---

## Rozstrzygnięcia wyprzedzające

Zapadły przed napisaniem planu, po przeczytaniu specyfikacji. Wiążą wykonawców tak samo jak treść zadań.

**`authorSnapshot` wiąże się z profilem.** Specyfikacja wprowadza w `posts` denormalizowaną mapę z danymi autora. Jest pisana przez klienta, więc bez wiązania każdy wystawiłby ogłoszenie z `isVerified = true` i pigułką „Zweryfikowany" przy koncie, które nigdy nie przesłało dyplomu. Reguła wiąże `isVerified` i `priceCoins` z dokumentem autora przez `get()`. Pola opisowe — imię, zdjęcie, wiek, płeć — zostają niezwiązane, bo ich sfałszowanie nie daje przywileju.

**`authorSnapshot` nie zawiera oceny.** Ocena zmienia się przez cudzą recenzję, a recenzent nie ma prawa pisać do ogłoszeń ocenianego — więc zdenormalizowana ocena rozjechałaby się nieodwracalnie. Karta czyta ocenę z `users`, który i tak jest czytelny dla każdego zalogowanego. To odstępstwo od sekcji 6 specyfikacji, świadome.

**`type` wynika z roli, nie z formularza.** Inaczej pacjent ominie interfejs i pojawi się na liście psychologów.

**`createdAt` musi mieścić się w oknie czasu serwera.** Lista jest sortowana malejąco po tym polu. Bez ograniczenia wystarczy wpisać rok 3000, żeby zostać na szczycie listy na zawsze.

---

## Struktura plików

### Nowe

- `app/src/main/java/com/example/stellare/data/model/PostModel.kt` — encja i `PostType`
- `app/src/main/java/com/example/stellare/data/remote/FirestorePostDataSource.kt`
- `app/src/main/java/com/example/stellare/data/repository/PostRepository.kt`
- `app/src/main/java/com/example/stellare/ui/posts/PostViewModel.kt`
- `app/src/main/java/com/example/stellare/ui/posts/PostComposerScreen.kt`
- `app/src/main/java/com/example/stellare/ui/posts/PostCard.kt`
- `firebase/test/posts.test.js`

### Zmieniane

- `firebase/firestore.rules` — blok `match /posts/{id}`
- `firebase/firestore.indexes.json` — indeks złożony dla listy
- `app/src/main/java/com/example/stellare/data/remote/DataSources.kt` — `PostDataSource`
- `app/src/main/java/com/example/stellare/di/AppContainer.kt`
- `app/src/main/java/com/example/stellare/ui/navigation/Routes.kt`
- `app/src/main/java/com/example/stellare/ui/navigation/StellareNavHost.kt`
- `app/src/main/java/com/example/stellare/ui/dashboard/MainDashboardScreen.kt`
- `app/src/main/java/com/example/stellare/ui/feed/FeedFilters.kt` — tagi
- `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`

---

## Zadanie 1: Reguły `posts` i testy reguł

Najpierw reguły, przed jakimkolwiek kodem Androida. W Etapie 1 odwrotna kolejność kosztowała sześć dziur wykrytych po fakcie.

**Pliki:**
- Zmodyfikuj: `firebase/firestore.rules`
- Utwórz: `firebase/test/posts.test.js`

- [ ] **Krok 1: Napisz testy, zanim napiszesz regułę**

Minimum, każdy jako osobny `it`:

1. autor tworzy własne ogłoszenie — przechodzi
2. tworzenie ogłoszenia z cudzym `authorId` — odrzucone
3. pacjent z `type = OFFERING_THERAPY` — odrzucone
4. psycholog z `type = SEEKING_PSYCHOLOGIST` — przechodzi, wolno mu szukać terapii dla siebie
5. **niezweryfikowany z `authorSnapshot.isVerified = true`** — odrzucone
6. zweryfikowany z `authorSnapshot.isVerified = false` — odrzucone, snapshot ma być prawdziwy, nie „nie gorszy"
7. `priceCoins` różne od `pricePerSession` autora — odrzucone
8. ogłoszenie pacjenta z `priceCoins > 0` — odrzucone
9. `createdAt` w przyszłości o godzinę — odrzucone
10. `createdAt` sprzed dwóch dni — odrzucone
11. edycja cudzego ogłoszenia — odrzucona
12. autor zmienia `authorId` we własnym ogłoszeniu — odrzucone
13. autor zmienia `type` po utworzeniu — odrzucone
14. autor ustawia `isActive = false` — przechodzi
15. `delete` własnego ogłoszenia — odrzucone, dezaktywacja zamiast usuwania
16. odczyt przez zalogowanego — przechodzi; przez niezalogowanego — odrzucony

- [ ] **Krok 2: Uruchom testy i potwierdź, że padają**

```bash
cd firebase && npm test
```

Oczekiwane: 16 czerwonych. Test przechodzący przed napisaniem reguły sprawdza coś innego, niż deklaruje.

- [ ] **Krok 3: Napisz regułę**

```
    match /posts/{id} {
      allow read: if isSignedIn();

      function autor() {
        return get(/databases/$(database)/documents/users/$(request.auth.uid)).data;
      }

      function snapshotPrawdziwy() {
        return request.resource.data.authorSnapshot.get('isVerified', false)
               == autor().get('isVerified', false);
      }

      function typZgodnyZRola() {
        let rola = autor().role;
        let typ = request.resource.data.type;
        return (typ == 'OFFERING_THERAPY'
                  && (rola == 'PSYCHOLOGIST' || rola == 'PSYCHOLOGY_STUDENT'))
            || typ == 'SEEKING_PSYCHOLOGIST';
      }

      function cenaZgodnaZProfilem() {
        return request.resource.data.type == 'SEEKING_PSYCHOLOGIST'
          ? request.resource.data.get('priceCoins', 0) == 0
          : request.resource.data.priceCoins == int(autor().pricePerSession);
      }

      function czasWiarygodny() {
        return request.resource.data.createdAt <= request.time.toMillis()
            && request.resource.data.createdAt > request.time.toMillis() - 300000;
      }

      allow create: if isSignedIn()
        && request.resource.data.authorId == request.auth.uid
        && request.resource.data.isActive == true
        && request.resource.data.title is string
        && request.resource.data.title.size() > 0
        && request.resource.data.title.size() <= 120
        && request.resource.data.body.size() <= 2000
        && request.resource.data.tags.size() <= 5
        && czasWiarygodny()
        && typZgodnyZRola()
        && cenaZgodnaZProfilem()
        && snapshotPrawdziwy();

      allow update: if isSignedIn()
        && resource.data.authorId == request.auth.uid
        && request.resource.data.authorId == resource.data.authorId
        && request.resource.data.createdAt == resource.data.createdAt
        && request.resource.data.type == resource.data.type
        && cenaZgodnaZProfilem()
        && snapshotPrawdziwy();

      allow delete: if false;
    }
```

Komentarze w regule dopisz przy trzech miejscach: dlaczego snapshot musi być wiązany, dlaczego `createdAt` ma okno czasu, dlaczego usuwanie jest zablokowane.

- [ ] **Krok 4: Uruchom testy — mają przejść**

```bash
cd firebase && npm test
```

- [ ] **Krok 5: Mutacja per klauzula — obowiązkowa**

Usuń **pojedynczo** każdą z klauzul: `snapshotPrawdziwy()`, `typZgodnyZRola()`, `cenaZgodnaZProfilem()`, `czasWiarygodny()`, warunek na `authorId`, warunek na `isActive`. Po każdej uruchom `npm test` i zanotuj, który test się zaczerwienił.

Klauzula, której usunięcie nie zapala żadnego testu, **nie jest przetestowana** — niezależnie od tego, ile testów przechodzi obok. Dopisz test izolujący i powtórz.

- [ ] **Krok 6: Indeks złożony**

Do `firebase/firestore.indexes.json` dopisz indeks dla `posts`: `isActive` rosnąco, `type` rosnąco, `createdAt` malejąco.

---

## Zadanie 2: Model, źródło danych, repozytorium, atrapa

**Pliki:**
- Utwórz: `PostModel.kt`, `FirestorePostDataSource.kt`, `PostRepository.kt`
- Zmodyfikuj: `DataSources.kt`, `AppContainer.kt`, `FakeDataSources.kt`

**Interfejsy:**
- Produkuje: `PostRepository(postDataSource)` z `activePosts(type)`, `postsByAuthor(id)`, `savePost(post)`, `deactivatePost(id)`, `newPostId()`

- [ ] **Krok 1: `PostModel.kt`**

```kotlin
package com.example.stellare.data.model

import com.google.firebase.firestore.PropertyName

enum class PostType { SEEKING_PSYCHOLOGIST, OFFERING_THERAPY }

data class AuthorSnapshot(
    val firstName: String = "",
    val lastName: String = "",
    val profileImageUri: String? = null,
    val age: Int = 0,
    val gender: String = "",
    @get:PropertyName("isVerified") val isVerified: Boolean = false
)

data class PostModel(
    val id: String = "",
    val authorId: String = "",
    val type: PostType = PostType.SEEKING_PSYCHOLOGIST,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val priceCoins: Int = 0,
    val createdAt: Long = 0L,
    @get:PropertyName("isActive") val isActive: Boolean = true,
    val authorSnapshot: AuthorSnapshot = AuthorSnapshot()
)
```

**Adnotacje `@get:PropertyName` nie są ozdobą.** `CustomClassMapper` obcina przedrostek `is` w nazwach pól typu `Boolean`, więc `isVerified` zapisałoby się jako `verified`, a reguła szukająca `isVerified` nie znalazłaby niczego. W Etapie 1 ten sam błąd zablokował całą rejestrację i przez sześć zadań zostawiał otwartą samoweryfikację.

- [ ] **Krok 2: Interfejs w `DataSources.kt`**

```kotlin
interface PostDataSource {
    fun getActivePosts(type: String): Flow<List<PostModel>>
    fun getPostsByAuthor(authorId: String): Flow<List<PostModel>>
    suspend fun savePost(post: PostModel)
    suspend fun deactivatePost(postId: String)
    fun newPostId(): String
}
```

- [ ] **Krok 3: Implementacja Firestore**

Wzoruj się na `FirestoreReviewDataSource`. W gałęzi błędu **loguj i wracaj, bez `close()`** — zamknięty strumień nie wraca do końca sesji, co w Etapie 2 zostawiało puste ekrany po mignięciu sieci.

- [ ] **Krok 4: Atrapa**

`FakePostDataSource` z deterministycznym `newPostId()` zwracającym `post-1`, `post-2`. **Atrapa nie może być łagodniejsza od prawdziwego źródła** — skoro `getActivePosts` filtruje po `isActive`, atrapa też musi. Rozjazd atrapy ze źródłem ugryzł w tym projekcie pięć razy.

- [ ] **Krok 5: Test wierności atrapy**

Dopisz do `FakeDataSourcesFidelityTest`: `getActivePosts` pomija nieaktywne, `deactivatePost` ustawia `isActive = false` zamiast usuwać dokument.

---

## Zadanie 3: `PostViewModel`

**Pliki:**
- Utwórz: `ui/posts/PostViewModel.kt`, `app/src/test/java/com/example/stellare/posts/PostViewModelTest.kt`

**Interfejsy:**
- Konsumuje: `PostRepository`, `UserRepository`
- Produkuje: `seekingPosts`, `offeringPosts`, `myPosts`, `publish(title, body, tags)`, `deactivate(postId)`

- [ ] **Krok 1: `publish` wylicza wszystko z profilu**

`type`, `priceCoins` i `authorSnapshot` **wylicz z `userRepository.getCurrentUser()`**, nigdy z parametrów. Parametr, który wywołujący może podać, jest polem, które atakujący może podstawić — a reguła i tak odrzuci zapis, więc niezgodność objawi się jako cicha awaria zapisu.

Czytaj użytkownika przez `getCurrentUser()`, nie przez `.value` na strumieniu. Strumień budowany przez `stateIn(..., WhileSubscribed, ...)` bez aktywnego subskrybenta zwraca wartość początkową, przez co metoda po cichu nic nie robi. W Etapie 2 ten błąd wystąpił siedem razy.

- [ ] **Krok 2: Testy**

Minimum pięć:
- psycholog publikuje → `type == OFFERING_THERAPY`, `priceCoins` równe jego `pricePerSession`
- pacjent publikuje → `type == SEEKING_PSYCHOLOGIST`, `priceCoins == 0`
- `authorSnapshot.isVerified` pochodzi z profilu, nie z parametru
- `publish` bez zalogowanego użytkownika nie zapisuje niczego
- `deactivate` ustawia `isActive = false` i nie usuwa dokumentu

- [ ] **Krok 3: Mutacje**

Po jednej, każda cofnięta przed następną: podmień `type` na stałą, usuń przepisanie `isVerified` z profilu, usuń bramkę zalogowania. Każda musi zapalić test. Zanotuj, który.

---

## Zadanie 4: Ekran „Napisz ogłoszenie"

**Pliki:**
- Utwórz: `ui/posts/PostComposerScreen.kt`
- Zmodyfikuj: `Routes.kt`, `StellareNavHost.kt`, `MainDashboardScreen.kt`

- [ ] **Krok 1: Trasa i wejście**

Dodaj `const val POST_COMPOSER = "posts/new"`. Wejście to `+` w górnym pasku kanału ogłoszeń.

- [ ] **Krok 2: Formularz**

Tytuł, treść, tagi wybierane z `ui/constants/Specializations.kt`. **Pole ceny pokazuj wyłącznie psychologowi i tylko do odczytu** — cena pochodzi z profilu, a formularz nie modyfikuje profilu (sekcja 8 specyfikacji).

Po publikacji wróć przez `navController.popBackStack()`. ViewModel nie nawiguje — tak jest w całym projekcie od Etapu 2.

- [ ] **Krok 3: Kompilacja**

```bash
./gradlew :app:compileDebugKotlin
```

---

## Zadanie 5: Kanał ogłoszeń na `posts`

**Pliki:**
- Zmodyfikuj: `MainDashboardScreen.kt`, `DashboardComponents.kt`, `StellareNavHost.kt`

- [ ] **Krok 1: Zakładki zależne od roli**

Pacjent widzi jedną zakładkę — `Szukam psychologa`, wypełnioną ogłoszeniami typu `OFFERING_THERAPY`. Psycholog widzi dwie.

**Rozdział ról jest wymogiem prywatności, nie kosmetyką**: pacjent nie ogląda ogłoszeń innych pacjentów, bo mało kto chce ujawniać swoje problemy przed osobami w podobnej sytuacji. Sprawdź to na urządzeniu w Zadaniu 8, nie tylko w kodzie.

- [ ] **Krok 2: Kliknięcie karty**

Prowadzi do profilu autora — `Routes.psychologistDetail(post.authorId)`.

- [ ] **Krok 3: Kompilacja i testy**

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

---

## Zadanie 6: Filtry po tagach

**Pliki:**
- Zmodyfikuj: `FeedFilters.kt`, `PostViewModel.kt`, `DashboardComponents.kt`

- [ ] **Krok 1: Dopisz `tags: Set<String>` do `FeedFilters`**

Ogłoszenie pasuje, gdy **zawiera co najmniej jeden** z wybranych tagów. Pusty zbiór nie filtruje niczego.

- [ ] **Krok 2: Testy `FeedFilters`**

Dopisz do istniejącego `FeedFiltersTest`: pusty zbiór przepuszcza wszystko, jeden wspólny tag przepuszcza, brak wspólnego odrzuca.

- [ ] **Krok 3: Pułapka z Etapu 2**

Arkusz filtrów buduje nowy obiekt przez `filters.copy(...)`, **nigdy `FeedFilters(...)` od zera** — inaczej zastosowanie filtrów kasuje wpisane wyszukiwanie.

---

## Zadanie 7: Odświeżanie `authorSnapshot`

**Pliki:**
- Zmodyfikuj: `ProfileViewModel.kt`, `PostRepository.kt`, `PostViewModel.kt`

- [ ] **Krok 1: Po zmianie profilu przepisz własne ogłoszenia**

`updateProfile` po udanym zapisie użytkownika pobiera własne ogłoszenia i aktualizuje w nich `authorSnapshot`. Bez tego reguła odrzuci **każdą późniejszą edycję** ogłoszenia, bo snapshot przestanie zgadzać się z profilem.

- [ ] **Krok 2: To samo po zmianie weryfikacji**

`isVerified` zmienia administrator, a ten nie ma prawa pisać do cudzych ogłoszeń. Snapshot odświeży się przy najbliższym wejściu autora do aplikacji — zrób to w `PostViewModel` jako jednorazowe uzgodnienie po zalogowaniu.

**Zgłoś mi, jeśli uznasz, że to nie wystarcza.** Alternatywą jest czytanie `isVerified` z `users` przy renderowaniu karty, czyli rezygnacja z denormalizacji także tego pola.

- [ ] **Krok 3: Test**

Zmiana imienia w profilu aktualizuje `authorSnapshot` we własnym ogłoszeniu. Mutacja: usuń przepisanie, test ma paść.

---

## Zadanie 8: Test na urządzeniu

Bez tego etap nie jest zamknięty. W Etapie 2 trzy realne błędy przeszły przez 185 testów i dziesięć recenzji z mutacjami.

- [ ] **Krok 1: Środowisko**

Emulatory Firebase (`--project stellare-a39ef --only firestore,auth`), zasiew, emulator Androida, `installDebug`, `adb shell pm clear com.example.stellare`.

- [ ] **Krok 2: Ścieżka**

1. Pacjent publikuje ogłoszenie — czy pojawia się w zakładce psychologa, a **nie** w swojej własnej?
2. Psycholog publikuje — czy karta pokazuje cenę z jego profilu?
3. Czy pacjent widzi **jedną** zakładkę, a psycholog dwie?
4. Filtr po tagu — czy zawęża listę i **nie kasuje** wpisanego wyszukiwania?
5. Dezaktywacja własnego ogłoszenia — znika z listy, zostaje w bazie?
6. Kliknięcie karty prowadzi do profilu autora?
7. `adb logcat` — żadnego `PERMISSION_DENIED`

- [ ] **Krok 3: Zrzuty ekranu jako dowód**

Każdy punkt ze zrzutem. Opis bez zrzutu nie jest dowodem — w Etapie 2 trzy niezależne potwierdzenia z lektury kodu obalił jeden zrzut ekranu.

---

## Definicja ukończenia

- Reguły `posts` pokryte testami, **każda klauzula zweryfikowana osobną mutacją**
- Nie da się wystawić ogłoszenia z fałszywą odznaką weryfikacji, cudzą ceną ani cudzym `authorId`
- Pacjent nie widzi ogłoszeń innych pacjentów — potwierdzone na urządzeniu
- Testy jednostkowe i testy reguł zielone
- Test na urządzeniu przeszedł, ze zrzutami

## Czego ten plan świadomie nie obejmuje

- Powiadomień o nowych ogłoszeniach — dzwonek w pasku zostaje atrapą do Etapu 8
- Wyszukiwania pełnotekstowego — Firestore go nie ma, filtrowanie jest po polach
- Zgłaszania nadużyć i moderacji ogłoszeń
- Zamknięcia dwóch dziur w ocenach (recenzja z drugiego konta, ocena podawana przez klienta) — to Etap 5, wymaga zmiany modelu danych
- Nowego stylu kart z referencji graficznej — Etap 6 przerabia całą warstwę wizualną naraz, więc rozbijanie tego na dwa etapy dałoby podwójną pracę
