import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from 'firebase/firestore'
import { authedDb, seed, setupEnv, unauthedDb } from './helpers.js'

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
    await setDoc(doc(db, 'users/niezweryfikowany'), {
      id: 'niezweryfikowany', role: 'PSYCHOLOGIST', isVerified: false,
      isAdmin: false, coinsBalance: 0, pricePerSession: 60,
    })
    // pricePerSession: 0 nie ma znaczenia dla pacjenta w praktyce, ale musi
    // istnieć — inaczej test 3. (typ vs rola) nie izoluje typZgodnyZRola():
    // bez tego pola cenaZgodnaZProfilem() rzuca blad ewaluacji i tworzenie
    // padnie z INNEGO powodu, maskujac usuniecie sprawdzanej klauzuli.
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false,
      isAdmin: false, coinsBalance: 500, pricePerSession: 0,
    })
    // Profil dobrany tak, by pasował do posta z 'psycholog' na każdym polu poza
    // wlasnoscia (cena, typ, snapshot) — jedyna rzecz, ktora go blokuje, to
    // warunek wlasnosci przy update. Gdyby ten warunek zniknal, atak by przeszedl.
    await setDoc(doc(db, 'users/napastnik'), {
      id: 'napastnik', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    // pricePerSession z rzeczywistym ułamkiem: JS SDK zapisuje liczby całkowite
    // (np. 80) jako integerValue, więc int() w regule nigdy realnie nie rzutuje
    // na pozostałych fikstur ach. 80.5 wymusza doubleValue — tak, jak zawsze
    // zapisuje Kotlin (pricePerSession: Double) z prawdziwej aplikacji.
    await setDoc(doc(db, 'users/psychologUlamkowy'), {
      id: 'psychologUlamkowy', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80.5,
    })
  })
})

describe('ogłoszenia — reguły posts', () => {
  it('1. autor tworzy własne ogłoszenie', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'posts/wlasne'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia poznawczo-behawioralna', body: 'Zapraszam na konsultacje.',
      tags: ['CBT'], priceCoins: 80, createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  it('2. tworzenie ogłoszenia z cudzym authorId jest odrzucane', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'posts/podszyte'), {
      authorId: 'psycholog', type: 'SEEKING_PSYCHOLOGIST',
      title: 'Szukam pomocy', body: 'Opis', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Bogdan', isVerified: false },
    }))
  })

  it('3. pacjent nie może opublikować ogłoszenia typu OFFERING_THERAPY', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'posts/pacjentOferuje'), {
      authorId: 'pacjent', type: 'OFFERING_THERAPY',
      title: 'Oferuję terapię', body: 'Opis', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Bogdan', isVerified: false },
    }))
  })

  it('4. psycholog może szukać terapii dla siebie (type = SEEKING_PSYCHOLOGIST)', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'posts/psychologSzuka'), {
      authorId: 'psycholog', type: 'SEEKING_PSYCHOLOGIST',
      title: 'Szukam superwizji', body: 'Opis', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  it('5. niezweryfikowany nie może podać authorSnapshot.isVerified = true', async () => {
    const db = authedDb(env, 'niezweryfikowany')
    await assertFails(setDoc(doc(db, 'posts/falszywaOdznaka'), {
      authorId: 'niezweryfikowany', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 60,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Nieznajomy', isVerified: true },
    }))
  })

  it('6. zweryfikowany nie może podać authorSnapshot.isVerified = false', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/zanizonaOdznaka'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: false },
    }))
  })

  it('7. priceCoins różne od pricePerSession autora jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/cenaNiezgodna'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 999,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  it('8. ogłoszenie pacjenta z priceCoins > 0 jest odrzucane', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'posts/pacjentZCena'), {
      authorId: 'pacjent', type: 'SEEKING_PSYCHOLOGIST',
      title: 'Szukam pomocy', body: 'Opis', tags: [], priceCoins: 50,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Bogdan', isVerified: false },
    }))
  })

  it('9. createdAt w przyszłości o godzinę jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/zPrzyszlosci'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now() + 60 * 60 * 1000, isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  it('10. createdAt sprzed dwóch dni jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/zPrzeszlosci'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now() - 2 * 24 * 60 * 60 * 1000, isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  it('11. obcy nie edytuje cudzego ogłoszenia', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/cudze'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'napastnik')
    await assertFails(updateDoc(doc(db, 'posts/cudze'), { title: 'Przejęte' }))
  })

  it('12. autor nie zmieni authorId we własnym ogłoszeniu', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/wlasneDoEdycji'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/wlasneDoEdycji'), { authorId: 'niezweryfikowany' }))
  })

  it('13. autor nie zmieni type po utworzeniu ogłoszenia', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/typDoEdycji'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/typDoEdycji'), {
      type: 'SEEKING_PSYCHOLOGIST', priceCoins: 0,
    }))
  })

  it('14. autor dezaktywuje własne ogłoszenie (isActive = false)', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/doDezaktywacji'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'posts/doDezaktywacji'), { isActive: false }))
  })

  it('15. nie da się skasować własnego ogłoszenia — tylko dezaktywacja', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/doSkasowania'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(deleteDoc(doc(db, 'posts/doSkasowania')))
  })

  it('16. zalogowany odczytuje ogłoszenia, niezalogowany nie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/doOdczytu'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const zalogowany = authedDb(env, 'pacjent')
    await assertSucceeds(getDoc(doc(zalogowany, 'posts/doOdczytu')))
    const niezalogowany = unauthedDb(env)
    await assertFails(getDoc(doc(niezalogowany, 'posts/doOdczytu')))
  })

  // Dopisany po mutacji z Kroku 5: usuniecie warunku "isActive == true" przy
  // tworzeniu nie zapalalo zadnego z testow 1-16 — poza jego zasiegiem trafial
  // tylko przypadek tworzenia od razu nieaktywnego ogloszenia. Bez tego testu
  // klauzula bylaby deklarowana jako pokryta, a w rzeczywistosci nie byla.
  it('17. tworzenie ogłoszenia od razu jako nieaktywne jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/nieaktywneOdStartu'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now(), isActive: false,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    }))
  })

  // Runda poprawek 1 — koordynator: pricePerSession w produkcji jest Double
  // (Kotlin), wiec zawsze zapisuje sie jako doubleValue. Wszystkie fikstury
  // powyzej maja pricePerSession bez ulamka, co w JS SDK serializuje sie jako
  // integerValue — int() nigdy realnie nie rzutowal. psychologUlamkowy (80.5)
  // wymusza prawdziwe rzutowanie float->int i dokumentuje, ze obcina, nie zaokragla.
  it('18. int() obcina pricePerSession — priceCoins = 80 przy pricePerSession = 80.5 przechodzi', async () => {
    const db = authedDb(env, 'psychologUlamkowy')
    await assertSucceeds(setDoc(doc(db, 'posts/obciecieWDol'), {
      authorId: 'psychologUlamkowy', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ula', isVerified: true },
    }))
  })

  it('19. int() nie zaokrągla w górę — priceCoins = 81 przy pricePerSession = 80.5 jest odrzucone', async () => {
    const db = authedDb(env, 'psychologUlamkowy')
    await assertFails(setDoc(doc(db, 'posts/zaokraglenieWGore'), {
      authorId: 'psychologUlamkowy', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 81,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ula', isVerified: true },
    }))
  })

  // Runda poprawek 1 — koordynator: zamrozenie createdAt przy update nie mialo
  // izolujacego testu. Bez niego autor podnioslby date wlasnego ogloszenia przy
  // KAZDEJ edycji tresci i wracal na szczyt listy — ta sama dziura, przed ktora
  // broni okno czasu przy tworzeniu, tylko wejsciem przez edycje.
  it('20. autor nie zmieni createdAt przy edycji ogłoszenia', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/dataDoEdycji'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: 1, isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/dataDoEdycji'), { createdAt: Date.now() }))
  })

  // Runda poprawek 2 — proba adwersaryjna koordynatora: bez listy dozwolonych
  // pol autor dopisuje do wlasnego ogloszenia dowolne nieprzewidziane pole.
  // Dzis niegrozne (lista sortuje sie po chronionym createdAt), ale to jedyna
  // rzecz stojaca miedzy tym a trwalym przypieciem na szczycie.
  it('21. tworzenie ogłoszenia z polem spoza listy jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'posts/dodatkowePoleCreate'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
      pinned: true,
    }))
  })

  it('22. edycja dopisująca pole spoza listy jest odrzucana', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/dodatkowePoleUpdate'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/dodatkowePoleUpdate'), { boost: 'MAX' }))
  })

  // Runda poprawek 2: limity rozmiaru dzialaly wylacznie przy tworzeniu —
  // autor wystawial poprawne ogloszenie, a potem rozdymal je bez ograniczen.
  // Trzy testy, po jednym na kazde pole objete rozmiaryPoprawne().
  it('23. edycja z tytułem ponad 120 znaków jest odrzucana', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/rozdetyTytul'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/rozdetyTytul'), { title: 'a'.repeat(500) }))
  })

  it('24. edycja z treścią ponad 2000 znaków jest odrzucana', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/rozdetaTresc'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/rozdetaTresc'), { body: 'a'.repeat(5000) }))
  })

  it('25. edycja z ponad 5 tagami jest odrzucana', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/rozdetaListaTagow'), {
        authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: 'Opis', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true,
        authorSnapshot: { firstName: 'Ala', isVerified: true },
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'posts/rozdetaListaTagow'), {
      tags: Array.from({ length: 500 }, (_, i) => 'tag' + i),
    }))
  })
})

// Runda poprawek 4 — luka prywatności: allow read: if isSignedIn() pozwalał
// każdemu zalogowanemu odczytać ogłoszenia pacjentów zapytaniem z pominięciem
// aplikacji, mimo że interfejs ukrywał je w zakładkach. Firestore wymaga, żeby
// SAMO ZAPYTANIE gwarantowało regułę — nie sprawdza wyników po fakcie — więc
// testy muszą używać getDocs(query(...)), nie tylko getDoc na znanym dokumencie.
describe('ogłoszenia — reguły odczytu zapytaniami (runda poprawek 4)', () => {
  const zasiejOgloszenia = () => seed(env, async (s) => {
    await setDoc(doc(s, 'posts/szukamPomocy'), {
      authorId: 'pacjent', type: 'SEEKING_PSYCHOLOGIST',
      title: 'Szukam pomocy', body: 'Opis', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Bogdan', isVerified: false },
    })
    await setDoc(doc(s, 'posts/oferujeTerapie'), {
      authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Oferuję terapię', body: 'Opis', tags: [], priceCoins: 80,
      createdAt: Date.now(), isActive: true,
      authorSnapshot: { firstName: 'Ala', isVerified: true },
    })
  })

  it('26. psycholog odpytuje ogłoszenia typu SEEKING_PSYCHOLOGIST', async () => {
    await zasiejOgloszenia()
    const db = authedDb(env, 'psycholog')
    const q = query(collection(db, 'posts'), where('type', '==', 'SEEKING_PSYCHOLOGIST'))
    await assertSucceeds(getDocs(q))
  })

  it('27. pacjent nie odpyta ogłoszeń typu SEEKING_PSYCHOLOGIST (cudze problemy)', async () => {
    await zasiejOgloszenia()
    const db = authedDb(env, 'pacjent')
    const q = query(collection(db, 'posts'), where('type', '==', 'SEEKING_PSYCHOLOGIST'))
    await assertFails(getDocs(q))
  })

  it('28. pacjent odpytuje własne ogłoszenia po authorId', async () => {
    await zasiejOgloszenia()
    const db = authedDb(env, 'pacjent')
    const q = query(collection(db, 'posts'), where('authorId', '==', 'pacjent'))
    await assertSucceeds(getDocs(q))
  })

  it('29. pacjent odpytuje ogłoszenia typu OFFERING_THERAPY', async () => {
    await zasiejOgloszenia()
    const db = authedDb(env, 'pacjent')
    const q = query(collection(db, 'posts'), where('type', '==', 'OFFERING_THERAPY'))
    await assertSucceeds(getDocs(q))
  })

  // Najważniejszy test — to jest droga ataku: zapytanie bez żadnego filtra
  // nie daje Firestore żadnej gwarancji co do zwracanych dokumentów, więc
  // reguła musi odrzucić całe zapytanie, nie przefiltrować wyniki po fakcie.
  it('30. pacjent nie odpyta całej kolekcji posts bez filtra', async () => {
    await zasiejOgloszenia()
    const db = authedDb(env, 'pacjent')
    await assertFails(getDocs(collection(db, 'posts')))
  })
})
