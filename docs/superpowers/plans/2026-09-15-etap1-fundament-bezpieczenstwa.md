# Etap 1 — Fundament bezpieczeństwa — plan wdrożenia

> **Dla wykonawców agentowych:** WYMAGANY SUB-SKILL: użyj `superpowers:subagent-driven-development` (zalecane) albo `superpowers:executing-plans`, żeby wykonać ten plan zadanie po zadaniu. Kroki mają składnię checkboxów (`- [ ]`).

**Cel:** Zamknąć cztery luki bezpieczeństwa opisane w specyfikacji, tak żeby klient Androida przestał być zaufaną stroną — bez Cloud Functions, wyłącznie regułami Firestore i transakcjami po stronie klienta.

**Architektura:** Reguły bezpieczeństwa Firestore stają się jedyną granicą zaufania. Testujemy je metodą TDD na emulatorze Firestore przy użyciu `@firebase/rules-unit-testing` — każdy test najpierw przechodzi na złych regułach (dowodząc luki), potem regułę dopisujemy i test zaczyna blokować atak. Przelew monet egzekwuje funkcja `getAfter()`, która w regułach widzi stan innego dokumentu po zapisie, dzięki czemu cała operacja jest atomowa i weryfikowalna bez serwera.

**Stos:** Firestore Security Rules (`rules_version = '2'`), Node 23.11.1, `firebase-tools`, `@firebase/rules-unit-testing` v3, Vitest, emulator Firestore (wymaga JDK — dostępny w `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`). Zmiany w kliencie: Kotlin 2.2.10 / Firestore KTX.

**Specyfikacja:** [docs/superpowers/specs/2026-09-15-stellare-design.md](../specs/2026-09-15-stellare-design.md)

## Ograniczenia globalne

- **Plan Firebase: Spark (darmowy).** Żadne rozwiązanie nie może wymagać Cloud Functions ani Firebase Storage.
- **Projekt Firebase:** `stellare-a39ef`. Testy działają na lokalnym emulatorze pod identyfikatorem `stellare-test` i **nigdy** nie dotykają prawdziwego projektu.
- **`rules_version = '2'`** — wymagane przez `getAfter()` i `diff().affectedKeys()`.
- **Dane nie są kasowane.** Żadne zadanie nie usuwa ani nie modyfikuje istniejących dokumentów w prawdziwym projekcie.
- **Nazewnictwo:** enumy i stałe po angielsku (zgodnie z `UserRole` w istniejącym kodzie), komentarze i treści dla użytkownika po polsku.
- **Kolekcje objęte regułami:** `users`, `admins`, `appointments`, `walletTransactions`, `chats`, `messages`, `notes`, `reviews`, `favorites`, `verificationRequests`, `posts`. Wszystko spoza tej listy musi być domyślnie zablokowane.
- **Bez commitów.** Zgodnie ze stałą zasadą właściciela repozytorium żadne zadanie nie wykonuje `git commit` ani `git push`. Po zakończeniu zadania zostaw zmiany w drzewie roboczym i przejdź dalej.
- **Emulator wymaga JDK 17 na `PATH`, nie w `JAVA_HOME`.** Zweryfikowane w źródłach: `firebase-tools` nie odczytuje `JAVA_HOME` — wywołuje `java` wprost z `PATH`, a domyślna java w tym środowisku to Temurin 1.8, za stara. Każde uruchomienie testów poprzedź:

  ```bash
  export PATH="/c/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin:$PATH"
  ```

  Bez tego `npm test` kończy się komunikatem „firebase-tools no longer supports Java versions before 11".

---

## Struktura plików

Cała warstwa reguł mieszka w nowym katalogu `firebase/` w korzeniu repozytorium — odseparowana od projektu Gradle, z własnym `package.json`. Aplikacja Androida nie wie o jej istnieniu.

| Plik | Odpowiedzialność |
| --- | --- |
| `firebase/firebase.json` | Konfiguracja emulatora i wskazanie pliku reguł |
| `firebase/.firebaserc` | Powiązanie z projektem `stellare-a39ef` przy wdrożeniu |
| `firebase/firestore.rules` | **Jedyne** źródło reguł bezpieczeństwa |
| `firebase/package.json` | Zależności testowe i skrypt `test` |
| `firebase/vitest.config.js` | Konfiguracja Vitest (sekwencyjnie, jeden wątek) |
| `firebase/test/helpers.js` | Wspólne tworzenie środowiska testowego i kontekstów użytkowników |
| `firebase/test/users.test.js` | Przywileje na dokumencie użytkownika |
| `firebase/test/appointments.test.js` | Cykl życia terminu i podwójna rezerwacja |
| `firebase/test/wallet.test.js` | Zachowanie monet przy przelewie |
| `firebase/test/collections.test.js` | Pozostałe kolekcje i domyślna blokada |

Zmiany po stronie klienta Androida:

| Plik | Zmiana |
| --- | --- |
| `app/src/main/java/com/example/stellare/data/model/AppointmentModel.kt` | `+ priceCoins` |
| `app/src/main/java/com/example/stellare/data/model/WalletTransactionModel.kt` | `+ type`, `+ counterpartyId`, `+ appointmentId` |
| `app/src/main/java/com/example/stellare/data/model/UserModel.kt` | `+ isAdmin` |
| `app/src/main/java/com/example/stellare/data/remote/FirestoreAppointmentDataSource.kt` | `bookAppointment` przechodzi na transakcję obejmującą oba portfele |
| `app/src/main/java/com/example/stellare/ui/StellareViewModel.kt` | Usunięcie samodzielnej weryfikacji, przekazanie ceny psychologa |

---

## Zadanie 1: Środowisko testowe reguł

Zadanie kończy się działającym testem, który **przechodzi** — dowodząc, że przy braku reguł każdy może zapisać cokolwiek. To jest nasz czerwony stan: dowód luki.

**Pliki:**
- Utwórz: `firebase/package.json`
- Utwórz: `firebase/firebase.json`
- Utwórz: `firebase/.firebaserc`
- Utwórz: `firebase/vitest.config.js`
- Utwórz: `firebase/firestore.rules`
- Utwórz: `firebase/test/helpers.js`
- Utwórz: `firebase/test/smoke.test.js`
- Zmodyfikuj: `.gitignore`

**Interfejsy:**
- Produkuje: `firebase/test/helpers.js` eksportuje `setupEnv(): Promise<RulesTestEnvironment>`, `authedDb(env, uid): Firestore`, `unauthedDb(env): Firestore`, `seed(env, fn): Promise<void>` — używane przez wszystkie kolejne pliki testowe.

- [ ] **Krok 1: Utwórz `firebase/package.json`**

```json
{
  "name": "stellare-firestore-rules",
  "private": true,
  "type": "module",
  "scripts": {
    "test": "firebase emulators:exec --only firestore \"vitest run\"",
    "test:watch": "firebase emulators:exec --only firestore \"vitest\"",
    "deploy": "firebase deploy --only firestore:rules"
  },
  "devDependencies": {
    "@firebase/rules-unit-testing": "^3.0.4",
    "firebase": "^11.0.0",
    "firebase-tools": "^13.29.1",
    "vitest": "^2.1.8"
  }
}
```

- [ ] **Krok 2: Utwórz `firebase/firebase.json`**

```json
{
  "firestore": {
    "rules": "firestore.rules"
  },
  "emulators": {
    "firestore": {
      "port": 8080
    },
    "ui": {
      "enabled": false
    },
    "singleProjectMode": true
  }
}
```

- [ ] **Krok 3: Utwórz `firebase/.firebaserc`**

```json
{
  "projects": {
    "default": "stellare-a39ef"
  }
}
```

- [ ] **Krok 4: Utwórz `firebase/vitest.config.js`**

Testy reguł dzielą jedną instancję emulatora, więc muszą iść sekwencyjnie — równoległe pliki nadpisywałyby sobie dane.

```javascript
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    fileParallelism: false,
    sequence: { concurrent: false },
    testTimeout: 20000,
    hookTimeout: 30000,
  },
})
```

- [ ] **Krok 5: Utwórz `firebase/firestore.rules` z regułami celowo dziurawymi**

To jest stan wyjściowy odzwierciedlający dzisiejszą rzeczywistość: brak reguł oznacza pełną swobodę.

```
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if true;
    }
  }
}
```

- [ ] **Krok 6: Utwórz `firebase/test/helpers.js`**

```javascript
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { initializeTestEnvironment } from '@firebase/rules-unit-testing'

const here = path.dirname(fileURLToPath(import.meta.url))
const rulesPath = path.join(here, '..', 'firestore.rules')

export async function setupEnv() {
  return initializeTestEnvironment({
    projectId: 'stellare-test',
    firestore: {
      rules: fs.readFileSync(rulesPath, 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  })
}

export function authedDb(env, uid) {
  return env.authenticatedContext(uid).firestore()
}

export function unauthedDb(env) {
  return env.unauthenticatedContext().firestore()
}

export function seed(env, fn) {
  return env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()))
}
```

- [ ] **Krok 7: Utwórz `firebase/test/smoke.test.js` — test dowodzący luki**

```javascript
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc } from 'firebase/firestore'
import { authedDb, setupEnv } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => { await env.clearFirestore() })

describe('stan wyjściowy', () => {
  it('bez reguł obcy użytkownik może nadpisać cudzy profil', async () => {
    const db = authedDb(env, 'napastnik')
    await assertSucceeds(
      setDoc(doc(db, 'users/ofiara'), { isVerified: true, coinsBalance: 999999 }),
    )
  })
})
```

- [ ] **Krok 8: Zainstaluj zależności**

Uruchom z katalogu `firebase/`:

```bash
npm install
```

- [ ] **Krok 9: Uruchom test i potwierdź, że PRZECHODZI**

```bash
cd firebase && npm test
```

Oczekiwany wynik: **PASS**. Przejście tego testu to dowód, że luka istnieje. Emulator pobierze się przy pierwszym uruchomieniu (kilkadziesiąt megabajtów).

Instalacja wymaga flagi zgodności — `@firebase/rules-unit-testing` deklaruje peer `firebase@^10`, a my używamy `^11`:

```bash
cd firebase && npm install --legacy-peer-deps
```

Trwałe rozwiązanie: plik `firebase/.npmrc` z jedną linią `legacy-peer-deps=true`, dzięki czemu zwykłe `npm install` wystarcza.

- [ ] **Krok 10: Dopisz do `.gitignore`**

Dopisz na końcu pliku `.gitignore` w korzeniu repozytorium:

```
firebase/node_modules
firebase/.firebase
firebase-debug.log
firestore-debug.log
ui-debug.log
```

---

## Zadanie 2: Przywileje na dokumencie użytkownika

Zamyka lukę 1 (samodzielna weryfikacja) i połowę luki 2 (produkcja monet).

**Pliki:**
- Utwórz: `firebase/test/users.test.js`
- Zmodyfikuj: `firebase/firestore.rules`

**Interfejsy:**
- Konsumuje: `setupEnv`, `authedDb`, `seed` z `firebase/test/helpers.js` (Zadanie 1)
- Produkuje: funkcje pomocnicze w regułach — `isSignedIn()`, `isAdmin()`, `unchanged(field)`, `onlyChanged(fields)` — używane przez Zadania 3, 4 i 5.

- [ ] **Krok 1: Napisz testy, które muszą padać**

Utwórz `firebase/test/users.test.js`:

```javascript
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, updateDoc } from 'firebase/firestore'
import { authedDb, seed, setupEnv } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: false,
      isAdmin: false, coinsBalance: 0, firstName: 'Ala', pricePerSession: 80,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false,
      isAdmin: false, coinsBalance: 500, firstName: 'Bogdan',
    })
    await setDoc(doc(db, 'admins/szef'), {})
  })
})

describe('users — przywileje', () => {
  it('nie pozwala samemu ustawić isVerified', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { isVerified: true }))
  })

  it('nie pozwala samemu zmienić roli', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { role: 'PSYCHOLOGIST' }))
  })

  it('nie pozwala samemu zostać administratorem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { isAdmin: true }))
  })

  it('pozwala edytować własne dane opisowe', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'users/psycholog'), { firstName: 'Alicja', bio: 'nowy opis' }))
  })

  it('nie pozwala edytować cudzego profilu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { firstName: 'Zmieniony' }))
  })

  it('pozwala administratorowi zatwierdzić weryfikację', async () => {
    const db = authedDb(env, 'szef')
    await assertSucceeds(updateDoc(doc(db, 'users/psycholog'), { isVerified: true }))
  })

  it('zakłada konto tylko niezweryfikowane i z zerowym saldem', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: true, isAdmin: false, coinsBalance: 0,
    }))
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 10000,
    }))
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    }))
  })

  it('nie pozwala nikomu pisać do kolekcji admins', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'admins/pacjent'), {}))
  })
})
```

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
cd firebase && npx vitest run test/users.test.js
```

Emulator musi działać. Jeśli nie działa, użyj pełnego polecenia:

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/users.test.js"
```

Oczekiwany wynik: **FAIL** na wszystkich testach `assertFails` — reguła `allow read, write: if true` wciąż wpuszcza wszystko.

- [ ] **Krok 3: Zastąp zawartość `firebase/firestore.rules`**

```
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {

    function isSignedIn() {
      return request.auth != null;
    }

    function isAdmin() {
      return isSignedIn()
        && exists(/databases/$(database)/documents/admins/$(request.auth.uid));
    }

    function unchanged(field) {
      return request.resource.data[field] == resource.data[field];
    }

    function onlyChanged(fields) {
      return request.resource.data.diff(resource.data).affectedKeys().hasOnly(fields);
    }

    // Lista administratorów. Wpisy dodaje się wyłącznie ręcznie z konsoli Firebase.
    match /admins/{uid} {
      allow read: if isAdmin();
      allow write: if false;
    }

    match /users/{uid} {
      allow read: if isSignedIn();

      // Nowe konto zawsze startuje niezweryfikowane, bez uprawnień i bez monet.
      allow create: if isSignedIn()
        && request.auth.uid == uid
        && request.resource.data.isVerified == false
        && request.resource.data.get('isAdmin', false) == false
        && request.resource.data.get('coinsBalance', 0) == 0;

      // Administrator rozstrzyga o weryfikacji i rolach.
      allow update: if isAdmin();

      // Właściciel edytuje swoje dane, ale nie pola przywilejów.
      allow update: if isSignedIn()
        && request.auth.uid == uid
        && unchanged('isVerified')
        && unchanged('role')
        && unchanged('isAdmin');

      allow delete: if isSignedIn() && request.auth.uid == uid;
    }

    // Wszystko, co nie zostało wymienione powyżej, jest zablokowane.
    match /{document=**} {
      allow read, write: if false;
    }
  }
}
```

- [ ] **Krok 4: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/users.test.js"
```

Oczekiwany wynik: **PASS** — wszystkie osiem testów.

- [ ] **Krok 5: Usuń test dowodzący luki**

Usuń plik `firebase/test/smoke.test.js` — spełnił swoje zadanie i od teraz będzie padał, bo reguły przestały wpuszczać obcych.

```bash
rm firebase/test/smoke.test.js
```

- [ ] **Krok 6: Uruchom cały zestaw**

```bash
cd firebase && npm test
```

Oczekiwany wynik: **PASS**.

---

## Zadanie 3: Cykl życia terminu i podwójna rezerwacja

Zamyka lukę 3.

**Pliki:**
- Utwórz: `firebase/test/appointments.test.js`
- Zmodyfikuj: `firebase/firestore.rules`

**Interfejsy:**
- Konsumuje: `isSignedIn()`, `onlyChanged()` z Zadania 2
- Produkuje: reguła bloku `match /appointments/{id}` — Zadanie 4 dopisze do niej warunek na `priceCoins`.

- [ ] **Krok 1: Napisz testy, które muszą padać**

Utwórz `firebase/test/appointments.test.js`:

```javascript
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'
import { authedDb, seed, setupEnv } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'users/intruz'), {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 80,
    })
  })
})

const wolnyTermin = {
  appointmentId: 'nowy', psychologistId: 'psycholog', patientId: null,
  date: '2026-10-02', time: '11:00', notes: '', status: 'FREE', priceCoins: 80,
}

describe('appointments — cykl życia', () => {
  it('psycholog może wystawić własny wolny termin', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'appointments/nowy'), wolnyTermin))
  })

  it('pacjent nie może wystawić terminu w imieniu psychologa', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'appointments/nowy'), wolnyTermin))
  })

  it('psycholog nie może wystawić terminu od razu zarezerwowanego', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/nowy'), {
      ...wolnyTermin, status: 'BOOKED', patientId: 'pacjent',
    }))
  })

  it('pacjent nie może zarezerwować terminu na kogoś innego', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'intruz',
    }))
  })

  it('pacjent nie może podmienić daty przy rezerwacji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'pacjent', date: '2026-12-24',
    }))
  })

  it('nie da się zarezerwować terminu już zajętego', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent' })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'intruz',
    }))
  })

  it('psycholog może usunąć własny wolny termin', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(deleteDoc(doc(db, 'appointments/termin1')))
  })

  it('psycholog nie może usunąć terminu już zarezerwowanego', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent' })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(deleteDoc(doc(db, 'appointments/termin1')))
  })

  it('obca osoba nie może dotknąć cudzego terminu', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), { notes: 'cokolwiek' }))
  })
})
```

Uwaga: rezerwacja w prawdziwej aplikacji zmienia równocześnie oba portfele. Tutaj testujemy wyłącznie warstwę terminu — test „pacjent może zarezerwować wolny termin" trafia do Zadania 4, gdzie rezerwacja jest już kompletną operacją wielodokumentową.

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/appointments.test.js"
```

Oczekiwany wynik: **FAIL** — brak bloku `appointments` oznacza, że łapie je domyślna blokada i nawet dozwolone operacje są odrzucane.

- [ ] **Krok 3: Dopisz blok `appointments` do `firebase/firestore.rules`**

Wstaw poniższy blok **przed** końcowym `match /{document=**}`:

```
    match /appointments/{id} {
      allow read: if isSignedIn();

      // Termin wystawia wyłącznie psycholog, zawsze jako wolny.
      allow create: if isSignedIn()
        && request.resource.data.psychologistId == request.auth.uid
        && request.resource.data.status == 'FREE'
        && request.resource.data.patientId == null;

      // Rezerwacja: tylko z wolnego terminu, tylko na siebie,
      // bez podmiany psychologa, daty ani godziny.
      allow update: if isSignedIn()
        && resource.data.status == 'FREE'
        && request.resource.data.status == 'BOOKED'
        && request.resource.data.patientId == request.auth.uid
        && request.resource.data.psychologistId == resource.data.psychologistId
        && request.resource.data.date == resource.data.date
        && request.resource.data.time == resource.data.time
        && request.resource.data.priceCoins == resource.data.priceCoins;

      // Odwołanie lub zakończenie — wyłącznie przez stronę umowy.
      allow update: if isSignedIn()
        && (request.auth.uid == resource.data.psychologistId
            || request.auth.uid == resource.data.patientId)
        && request.resource.data.status in ['FREE', 'CANCELLED', 'COMPLETED']
        && request.resource.data.psychologistId == resource.data.psychologistId
        && request.resource.data.date == resource.data.date
        && request.resource.data.time == resource.data.time;

      // Skasować można tylko własny, niezajęty termin.
      allow delete: if isSignedIn()
        && request.auth.uid == resource.data.psychologistId
        && resource.data.status == 'FREE';
    }
```

- [ ] **Krok 4: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/appointments.test.js"
```

Oczekiwany wynik: **PASS** — wszystkie dziewięć testów.

---

## Zadanie 4: Zachowanie monet przy przelewie

Zamyka drugą połowę luki 2 i lukę 4. To jest sedno całego etapu.

**Pliki:**
- Utwórz: `firebase/test/wallet.test.js`
- Zmodyfikuj: `firebase/firestore.rules`

**Interfejsy:**
- Konsumuje: `isSignedIn()`, `onlyChanged()` z Zadania 2; blok `appointments` z Zadania 3
- Produkuje: stała `MAX_TOPUP = 1000` zaszyta w regule; blok `match /walletTransactions/{id}`.

- [ ] **Krok 1: Napisz testy, które muszą padać**

Utwórz `firebase/test/wallet.test.js`:

```javascript
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 80,
    })
  })
})

describe('portfel — zachowanie monet', () => {
  it('nie pozwala dopisać monet obcej osobie bez pokrycia', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { coinsBalance: 999999 }))
  })

  it('nie pozwala doładować sobie ponad limit jednorazowy', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 500 + 1001 }))
  })

  it('pozwala doładować sobie w granicach limitu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 500 + 1000 }))
  })

  it('przepuszcza rezerwację, gdy monety się bilansują', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: 'pierwsza sesja' })
    batch.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 80 })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 80 })
    batch.set(doc(db, 'walletTransactions/t1'), {
      transactionId: 't1', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Opłata za wizytę', timestamp: Date.now(),
    })
    batch.set(doc(db, 'walletTransactions/t2'), {
      transactionId: 't2', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wpływ za wizytę', timestamp: Date.now(),
    })
    await assertSucceeds(batch.commit())
  })

  it('odrzuca rezerwację, w której psycholog dostaje więcej, niż zapłacił pacjent', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    batch.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 80 })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 5000 })
    await assertFails(batch.commit())
  })

  it('odrzuca dopisanie monet psychologowi bez ruchu po stronie pacjenta', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 80 })
    await assertFails(batch.commit())
  })

  it('księga operacji jest niezmienna', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'walletTransactions/t1'), {
        transactionId: 't1', userId: 'pacjent', counterpartyId: 'psycholog',
        appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
        title: 'Opłata za wizytę', timestamp: Date.now(),
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'walletTransactions/t1'), { amount: 0 }))
  })

  it('nie pozwala czytać cudzych operacji', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'walletTransactions/t9'), {
        transactionId: 't9', userId: 'psycholog', counterpartyId: null,
        appointmentId: null, amount: 100, type: 'TOPUP',
        title: 'Doładowanie', timestamp: Date.now(),
      })
    })
    const { getDoc } = await import('firebase/firestore')
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'walletTransactions/t9')))
  })
})
```

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/wallet.test.js"
```

Oczekiwany wynik: **FAIL**.

- [ ] **Krok 3: Zmień blok `users` — dodaj zasadę zachowania monet**

W `firebase/firestore.rules` zastąp cały blok `match /users/{uid}` poniższym. Zmiany wobec Zadania 2: doszły funkcje `topUpWithinLimit()` i `receivesPaidTransfer()` oraz reguła pozwalająca zwiększyć cudze saldo wyłącznie z jednoczesnym pokryciem.

```
    match /users/{uid} {
      allow read: if isSignedIn();

      allow create: if isSignedIn()
        && request.auth.uid == uid
        && request.resource.data.isVerified == false
        && request.resource.data.get('isAdmin', false) == false
        && request.resource.data.get('coinsBalance', 0) == 0;

      allow update: if isAdmin();

      // Właściciel: dane opisowe dowolnie, przywileje nigdy,
      // saldo w dół dowolnie, w górę tylko do limitu doładowania.
      allow update: if isSignedIn()
        && request.auth.uid == uid
        && unchanged('isVerified')
        && unchanged('role')
        && unchanged('isAdmin')
        && (unchanged('coinsBalance')
            || request.resource.data.coinsBalance < resource.data.coinsBalance
            || topUpWithinLimit());

      // Obca osoba może zwiększyć czyjeś saldo wyłącznie wtedy,
      // gdy w tym samym zapisie jej własne spada o dokładnie tyle samo.
      allow update: if isSignedIn()
        && request.auth.uid != uid
        && onlyChanged(['coinsBalance'])
        && receivesPaidTransfer();

      allow delete: if isSignedIn() && request.auth.uid == uid;
    }

    // Doładowanie portfela. ŚWIADOME OGRANICZENIE: bez serwera nie da się
    // potwierdzić, że płatność wpłynęła. Limit ogranicza skalę nadużycia.
    function topUpWithinLimit() {
      return request.resource.data.coinsBalance - resource.data.coinsBalance <= 1000;
    }

    // Zasada zachowania monet: ile przybywa odbiorcy, tyle ubywa płacącemu
    // w tym samym zatwierdzeniu. getAfter() widzi stan po zapisie.
    function receivesPaidTransfer() {
      let kwota = request.resource.data.coinsBalance - resource.data.coinsBalance;
      let placacyPrzed = get(/databases/$(database)/documents/users/$(request.auth.uid)).data.coinsBalance;
      let placacyPo = getAfter(/databases/$(database)/documents/users/$(request.auth.uid)).data.coinsBalance;
      return kwota > 0 && placacyPo == placacyPrzed - kwota;
    }
```

Uwaga dla wykonawcy: funkcje w regułach Firestore muszą być zadeklarowane w tym samym bloku `match`, w którym są używane, albo wyżej. Umieść `topUpWithinLimit()` i `receivesPaidTransfer()` bezpośrednio po bloku `match /users/{uid}`, wewnątrz `match /databases/{database}/documents`.

- [ ] **Krok 4: Dopisz blok `walletTransactions`**

Wstaw przed końcowym `match /{document=**}`:

```
    match /walletTransactions/{id} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;

      allow create: if isSignedIn()
        && (request.resource.data.userId == request.auth.uid
            || request.resource.data.counterpartyId == request.auth.uid);

      // Księga jest niezmienna — żadnych poprawek ani usuwania.
      allow update, delete: if false;
    }
```

- [ ] **Krok 5: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/wallet.test.js"
```

Oczekiwany wynik: **PASS** — wszystkie osiem testów.

- [ ] **Krok 6: Uruchom cały zestaw**

```bash
cd firebase && npm test
```

Oczekiwany wynik: **PASS** — testy z Zadań 2, 3 i 4.

---

## Zadanie 5: Pozostałe kolekcje

Domyślna blokada z Zadania 2 odcięła czaty, wiadomości, notatki, recenzje i ulubione. Trzeba je przywrócić — z właściwymi ograniczeniami. Przy okazji zakładamy reguły dla kolekcji, które powstaną w kolejnych etapach.

**Pliki:**
- Utwórz: `firebase/test/collections.test.js`
- Zmodyfikuj: `firebase/firestore.rules`

**Interfejsy:**
- Konsumuje: `isSignedIn()`, `isAdmin()` z Zadania 2
- Produkuje: bloki `chats`, `messages`, `notes`, `reviews`, `favorites`, `verificationRequests`, `posts` — używane przez Etapy 3 i 4.

- [ ] **Krok 1: Napisz testy, które muszą padać**

Utwórz `firebase/test/collections.test.js`:

```javascript
import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc } from 'firebase/firestore'
import { authedDb, seed, setupEnv, unauthedDb } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, rating: 5.0, ratingCount: 0,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 100,
    })
    await setDoc(doc(db, 'users/intruz'), {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'admins/szef'), {})
    await setDoc(doc(db, 'chats/czat1'), {
      chatId: 'czat1', psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
    })
  })
})

describe('czaty i wiadomości', () => {
  it('uczestnik czyta swój czat', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(getDoc(doc(db, 'chats/czat1')))
  })

  it('osoba spoza czatu go nie czyta', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'chats/czat1')))
  })

  it('uczestnik wysyła wiadomość jako on sam', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'messages/w1'), {
      messageId: 'w1', chatId: 'czat1', senderId: 'pacjent', text: 'dzień dobry', timestamp: Date.now(),
    }))
  })

  it('nie da się podszyć pod nadawcę', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'messages/w2'), {
      messageId: 'w2', chatId: 'czat1', senderId: 'psycholog', text: 'podszycie', timestamp: Date.now(),
    }))
  })

  it('obcy nie wyśle wiadomości do cudzego czatu', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'messages/w3'), {
      messageId: 'w3', chatId: 'czat1', senderId: 'intruz', text: 'wtręt', timestamp: Date.now(),
    }))
  })
})

describe('notatki do sesji', () => {
  it('uczestnik czatu zapisuje notatkę', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'notes/n1'), {
      noteId: 'n1', chatId: 'czat1', title: 'Sesja 1', content: 'praca domowa',
    }))
  })

  it('obcy nie czyta cudzych notatek', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'notes/n1'), {
        noteId: 'n1', chatId: 'czat1', title: 'Sesja 1', content: 'praca domowa',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'notes/n1')))
  })
})

describe('recenzje', () => {
  it('zalogowany wystawia recenzję jako on sam', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'reviews/r1'), {
      reviewId: 'r1', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'polecam',
    }))
  })

  it('nie da się wystawić recenzji w cudzym imieniu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'reviews/r2'), {
      reviewId: 'r2', psychologistId: 'psycholog', reviewerId: 'intruz',
      reviewerName: 'Intruz', rating: 1, comment: 'fałszywka',
    }))
  })

  it('recenzje są publiczne dla zalogowanych', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'reviews/r1'), {
        reviewId: 'r1', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 5, comment: 'polecam',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertSucceeds(getDoc(doc(db, 'reviews/r1')))
  })

  it('recenzja podbija ocenę ocenianego o jedną pozycję', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/psycholog'), { rating: 4.5, ratingCount: 1 }))
  })

  it('nie da się podbić licznika ocen o więcej niż jeden', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { rating: 5, ratingCount: 500 }))
  })

  it('nie da się wystawić oceny spoza skali', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { rating: 99, ratingCount: 1 }))
  })

  it('przy okazji oceny nie da się przemycić zmiany salda', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), {
      rating: 5, ratingCount: 1, coinsBalance: 999999,
    }))
  })
})

describe('zgłoszenia weryfikacyjne', () => {
  it('psycholog składa własne zgłoszenie jako oczekujące', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
      submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
    }))
  })

  it('nie da się złożyć zgłoszenia od razu zatwierdzonego', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'APPROVED',
      submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
    }))
  })

  it('obcy nie czyta cudzego dokumentu', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
        submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'verificationRequests/psycholog')))
  })

  it('administrator czyta i rozstrzyga', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
        submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
      })
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(getDoc(doc(db, 'verificationRequests/psycholog')))
    await assertSucceeds(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'REJECTED', reviewedBy: 'szef', reviewedAt: Date.now(), reviewNote: 'nieczytelny skan',
    }))
  })
})

describe('ogłoszenia', () => {
  it('autor publikuje własne ogłoszenie', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'posts/p1'), {
      id: 'p1', authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia poznawczo-behawioralna', body: 'Zapraszam', tags: ['LEK'],
      priceCoins: 80, createdAt: Date.now(), isActive: true, authorSnapshot: {},
    }))
  })

  it('nie da się opublikować ogłoszenia w cudzym imieniu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'posts/p2'), {
      id: 'p2', authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Podszycie', body: '', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true, authorSnapshot: {},
    }))
  })

  it('obcy nie edytuje cudzego ogłoszenia', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'posts/p1'), {
        id: 'p1', authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: '', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true, authorSnapshot: {},
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'posts/p1'), { title: 'Przejęte' }))
  })
})

describe('blokada domyślna', () => {
  it('niezalogowany nie czyta niczego', async () => {
    const db = unauthedDb(env)
    await assertFails(getDoc(doc(db, 'users/psycholog')))
  })

  it('nieznana kolekcja jest zablokowana', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'cokolwiek/x'), { a: 1 }))
  })
})
```

- [ ] **Krok 2: Uruchom testy i potwierdź, że PADAJĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/collections.test.js"
```

Oczekiwany wynik: **FAIL**.

- [ ] **Krok 3: Dopisz pozostałe bloki do `firebase/firestore.rules`**

Wstaw przed końcowym `match /{document=**}`:

```
    function jestUczestnikiem(chatId) {
      return isSignedIn()
        && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.participants;
    }

    match /chats/{chatId} {
      allow read: if isSignedIn() && request.auth.uid in resource.data.participants;
      allow create: if isSignedIn() && request.auth.uid in request.resource.data.participants;
      allow update: if isSignedIn() && request.auth.uid in resource.data.participants;
      allow delete: if false;
    }

    match /messages/{id} {
      allow read: if jestUczestnikiem(resource.data.chatId);
      allow create: if request.resource.data.senderId == request.auth.uid
        && jestUczestnikiem(request.resource.data.chatId);
      allow update, delete: if false;
    }

    match /notes/{id} {
      allow read: if jestUczestnikiem(resource.data.chatId);
      allow create: if jestUczestnikiem(request.resource.data.chatId);
      allow update, delete: if jestUczestnikiem(resource.data.chatId);
    }

    match /reviews/{id} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() && request.resource.data.reviewerId == request.auth.uid;
      allow update, delete: if isSignedIn() && resource.data.reviewerId == request.auth.uid;
    }

    // Identyfikator dokumentu ma postać "<userId>_<psychologistId>"
    // (patrz FirestoreFavoriteDataSource.saveFavorite), ale warunek opieramy
    // na polu userId — jest odporny na zmianę schematu identyfikatorów.
    match /favorites/{id} {
      allow read: if isSignedIn() && resource.data.userId == request.auth.uid;
      allow create: if isSignedIn() && request.resource.data.userId == request.auth.uid;
      allow update, delete: if isSignedIn() && resource.data.userId == request.auth.uid;
    }

    match /verificationRequests/{uid} {
      allow read: if isAdmin() || (isSignedIn() && request.auth.uid == uid);

      // Zgłoszenie zawsze startuje jako oczekujące.
      allow create: if isSignedIn()
        && request.auth.uid == uid
        && request.resource.data.status == 'PENDING';

      allow update: if isAdmin()
        || (isSignedIn() && request.auth.uid == uid
            && resource.data.status == 'REJECTED'
            && request.resource.data.status == 'PENDING');

      allow delete: if isAdmin();
    }

    match /posts/{id} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() && request.resource.data.authorId == request.auth.uid;
      allow update, delete: if isSignedIn() && resource.data.authorId == request.auth.uid;
    }
```

- [ ] **Krok 4: Rozszerz blok `users` o aktualizację oceny psychologa**

Wystawienie recenzji przelicza średnią ocenę i zapisuje ją **na dokumencie ocenianego psychologa** (`StellareViewModel.addReview`). Po Zadaniu 4 jedyną dozwoloną zmianą na cudzym dokumencie jest przelew monet, więc bez tego kroku każda recenzja kończyłaby się `PERMISSION_DENIED`.

W `firebase/firestore.rules` dopisz **trzecią** regułę `allow update` wewnątrz bloku `match /users/{uid}`, bezpośrednio pod regułą przelewu:

```
      // Recenzja podbija ocenę ocenianego. Liczba ocen musi wzrosnąć
      // dokładnie o jeden, a średnia pozostać w skali od 1 do 5.
      allow update: if isSignedIn()
        && request.auth.uid != uid
        && onlyChanged(['rating', 'ratingCount'])
        && request.resource.data.ratingCount == resource.data.ratingCount + 1
        && request.resource.data.rating >= 1
        && request.resource.data.rating <= 5;
```

Ograniczenie świadome: reguła nie potrafi sprawdzić, czy nowa średnia została policzona uczciwie — brak dostępu do sumy poprzednich ocen. Wymusza jednak, że jedna operacja to dokładnie jedna ocena w prawidłowej skali, więc nadużycie sprowadza się do wystawienia fałszywej recenzji, co jest możliwe także uczciwą drogą.

- [ ] **Krok 5: Uruchom testy i potwierdź, że PRZECHODZĄ**

```bash
cd firebase && npx firebase emulators:exec --only firestore "npx vitest run test/collections.test.js"
```

Oczekiwany wynik: **PASS**.

- [ ] **Krok 6: Uruchom cały zestaw**

```bash
cd firebase && npm test
```

Oczekiwany wynik: **PASS** — komplet testów z Zadań 2–5.

---

## Zadanie 6: Klient — model danych pod nowe reguły

Reguły wymagają pól, których modele jeszcze nie mają. Bez tego zadania aplikacja przestanie działać po wdrożeniu reguł.

**Pliki:**
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/model/AppointmentModel.kt`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/model/WalletTransactionModel.kt`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/model/UserModel.kt`
- Utwórz: `app/src/main/java/com/example/stellare/data/model/WalletTransactionType.kt`

**Interfejsy:**
- Produkuje: `AppointmentModel.priceCoins: Int`, `WalletTransactionModel.type: WalletTransactionType`, `WalletTransactionModel.counterpartyId: String?`, `WalletTransactionModel.appointmentId: String?`, `UserModel.isAdmin: Boolean`, `ReviewModel.reviewerId: String` — używane przez Zadanie 7.

- [ ] **Krok 1: Utwórz `WalletTransactionType.kt`**

```kotlin
package com.example.stellare.data.model

enum class WalletTransactionType {
    TOPUP,
    BOOKING_PAYMENT,
    BOOKING_EARNING,
    REFUND
}
```

- [ ] **Krok 2: Dodaj `priceCoins` do `AppointmentModel.kt`**

Zastąp całą deklarację klasy:

```kotlin
package com.example.stellare.data.model

data class AppointmentModel(
    val appointmentId: String = "",
    val psychologistId: String = "",
    val patientId: String? = null,
    val date: String = "",
    val time: String = "",
    val notes: String = "",
    val status: String = "FREE",
    val priceCoins: Int = 0
)
```

- [ ] **Krok 3: Rozszerz `WalletTransactionModel.kt`**

```kotlin
package com.example.stellare.data.model

data class WalletTransactionModel(
    val transactionId: String = "",
    val userId: String = "",
    val counterpartyId: String? = null,
    val appointmentId: String? = null,
    val amount: Int = 0,
    val type: WalletTransactionType = WalletTransactionType.TOPUP,
    val title: String = "",
    val timestamp: Long = 0L
)
```

- [ ] **Krok 4: Dodaj `isAdmin` do `UserModel.kt`**

W pliku `app/src/main/java/com/example/stellare/data/model/UserModel.kt` dopisz pole bezpośrednio pod `isVerified`:

```kotlin
    val isVerified: Boolean = false,
    val isAdmin: Boolean = false,
```

Dopisz też dwa pola obsługujące deklarację przelewu — reguła zachowania monet wymaga, żeby płacący wskazał odbiorcę we własnym dokumencie (patrz sekcja 5.3 specyfikacji). Bez nich **żadna rezerwacja nie przejdzie przez reguły**:

```kotlin
    val lastTransferTo: String? = null,
    val lastTransferAmount: Int = 0,
```

- [ ] **Krok 5: Dodaj `reviewerId` do `ReviewModel.kt`**

Model przechowuje dziś wyłącznie `reviewerName`, więc nie da się stwierdzić, kto naprawdę wystawił recenzję — reguła z Zadania 5 tego wymaga. Zastąp całą deklarację klasy w `app/src/main/java/com/example/stellare/data/model/ReviewModel.kt`:

```kotlin
package com.example.stellare.data.model

data class ReviewModel(
    val reviewId: String = "",
    val psychologistId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val timestamp: Long = 0L
)
```

Recenzje zapisane wcześniej nie mają tego pola i po odczycie dostaną pusty łańcuch. Wyświetlają się poprawnie, natomiast ich autorzy nie będą mogli ich edytować ani usuwać — to akceptowalne, bo funkcja edycji recenzji i tak nie istnieje.

- [ ] **Krok 6: Sprawdź, czy projekt się kompiluje**

```bash
./gradlew :app:compileDebugKotlin
```

Oczekiwany wynik: BUILD SUCCESSFUL. Wszystkie dodane pola mają wartości domyślne, więc istniejące miejsca konstruujące te modele kompilują się bez zmian.

---

## Zadanie 7: Klient — transakcyjna rezerwacja i koniec samodzielnej weryfikacji

**Pliki:**
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/remote/FirestoreAppointmentDataSource.kt`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/data/repository/AppointmentRepository.kt`
- Zmodyfikuj: `app/src/main/java/com/example/stellare/ui/StellareViewModel.kt`

**Interfejsy:**
- Konsumuje: pola z Zadania 6
- Produkuje: `FirestoreAppointmentDataSource.bookAppointmentAtomically(appointmentId: String, patientId: String, psychologistId: String, priceCoins: Int, notes: String): Unit` — rzuca `IllegalStateException` przy braku środków lub zajętym terminie.

- [ ] **Krok 1: Zastąp `bookAppointment` w `FirestoreAppointmentDataSource.kt`**

Usuń dotychczasową metodę `bookAppointment` i wstaw w jej miejsce:

```kotlin
    suspend fun bookAppointmentAtomically(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) {
        val appointmentRef = collection.document(appointmentId)
        val patientRef = db.collection("users").document(patientId)
        val psychologistRef = db.collection("users").document(psychologistId)
        val ledger = db.collection("wallet_transactions")
        val paymentRef = ledger.document()
        val earningRef = ledger.document("${appointmentId}_${psychologistId}")
        val now = System.currentTimeMillis()

        db.runTransaction { transaction ->
            val appointment = transaction.get(appointmentRef)
            val patient = transaction.get(patientRef)
            val psychologist = transaction.get(psychologistRef)

            if (appointment.getString("status") != "FREE") {
                throw IllegalStateException("Termin został już zarezerwowany.")
            }

            val patientBalance = patient.getLong("coinsBalance") ?: 0L
            if (patientBalance < priceCoins) {
                throw IllegalStateException("Za mało monet w portfelu.")
            }
            val psychologistBalance = psychologist.getLong("coinsBalance") ?: 0L

            transaction.update(
                appointmentRef,
                mapOf("patientId" to patientId, "status" to "BOOKED", "notes" to notes)
            )
            transaction.update(
                patientRef,
                mapOf(
                    "coinsBalance" to patientBalance - priceCoins,
                    "lastTransferTo" to psychologistId,
                    "lastTransferAmount" to priceCoins
                )
            )
            transaction.update(psychologistRef, "coinsBalance", psychologistBalance + priceCoins)

            transaction.set(
                paymentRef,
                WalletTransactionModel(
                    transactionId = paymentRef.id,
                    userId = patientId,
                    counterpartyId = psychologistId,
                    appointmentId = appointmentId,
                    amount = -priceCoins,
                    type = WalletTransactionType.BOOKING_PAYMENT,
                    title = "Opłata za wizytę",
                    timestamp = now
                )
            )
            transaction.set(
                earningRef,
                WalletTransactionModel(
                    transactionId = earningRef.id,
                    userId = psychologistId,
                    counterpartyId = patientId,
                    appointmentId = appointmentId,
                    amount = priceCoins,
                    type = WalletTransactionType.BOOKING_EARNING,
                    title = "Wpływ za wizytę",
                    timestamp = now
                )
            )
            null
        }.await()
    }
```

Dopisz brakujące importy na górze pliku:

```kotlin
import com.example.stellare.data.model.WalletTransactionModel
import com.example.stellare.data.model.WalletTransactionType
```

**Dlaczego pacjent zapisuje `lastTransferTo` i `lastTransferAmount`.** Reguły Firestore oceniają każdy dokument osobno i nie widzą całego zapisu wsadowego. Bez deklaracji odbiorcy we własnym dokumencie płacącego da się wstawić do jednego wsadu wielu odbiorców, z których każdy niezależnie przejdzie tę samą kontrolę bilansu — i monety powstają z niczego. Deklaracja przypina przelew do jednego odbiorcy na commit. Szczegóły w sekcji 5.3 specyfikacji.

Pola zostają na dokumencie po zakończeniu operacji i nie są wrażliwe — mówią wyłącznie, komu i ile zapłacono ostatnim razem. Kolejny przelew je nadpisuje.

**Dwie pułapki w nazwach.** Kolekcja księgi nazywa się `wallet_transactions` z podkreślnikiem — tak zapisuje ją `FirestoreWalletTransactionDataSource` i tak brzmi reguła. Zapis do `walletTransactions` wpadnie w blokadę domyślną.

Identyfikator wpisu o wpływie psychologa **musi** mieć postać `<appointmentId>_<psychologistId>`. Reguła wyprowadza go z wizyty, żeby przy jednym przelewie nie dało się wstawić wielu wpisów do cudzej księgi. Wpis o opłacie pacjenta idzie ścieżką własnej księgi i może mieć identyfikator generowany automatycznie.

- [ ] **Krok 2: Zaktualizuj `AppointmentRepository.kt`**

Zastąp metodę `bookAppointment`:

```kotlin
    suspend fun bookAppointment(
        appointmentId: String,
        patientId: String,
        psychologistId: String,
        priceCoins: Int,
        notes: String
    ) = appointmentDataSource.bookAppointmentAtomically(
        appointmentId, patientId, psychologistId, priceCoins, notes
    )
```

- [ ] **Krok 3: Przepisz `bookAppointment` w `StellareViewModel.kt`**

Dotychczasowa wersja pobierała sztywne 40 monet, zapisywała saldo osobnym zapisem i zwracała `Boolean` przed zakończeniem operacji — czyli zawsze `true`. Zastąp ją w całości:

```kotlin
    private val _bookingError = MutableStateFlow<String?>(null)
    val bookingError: StateFlow<String?> = _bookingError.asStateFlow()

    fun clearBookingError() {
        _bookingError.value = null
    }

    fun bookAppointment(appointment: AppointmentModel, notes: String = "") {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            _bookingError.value = null
            try {
                appointmentRepository.bookAppointment(
                    appointmentId = appointment.appointmentId,
                    patientId = user.id,
                    psychologistId = appointment.psychologistId,
                    priceCoins = appointment.priceCoins,
                    notes = notes
                )
            } catch (e: Exception) {
                Log.e("Booking", "Rezerwacja nieudana", e)
                _bookingError.value = e.message ?: "Nie udało się zarezerwować terminu."
            }
        }
    }
```

- [ ] **Krok 4: Ustaw cenę przy wystawianiu terminu**

W `StellareViewModel.kt` zastąp `addFreeSlot`:

```kotlin
    fun addFreeSlot(date: String, time: String) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            appointmentRepository.insertAppointment(
                AppointmentModel(
                    psychologistId = user.id,
                    date = date,
                    time = time,
                    status = "FREE",
                    priceCoins = user.pricePerSession.toInt()
                )
            )
        }
    }
```

- [ ] **Krok 5: Usuń samodzielną weryfikację**

W `StellareViewModel.kt` usuń w całości metodę `completePsychologistVerification`. Reguły z Zadania 2 i tak odrzucą ten zapis, a zostawienie jej dałoby użytkownikowi mylący komunikat o sukcesie.

W metodzie `publishOffer` usuń linię ustawiającą `isVerified`:

```kotlin
                isVerified = curr.role == UserRole.PSYCHOLOGIST && qual.isNotEmpty(),
```

Po usunięciu `publishOffer` nie dotyka już pola `isVerified`, więc zapis przejdzie przez reguły.

**Trzecia ścieżka — rejestracja.** `registerUser` przyjmuje parametr `isVerified`, a ekran rejestracji przekazuje do niego `isDocumentScanned`:

```kotlin
// LoginRegistrationScreens.kt:555 — do usunięcia
isVerified = if (role == UserRole.PSYCHOLOGIST || role == UserRole.PSYCHOLOGY_STUDENT) isDocumentScanned else false,
```

Reguła `create` wymaga `isVerified == false`, więc bez tej poprawki **psycholog zaznaczający „dokument zeskanowany" nie założy konta w ogóle** — dostanie `PERMISSION_DENIED`.

Usuń parametr `isVerified` z sygnatury `registerUser` w `StellareViewModel.kt` i z tworzonego tam obiektu `UserModel` (pole przyjmie wartość domyślną `false`). Następnie usuń argument z wywołania w `LoginRegistrationScreens.kt`.

Pole wyboru „dokument zeskanowany" zostaw w interfejsie — od teraz niczego nie ustawia. Prawdziwy obieg weryfikacji powstaje w Etapie 4; do tego czasu jedyną drogą do `isVerified = true` jest administrator.

- [ ] **Krok 6: Zapisuj autora recenzji**

W `StellareViewModel.kt` w metodzie `addReview` dopisz `reviewerId` przy tworzeniu obiektu. Bez tego reguła z Zadania 5 odrzuci każdą nową recenzję.

```kotlin
            reviewRepository.insertReview(
                ReviewModel(
                    psychologistId = psychologistId,
                    reviewerId = current?.id ?: return@launch,
                    reviewerName = reviewerName,
                    rating = rating,
                    comment = comment
                )
            )
```

**Uwaga: to nie wystarczy.** Reguła oceny wymaga, żeby recenzja i podbicie oceny trafiły do bazy w **jednym zatwierdzeniu**, a recenzja miała deterministyczny identyfikator. Obecna metoda robi dwa osobne zapisy, więc po wdrożeniu reguł recenzja przeszłaby, a podbicie oceny już nie.

Przepisz `addReview` na jeden `WriteBatch`:

```kotlin
    fun addReview(psychologistId: String, rating: Int, comment: String) {
        val current = currentUser.value ?: return
        viewModelScope.launch {
            val psychologist = userRepository.getUserById(psychologistId) ?: return@launch
            val reviewId = "${psychologistId}_${current.id}"
            val newCount = psychologist.ratingCount + 1
            val newRating = ((psychologist.rating * psychologist.ratingCount) + rating) / newCount

            val batch = FirebaseFirestore.getInstance().batch()
            batch.set(
                FirebaseFirestore.getInstance().collection("reviews").document(reviewId),
                ReviewModel(
                    reviewId = reviewId,
                    psychologistId = psychologistId,
                    reviewerId = current.id,
                    reviewerName = "${current.firstName} ${current.lastName}",
                    rating = rating,
                    comment = comment,
                    timestamp = System.currentTimeMillis()
                )
            )
            batch.update(
                FirebaseFirestore.getInstance().collection("users").document(psychologistId),
                mapOf("rating" to newRating, "ratingCount" to newCount)
            )
            batch.commit().await()
        }
    }
```

Identyfikator `<psychologistId>_<reviewerId>` jest wymagany przez regułę i wymusza przy okazji jedną recenzję na osobę na psychologa. Ponowne wywołanie nadpisze treść recenzji, ale oceny już nie podbije — reguła dopuszcza podbicie wyłącznie przy tworzeniu recenzji od zera.

- [ ] **Krok 7: Napraw miejsca wywołań**

Znajdź wszystkie wywołania zmienionych metod:

```bash
grep -rn "completePsychologistVerification\|bookAppointment(" app/src/main/java --include=*.kt
```

Każde wywołanie `bookAppointment` przekazujące identyfikator terminu zamień na przekazanie całego obiektu `AppointmentModel`. Każde wywołanie `completePsychologistVerification` usuń wraz z przyciskiem, który je wywoływał — ekran składania dokumentów powstanie w Etapie 4.

Stare wywołania sprawdzały zwracaną wartość `Boolean`, żeby pokazać komunikat o braku monet. Nowa wersja nie zwraca niczego — komunikat odczytaj ze strumienia `bookingError` i pokaż go w miejscu, w którym dotąd obsługiwano `false`.

- [ ] **Krok 8: Sprawdź kompilację**

```bash
./gradlew :app:compileDebugKotlin
```

Oczekiwany wynik: BUILD SUCCESSFUL.

---

## Zadanie 8: Wdrożenie reguł

**Pliki:**
- Brak zmian w plikach. Zadanie polega na wdrożeniu i weryfikacji.

- [ ] **Krok 1: Uruchom pełny zestaw testów po raz ostatni**

```bash
cd firebase && npm test
```

Oczekiwany wynik: **PASS** — komplet testów z Zadań 2–5. Nie wdrażaj reguł, jeżeli którykolwiek test pada.

- [ ] **Krok 2: Zaloguj się do Firebase**

```bash
cd firebase && npx firebase login
```

Wymaga konta z dostępem do projektu `stellare-a39ef`.

- [ ] **Krok 3: Wdróż reguły**

```bash
cd firebase && npm run deploy
```

- [ ] **Krok 4: Dodaj pierwszego administratora**

W konsoli Firebase → Firestore Database utwórz kolekcję `admins` i w niej pusty dokument, którego identyfikatorem jest UID konta administracyjnego. UID znajdziesz w zakładce Authentication.

Bez tego kroku panel weryfikacji z Etapu 4 będzie niedostępny dla wszystkich, a żaden psycholog nie zostanie zweryfikowany.

- [ ] **Krok 5: Sprawdź działanie aplikacji**

Zbuduj i uruchom aplikację, a następnie potwierdź ręcznie:

1. Logowanie i rejestracja działają.
2. Lista psychologów się wczytuje.
3. Czat otwiera się i przyjmuje wiadomość.
4. Psycholog wystawia wolny termin.
5. Pacjent z wystarczającym saldem rezerwuje termin, a monety przechodzą na konto psychologa.
6. Pacjent bez pokrycia dostaje komunikat o braku monet, a rezerwacja nie dochodzi do skutku.

Jeżeli którakolwiek operacja zwraca `PERMISSION_DENIED`, zajrzyj do zakładki Firestore → Rules → Monitor w konsoli Firebase, żeby zobaczyć, która reguła odrzuciła zapis.

---

## Definicja ukończenia

- [ ] Komplet testów reguł przechodzi (`cd firebase && npm test`)
- [ ] Nikt nie może sam sobie ustawić `isVerified`, `role` ani `isAdmin`
- [ ] Nikt nie może dopisać monet obcej osobie bez pokrycia z własnego portfela
- [ ] Dwóch pacjentów nie zarezerwuje tego samego terminu
- [ ] Rezerwacja pobiera cenę psychologa i przekazuje mu monety
- [ ] Wszystkie kolekcje spoza listy są domyślnie zablokowane
- [ ] Aplikacja kompiluje się i przechodzi ręczny test z Zadania 8
- [ ] Reguły wdrożone na `stellare-a39ef`, pierwszy administrator dodany

---

## Czego ten plan świadomie nie obejmuje

**Doładowanie portfela pozostaje niezabezpieczone** — reguła ogranicza pojedyncze doładowanie do 1000 monet, ale nie weryfikuje płatności. Wymagałoby to serwera. Ograniczenie opisane w sekcji 10 specyfikacji.

**Zwroty przy odwołaniu sesji** trafiają do Etapu 5 razem z resztą przebudowy portfela. Reguły z Zadania 4 już je dopuszczają, bo warunek zachowania monet działa w obie strony.

**Ekran składania dokumentu i panel administratora** to Etap 4. Tutaj powstają wyłącznie reguły chroniące kolekcję `verificationRequests`.
