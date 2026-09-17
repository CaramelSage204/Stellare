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

  it('nie pozwala zalozyc konta z podniesiona ocena', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      rating: 5.0, ratingCount: 9999,
    }))
  })

  it('pozwala zalozyc konto z zerowa ocena', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      rating: 0, ratingCount: 0,
    }))
  })

  it('nie pozwala zalozyc konta z sama podniesiona ocena przy zerowym liczniku', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      rating: 5.0, ratingCount: 0,
    }))
  })

  it('nie pozwala zalozyc konta z samym podniesionym licznikiem ocen', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      rating: 0, ratingCount: 9999,
    }))
  })

  it('wlasciciel nie moze podniesc sobie samej oceny bez recenzji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { rating: 5 }))
  })

  it('wlasciciel nie moze podniesc sobie samego licznika ocen bez recenzji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { ratingCount: 1 }))
  })

  it('wlasciciel edytuje caly dokument (jak realny zapis .set) zachowujac ocene bez zmian', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog3'), {
        id: 'psycholog3', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
        coinsBalance: 0, firstName: 'Ala', rating: 4.5, ratingCount: 10,
      })
    })
    const db = authedDb(env, 'psycholog3')
    await assertSucceeds(setDoc(doc(db, 'users/psycholog3'), {
      id: 'psycholog3', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, firstName: 'Alicja Zaktualizowana', rating: 4.5, ratingCount: 10,
    }))
  })

  it('nie pozwala zalozyc konta z nieznana rola', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'ADMINISTRATOR', isVerified: false, isAdmin: false, coinsBalance: 0,
    }))
  })

  it('pozwala zalozyc konto z kazda ze znanych rol', async () => {
    for (const rola of ['PATIENT', 'PSYCHOLOGIST', 'PSYCHOLOGY_STUDENT']) {
      const uid = 'nowy_' + rola
      const db = authedDb(env, uid)
      await assertSucceeds(setDoc(doc(db, 'users/' + uid), {
        id: uid, role: rola, isVerified: false, isAdmin: false, coinsBalance: 0,
      }))
    }
  })

  it('konto zalozone przed dodaniem pola isAdmin daje sie dalej edytowac', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stareKonto'), {
        id: 'stareKonto', role: 'PATIENT', isVerified: false, coinsBalance: 0,
        firstName: 'Stary', rating: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto')
    await assertSucceeds(updateDoc(doc(db, 'users/stareKonto'), {
      firstName: 'Zaktualizowany', isAdmin: false,
    }))
  })

  it('konto bez pola isAdmin nadal nie moze go podniesc na true', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stareKonto2'), {
        id: 'stareKonto2', role: 'PATIENT', isVerified: false, coinsBalance: 0,
        firstName: 'Stary', rating: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto2')
    await assertFails(updateDoc(doc(db, 'users/stareKonto2'), { isAdmin: true }))
  })

  it('rejestracja z dokumentem tak, jak serializuje go Android SDK po poprawce', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      firstName: 'Jan', lastName: 'Nowak', age: 30, gender: 'Mezczyzna',
      phone: '', email: 'jan@example.com', qualifications: '', specializations: '',
      pricePerSession: 0, rating: 0, ratingCount: 0, bio: '', customPrices: '',
      profileImageUri: null, lastTransferTo: null, lastTransferAmount: 0,
    }))
  })

  it('weryfikacji nie da sie ustawic z klienta; stara nazwa pola jest martwym kluczem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { verified: true }))
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { isVerified: true }))
  })

  it('konto sprzed poprawki serializacji daje sie dalej edytowac', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'users/stareKonto3'), {
        id: 'stareKonto3', role: 'PATIENT', verified: false, admin: false,
        coinsBalance: 0, firstName: 'Stary', rating: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto3')
    await assertSucceeds(updateDoc(doc(db, 'users/stareKonto3'), {
      firstName: 'Zaktualizowany', isVerified: false, isAdmin: false,
    }))
  })

  // ---------------------------------------------------------------------------
  // DWIE ZNANE DZIURY W NIEZMIENNIKU OCENY. Oba testy sa celowo pominiete —
  // opisuja stan pozadany, ktorego dzisiejsze reguly NIE zapewniaja.
  // Zamkniecie wymaga zmiany modelu danych, nie poprawki reguly:
  //   * `ratingSum` jako liczba calkowita zamiast `rating` jako srednia
  //     (arytmetyka calkowita w regule jest dokladna, na doublach rownosc jest krucha),
  //   * dokument-znacznik odbytej wizyty o deterministycznym identyfikatorze,
  //     bo reguly Firestore nie potrafia odpytywac kolekcji, tylko `get()` po sciezce.
  // Zaplanowane jako osobne zadanie na poczatku Etapu 5.
  // ---------------------------------------------------------------------------

  it.skip('recenzja wymaga odbytej wizyty — konto bez historii nie podbija oceny', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog5'), {
        id: 'psycholog5', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
        coinsBalance: 0, firstName: 'Ala', rating: 0, ratingCount: 0,
      })
    })
    const obcy = authedDb(env, 'nieznajomy')
    const b = writeBatch(obcy)
    b.set(doc(obcy, 'reviews/psycholog5_nieznajomy'), {
      reviewId: 'psycholog5_nieznajomy', psychologistId: 'psycholog5',
      reviewerId: 'nieznajomy', reviewerName: 'Nieznajomy',
      rating: 5, comment: '', timestamp: 1,
    })
    b.update(doc(obcy, 'users/psycholog5'), { rating: 5, ratingCount: 1 })
    await assertFails(b.commit())
  })

  it.skip('pojedyncza recenzja nie przesuwa sredniej dowolnie — wartosc musi wynikac z historii', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog6'), {
        id: 'psycholog6', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
        coinsBalance: 0, firstName: 'Ala', rating: 1.0, ratingCount: 9998,
      })
    })
    const recenzent = authedDb(env, 'pacjent')
    const b = writeBatch(recenzent)
    b.set(doc(recenzent, 'reviews/psycholog6_pacjent'), {
      reviewId: 'psycholog6_pacjent', psychologistId: 'psycholog6',
      reviewerId: 'pacjent', reviewerName: 'Bogdan',
      rating: 5, comment: '', timestamp: 1,
    })
    b.update(doc(recenzent, 'users/psycholog6'), { rating: 5, ratingCount: 9999 })
    await assertFails(b.commit())
  })
})
