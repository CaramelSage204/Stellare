# Stellare — projekt techniczny

**Data:** 2026-09-15
**Repozytorium:** https://github.com/CaramelSage204/Stellare
**Branch roboczy:** `dev` (od `main`, commit `f7f1993`)
**Status:** zatwierdzony, gotowy do rozpisania planu wdrożenia

---

## 1. Czym jest Stellare

Aplikacja mobilna na Androida łącząca psychologów i studentów psychologii z pacjentami.
Użytkownik zakłada konto w jednej z trzech ról, publikuje ogłoszenia, przegląda ogłoszenia
innych, rezerwuje sesje w kalendarzu, rozmawia na czacie i płaci wirtualnymi monetami.

**Charakter projektu:** projekt studencki na zaliczenie. Aplikacja ma działać i dobrze
wyglądać, ale nie trafia do prawdziwych użytkowników. Ta decyzja przesądza o architekturze
— patrz sekcja 3.

**Stos technologiczny:** Kotlin 2.2.10, Jetpack Compose, AGP 9.1.1, Gradle 9.3.1,
`compileSdk 36.1`, `minSdk 24`. Backend w całości na Firebase (Auth + Firestore + Analytics).

---

## 2. Dlaczego wirtualne monety, a nie złotówki

Decyzja produktowa, podjęta świadomie:

1. Rozliczenie sesji jest prostsze, gdy w aplikacji krąży jedna waluta.
2. „100 monet za godzinę" czyta się psychologicznie łagodniej niż „100 zł za godzinę",
   co obniża opór przed pierwszą sesją.
3. Przy użytkownikach z różnych krajów każdy kupuje monety w swojej walucie,
   a wewnątrz aplikacji obowiązuje już jeden kurs — nie trzeba godzić euro z dolarem.

---

## 3. Ograniczenie kluczowe: brak serwera

Projekt działa na **darmowym planie Firebase Spark**, co oznacza **brak Cloud Functions**.
Nie ma więc miejsca, w którym dałoby się uruchomić zaufany kod. Jedyną linią obrony są
**Firestore Security Rules**.

To nie jest wyłącznie ograniczenie — reguły Firestore potrafią znacznie więcej, niż się
powszechnie zakłada, i wystarczają do zamknięcia trzech z czterech luk opisanych niżej.

---

## 4. Stan zastany

Repozytorium zawiera ok. 8600 linii Kotlina i jest znacznie bardziej zaawansowane, niż
sugeruje README (wygenerowany przez AI Studio i nieaktualny).

### Działa

Logowanie e-mailem i przez Google (Credential Manager), lista psychologów z filtrami
(wiek, płeć, specjalizacja, cena, ocena, status weryfikacji), ulubieni, profil psychologa
z recenzjami, czaty z notatkami do sesji, kalendarz z wolnymi terminami i rezerwacją,
portfel monet, edycja profilu, FAQ, gest swipe-back od krawędzi ekranu.

Motyw jest już brzoskwiniowo-terakotowy (`Color.kt`), z pełnym wariantem jasnym i ciemnym —
zgodny z docelową kolorystyką.

### Brakuje

| Obszar | Stan |
| --- | --- |
| Ogłoszenia (posty) | Nie istnieją. `publishOffer()` nadpisuje profil autora zamiast tworzyć post |
| Filtry przy „szukaj pacjenta" | Tylko wyszukiwanie tekstowe |
| Skan dokumentu | Brak — nie ma też Firebase Storage w zależnościach |
| Panel weryfikacji | Brak — nie ma gdzie oglądać dokumentów |
| 2FA telefon | Brak |
| Logowanie Facebook | Brak |
| Ustawienia | Brak (jest wyłącznie FAQ) |
| Realne doładowania | `topUpWallet()` dopisuje monety bez jakiejkolwiek płatności |
| Reguły Firestore | **Brak w repozytorium** |

### Cztery luki bezpieczeństwa

1. **Samodzielna weryfikacja.** `completePsychologistVerification()` ustawia
   `isVerified = true` z poziomu klienta. Każdy może ogłosić się zweryfikowanym
   psychologiem. Ręczne sprawdzanie dokumentów nie ma sensu, dopóki to zostaje.
2. **Produkcja monet.** `topUpWallet()` i `spendCoins()` zapisują `coinsBalance`
   bezpośrednio na dokumencie użytkownika. Każdy może dopisać sobie dowolną kwotę.
3. **Podwójna rezerwacja.** `bookAppointment()` nie jest transakcją — dwóch pacjentów
   zarezerwuje ten sam termin.
4. **Monety znikają w próżni.** Rezerwacja pobiera sztywne 40 monet od pacjenta
   i nie przekazuje ich nikomu. `pricePerSession` psychologa jest ignorowane.

### Dług strukturalny

`StellareViewModel` liczy 667 linii i obsługuje całą aplikację: nawigację, logowanie,
filtry, czaty, recenzje, kalendarz i portfel. Po dołożeniu zaplanowanych funkcji urósłby
do ok. 1300 linii.

Nawigacja jest ręcznie zaimplementowanym stosem ekranów wewnątrz ViewModelu, mimo że
`navigation-compose` jest już w zależnościach i pozostaje nieużywane.

---

## 5. Architektura bezpieczeństwa

### 5.1 Weryfikacja kwalifikacji

Pola `isVerified`, `role` i `isAdmin` w `users/{uid}` stają się niezapisywalne przez
właściciela konta:

```
allow update: if request.auth.uid == uid
  && request.resource.data.isVerified == resource.data.isVerified
  && request.resource.data.role       == resource.data.role
  && request.resource.data.isAdmin    == resource.data.isAdmin;
```

Psycholog wysyła skan do `verificationRequests/{uid}`. Dokument ten czyta wyłącznie
administrator. Po zatwierdzeniu w panelu administrator — i tylko on — przestawia
`isVerified` na profilu użytkownika.

Administratorzy są wskazani przez dokumenty w kolekcji `admins/{uid}`, którą reguły
czytają funkcją `exists()`. Kolekcja jest niezapisywalna dla wszystkich; wpisy dodaje się
ręcznie z konsoli Firebase.

### 5.2 Rezerwacja terminu

Rezerwacja wykonywana jest w **transakcji Firestore** po stronie klienta — transakcje są
atomowe i nie wymagają Cloud Functions. Reguły dokładają warunek na przejście statusu:

```
// FREE -> BOOKED tylko przez samego pacjenta i tylko z wolnego terminu
allow update: if resource.data.status == 'FREE'
  && request.resource.data.status == 'BOOKED'
  && request.resource.data.patientId == request.auth.uid;
```

Drugi pacjent próbujący zająć ten sam termin dostaje odmowę.

### 5.3 Przelew monet

Reguły Firestore udostępniają funkcję `getAfter()`, która widzi stan **innego** dokumentu
po zapisie. Pozwala to związać ze sobą dwa dokumenty w jednym zapisie wsadowym.

**Pułapka, w którą wpadła pierwsza wersja tego projektu.** Naiwny zapis niezmiennika
wygląda tak i jest **złamany**:

```
// ZŁE — nie używać
getAfter(/…/users/$(psychId)).data.coinsBalance
  == get(/…/users/$(psychId)).data.coinsBalance + priceCoins
```

Reguła jest oceniana **osobno dla każdego dokumentu odbiorcy**, a każda ocena porównuje
spadek salda płacącego z przyrostem tego jednego odbiorcy. Wstawiając do jednego zapisu
wsadowego N odbiorców z identyczną kwotą, wszyscy przechodzą ten sam test: płacący traci
kwotę **raz**, odbiorcy zyskują **N razy**. Przy limicie 500 zapisów na wsad daje to 499
kont słupów na jedną operację. Do tego brak podłogi salda pozwala płacącemu zejść
w dowolny debet, więc monety powstają na koncie jednorazowym. Atak potwierdzony na
emulatorze: sześć kont z zerowym saldem, jeden commit, pięć milionów monet do wydania.

**Poprawny mechanizm** wymaga, żeby płacący **zadeklarował odbiorcę we własnym
dokumencie**. Dokument po zapisie ma jedną wartość, więc tylko jeden odbiorca może się
nią wylegitymować:

```
function receivesPaidTransfer() {
  let kwota = request.resource.data.coinsBalance - resource.data.coinsBalance;
  let platnik = /databases/$(database)/documents/users/$(request.auth.uid);
  let przed = get(platnik).data.coinsBalance;
  let po = getAfter(platnik).data;
  return kwota is int
    && kwota > 0
    && po.coinsBalance == przed - kwota
    && po.coinsBalance >= 0
    && po.get('lastTransferTo', '') == uid
    && po.get('lastTransferAmount', 0) == kwota;
}
```

Dwa warunki niosą ciężar: `lastTransferTo` przypina przelew do jednego odbiorcy na
commit, a `coinsBalance >= 0` odbiera możliwość produkowania monet przez debet. Zwrot
przy odwołaniu działa analogicznie, w przeciwną stronę.

### 5.4 Doładowanie portfela — znane ograniczenie

**To jedyna świadomie niezabezpieczona operacja w całym systemie.**

Bez zaufanego kodu po stronie serwera nie da się zweryfikować, czy płatność faktycznie
wpłynęła. Klient musi mieć prawo dopisania sobie monet. Reguły ograniczają szkody:

- maksymalna kwota pojedynczego doładowania,
- każde doładowanie musi utworzyć odpowiadający mu wpis w księdze operacji,
- wpisy w księdze są niezmienne i nieusuwalne.

Użytkownik technicznie sprawny obejdzie to mimo wszystko. **W wersji produkcyjnej ta
operacja wymagałaby serwera** — patrz sekcja 10.

---

## 6. Model danych

### Kolekcje nowe

**`posts`**

| Pole | Typ | Opis |
| --- | --- | --- |
| `id` | String | identyfikator dokumentu |
| `authorId` | String | UID autora |
| `type` | Enum | `SEEKING_PSYCHOLOGIST` / `OFFERING_THERAPY` |
| `title` | String | tytuł ogłoszenia |
| `body` | String | treść |
| `tags` | List\<String\> | specjalizacje / tematy |
| `priceCoins` | Int | cena za sesję (tylko posty psychologów) |
| `createdAt` | Long | znacznik czasu |
| `isActive` | Boolean | ukrycie bez usuwania |
| `authorSnapshot` | Map | imię, zdjęcie, ocena, `isVerified`, wiek, płeć |

`authorSnapshot` to celowa denormalizacja — pozwala filtrować i renderować listę bez
dociągania profilu każdego autora osobno. Aktualizowany przy edycji profilu.

Autor może edytować i usuwać wyłącznie własne ogłoszenia; usunięcie polega na ustawieniu
`isActive = false`, dzięki czemu historia rozmów prowadzonych z tego ogłoszenia zostaje
nienaruszona. Liczba aktywnych ogłoszeń na użytkownika nie jest ograniczona.

**`verificationRequests`** — klucz dokumentu równy UID zgłaszającego.

| Pole | Typ | Opis |
| --- | --- | --- |
| `docImageBase64` | String | skan po kompresji, poniżej 1 MB |
| `docType` | String | dyplom / legitymacja / certyfikat |
| `status` | Enum | `PENDING` / `APPROVED` / `REJECTED` |
| `submittedAt` | Long | data wysłania |
| `reviewedBy` | String | UID administratora |
| `reviewedAt` | Long | data rozpatrzenia |
| `reviewNote` | String | powód odrzucenia, widoczny dla zgłaszającego |

**`admins`** — puste dokumenty, klucz to UID administratora. Zapis zablokowany dla
wszystkich; wpisy dodawane ręcznie z konsoli Firebase.

### Kolekcje zmieniane

| Kolekcja | Zmiana |
| --- | --- |
| `users` | `+ isAdmin`, `+ phone` (tylko numer potwierdzony — patrz niżej), `+ lastTransferTo`, `+ lastTransferAmount`. **Nie ma pola `settings`**: motyw, rozmiar czcionki i kolor dymków czatu są ustawieniami urządzenia, nie konta (patrz sekcja 10) |
| `appointments` | `+ priceCoins`, statusy `FREE` / `BOOKED` / `COMPLETED` / `CANCELLED` |
| `walletTransactions` | `+ type` (`TOPUP` / `BOOKING_PAYMENT` / `BOOKING_EARNING` / `REFUND`), `+ counterpartyId`, `+ appointmentId` |

### Numer telefonu nie jest polem „zweryfikowany"

Pierwotnie ta sekcja wymieniała `phoneVerified` jako pole w `users`. **Zrezygnowano z niego
świadomie.** Pole w dokumencie jest twierdzeniem klienta: reguły nie mają żadnego sposobu sprawdzić,
czy SMS dotarł, więc każdy mógłby ustawić `phoneVerified: true` z własnego urządzenia. Byłaby to
dokładnie taka obietnica bez pokrycia, jakich w etapach 5 i 6 usunięto z aplikacji kilkanaście.

Zamiast tego numer **wiąże się z kontem w Firebase Auth**, więc trafia do tokenu uwierzytelnienia,
a reguły czytają `request.auth.token.phone_number` — wartości w tokenie klient nie podrobi.
Pole `users.phone` jest niepuste **tylko wtedy, gdy numer zgadza się z tokenem**; pilnuje tego
reguła, nie aplikacja. Wyczyszczenie niepustego numeru przechodzi wyłącznie wtedy, gdy token
numeru nie ma, żeby zapis z nieaktualnego modelu nie skasował potwierdzenia.

Sprawdzone doświadczalnie na emulatorze: token konta e-mail nie ma numeru, po powiązaniu i
odświeżeniu go ma, a wobec prawdziwych reguł zapis cudzego numeru dostaje odmowę, a zapis numeru
z tokenu przechodzi.

### Limit techniczny

Dokument Firestore ma limit **1 MiB**. Skan dokumentu trafia do Firestore jako base64,
więc przed zapisem jest kompresowany do JPEG o wadze około **500 KB** (base64 zwiększa
rozmiar o mniej więcej jedną trzecią, co daje zapas). Wybór tego rozwiązania zamiast
Firebase Storage wynika z tego, że Storage wymaga dziś planu Blaze — patrz sekcja 10.

---

## 7. Ekonomia monet

> **Zaktualizowano po etapie 9.** Pierwotna wersja tej tabeli opisywała model, w którym monety
> przy rezerwacji trafiały **wprost na konto psychologa**. Od wprowadzenia depozytu
> (`escrows/{appointmentId}`) tak już nie jest — i to jest sedno zmiany, bo tylko dzięki temu
> pacjent odzyskuje monety **bez udziału psychologa**.

| Zdarzenie | Skutek |
| --- | --- |
| Doładowanie | Saldo pacjenta rośnie. Wpis `TOPUP` w księdze |
| Rezerwacja | Pacjentowi ubywa `priceCoins`, a monety trafiają **na depozyt wizyty** (`escrows/{appointmentId}`, status `HELD`). **Saldo psychologa nie zmienia się ani o monetę** |
| Oznaczenie sesji jako odbytej | **Dopiero teraz** depozyt przechodzi w `RELEASED`, saldo psychologa rośnie i powstaje wpis „Wpływ za wizytę". To jedyny moment, w którym psycholog dostaje monety |
| Odwołanie przez pacjenta ponad 24 h przed sesją | Pełny zwrot, **samoobsługowy** — depozyt przechodzi w `REFUNDED` bez żadnego działania psychologa |
| Odwołanie przez pacjenta poniżej 24 h przed sesją | Bez zwrotu. Monety pozostają w depozycie do rozliczenia |
| Odwołanie przez psychologa | Zawsze pełny zwrot, niezależnie od terminu |
| Termin, którego psycholog nigdy nie rozliczył | Po **14 dniach** od minionego terminu pacjent odzyskuje monety sam (`REFUNDED`). Bez tej drogi monety psychologa nieaktywnego w aplikacji zostawałyby w depozycie bezterminowo |

Bez prowizji platformy. Psycholog widzi zarobek w portfelu. Cena bierze się z pola
`pricePerSession` psychologa w chwili rezerwacji i jest zapisywana na dokumencie terminu
(`priceCoins`), żeby późniejsza zmiana cennika nie wpływała na zwrot. Sztywne 40 monet
z obecnego kodu znika.

---

## 8. Nawigacja i ekrany

### Szkielet

Referencja graficzna zakłada pływający dolny pasek, a pierwotny opis — szufladę spod
ikony profilu. Rozwiązanie łączy oba, zamiast wybierać jedno:

**Dolny pasek** — cztery miejsca używane codziennie:
`Ogłoszenia` · `Kalendarz` · `Czaty` · `Portfel`

**Ikona profilu w lewym górnym rogu** → arkusz z resztą:
`Mój profil` · `Ulubione` · `Ustawienia` · `FAQ` · `Panel weryfikacji` (tylko administrator)
· `Wyloguj`

Nawigacja przechodzi na **Navigation Compose**; ręczny stos ekranów w ViewModelu znika.
Gest swipe-back od krawędzi zostaje zachowany.

### Ogłoszenia — ekran główny

Górny pasek: awatar po lewej, `+` i dzwonek po prawej. Poniżej pigułkowe zakładki:

- pacjent widzi jedną — `Szukam psychologa`,
- psycholog widzi dwie — `Szukam psychologa` oraz `Szukam pacjenta`.

Rozdział ról jest celowy: pacjent nie ogląda ogłoszeń innych pacjentów, bo mało kto chce
ujawniać swoje problemy przed osobami w podobnej sytuacji.

Karty w stylu referencji: mocno zaokrąglone rogi, brzoskwiniowe tło, pigułka z ceną,
pigułka `Zweryfikowany`, awatar z oceną, strzałka w kółku w prawym górnym rogu. Jedna
karta akcentowana na ciemno.

`+` otwiera **„Napisz ogłoszenie"**. Formularz odczytuje rolę autora i kieruje post do
właściwej zakładki automatycznie. Ikona filtra przy zakładkach otwiera arkusz z filtrami
(istniejące filtry plus tagi).

Kliknięcie karty prowadzi do profilu autora: oceny, komentarze, cena, wolne terminy.

### Kalendarz

Wzorowany na drugim ekranie referencji: pasek dni tygodnia z zaznaczonym kółkiem dniem
bieżącym, poniżej oś godzinowa 08:00–20:00 z kolorowymi blokami sesji. Blok pokazuje
uczestnika i pigułkę z zakresem godzin.

- **Pacjent** — własne rezerwacje.
- **Psycholog** — zajęcia oraz `+` do dodawania wolnych terminów.

### Ekrany nowe

| Ekran | Zawartość |
| --- | --- |
| Napisz ogłoszenie | Tytuł, treść, tagi, cena (psycholog). Nie modyfikuje profilu |
| Weryfikacja kwalifikacji | Aparat lub galeria, kompresja, wysyłka, status zgłoszenia z powodem odrzucenia |
| Panel weryfikacji | Lista zgłoszeń, podgląd skanu, `Zatwierdź` / `Odrzuć` z notatką. Widoczny tylko dla administratora |
| Ustawienia | Rozmiar czcionki (globalny), kolor dymków czatu, motyw jasny/ciemny/systemowy, weryfikacja telefonu, usunięcie konta |
| 2FA telefon | Numer → SMS → kod. Opcjonalne, wejście z Ustawień |

### Logowanie

Zostaje e-mail z hasłem i Google. Dochodzi Facebook oraz opcjonalne 2FA SMS.
Do naprawienia istniejący błąd: użytkownik logujący się pierwszy raz przez Google nie ma
gdzie wybrać roli ani uzupełnić profilu.

---

## 9. Kolejność wdrożenia

Etapy ułożone tak, by po każdym aplikacja działała i nadawała się do pokazania.

| # | Etap | Zakres |
| --- | --- | --- |
| 1 | **Fundament bezpieczeństwa** | Reguły Firestore, transakcyjna rezerwacja, niezmiennik `getAfter()`, zablokowanie `isVerified` / `role` / `coinsBalance` dla klienta |
| 2 | **Refaktor szkieletu** | Podział ViewModelu na sześć, Navigation Compose, dolny pasek, arkusz profilu |
| 3 | **Ogłoszenia** | Encja `posts`, dwie zakładki, formularz, filtry, karty w nowym stylu |
| 4 | **Weryfikacja** | Skan do Firestore, ekran statusu, panel administratora |
| 5 | **Portfel** | Cena psychologa zamiast stałej, przelew, zwroty, historia z typami operacji |
| 6 | **Redesign pozostałych ekranów** | Kalendarz, profil, czaty, detal psychologa |
| 7 | **Logowanie** | Facebook, 2FA SMS, reset hasła, dobór roli po rejestracji przez Google |
| 8 | **Ustawienia i szlify** | Ekran ustawień, rozmiar czcionki, kolory czatu, FAQ |

Etapy 1 i 2 są warunkiem wstępnym dla pozostałych. Etapy 3–8 można przestawiać.

**Zakres pierwszego planu wdrożenia:** etapy 1 i 2. Razem obejmują reguły bezpieczeństwa
i przebudowę szkieletu — po ich zakończeniu aplikacja ma ten sam zestaw funkcji co dziś,
ale bezpieczny i gotowy na rozbudowę. Każdy kolejny etap dostaje własny plan, pisany już
na działającym fundamencie.

---

## 10. Wymagania zewnętrzne i blokery

**Sekcja do przekazania osobie prowadzącej projekt — to rzeczy, których nie da się
załatwić kodem.**

### Konieczne do uruchomienia projektu

| Co | Po co | Kto załatwia |
| --- | --- | --- |
| Dostęp do projektu Firebase `stellare-a39ef` | Wgranie reguł Firestore, dodanie administratorów, podgląd danych | Właściciel projektu |
| Wpis w kolekcji `admins` | Bez tego panel weryfikacji jest niedostępny dla nikogo | Ręcznie z konsoli Firebase |
| Odcisk SHA-1 i SHA-256 aplikacji w konsoli Firebase | Wymagane przez logowanie Google i 2FA SMS | Deweloper, z konsoli Firebase |

### Wymagane dla konkretnych funkcji

| Funkcja | Wymaganie | Uwagi |
| --- | --- | --- |
| **Logowanie Facebook** | Aplikacja w Meta for Developers, App ID i App Secret | W trybie deweloperskim działa wyłącznie dla kont testowych. Logowanie publiczne wymaga weryfikacji biznesowej Meta, która trwa tygodniami. Na zaliczenie tryb deweloperski wystarcza |
| **2FA SMS** | Firebase Phone Auth, odciski SHA | Na planie Spark limit **10 SMS na dobę na cały projekt**. Wystarcza na demonstrację, nie wystarcza na prawdziwych użytkowników |
| **Firebase Storage** | **Plan Blaze (karta płatnicza)** | Nowe projekty nie dostają domyślnego kubełka bez rozliczeń. **Dlatego skany trafiają do Firestore jako base64** — omija to wymóg Blaze, kosztem limitu 1 MB na dokument |
| **Cloud Functions** | **Plan Blaze** | Nieużywane. Jedyna funkcja, która by ich wymagała, to bezpieczne doładowanie portfela |
| **Realne płatności** | Operator płatności plus serwer | Poza zakresem. Doładowanie pozostaje symulowane |

### Znane ograniczenia, świadomie zaakceptowane

1. **Doładowanie portfela jest niezabezpieczone.** Użytkownik technicznie sprawny dopisze
   sobie monety. Wymagałoby serwera. Reguły ograniczają kwotę i wymuszają wpis w księdze.
2. **Limit 1 MB na skan dokumentu.** Wynika z zapisu w Firestore. Rozwiązaniem docelowym
   jest Firebase Storage na planie Blaze.
3. **Logowanie Facebook działa tylko dla kont testowych**, dopóki Meta nie zweryfikuje
   aplikacji.
4. **System ocen da się oszukać drugim kontem — próg podniesiony, nie zamknięty.**
   Etap 5 zamknął dwie drogi: ocena jest sumą całkowitą, więc jedna recenzja nie przesuwa
   średniej dowolnie, a recenzja wymaga dokumentu-znacznika powstającego przy oznaczeniu
   wizyty jako odbytej. Zostaje jednak droga przez **drugie konto**: psycholog wystawia termin,
   zakłada drugie konto, rezerwuje nim własny termin, oznacza jako odbyty i wystawia sobie recenzję.

   Koszt tego ataku jest **zerowy**, i to nie z powodu darmowych terminów: przy terminie płatnym
   monety wracają do konta psychologa, a brakujące doładowanie użytkownik dopisuje sobie sam,
   bo doładowanie portfela jest nieszczelne (patrz punkt 1). Zabezpieczenie podnosi więc próg
   z „jedno darmowe konto" do „jedno darmowe konto plus kilka dotknięć ekranu".

   **Wyżej bez serwera tego nie podniesiemy.** Każda klauzula, którą dałoby się dopisać do reguł,
   opiera się ostatecznie na tym, że monety mają realną wartość — a nie mają, dopóki każdy może
   je sobie dopisać. Domknięcie wymaga weryfikacji tożsamości albo płatności po stronie serwera.

5. **System ocen da się oszukać drugim kontem.** Reguła recenzji sprawdza wyłącznie,
   że recenzent to ktoś inny niż oceniany — nie sprawdza, czy ta osoba kiedykolwiek
   odbyła wizytę. Konto Firebase Auth jest darmowe i nie wymaga potwierdzenia tożsamości,
   więc jedno dodatkowe konto wystarcza, by wystawić sobie piątkę. Dodatkowo `rating` jest
   wartością podawaną przez klienta, a nie wyliczaną przez regułę, więc pojedyncza recenzja
   przesuwa średnią dowolnie — z 1.0 przy 9998 głosach prosto na 5.0.

   Obie naprawy wymagają zmiany modelu danych, nie poprawki reguły: `ratingSum` jako liczba
   całkowita zamiast `rating` jako średniej (arytmetyka całkowita w regule jest dokładna,
   równość na liczbach zmiennoprzecinkowych jest krucha i przy najmniejszym rozjeździe
   zablokowałaby wszystkie recenzje) oraz dokument-znacznik odbytej wizyty o przewidywalnym
   identyfikatorze, bo reguły Firestore nie potrafią odpytywać kolekcji — wyłącznie pobrać
   dokument po ścieżce. Zaplanowane na początek Etapu 5. Udokumentowane dwoma pominiętymi
   testami w `firebase/test/users.test.js`.

   Dla porównania portfel jest zabezpieczony poprawnie — przelew i wpis do księgi są zakotwiczone
   w realnym stanie drugiego dokumentu. Monety, czyli to, co ma wartość, pozostają chronione;
   podatna jest reputacja.

5. **Dane o zdrowiu psychicznym.** Aplikacja przetwarza dane szczególnej kategorii
   w rozumieniu RODO. W wersji studenckiej nie wdrażamy polityki prywatności, zgód ani
   procedury usuwania danych. **Przed jakimkolwiek kontaktem z prawdziwymi użytkownikami
   jest to bezwzględnie konieczne.**

---

### Motyw i rozmiar czcionki są ustawieniami urządzenia, nie konta

Wcześniejsza wersja sekcji 6 wymieniała `users.settings` (rozmiar czcionki, kolor czatu, motyw).
**Tak nie zrobiliśmy i nie zrobimy.** Ustawienia mieszkają w magazynie na telefonie (DataStore),
nie w dokumencie użytkownika. Powody są dwa:

1. Dokument użytkownika wymaga zalogowania, a motyw musi działać już na ekranie logowania i
   rejestracji, czyli zanim istnieje jakiekolwiek konto.
2. To ustawienia **tego telefonu**, nie osoby: ktoś, kto na telefonie z małym ekranem wybiera
   większą czcionkę, nie chce jej na tablecie, a wylogowanie i zalogowanie innego konta na tym samym
   urządzeniu nie powinno zmieniać jego wyglądu.

Konsekwencja: po zalogowaniu na nowym urządzeniu ustawienia są domyślne, a usunięcie konta ich nie
dotyka, bo nie są jego częścią.

**Mignięcie złym motywem przy starcie jest zamknięte jawnie.** Ustawienia czytane są asynchronicznie,
więc bez zabezpieczenia pierwsza klatka mogłaby powstać w motywie domyślnym i dopiero po chwili
przeskoczyć na wybrany. `MainActivity` wstrzymuje pierwszą klatkę (`OnPreDrawListener` zwraca
`false`), dopóki kompozycja z wczytanymi ustawieniami się nie wykona; do tego czasu stan ustawień jest
pusty (`null`), a nie „domyślny", żeby nic nie narysowało się w złym motywie. Zabezpieczenie nie ma
limitu czasu, więc zawieszony magazyn zostawiłby aplikację na oknie startowym; uszkodzony plik i
błąd wejścia-wyjścia są natomiast obsłużone i dają ustawienia domyślne.

### Czego usunięcie konta nie usuwa, i dlaczego

**To informacja dla osoby prowadzącej projekt, bo dotyczy obowiązków wobec użytkownika.** Aplikacja
w ekranie „Usuń konto" mówi użytkownikowi prawdę o tym, co zniknie, a co zostanie. Zostaje:

| Co zostaje | Dlaczego |
| --- | --- |
| **Wizyty**: zarezerwowane (także na przyszłość), odbyte i odwołane | To historia drugiej strony. Przy nazwisku usuwającego widzi ona „inny użytkownik". Rezerwacji nie anulujemy, a monety za nie nie wracają |
| **Opinie** wystawione przez użytkownika | Reguły pozwalają zmienić ocenę psychologa tylko razem z nową opinią. Usunięcie opinii rozjechałoby sumę ocen, która mieszka w dokumencie psychologa |
| **Wpisy księgi portfela** | Księga jest niezmienna: nikt, także właściciel, nie może usunąć jej wpisów |
| **Czaty i wiadomości** | Dane drugiej strony rozmowy. Aplikacja ich nie usuwa |
| Znaczniki odbytych wizyt, lista ulubionych | Aplikacja nie usuwa tych danych |
| Notatki psychologa o pacjencie (po usunięciu konta pacjenta) | Należą do psychologa, nie do pacjenta |

**Saldo monet przepada bez zwrotu.** Dziś monety są wirtualne i nie mają wartości pieniężnej, więc
to akceptowalne. **Przy włączeniu prawdziwych płatności trzeba rozstrzygnąć, co dzieje się z
niewykorzystanymi monetami** (zwrot, wypłata, albo wyraźna zgoda w regulaminie), zanim ktokolwiek
zapłaci prawdziwe pieniądze. Tej decyzji nie da się podjąć w kodzie.

Powiązane z punktem o danych o zdrowiu psychicznym (punkt 5 wyżej): pozostawienie czatów i wiadomości
po usunięciu konta jest dla prawdziwych użytkowników decyzją do rozstrzygnięcia prawnie, nie
technicznie.

### Wymagane dla płatności (etap po 7)

Doładowanie portfela **przyznaje monety bez pobierania płatności** — bez serwera nie ma kto
zweryfikować przelewu, co opisuje sekcja 5.4. W etapie 6 usunięto z aplikacji **udawany formularz
płatności** (wybór BLIK, karty i przelewu oraz pole kodu), bo pokazywał komplet elementów
prawdziwej transakcji, po których nic się nie działo. Ceny pakietów w złotych zostały, bo definiują
przelicznik.

Żeby doładowanie pobierało pieniądze, potrzebne jest **poza kodem aplikacji**: konto u operatora
płatności, jego klucze, oraz **serwer albo Cloud Functions** przyjmujące potwierdzenie transakcji —
monet nie wolno przyznawać na podstawie odpowiedzi, którą może podrobić klient. To ta sama
przyczyna, dla której sekcja 5.4 jest ograniczeniem, a nie usterką: na planie Spark nie ma gdzie
tego sprawdzić.

Do tego czasu w aplikacji nie może pojawić się żadne słowo sugerujące zapłatę.

### Wymagane dla weryfikacji numeru telefonu

Kod jest gotowy i sprawdzony wobec reguł, ale prawdziwy SMS wymaga rzeczy, których nie da się
dopisać w aplikacji:

1. **Włączyć dostawcę „Telefon"** w konsoli Firebase (Authentication → Sign-in method).
2. **Odciski certyfikatów SHA‑1 i SHA‑256** dla pakietu `com.example.stellare`: klucz debug, klucz
   podpisujący wysyłkę oraz klucz Play App Signing, jeśli jest używany. Po dodaniu pobrać nowy
   `google-services.json`.
3. **Play Integrity** (albo zapasowe reCAPTCHA) — Firebase używa ich do ochrony przed nadużyciami.
   Bez tego weryfikacja na prawdziwym urządzeniu nie przejdzie.
4. **Pula bezpłatnych SMS‑ów.** Weryfikacja telefonu działa na planie Spark w granicach puli; poza
   nią jest płatna. Aktualne limity i stawki są po stronie Firebase i trzeba je sprawdzić przed
   uruchomieniem, bo to jedyny element logowania, który **kosztuje za użycie**.
5. **Test na prawdziwym urządzeniu z prawdziwym numerem.** Emulator Auth nie ma numerów testowych
   ze stałym kodem: każdy numer działa, a kod jest losowy i czyta się go z jego własnego API.
   Nazwy kodów błędów prawdziwego SDK **różnią się** od emulatorowych, więc mapowanie komunikatów
   trzeba potwierdzić na urządzeniu.

Ograniczenie po stronie klienta: ponowna wysyłka kodu jest zablokowana na 60 sekund. To limit
w aplikacji, nie w projekcie Firebase.

### Wymagane dla logowania przez Facebooka

Kod jest gotowy, ale **przycisk nie pojawia się bez konfiguracji**. Uwaga, która musi tu zostać
powiedziana wprost: przycisk pojawia się, gdy w `FACEBOOK_APP_ID` znajdą się same cyfry.
**Ta wartość niczego nie zaświadcza** — w użytym przepływie kod jej nie używa, więc dowolne cyfry
pokażą przycisk, a samo logowanie zawiedzie, dopóki nie zrobi się punktów 1–5.

1. Aplikacja na `developers.facebook.com` z produktem **Facebook Login**.
2. **App ID** (liczbowy) do `.env` jako `FACEBOOK_APP_ID=<ID>` albo do zmiennych budowania.
3. **App Secret** wpisać **w konsoli Firebase**, nigdy w aplikacji.
4. Włączyć dostawcę **Facebook** w konsoli Firebase i podać App ID oraz App Secret.
5. Adres przekierowania `https://<project-id>.firebaseapp.com/__/auth/handler` skopiować z konsoli
   Firebase do Facebook Login → Settings → „Valid OAuth Redirect URIs".
6. Do trybu **Live** Facebook wymaga: App Domains, adresu polityki prywatności, kategorii i ikony
   1024×1024. W trybie Development logują się **tylko** osoby z rolą w aplikacji.
7. **Sklep Play**: deklaracja bezpieczeństwa danych musi ująć logowanie kontem zewnętrznym
   (imię i adres e‑mail z Facebooka) oraz aktualną politykę prywatności.
8. Test na prawdziwym urządzeniu z kontem testowym Facebooka.

**Świadomie nie dodano SDK Facebooka.** Użyto przepływu webowego, który jest już w `firebase-auth`,
więc aplikacja nie ma nowej zależności, wpisów w manifeście ani tokenu klienta. Powód jest
prywatnościowy, nie techniczny: SDK Facebooka domyślnie **loguje zdarzenia aplikacji do Facebooka**
i zbiera identyfikator reklamowy. W aplikacji, w której pacjent szuka psychologa, niewpuszczanie
tej biblioteki jest właściwością, nie oszczędnością.

Cena tej decyzji: w przepływie webowym **nie da się odróżnić** odmowy uprawnień od innego błędu
strony, więc użytkownik dostaje komunikat ogólny. Przejście na SDK wymagałoby tokenu klienta,
wpisów w manifeście, wyłączenia dwóch flag auto‑logowania i ujawnienia tego w deklaracji danych.

### Czego nie da się zweryfikować lokalnie

Te rzeczy są w kodzie, mają testy jednostkowe albo regułowe, ale **nikt nie widział ich w działaniu
na prawdziwej usłudze** i trzeba je sprawdzić przed oddaniem:

- logowanie przez Facebooka od początku do końca (wymaga punktów 1–5 wyżej),
- prawdziwy SMS, Play Integrity i reCAPTCHA,
- pobranie płatności (patrz podsekcja o płatnościach),
- wznowienie logowania webowego po uśmierceniu procesu w jego trakcie — **nieobsługiwane
  świadomie**: kilka linii dałoby obsługę wyglądającą na działającą, bez możliwości dowodu,
- mapowanie kodów błędów prawdziwego SDK na polskie komunikaty.

### Co wymaga planu Blaze, a co nie

Plan Spark wystarcza do wszystkiego, co dziś działa. Blaze jest potrzebny do trzech rzeczy:

1. **Pobieranie płatności za monety** — wymaga Cloud Functions, bo monet nie wolno przyznawać na
   podstawie odpowiedzi, którą klient może podrobić. To jedyna droga do zamknięcia ograniczenia
   z sekcji 5.4.
2. **Firebase Storage na skany dokumentów** — dziś skan trafia do Firestore jako tekst, z limitem
   rozmiaru i kompresją po stronie telefonu. Storage zdjęłoby ten limit.
3. **SMS‑y poza bezpłatną pulą.**

Blaze **nie był potrzebny** do dwóch największych rzeczy, które były do zrobienia: zamknięcia
dziury w prywatności ani przebudowy przenoszenia monet na depozyt. Oba były zmianą modelu danych
i reguł, nie usługi — i oba zostały wykonane w etapie 9 (opis niżej).

### Trzy dziury zamknięte w etapie 9 — opis działającego rozwiązania

Wszystkie trzy były opisane w tym miejscu jako **NIE zaakceptowane**. Etap 9 je zamknął, a każde
zamknięcie zostało dowiedzione **na urządzeniu**, nie tylko testem.

**1. Dane kontaktowe i saldo nie są już widoczne dla nikogo poza właścicielem.**
Model danych został rozdzielony: dokument `users/{uid}` zawiera wyłącznie pola publiczne
(`id, role, isVerified, isAdmin, firstName, age, gender, bio`), a telefon, e-mail i **saldo monet**
przeniesiono do `users/{uid}/private/contact` z odczytem tylko dla właściciela i administratora.
Tworzenie profilu publicznego ma **listę dozwolonych pól**, a edycja — zakazanych; test po stronie
reguł **czyta plik Kotlina** z listą pól klienta, więc dopisanie pola prywatnego do modelu zapali
test, zamiast wyciec. Zmierzone: osiem dróg odczytu odrzuconych (`get` wprost, listowanie,
`collectionGroup`, `collectionGroup` z filtrem po saldzie, zapytanie po numerze, niezalogowany,
inny dokument w podkolekcji, obcy w cudzym). Na urządzeniu: obcy i psycholog dostają **403**
na dokument kontaktowy pacjentki, a profil publiczny ma 14 pól **bez telefonu, e-maila i salda**.

**2. Monety pacjenta idą na depozyt i wracają bez udziału psychologa.**
`escrows/{appointmentId}` ze statusami `HELD → RELEASED | REFUNDED` i `allow delete: if false`.
Pacjent odzyskuje monety **sam**: przy odwołaniu ponad 24 h przed terminem oraz po oknie 14 dni od
minionego terminu (to druga droga zamyka przypadek psychologa, który nie oznaczył wizyty).
Niezmiennik pilnowany regułami, w dokładnym brzmieniu: *suma sald plus suma depozytów `HELD` jest
stała, z wyjątkiem doładowania (do limitu) i dobrowolnego zniszczenia własnych monet; doładowanie
może zajść w tej samej partii co rozliczenie.* Na urządzeniu: zwrot zadziałał, a **dokument
psychologa nie został zapisany ani razu** (niezmieniony `updateTime`) — czyli zwrot naprawdę nie
wymaga drugiej strony.

**3. Oceny nie da się wyzerować, usuwając i odtwarzając profil.**
Suma ocen żyje w `ratings/{uid}` — **poza** dokumentem zapisywanym przez właściciela, z
`allow delete: if false`, aktualizowana wyłącznie razem z opinią. Na urządzeniu: konto skasowane
**prawdziwą ścieżką z aplikacji** (`users/{uid}` → 404), profil odtworzony na tym samym
identyfikatorze, a `ratings/{uid}` nietknięte (niezmieniony `updateTime`).

**Zdanie o widoczności danych w aplikacji można dziś napisać prawdziwie** — poprzednia wersja tej
sekcji ostrzegała, że nie wolno twierdzić, iż dane pacjenta widzi wyłącznie jego psycholog. Dziś
dane kontaktowe widzi wyłącznie właściciel i administrator; to jest zdanie do obrony.

### Ograniczenia przyjęte świadomie w etapie 9 (zapisane, nie przemilczane)

1. **Doładowanie bez potwierdzenia serwerowego** — bez zmian, patrz punkt 1 listy wyżej. Zamknięcie
   wymaga Cloud Functions, czyli planu Blaze.
2. **Orakuł salda.** Kontrahent depozytu `HELD` może poznać dokładne saldo drugiej strony liniowym
   zgadywaniem kwoty rozliczenia (odmowa dla złej kwoty, zgoda dla prawdziwej). **Nie jest to
   regresja** — wcześniej saldo czytało się wprost. Bez serwera nie da się domknąć, bo reguła musi
   porównać kwotę z saldem. Decyzja zapisana **w komentarzu reguły**.
3. **Monety przepadają przy usunięciu własnego konta** — brak ścieżki wypłaty, bo wypłata to
   prawdziwe pieniądze.
4. **Brak migracji historycznych sald i ocen.** Odtworzenie salda wymagałoby użycia dziury
   niepotwierdzonego doładowania jako narzędzia. Konta sprzed etapu 9 dostają dokument prywatny
   z saldem `0` przy pierwszym logowaniu; **istniejące saldo nie jest ruszane** (pilnuje tego test).
5. **Nowe konto nie założy profilu bez sieci.** To cena rozdzielenia stanów „nie ma profilu" i
   „nie wiem, bo odczyt padł" — alternatywą jest wprost ścieżka utraty danych (patrz niżej).
6. **Blokada zatwierdzania weryfikacji istnieje wyłącznie po stronie interfejsu.** Reguły nie
   obejrzą obrazu, więc nie mogą jej strzec; jednolicie biała kartka w pełnym wymiarze przejdzie.
7. **Rejestracja i zapis profilu przestały kolejkować się offline**, bo przeszły na transakcje.
   Użytkownik dostaje uczciwy komunikat zamiast milczącego „zapisano".

## 11. Poza zakresem

Powiadomienia push, wideorozmowy, wyszukiwanie pełnotekstowe, wersje językowe,
eksport danych, prowizja platformy, wypłaty monet na konto bankowe.

---

## 12. Środowisko deweloperskie

| Składnik | Wersja / lokalizacja |
| --- | --- |
| Android Studio | `C:\Program Files\Android\Android Studio` |
| JDK (wbudowany w Studio) | `...\Android Studio\jbr` |
| Microsoft OpenJDK 17 | `C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot` |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |
| Gradle | 9.3.1 przez wrapper |

Plik `.env` z kluczem `GEMINI_API_KEY` jest wymagany przez wtyczkę Secrets, ale sam klucz
**nie jest nigdzie używany w kodzie** — zależność `firebase-ai` pozostaje zakomentowana.
Wystarczy wartość zastępcza.
