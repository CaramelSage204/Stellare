# Etap 2 — Refaktor szkieletu — plan wdrożenia

> **Dla wykonawców agentowych:** WYMAGANY SUB-SKILL: użyj `superpowers:subagent-driven-development` (zalecane) albo `superpowers:executing-plans`, żeby wykonać ten plan zadanie po zadaniu. Kroki mają składnię checkboxów (`- [ ]`).

**Cel:** Rozbić `StellareViewModel` (667 linii, obsługuje całą aplikację) na sześć ViewModeli z jasnymi granicami, przenieść nawigację na Navigation Compose i zbudować powłokę z dolnym paskiem oraz arkuszem profilu — bez zmiany zestawu funkcji widocznych dla użytkownika.

**Architektura:** Źródła danych dostają interfejsy, dzięki czemu repozytoria da się karmić atrapami, a ViewModele testować na maszynie wirtualnej Javy bez emulatora i bez Firebase. Zależności składa ręczny kontener `AppContainer` — bez biblioteki wstrzykiwania zależności, bo przy sześciu ViewModelach byłaby to przesada. Nawigacja przechodzi na `NavHost` z trasami tekstowymi zebranymi w jednym obiekcie `Routes`.

**Stos:** Kotlin 2.2.10, Jetpack Compose, `androidx.navigation:navigation-compose` 2.8.9 (już w katalogu wersji, dotąd nieużywane), JUnit 4.13.2, `kotlinx-coroutines-test` 1.10.2 (oba już w katalogu wersji).

**Specyfikacja:** [docs/superpowers/specs/2026-09-15-stellare-design.md](../specs/2026-09-15-stellare-design.md)

**Poprzedni etap:** [2026-09-15-etap1-fundament-bezpieczenstwa.md](2026-09-15-etap1-fundament-bezpieczenstwa.md) — musi być ukończony. Ten plan zakłada, że `AppointmentModel.priceCoins`, `WalletTransactionType`, `ReviewModel.reviewerId` i transakcyjna rezerwacja już istnieją.

## Ograniczenia globalne

- **Zero zmian funkcjonalnych.** Po tym etapie użytkownik ma widzieć dokładnie te same możliwości co przed nim. Jedyna widoczna różnica to dolny pasek nawigacji zamiast wyłącznie szuflady.
- **Żaden ViewModel nie przekracza 200 linii.** Przekroczenie oznacza, że granica została źle poprowadzona.
- **ViewModele nie znają Firebase.** Zależą wyłącznie od repozytoriów. Import `com.google.firebase` w pakiecie `ui` to błąd — jedynym wyjątkiem jest `AuthViewModel`, który obsługuje poświadczenia Google (`GetCredentialRequest`).
- **Testy jednostkowe działają bez emulatora i bez urządzenia** — `./gradlew :app:testDebugUnitTest`.
- **Nazewnictwo:** kod, nazwy klas i enumy po angielsku; komunikaty dla użytkownika po polsku (zgodnie z istniejącym kodem).
- **Bez commitów.** Zgodnie ze stałą zasadą właściciela repozytorium żadne zadanie nie wykonuje `git commit` ani `git push`.

---

## Struktura plików

### Nowe

| Plik | Odpowiedzialność |
| --- | --- |
| `data/remote/DataSources.kt` | Interfejsy wszystkich źródeł danych |
| `di/AppContainer.kt` | Składanie zależności i fabryka ViewModeli |
| `ui/auth/AuthViewModel.kt` | Logowanie, rejestracja, wylogowanie, błędy uwierzytelniania |
| `ui/feed/FeedViewModel.kt` | Listy ogłoszeń, filtry, ulubione |
| `ui/feed/FeedFilters.kt` | Model filtrów jako jedna struktura |
| `ui/calendar/CalendarViewModel.kt` | Terminy, wolne sloty, rezerwacja |
| `ui/chat/ChatViewModel.kt` | Czaty, wiadomości, notatki |
| `ui/wallet/WalletViewModel.kt` | Saldo, historia operacji, doładowanie |
| `ui/profile/ProfileViewModel.kt` | Profil bieżącego użytkownika, recenzje |
| `ui/navigation/Routes.kt` | Wszystkie trasy w jednym miejscu |
| `ui/navigation/StellareNavHost.kt` | Graf nawigacji |
| `ui/shell/StellareShell.kt` | Powłoka: rusztowanie, dolny pasek, arkusz profilu |
| `ui/shell/BottomBar.kt` | Dolny pasek nawigacji |
| `ui/shell/ProfileSheet.kt` | Arkusz z pozycjami spod ikony profilu |

### Nowe testy

| Plik | Zakres |
| --- | --- |
| `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt` | Atrapy wszystkich źródeł danych |
| `app/src/test/java/com/example/stellare/feed/FeedViewModelTest.kt` | Filtrowanie i ulubione |
| `app/src/test/java/com/example/stellare/calendar/CalendarViewModelTest.kt` | Rezerwacja i błędy |
| `app/src/test/java/com/example/stellare/wallet/WalletViewModelTest.kt` | Saldo i historia |
| `app/src/test/java/com/example/stellare/auth/AuthViewModelTest.kt` | Błędy logowania |

### Usuwane

| Plik | Powód |
| --- | --- |
| `ui/StellareViewModel.kt` | Zastąpiony sześcioma ViewModelami |
| `ui/StellareApp.kt` | Zastąpiony powłoką i grafem nawigacji |

---

## Zadanie 0: Dług bezpieczeństwa po Etapie 1

Pięć ustaleń z review Etapu 1, które świadomie odłożono, bo wymagały zmian w modelach albo wykraczały poza zakres tamtych zadań. Wszystkie są potwierdzone empirycznie.

**Pliki:**
- Utwórz: `firebase/firestore.indexes.json`
- Zmodyfikuj: `firebase/firebase.json`, `firebase/package.json`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/model/NoteModel.kt`
- Zmodyfikuj: `firebase/firestore.rules` (blok `notes`)
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/remote/FirestoreChatDataSource.kt`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/ui/StellareViewModel.kt`
- Test: `firebase/test/collections.test.js`

- [ ] **Krok 1: Deklaracje indeksów złożonych**

Pięć zapytań ich wymaga i bez nich pięć ekranów będzie **pustych bez jednego komunikatu** — źródła danych połykają błąd przez `close()` bez przyczyny. Utwórz `firebase/firestore.indexes.json`:

```json
{
  "indexes": [
    {
      "collectionGroup": "chats",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "participants", "arrayConfig": "CONTAINS" },
        { "fieldPath": "lastMessageTime", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "messages",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "chatId", "order": "ASCENDING" },
        { "fieldPath": "timestamp", "order": "ASCENDING" }
      ]
    },
    {
      "collectionGroup": "notes",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "chatId", "order": "ASCENDING" },
        { "fieldPath": "timestamp", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "reviews",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "psychologistId", "order": "ASCENDING" },
        { "fieldPath": "timestamp", "order": "DESCENDING" }
      ]
    },
    {
      "collectionGroup": "wallet_transactions",
      "queryScope": "COLLECTION",
      "fields": [
        { "fieldPath": "userId", "order": "ASCENDING" },
        { "fieldPath": "timestamp", "order": "DESCENDING" }
      ]
    }
  ],
  "fieldOverrides": []
}
```

**Zanim to wpiszesz, sprawdź faktyczne zapytania** w `app/src/main/java/com/example/stellare/data/remote/` — każde `whereEqualTo` + `orderBy` po innym polu wymaga indeksu, a kolejność pól w indeksie musi odpowiadać zapytaniu. Jeżeli któreś zapytanie nie ma `orderBy`, indeks jest zbędny; jeżeli któreś ma inny zestaw pól, popraw wpis.

W `firebase/firebase.json` dopisz do bloku `firestore`:

```json
    "indexes": "firestore.indexes.json"
```

W `firebase/package.json` rozszerz skrypt wdrożenia:

```json
    "deploy": "firebase deploy --only firestore:rules,firestore:indexes"
```

- [ ] **Krok 2: Autor notatki klinicznej**

Dziś pacjent może **czytać i kasować** notatki, które psycholog prowadzi na jego temat — potwierdzone testem. Reguły nie mają czym rozróżnić stron, bo `NoteModel` nie ma pola autora.

W `NoteModel.kt` dopisz pole:

```kotlin
    val authorId: String = "",
```

W `StellareViewModel.saveClientNote` ustaw je na identyfikator zalogowanego użytkownika.

W `firestore.rules`, w bloku `match /notes/{id}`, zastąp regułę odczytu i zapisu:

```
      allow read: if jestUczestnikiem(resource.data.chatId)
        && (resource.data.get('authorId', '') == '' 
            || resource.data.authorId == request.auth.uid
            || request.auth.uid == get(/databases/$(database)/documents/chats/$(resource.data.chatId)).data.psychologistId);

      allow create: if jestUczestnikiem(request.resource.data.chatId)
        && request.resource.data.authorId == request.auth.uid;

      allow update: if resource.data.authorId == request.auth.uid
        && request.resource.data.authorId == resource.data.authorId
        && request.resource.data.chatId == resource.data.chatId;

      allow delete: if resource.data.authorId == request.auth.uid;
```

Notatki zapisane wcześniej nie mają `authorId` — pierwszy człon reguły odczytu je przepuszcza, żeby nie osierocić danych. Nowe notatki widzi wyłącznie ich autor oraz psycholog prowadzący czat.

- [ ] **Krok 3: `updateChat` przestaje nadpisywać cały dokument**

`FirestoreChatDataSource.updateChat` robi `set(chat)` przeciw regule dopuszczającej wyłącznie zmianę `lastMessage` i `lastMessageTime`. Dziś przechodzi tylko dlatego, że model dokładnie odpowiada dokumentowi — **jeden dodatkowy klucz w dokumencie trwale zablokuje czat**. Zastąp:

```kotlin
    override suspend fun updateChat(chat: ChatModel) {
        collection.document(chat.chatId).update(
            mapOf(
                "lastMessage" to chat.lastMessage,
                "lastMessageTime" to chat.lastMessageTime
            )
        ).await()
    }
```

- [ ] **Krok 4: Testy**

```javascript
  it('pacjent nie odczyta notatki psychologa o sobie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('pacjent nie skasuje notatki psychologa o sobie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(deleteDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('psycholog odczyta i skasuje wlasna notatke', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(getDoc(doc(db, 'notes/klinicznaNotatka')))
    await assertSucceeds(deleteDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('notatka sprzed dodania autora pozostaje czytelna dla uczestnikow', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraNotatka'), {
        noteId: 'staraNotatka', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(getDoc(doc(db, 'notes/staraNotatka')))
  })

  it('aktualizacja czatu zmienia wylacznie ostatnia wiadomosc', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'chats/czat1'), {
      lastMessage: 'nowa', lastMessageTime: 99,
    }))
  })
```

Dwa pierwsze mają **padać przed poprawką** — zmierz to.

- [ ] **Krok 5: Weryfikacja**

```bash
cd firebase && export PATH="/c/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin:$PATH" && npm test
```

Oczekiwane: cały dotychczasowy zestaw zielony plus nowe testy, przy zachowaniu 3 pominiętych.

```bash
./gradlew :app:compileDebugKotlin
```

**Uwaga na pułapkę serializacji:** jeśli będziesz dodawać pole `Boolean` o nazwie zaczynającej się od `is`, Firestore zapisze je pod nazwą bez tego prefiksu. Wymaga `@get:PropertyName`. Szczegóły w `task-4-report.md` i w komentarzu przy `bookingCounterpartyEntry()` w regułach.

---

## Zadanie 1: Testowalność — interfejsy, atrapy, kontener

Bez tego zadania żadnego ViewModelu nie da się przetestować, bo wszystkie źródła danych tworzą klienta Firestore w konstruktorze.

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/data/remote/DataSources.kt`
- Utwórz: `app/src/main/java/com/example/stellare/di/AppContainer.kt`
- Utwórz: `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`
- Utwórz: `app/src/test/java/com/example/stellare/fakes/FakeUserDataSourceTest.kt`
- Zmodyfikuj: `app/build.gradle.kts`
- Zmodyfikuj: wszystkie pliki w `data/remote/` (dopisanie `: Interfejs`)
- Zmodyfikuj: wszystkie pliki w `data/repository/` (zależność od interfejsów)

**Interfejsy:**
- Produkuje: `UserDataSource`, `AuthDataSource`, `ChatDataSource`, `MessageDataSource`, `NoteDataSource`, `AppointmentDataSource`, `WalletDataSource`, `ReviewDataSource`, `FavoriteDataSource` — implementowane przez klasy `Firestore*` i przez atrapy; `AppContainer` z polami `userRepository`, `chatRepository`, `appointmentRepository`, `reviewRepository`, `walletRepository`.

- [ ] **Krok 1: Dodaj zależności testowe do `app/build.gradle.kts`**

W bloku `dependencies` dopisz na końcu:

```kotlin
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
```

Obie pozycje są już zadeklarowane w `gradle/libs.versions.toml` — nie trzeba tam niczego dopisywać.

- [ ] **Krok 2: Utwórz `data/remote/DataSources.kt`**

Sygnatury przepisz dokładnie z istniejących klas `Firestore*`, żeby dodanie `: Interfejs` nie wymagało żadnej innej zmiany.

```kotlin
package com.example.stellare.data.remote

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.ChatModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.MessageModel
import com.example.stellare.data.model.NoteModel
import com.example.stellare.data.model.ReviewModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.WalletTransactionModel
import kotlinx.coroutines.flow.Flow

interface UserDataSource {
    fun getUser(uid: String): Flow<UserModel?>
    suspend fun getUserOnce(uid: String): UserModel?
    fun getAllByRole(role: String): Flow<List<UserModel>>
    fun getAllByRoleExcluding(role: String, excludeId: String): Flow<List<UserModel>>
    suspend fun saveUser(user: UserModel)
}

interface FavoriteDataSource {
    fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>>
    suspend fun saveFavorite(favorite: FavoriteModel)
    suspend fun deleteFavorite(userId: String, psychologistId: String)
}

interface ChatDataSource {
    fun getChatsForUser(userId: String): Flow<List<ChatModel>>
    suspend fun getChatByParticipants(psychId: String, patientId: String): ChatModel?
    suspend fun saveChat(chat: ChatModel)
    suspend fun updateChat(chat: ChatModel)
}

interface MessageDataSource {
    fun getMessagesForChat(chatId: String): Flow<List<MessageModel>>
    suspend fun saveMessage(message: MessageModel)
}

interface NoteDataSource {
    fun getNotesForChat(chatId: String): Flow<List<NoteModel>>
    suspend fun saveNote(note: NoteModel)
    suspend fun deleteNote(noteId: String)
}

interface AppointmentDataSource {
    fun getAppointmentsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>>
    fun getAppointmentsForPatient(patientId: String): Flow<List<AppointmentModel>>
    fun getFreeSlotsForPsychologist(psychologistId: String): Flow<List<AppointmentModel>>
    suspend fun saveAppointment(appointment: AppointmentModel)
    suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    )
    suspend fun cancelBooking(appointmentId: String)
    suspend fun completeAppointment(appointmentId: String)
    suspend fun deleteAppointment(appointmentId: String)
    suspend fun getAppointmentByDateTime(psychId: String, date: String, time: String): AppointmentModel?
}

interface WalletDataSource {
    fun getTransactionsForUser(userId: String): Flow<List<WalletTransactionModel>>
    suspend fun saveTransaction(transaction: WalletTransactionModel)
}

interface ReviewDataSource {
    fun getReviewsForPsychologist(psychologistId: String): Flow<List<ReviewModel>>
    suspend fun saveReview(review: ReviewModel)
}
```

**Uwaga dla wykonawcy:** zanim napiszesz ten plik, otwórz każdą klasę w `app/src/main/java/com/example/stellare/data/remote/` i przepisz sygnatury **dokładnie takie, jakie tam są**. Powyższe odzwierciedla stan z dnia pisania planu; jeżeli coś się różni, źródłem prawdy jest kod.

- [ ] **Krok 3: Oznacz klasy Firestore jako implementacje**

W każdym pliku `data/remote/Firestore*.kt` dopisz interfejs do deklaracji klasy i `override` przed każdą metodą z interfejsu. Przykład dla `FirestoreUserDataSource.kt`:

```kotlin
class FirestoreUserDataSource : UserDataSource {
    ...
    override fun getUser(userId: String): Flow<UserModel?> { ... }
```

`FirebaseAuthDataSource` zostaw bez interfejsu — obsługuje poświadczenia Google i będzie używany bezpośrednio przez `AuthViewModel`.

- [ ] **Krok 4: Przestaw repozytoria na interfejsy**

W każdym pliku `data/repository/*.kt` zamień typy w konstruktorze na interfejsy. Przykład:

```kotlin
class UserRepository(
    private val authDataSource: FirebaseAuthDataSource,
    private val userDataSource: UserDataSource,
    private val favoriteDataSource: FavoriteDataSource
) {
```

Ciała metod pozostają bez zmian.

- [ ] **Krok 5: Utwórz `di/AppContainer.kt`**

```kotlin
package com.example.stellare.di

import com.example.stellare.data.remote.FirebaseAuthDataSource
import com.example.stellare.data.remote.FirestoreAppointmentDataSource
import com.example.stellare.data.remote.FirestoreChatDataSource
import com.example.stellare.data.remote.FirestoreFavoriteDataSource
import com.example.stellare.data.remote.FirestoreMessageDataSource
import com.example.stellare.data.remote.FirestoreNoteDataSource
import com.example.stellare.data.remote.FirestoreReviewDataSource
import com.example.stellare.data.remote.FirestoreUserDataSource
import com.example.stellare.data.remote.FirestoreWalletTransactionDataSource
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.data.repository.ChatRepository
import com.example.stellare.data.repository.ReviewRepository
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.data.repository.WalletRepository

class AppContainer {
    private val authDataSource = FirebaseAuthDataSource()

    val userRepository = UserRepository(
        authDataSource,
        FirestoreUserDataSource(),
        FirestoreFavoriteDataSource()
    )
    val chatRepository = ChatRepository(
        FirestoreChatDataSource(),
        FirestoreMessageDataSource(),
        FirestoreNoteDataSource()
    )
    val appointmentRepository = AppointmentRepository(FirestoreAppointmentDataSource())
    val reviewRepository = ReviewRepository(FirestoreReviewDataSource())
    val walletRepository = WalletRepository(FirestoreWalletTransactionDataSource())
}
```

- [ ] **Krok 6: Utwórz atrapy — `app/src/test/java/com/example/stellare/fakes/FakeDataSources.kt`**

Atrapy trzymają dane w `MutableStateFlow`, dzięki czemu testy widzą zmiany natychmiast, bez opóźnień i bez sieci.

```kotlin
package com.example.stellare.fakes

import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.remote.FavoriteDataSource
import com.example.stellare.data.remote.UserDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUserDataSource(initial: List<UserModel> = emptyList()) : UserDataSource {
    val users = MutableStateFlow(initial)

    override fun getUser(userId: String): Flow<UserModel?> =
        users.map { list -> list.firstOrNull { it.id == userId } }

    override suspend fun getUserOnce(userId: String): UserModel? =
        users.value.firstOrNull { it.id == userId }

    override fun getAllByRole(role: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role } }

    override fun getAllByRoleExcluding(role: String, excludedId: String): Flow<List<UserModel>> =
        users.map { list -> list.filter { it.role.name == role && it.id != excludedId } }

    override suspend fun saveUser(user: UserModel) {
        users.value = users.value.filterNot { it.id == user.id } + user
    }
}

class FakeFavoriteDataSource : FavoriteDataSource {
    val favorites = MutableStateFlow<List<FavoriteModel>>(emptyList())

    override fun getFavoritesForUser(userId: String): Flow<List<FavoriteModel>> =
        favorites.map { list -> list.filter { it.userId == userId } }

    override suspend fun saveFavorite(favorite: FavoriteModel) {
        favorites.value = favorites.value + favorite
    }

    override suspend fun deleteFavorite(userId: String, psychologistId: String) {
        favorites.value = favorites.value.filterNot {
            it.userId == userId && it.psychologistId == psychologistId
        }
    }
}
```

Pozostałe atrapy (`FakeChatDataSource`, `FakeMessageDataSource`, `FakeNoteDataSource`, `FakeAppointmentDataSource`, `FakeWalletDataSource`, `FakeReviewDataSource`) napisz w tym samym pliku, według tego samego wzorca: `MutableStateFlow` z listą, filtrowanie w `map`, zapis podmieniający wartość. `FakeAppointmentDataSource.bookAppointmentAtomically` ma rzucać `IllegalStateException("Termin został już zarezerwowany.")`, gdy status terminu nie jest `FREE` — to pozwoli przetestować obsługę błędu w `CalendarViewModel`.

- [ ] **Krok 7: Napisz test sprawdzający, czy atrapy działają**

Utwórz `app/src/test/java/com/example/stellare/fakes/FakeUserDataSourceTest.kt`:

```kotlin
package com.example.stellare.fakes

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeUserDataSourceTest {

    private val psycholog = UserModel(id = "p1", firstName = "Ala", role = UserRole.PSYCHOLOGIST)
    private val pacjent = UserModel(id = "k1", firstName = "Bogdan", role = UserRole.PATIENT)

    @Test
    fun `zwraca uzytkownika po identyfikatorze`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog, pacjent))
        assertEquals("Ala", source.getUser("p1").first()?.firstName)
    }

    @Test
    fun `zwraca null dla nieznanego identyfikatora`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog))
        assertNull(source.getUser("nie-ma").first())
    }

    @Test
    fun `filtruje po roli`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog, pacjent))
        assertEquals(listOf(pacjent), source.getAllByRole("PATIENT").first())
    }

    @Test
    fun `zapis podmienia istniejacego uzytkownika`() = runTest {
        val source = FakeUserDataSource(listOf(psycholog))
        source.saveUser(psycholog.copy(firstName = "Alicja"))
        assertEquals(1, source.users.value.size)
        assertEquals("Alicja", source.getUser("p1").first()?.firstName)
    }
}
```

- [ ] **Krok 8: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*FakeUserDataSourceTest*"
```

Oczekiwany wynik: **PASS** — cztery testy.

- [ ] **Krok 9: Sprawdź, czy aplikacja nadal się kompiluje**

```bash
./gradlew :app:compileDebugKotlin
```

Oczekiwany wynik: BUILD SUCCESSFUL. `StellareViewModel` wciąż tworzy źródła danych bezpośrednio i to jest w porządku — zniknie w Zadaniu 10.

---

## Zadanie 2: FeedViewModel

Największy kawałek starego ViewModelu: dziewięć osobnych strumieni filtrów sklejanych przez `combine` z rzutowaniem `Array<Any>`. Zamieniamy to na jedną strukturę.

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/feed/FeedFilters.kt`
- Utwórz: `app/src/main/java/com/example/stellare/ui/feed/FeedViewModel.kt`
- Utwórz: `app/src/test/java/com/example/stellare/feed/FeedViewModelTest.kt`

**Interfejsy:**
- Konsumuje: `UserRepository` z Zadania 1, atrapy z `fakes/FakeDataSources.kt`
- Produkuje: `FeedFilters` (data class), `FeedViewModel(userRepository: UserRepository)` ze strumieniami `psychologists: StateFlow<List<UserModel>>`, `patients: StateFlow<List<UserModel>>`, `favoriteIds: StateFlow<Set<String>>`, `filters: StateFlow<FeedFilters>` i metodami `updateFilters(FeedFilters)`, `updateQuery(String)`, `resetFilters()`, `toggleFavorite(String)`.

- [ ] **Krok 1: Utwórz `FeedFilters.kt`**

```kotlin
package com.example.stellare.ui.feed

data class FeedFilters(
    val query: String = "",
    val ageMin: Int = 18,
    val ageMax: Int = 100,
    val gender: String = WSZYSTKIE,
    val specialization: String = WSZYSTKIE,
    val verifiedOnly: Boolean = false,
    val priceMax: Double = 400.0,
    val minRating: Double = 0.0
) {
    fun matches(user: com.example.stellare.data.model.UserModel): Boolean {
        val pasujeTekst = query.isEmpty() ||
            "${user.firstName} ${user.lastName}".contains(query, ignoreCase = true) ||
            user.specializations.contains(query, ignoreCase = true)
        val pasujeWiek = user.age in ageMin..ageMax
        val pasujePlec = gender == WSZYSTKIE || user.gender.equals(gender, ignoreCase = true)
        val pasujeSpec = specialization == WSZYSTKIE ||
            user.specializations.contains(specialization, ignoreCase = true)
        val pasujeWeryfikacja = !verifiedOnly || user.isVerified
        val pasujeCena = user.pricePerSession <= priceMax
        val pasujeOcena = user.rating >= minRating
        return pasujeTekst && pasujeWiek && pasujePlec && pasujeSpec &&
            pasujeWeryfikacja && pasujeCena && pasujeOcena
    }

    companion object {
        const val WSZYSTKIE = "Wszystkie"
    }
}
```

Przeniesienie warunku do modelu filtrów sprawia, że da się go przetestować bez tworzenia ViewModelu.

- [ ] **Krok 2: Napisz testy warunku filtrowania**

Utwórz `app/src/test/java/com/example/stellare/feed/FeedViewModelTest.kt` z pierwszą klasą testów:

```kotlin
package com.example.stellare.feed

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.feed.FeedFilters
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedFiltersTest {

    private val psycholog = UserModel(
        id = "p1", firstName = "Ala", lastName = "Kowalska", age = 35,
        gender = "Kobieta", role = UserRole.PSYCHOLOGIST, isVerified = true,
        specializations = "Rodzina, Praca", pricePerSession = 120.0, rating = 4.5
    )

    @Test
    fun `domyslne filtry przepuszczaja wszystkich`() {
        assertTrue(FeedFilters().matches(psycholog))
    }

    @Test
    fun `filtr ceny odcina za drogich`() {
        assertFalse(FeedFilters(priceMax = 100.0).matches(psycholog))
        assertTrue(FeedFilters(priceMax = 120.0).matches(psycholog))
    }

    @Test
    fun `filtr wieku dziala obustronnie`() {
        assertFalse(FeedFilters(ageMin = 40).matches(psycholog))
        assertFalse(FeedFilters(ageMax = 30).matches(psycholog))
        assertTrue(FeedFilters(ageMin = 30, ageMax = 40).matches(psycholog))
    }

    @Test
    fun `filtr weryfikacji przepuszcza zweryfikowanych`() {
        assertTrue(FeedFilters(verifiedOnly = true).matches(psycholog))
        assertFalse(FeedFilters(verifiedOnly = true).matches(psycholog.copy(isVerified = false)))
    }

    @Test
    fun `wyszukiwanie dziala po nazwisku i specjalizacji`() {
        assertTrue(FeedFilters(query = "kowalska").matches(psycholog))
        assertTrue(FeedFilters(query = "rodzina").matches(psycholog))
        assertFalse(FeedFilters(query = "nieistniejace").matches(psycholog))
    }

    @Test
    fun `filtr oceny odcina slabiej ocenianych`() {
        assertFalse(FeedFilters(minRating = 4.8).matches(psycholog))
        assertTrue(FeedFilters(minRating = 4.5).matches(psycholog))
    }
}
```

- [ ] **Krok 3: Uruchom testy i potwierdź, że PADAJĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*FeedFiltersTest*"
```

Oczekiwany wynik: **FAIL** — kompilacja nie przejdzie, bo `FeedFilters` jeszcze nie istnieje, jeżeli wykonujesz kroki po kolei. Jeżeli Krok 1 został już wykonany, testy przejdą od razu — wtedy zmień kolejność i najpierw napisz test.

- [ ] **Krok 4: Utwórz `FeedViewModel.kt`**

```kotlin
package com.example.stellare.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _filters = MutableStateFlow(FeedFilters())
    val filters: StateFlow<FeedFilters> = _filters.asStateFlow()

    private val currentUser = userRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val wszyscyPsycholodzy = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGIST),
        userRepository.getUsersOfRoleFlow(UserRole.PSYCHOLOGY_STUDENT)
    ) { zawodowi, studenci -> zawodowi + studenci }

    val psychologists: StateFlow<List<UserModel>> =
        combine(wszyscyPsycholodzy, _filters) { lista, filtry ->
            lista.filter(filtry::matches).sortedByDescending { it.id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val patients: StateFlow<List<UserModel>> = combine(
        userRepository.getUsersOfRoleFlow(UserRole.PATIENT),
        _filters
    ) { lista, filtry ->
        lista.filter { user ->
            filtry.query.isEmpty() ||
                "${user.firstName} ${user.lastName}".contains(filtry.query, ignoreCase = true) ||
                user.bio.contains(filtry.query, ignoreCase = true)
        }.sortedByDescending { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptySet())
            else userRepository.getFavoritesForUserFlow(user.id)
                .map { lista -> lista.map { it.psychologistId }.toSet() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun updateFilters(filters: FeedFilters) {
        _filters.value = filters
    }

    fun updateQuery(query: String) {
        _filters.value = _filters.value.copy(query = query)
    }

    fun resetFilters() {
        _filters.value = FeedFilters()
    }

    fun toggleFavorite(psychologistId: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            if (favoriteIds.value.contains(psychologistId)) {
                userRepository.deleteFavorite(user.id, psychologistId)
            } else {
                userRepository.insertFavorite(user.id, psychologistId)
            }
        }
    }
}
```

Zwróć uwagę: `refreshData()` ze starego ViewModelu, które udawało odświeżanie przez `delay(800)`, znika. Firestore dostarcza dane strumieniowo, więc odświeżanie nie ma czego robić — gest „pociągnij, żeby odświeżyć" wywoła `resetFilters()`.

- [ ] **Krok 5: Dopisz testy ViewModelu**

Do pliku `FeedViewModelTest.kt` dopisz drugą klasę testów:

```kotlin
package com.example.stellare.feed

import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.UserRole
import com.example.stellare.data.repository.UserRepository
import com.example.stellare.fakes.FakeFavoriteDataSource
import com.example.stellare.fakes.FakeUserDataSource
import com.example.stellare.ui.feed.FeedFilters
import com.example.stellare.ui.feed.FeedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val tani = UserModel(
        id = "p1", firstName = "Ala", age = 35, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 80.0, rating = 4.5, isVerified = true
    )
    private val drogi = UserModel(
        id = "p2", firstName = "Borys", age = 45, role = UserRole.PSYCHOLOGIST,
        pricePerSession = 300.0, rating = 4.9, isVerified = true
    )

    private fun viewModel(vararg users: UserModel): FeedViewModel {
        val repo = UserRepository(
            FakeAuthDataSource("k1"),
            FakeUserDataSource(users.toList()),
            FakeFavoriteDataSource()
        )
        return FeedViewModel(repo)
    }

    @Test
    fun `filtr ceny zaweza liste psychologow`() = runTest(dispatcher) {
        val vm = viewModel(tani, drogi)
        vm.updateFilters(FeedFilters(priceMax = 100.0))
        assertEquals(listOf("p1"), vm.psychologists.first { it.isNotEmpty() }.map { it.id })
    }

    @Test
    fun `reset filtrow przywraca pelna liste`() = runTest(dispatcher) {
        val vm = viewModel(tani, drogi)
        vm.updateFilters(FeedFilters(priceMax = 100.0))
        vm.resetFilters()
        assertEquals(2, vm.psychologists.first { it.size == 2 }.size)
    }
}
```

**Uwaga dla wykonawcy:** interfejs `AuthDataSource` i `FakeAuthDataSource` powstały już w Zadaniu 1 — nie twórz ich ponownie. `AuthDataSource` celowo udostępnia wyłącznie identyfikator użytkownika (`currentUserId: Flow<String?>`, `getCurrentUserIdOnce(): String?`), bo `FirebaseUser` jest klasą abstrakcyjną SDK, której bez biblioteki mockującej nie da się podstawić. `FakeAuthDataSource("k1")` zwraca ustalony identyfikator.

- [ ] **Krok 6: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*Feed*"
```

Oczekiwany wynik: **PASS**.

---

## Zadanie 3: CalendarViewModel

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/calendar/CalendarViewModel.kt`
- Utwórz: `app/src/test/java/com/example/stellare/calendar/CalendarViewModelTest.kt`

**Interfejsy:**
- Konsumuje: `AppointmentRepository`, `UserRepository` z Zadania 1
- Produkuje: `CalendarViewModel(appointmentRepository, userRepository)` ze strumieniami `psychologistAppointments`, `patientAppointments`, `bookingError: StateFlow<String?>` i metodami `addFreeSlot(date, time)`, `book(appointment, notes)`, `cancel(appointmentId)`, `complete(appointmentId)`, `deleteSlot(appointmentId)`, `clearBookingError()`, `freeSlotsFor(psychologistId): Flow<List<AppointmentModel>>`.

- [ ] **Krok 1: Napisz testy**

Utwórz `app/src/test/java/com/example/stellare/calendar/CalendarViewModelTest.kt`:

```kotlin
package com.example.stellare.calendar

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.repository.AppointmentRepository
import com.example.stellare.fakes.FakeAppointmentDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val wolnyTermin = AppointmentModel(
        appointmentId = "t1", psychologistId = "p1", patientId = null,
        date = "2026-10-01", time = "10:00", status = "FREE", priceCoins = 80
    )

    @Test
    fun `zajety termin ustawia komunikat bledu`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()

        assertEquals("Termin został już zarezerwowany.", vm.bookingError.value)
    }

    @Test
    fun `udana rezerwacja nie ustawia bledu`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin, notes = "pierwsza sesja")
        advanceUntilIdle()

        assertNull(vm.bookingError.value)
        assertEquals("BOOKED", source.appointments.value.first().status)
    }

    @Test
    fun `czyszczenie bledu dziala`() = runTest(dispatcher) {
        val source = FakeAppointmentDataSource(listOf(wolnyTermin.copy(status = "BOOKED")))
        val vm = CalendarViewModel(AppointmentRepository(source), fakeUserRepository("k1"))

        vm.book(wolnyTermin.copy(status = "BOOKED"), notes = "")
        advanceUntilIdle()
        vm.clearBookingError()

        assertNull(vm.bookingError.value)
    }
}
```

Funkcję pomocniczą `fakeUserRepository(uid: String): UserRepository` napisz w `fakes/FakeDataSources.kt` — ma zwracać repozytorium oparte na atrapach, w którym zalogowanym użytkownikiem jest `uid` z saldem 500 monet.

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*CalendarViewModelTest*"
```

Oczekiwany wynik: **FAIL** — `CalendarViewModel` nie istnieje.

- [ ] **Krok 3: Utwórz `CalendarViewModel.kt`**

Przenieś z `StellareViewModel` sekcję oznaczoną komentarzem `--- APPOINTMENTS / CALENDAR STATE & ACTIONS ---` wraz z metodą `bookAppointment` w wersji z Etapu 1, `addFreeSlot`, `cancelAppointmentBooking`, `completeAppointment`, `deleteAppointmentSlot` i `getFreeSlotsForPsychologist`. Nadaj metodom krótsze nazwy zgodnie z blokiem **Interfejsy** powyżej — przedrostek `Appointment` jest zbędny w klasie, która zajmuje się wyłącznie terminami.

- [ ] **Krok 4: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*CalendarViewModelTest*"
```

Oczekiwany wynik: **PASS**.

---

## Zadanie 4: WalletViewModel

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/wallet/WalletViewModel.kt`
- Utwórz: `app/src/test/java/com/example/stellare/wallet/WalletViewModelTest.kt`

**Interfejsy:**
- Konsumuje: `WalletRepository`, `UserRepository`
- Produkuje: `WalletViewModel(walletRepository, userRepository)` ze strumieniami `balance: StateFlow<Int>`, `transactions: StateFlow<List<WalletTransactionModel>>`, `topUpError: StateFlow<String?>` i metodami `topUp(amount: Int)`, `clearTopUpError()`.

- [ ] **Krok 1: Napisz testy**

```kotlin
package com.example.stellare.wallet

import com.example.stellare.data.model.WalletTransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WalletViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `doladowanie podnosi saldo i zapisuje operacje`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        vm.topUp(200)
        advanceUntilIdle()

        assertEquals(300, vm.balance.value)
        assertEquals(1, vm.transactions.value.size)
        assertEquals(WalletTransactionType.TOPUP, vm.transactions.value.first().type)
    }

    @Test
    fun `doladowanie ponad limit jest odrzucane`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        vm.topUp(5000)
        advanceUntilIdle()

        assertEquals(100, vm.balance.value)
        assertNotNull(vm.topUpError.value)
    }

    @Test
    fun `doladowanie kwota niedodatnia jest odrzucane`() = runTest(dispatcher) {
        val vm = walletViewModelZSaldem(100)
        vm.topUp(0)
        advanceUntilIdle()

        assertEquals(100, vm.balance.value)
        assertNotNull(vm.topUpError.value)
    }
}
```

Funkcję `walletViewModelZSaldem(saldo: Int): WalletViewModel` napisz w tym samym pliku — ma składać ViewModel na atrapach z podanym saldem początkowym.

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*WalletViewModelTest*"
```

- [ ] **Krok 3: Utwórz `WalletViewModel.kt`**

Przenieś `walletTransactions` i `topUpWallet` ze starego ViewModelu. Dołóż walidację, której wcześniej nie było, a która odpowiada regule z Etapu 1: kwota musi być dodatnia i nie większa niż 1000. Odrzucenie ustawia `topUpError` na `"Jednorazowo można doładować od 1 do 1000 monet."`.

Metoda `spendCoins` ze starego ViewModelu **znika** — wydawanie monet odbywa się wyłącznie w transakcji rezerwacji z Etapu 1, a osobna ścieżka obchodziłaby zasadę zachowania monet.

- [ ] **Krok 4: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
./gradlew :app:testDebugUnitTest --tests "*WalletViewModelTest*"
```

---

## Zadanie 5: ChatViewModel

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/chat/ChatViewModel.kt`

**Interfejsy:**
- Konsumuje: `ChatRepository`, `UserRepository`
- Produkuje: `ChatViewModel(chatRepository, userRepository)` ze strumieniami `chats: StateFlow<List<ChatModel>>`, `participants: StateFlow<Map<String, UserModel>>` i metodami `messagesFor(chatId): Flow<List<MessageModel>>`, `notesFor(chatId): Flow<List<NoteModel>>`, `send(chatId, text)`, `saveNote(chatId, title, content)`, `deleteNote(noteId)`, `openChatWith(otherUserId): String`.

- [ ] **Krok 1: Utwórz `ChatViewModel.kt`**

Przenieś ze starego ViewModelu: `activeChats`, `chatParticipants` wraz z `loadChatParticipants`, `getMessagesForChat`, `getNotesForChat`, `sendChatMessage`, `saveClientNote`, `deleteClientNote` oraz `startChatWith`.

Jedna zmiana w `startChatWith`: metoda nie nawiguje. Zwraca identyfikator czatu, a o przejściu na ekran decyduje warstwa interfejsu. Dzięki temu ViewModel przestaje zależeć od nawigacji, co było jedną z przyczyn spuchnięcia starej klasy.

```kotlin
    suspend fun openChatWith(otherUserId: String): String {
        val current = userRepository.getCurrentUser() ?: return ""
        val jestPsychologiem = current.role == UserRole.PSYCHOLOGIST ||
            current.role == UserRole.PSYCHOLOGY_STUDENT
        val psychologistId = if (jestPsychologiem) current.id else otherUserId
        val patientId = if (jestPsychologiem) otherUserId else current.id

        chatRepository.getChatByParticipants(psychologistId, patientId)?.let { return it.chatId }

        val nowyId = chatRepository.newChatId()
        chatRepository.createChat(
            ChatModel(
                chatId = nowyId,
                psychologistId = psychologistId,
                patientId = patientId,
                participants = listOf(psychologistId, patientId),
                lastMessage = "Rozpoczęto nowy czat.",
                lastMessageTime = System.currentTimeMillis()
            )
        )
        return nowyId
    }
```

Stary kod generował identyfikator przez `FirebaseFirestore.getInstance().collection("chats").document().id` wprost w ViewModelu. Przenieś to do `ChatRepository.newChatId()`, a stamtąd do `ChatDataSource` — ViewModel nie może dotykać Firebase.

- [ ] **Krok 2: Dopisz `newChatId()` do interfejsu i implementacji**

W `ChatDataSource` (plik `DataSources.kt`) dopisz:

```kotlin
    fun newChatId(): String
```

W `FirestoreChatDataSource`:

```kotlin
    override fun newChatId(): String = collection.document().id
```

W `FakeChatDataSource`:

```kotlin
    private var licznik = 0
    override fun newChatId(): String = "czat-${++licznik}"
```

W `ChatRepository`:

```kotlin
    fun newChatId(): String = chatDataSource.newChatId()
```

- [ ] **Krok 3: Sprawdź kompilację**

```bash
./gradlew :app:compileDebugKotlin
```

---

## Zadanie 6: ProfileViewModel

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/profile/ProfileViewModel.kt`

**Interfejsy:**
- Konsumuje: `UserRepository`, `ReviewRepository`
- Produkuje: `ProfileViewModel(userRepository, reviewRepository)` ze strumieniem `currentUser: StateFlow<UserModel?>` i metodami `updateProfile(...)`, `userById(userId): UserModel?`, `reviewsFor(psychologistId): Flow<List<ReviewModel>>`, `addReview(psychologistId, rating, comment)`.

- [ ] **Krok 1: Utwórz `ProfileViewModel.kt`**

Przenieś ze starego ViewModelu: `currentUser`, `updateProfile`, `getUserById`, `getReviewsForPsychologist` oraz `addReview` w wersji z Etapu 1 (z polem `reviewerId`).

Metoda `publishOffer` **nie jest przenoszona** — jej rolę przejmie ekran „Napisz ogłoszenie" w Etapie 3. Usuń ją wraz z wywołaniami; pola, które nadpisywała, są dostępne przez `updateProfile`.

`updateProfile` w starej wersji kończyła się wywołaniem `navigateBack()`. Usuń je — o cofnięciu decyduje warstwa interfejsu.

- [ ] **Krok 2: Sprawdź kompilację**

```bash
./gradlew :app:compileDebugKotlin
```

---

## Zadanie 7: AuthViewModel

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/auth/AuthViewModel.kt`
- Utwórz: `app/src/test/java/com/example/stellare/auth/AuthViewModelTest.kt`

**Interfejsy:**
- Konsumuje: `UserRepository`, `FirebaseAuthDataSource`
- Produkuje: `AuthViewModel(userRepository, authDataSource)` ze strumieniami `isLoggedIn: StateFlow<Boolean>`, `authError: StateFlow<String?>`, `showTutorialPrompt: StateFlow<Boolean>` i metodami `register(...)`, `login(email, password)`, `signInWithGoogle(credential)`, `buildGoogleSignInRequest(context)`, `logout()`, `clearAuthError()`, `dismissTutorialPrompt()`.

- [ ] **Krok 1: Utwórz `AuthViewModel.kt`**

Przenieś ze starego ViewModelu: `authError`, `clearAuthError`, `registerUser`, `loginUser`, `signInWithGoogle`, `buildGoogleSignInRequest`, `logout`, `showTutorialPrompt` wraz z `setShowTutorialPrompt`.

Usuń wszystkie wywołania `navigateTo(...)`. Zamiast nich wystaw strumień `isLoggedIn`, na który reaguje graf nawigacji:

```kotlin
    val isLoggedIn: StateFlow<Boolean> = userRepository.getCurrentUserFlow()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
```

To jest jedyny ViewModel, któremu wolno importować `com.google.android.libraries.identity.googleid` oraz `com.google.firebase.auth` — obsługa poświadczeń Google nie da się sensownie ukryć za repozytorium.

- [ ] **Krok 2: Napisz test obsługi błędu logowania**

```kotlin
package com.example.stellare.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `czyszczenie bledu zeruje komunikat`() = runTest(dispatcher) {
        val vm = authViewModelNaAtrapach()
        vm.clearAuthError()
        assertNull(vm.authError.value)
    }
}
```

Zakres testów `AuthViewModel` jest celowo wąski — logowanie wymaga prawdziwego Firebase Auth, więc testowalna jest wyłącznie obsługa stanu błędu. Pełne sprawdzenie logowania odbywa się ręcznie w Zadaniu 10.

- [ ] **Krok 3: Uruchom testy**

```bash
./gradlew :app:testDebugUnitTest --tests "*AuthViewModelTest*"
```

---

## Zadanie 8: Navigation Compose

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/navigation/Routes.kt`
- Utwórz: `app/src/main/java/com/example/stellare/ui/navigation/StellareNavHost.kt`
- Zmodyfikuj: `app/build.gradle.kts`

**Interfejsy:**
- Produkuje: obiekt `Routes` ze stałymi tras i funkcjami budującymi trasy z argumentami; `StellareNavHost(navController, container, modifier)`.

- [ ] **Krok 1: Sprawdź, czy `navigation-compose` jest w zależnościach**

```bash
grep -n "navigation.compose" app/build.gradle.kts
```

Jeżeli brak, dopisz do bloku `dependencies`:

```kotlin
  implementation(libs.androidx.navigation.compose)
```

- [ ] **Krok 2: Utwórz `Routes.kt`**

```kotlin
package com.example.stellare.ui.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTRATION = "registration"

    const val FEED = "feed"
    const val CALENDAR = "calendar"
    const val CHATS = "chats"
    const val WALLET = "wallet"

    const val PROFILE_EDIT = "profile/edit"
    const val MY_RATINGS = "ratings"
    const val MY_CLIENTS = "clients"
    const val FAQ = "faq"

    private const val PSYCHOLOGIST_DETAIL_BASE = "psychologist"
    const val PSYCHOLOGIST_DETAIL = "$PSYCHOLOGIST_DETAIL_BASE/{psychologistId}"
    fun psychologistDetail(psychologistId: String) = "$PSYCHOLOGIST_DETAIL_BASE/$psychologistId"

    private const val CHAT_ROOM_BASE = "chat"
    const val CHAT_ROOM = "$CHAT_ROOM_BASE/{chatId}"
    fun chatRoom(chatId: String) = "$CHAT_ROOM_BASE/$chatId"

    val BOTTOM_BAR_ROUTES = listOf(FEED, CALENDAR, CHATS, WALLET)
}
```

Trasy z argumentami mają parę: stałą ze wzorcem dla `composable(...)` i funkcję budującą konkretny adres. Dzięki temu literał ścieżki występuje w kodzie tylko raz.

- [ ] **Krok 3: Utwórz `StellareNavHost.kt`**

Graf ma dwie części: sesyjną (`onboarding`, `login`, `registration`) i zalogowaną (reszta). Punkt startowy zależy od `AuthViewModel.isLoggedIn`.

```kotlin
package com.example.stellare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType

@Composable
fun StellareNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.ONBOARDING) { /* OnboardingScreen */ }
        composable(Routes.LOGIN) { /* LoginScreen */ }
        composable(Routes.REGISTRATION) { /* RegistrationScreen */ }

        composable(Routes.FEED) { /* ekran ogłoszeń */ }
        composable(Routes.CALENDAR) { /* CalendarScreen */ }
        composable(Routes.CHATS) { /* ChatListScreen */ }
        composable(Routes.WALLET) { /* WalletScreen */ }

        composable(Routes.PROFILE_EDIT) { /* ProfileEditScreen */ }
        composable(Routes.MY_RATINGS) { /* MyRatingsScreen */ }
        composable(Routes.MY_CLIENTS) { /* MyClientsScreen */ }
        composable(Routes.FAQ) { /* FAQScreen */ }

        composable(
            route = Routes.PSYCHOLOGIST_DETAIL,
            arguments = listOf(navArgument("psychologistId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("psychologistId").orEmpty()
            /* PsychologistDetailScreen(psychologistId = id) */
        }

        composable(
            route = Routes.CHAT_ROOM,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("chatId").orEmpty()
            /* ChatRoomScreen(chatId = id) */
        }
    }
}
```

**Uwaga dla wykonawcy:** komentarze w miejscach ekranów uzupełnij wywołaniami istniejących funkcji z `ui/`, przekazując właściwy ViewModel. Sygnatury ekranów przyjmują dziś `viewModel: StellareViewModel` — zmień je na konkretny ViewModel, którego dany ekran naprawdę potrzebuje. Rób to ekran po ekranie, sprawdzając kompilację po każdym.

**Dwie pułapki wykryte w review Zadania 2 — przeczytaj przed dotknięciem ekranu ogłoszeń:**

**1. `updateFilters` wyzeruje wpisane wyszukiwanie.** Stary `updateFilters(ageMin, ageMax, gender, spec, verifiedOnly, priceMax, minRating)` **celowo nie dotykał** pola `query`, więc wpisany tekst przeżywał kliknięcie „Zastosuj filtry". Nowy `updateFilters(FeedFilters)` przyjmuje cały obiekt. Naiwne przepisanie wywołania w `DashboardComponents.kt:120` na `FeedFilters(ageMin = ..., ...)` skorzysta z domyślnego `query = ""` i **skasuje wyszukiwanie przy każdym zastosowaniu filtrów**.

Poprawnie:

```kotlin
viewModel.updateFilters(
    viewModel.filters.value.copy(
        ageMin = ..., ageMax = ..., gender = ..., specialization = ...,
        verifiedOnly = ..., priceMax = ..., minRating = ...
    )
)
```

**2. `isRefreshing` zniknęło, a ekran z niego korzysta.** `MainDashboardScreen.kt` (linie 41, 172, 272) używa go do sterowania pełnoekranowym wskaźnikiem ładowania, który **zastępuje listę** na czas odświeżania, a przycisk odświeżania woła `refreshData()`. `FeedViewModel` nie ma odpowiednika żadnego z nich — dane płyną strumieniowo z Firestore, więc sztuczne opóźnienie 800 ms nie miało czego pokazywać.

Ten fragment ekranu wymaga przeprojektowania, nie podmiany: usuń wskaźnik i przycisk odświeżania albo zamień przycisk na `resetFilters()` bez stanu ładowania. Wybierz jedno i opisz w raporcie.

- [ ] **Krok 4: Sprawdź kompilację**

```bash
./gradlew :app:compileDebugKotlin
```

---

## Zadanie 9: Powłoka — dolny pasek i arkusz profilu

**Pliki:**
- Utwórz: `app/src/main/java/com/example/stellare/ui/shell/BottomBar.kt`
- Utwórz: `app/src/main/java/com/example/stellare/ui/shell/ProfileSheet.kt`
- Utwórz: `app/src/main/java/com/example/stellare/ui/shell/StellareShell.kt`

**Interfejsy:**
- Konsumuje: `Routes` z Zadania 8
- Produkuje: `StellareShell(container: AppContainer)` — jedyny punkt wejścia z `MainActivity`.

- [ ] **Krok 1: Utwórz `BottomBar.kt`**

Pasek pokazuje cztery miejsca. Ukrywa się na ekranach sesyjnych i na ekranach szczegółowych.

```kotlin
package com.example.stellare.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.stellare.ui.navigation.Routes

private data class PozycjaPaska(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val POZYCJE = listOf(
    PozycjaPaska(Routes.FEED, "Ogłoszenia", Icons.Filled.Home),
    PozycjaPaska(Routes.CALENDAR, "Kalendarz", Icons.Filled.CalendarMonth),
    PozycjaPaska(Routes.CHATS, "Czaty", Icons.Filled.Chat),
    PozycjaPaska(Routes.WALLET, "Portfel", Icons.Filled.AccountBalanceWallet)
)

@Composable
fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        POZYCJE.forEach { pozycja ->
            NavigationBarItem(
                selected = currentRoute == pozycja.route,
                onClick = { onNavigate(pozycja.route) },
                icon = { Icon(pozycja.icon, contentDescription = pozycja.label) },
                label = { Text(pozycja.label) }
            )
        }
    }
}
```

- [ ] **Krok 2: Utwórz `ProfileSheet.kt`**

Arkusz z pozycjami, które dotąd były w szufladzie: `Mój profil`, `Ulubione`, `Moje oceny`, `Moi klienci`, `FAQ`, `Wyloguj`. Pozycja `Panel weryfikacji` pojawia się wyłącznie, gdy `user.isAdmin` — sam ekran powstaje w Etapie 4, więc na razie wskaż trasę zastępczą albo ukryj pozycję za stałą `false` z komentarzem wskazującym Etap 4.

Użyj `ModalBottomSheet` z Material 3.

- [ ] **Krok 3: Utwórz `StellareShell.kt`**

Składa całość: `Scaffold` z dolnym paskiem pokazywanym tylko dla `Routes.BOTTOM_BAR_ROUTES`, górny pasek z awatarem otwierającym arkusz, `StellareNavHost` w środku.

```kotlin
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val pokazPasek = currentRoute in Routes.BOTTOM_BAR_ROUTES
```

Przy przełączaniu zakładek użyj wzorca zapobiegającego narastaniu stosu:

```kotlin
    navController.navigate(route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
```

- [ ] **Krok 4: Sprawdź kompilację**

```bash
./gradlew :app:compileDebugKotlin
```

---

## Zadanie 10: Usunięcie starego szkieletu

**Pliki:**
- Zmodyfikuj: `app/src/main/java/com/example/stellare/MainActivity.kt`
- Usuń: `app/src/main/java/com/example/stellare/ui/StellareViewModel.kt`
- Usuń: `app/src/main/java/com/example/stellare/ui/StellareApp.kt`
- Usuń: `app/src/main/java/com/example/stellare/ui/navigation/Screen.kt`

- [ ] **Krok 1: Przepisz `MainActivity.kt`**

```kotlin
package com.example.stellare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.stellare.di.AppContainer
import com.example.stellare.ui.shell.StellareShell
import com.example.stellare.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StellareShell(container = container)
                }
            }
        }
    }
}
```

- [ ] **Krok 2: Usuń stare pliki**

```bash
rm app/src/main/java/com/example/stellare/ui/StellareViewModel.kt
rm app/src/main/java/com/example/stellare/ui/StellareApp.kt
rm app/src/main/java/com/example/stellare/ui/navigation/Screen.kt
```

- [ ] **Krok 3: Znajdź pozostałe odwołania**

```bash
grep -rn "StellareViewModel\|StellareApp\|navigation.Screen" app/src --include=*.kt
```

Każde trafienie zamień na właściwy ViewModel albo na `Routes`. Wynik powinien być pusty.

- [ ] **Krok 4: Sprawdź kompilację i testy**

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Oczekiwany wynik: BUILD SUCCESSFUL i wszystkie testy jednostkowe PASS.

- [ ] **Krok 5: Sprawdź działanie na urządzeniu**

Zbuduj, zainstaluj i potwierdź ręcznie:

1. Uruchomienie aplikacji bez zalogowania prowadzi do ekranu powitalnego.
2. Rejestracja i logowanie działają, po zalogowaniu pojawia się ekran ogłoszeń z dolnym paskiem.
3. Cztery zakładki paska przełączają się, a przycisk cofania nie zamyka aplikacji z drugiej zakładki.
4. Ikona profilu otwiera arkusz, wszystkie jego pozycje prowadzą do właściwych ekranów.
5. Kliknięcie psychologa otwiera jego profil, a z niego da się wejść na czat.
6. Rezerwacja terminu działa, a przy braku monet pojawia się komunikat.
7. Wylogowanie wraca na ekran powitalny i czyści stos nawigacji.

- [ ] **Krok 6: Sprawdź, czy granice zostały dotrzymane**

```bash
wc -l app/src/main/java/com/example/stellare/ui/*/[A-Z]*ViewModel.kt
grep -rn "com.google.firebase" app/src/main/java/com/example/stellare/ui --include=*.kt
```

Żaden ViewModel nie może przekraczać 200 linii. Jedyne dozwolone trafienie w drugim poleceniu to `AuthViewModel`.

---

## Definicja ukończenia

- [ ] `StellareViewModel`, `StellareApp` i `Screen` nie istnieją
- [ ] Sześć ViewModeli, każdy poniżej 200 linii
- [ ] Żaden ViewModel poza `AuthViewModel` nie importuje Firebase
- [ ] `./gradlew :app:testDebugUnitTest` przechodzi
- [ ] Nawigacja działa na `NavHost`, dolny pasek pokazuje cztery zakładki
- [ ] Arkusz profilu zawiera pozostałe pozycje
- [ ] Ręczny test z Zadania 10 przechodzi w całości
- [ ] Zestaw funkcji widoczny dla użytkownika nie zmienił się

---

## Czego ten plan świadomie nie obejmuje

**Wyglądu ekranów.** Powstaje wyłącznie powłoka — dolny pasek i arkusz. Przepisanie kart ogłoszeń, kalendarza i pozostałych ekranów pod referencję graficzną to Etap 6.

**Ekranu ogłoszeń.** `Routes.FEED` wskazuje na razie istniejący `MainDashboardScreen`. Prawdziwe ogłoszenia z dwiema zakładkami powstają w Etapie 3.

**Testów interfejsu.** Roborazzi i Robolectric są w katalogu wersji, ale ich uruchomienie to osobne zadanie. Tutaj testujemy wyłącznie ViewModele.
