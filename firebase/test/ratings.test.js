import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteDoc, doc, getDoc, runTransaction, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv, unauthedDb , zalozUzytkownika } from './helpers.js'

let env

function znacznik(psychologistId, patientId) {
  return { psychologistId, patientId, appointmentId: 'wizyta_' + psychologistId, completedAt: 1 }
}

function recenzja(psych, recenzent, rating) {
  return {
    reviewId: psych + '_' + recenzent, psychologistId: psych,
    reviewerId: recenzent, reviewerName: 'Bogdan', rating, comment: 'x', timestamp: 1,
  }
}

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psych', {
      id: 'psych', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, firstName: 'Ala', pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/recenzent', {
      id: 'recenzent', role: 'PATIENT', isVerified: false, isAdmin: false,
      coinsBalance: 0, firstName: 'Bogdan',
    })
    await setDoc(doc(db, 'completions/psych_recenzent'), znacznik('psych', 'recenzent'))
  })
})

async function seedAgregat(sum, count) {
  await seed(env, async (db) => {
    await setDoc(doc(db, 'ratings/psych'), { sum, count })
  })
}

async function seedRecenzje(rating) {
  await seed(env, async (db) => {
    await setDoc(doc(db, 'reviews/psych_recenzent'), recenzja('psych', 'recenzent', rating))
  })
}

function partiaTworzaca(rating, agregat) {
  const db = authedDb(env, 'recenzent')
  const b = writeBatch(db)
  b.set(doc(db, 'reviews/psych_recenzent'), recenzja('psych', 'recenzent', rating))
  b.set(doc(db, 'ratings/psych'), agregat)
  return b
}

function partiaPodbijajaca(rating, zmiana) {
  const db = authedDb(env, 'recenzent')
  const b = writeBatch(db)
  b.set(doc(db, 'reviews/psych_recenzent'), recenzja('psych', 'recenzent', rating))
  b.update(doc(db, 'ratings/psych'), zmiana)
  return b
}

describe('ratings — odczyt', () => {
  it('1. kazdy zalogowany czyta agregat', async () => {
    await seedAgregat(8, 2)
    const db = authedDb(env, 'recenzent')
    const snap = await assertSucceeds(getDoc(doc(db, 'ratings/psych')))
    expect(snap.data()).toEqual({ sum: 8, count: 2 })
  })

  it('2. niezalogowany nie czyta agregatu', async () => {
    await seedAgregat(8, 2)
    await assertFails(getDoc(doc(unauthedDb(env), 'ratings/psych')))
  })
})

describe('ratings — zapis przez recenzenta', () => {
  it('3. recenzent tworzy agregat razem z recenzja w tej samej partii', async () => {
    await assertSucceeds(partiaTworzaca(4, { sum: 4, count: 1 }).commit())
  })

  it('4. recenzent podbija istniejacy agregat o swoja ocene', async () => {
    await seedAgregat(8, 2)
    await assertSucceeds(partiaPodbijajaca(4, { sum: 12, count: 3 }).commit())
  })

  it('5. sum wieksze niz ocena w recenzji jest odrzucone', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 13, count: 3 }).commit())
  })

  it('5b. sum mniejsze niz ocena w recenzji jest odrzucone', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 11, count: 3 }).commit())
  })

  it('5c. utworzenie agregatu z sum wiekszym niz ocena jest odrzucone', async () => {
    await assertFails(partiaTworzaca(4, { sum: 5000, count: 1 }).commit())
  })

  it('6. count zwiekszone o 2 jest odrzucone', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 12, count: 4 }).commit())
  })

  it('6b. utworzenie agregatu z count 2 jest odrzucone', async () => {
    await assertFails(partiaTworzaca(4, { sum: 4, count: 2 }).commit())
  })

  it('7. zapis agregatu bez recenzji w tej samej partii jest odrzucony', async () => {
    await seedAgregat(8, 2)
    const db = authedDb(env, 'recenzent')
    await assertFails(updateDoc(doc(db, 'ratings/psych'), { sum: 13, count: 3 }))
    await assertFails(setDoc(doc(db, 'ratings/nowy_psych'), { sum: 5, count: 1 }))
  })

  it('8. recenzja, ktora juz istniala przed partia, nie pozwala podbic agregatu', async () => {
    await seedRecenzje(4)
    await seedAgregat(4, 1)
    await assertFails(partiaPodbijajaca(4, { sum: 8, count: 2 }).commit())
  })

  it('8b. kontrola: ta sama partia przechodzi, gdy recenzji jeszcze nie bylo', async () => {
    await seedAgregat(4, 1)
    await assertSucceeds(partiaPodbijajaca(4, { sum: 8, count: 2 }).commit())
  })

  it('12. obcy dopisuje do agregatu pole poza sum i count — odmowa przy update i create', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 12, count: 3, pinned: true }).commit())
    await assertFails(partiaTworzaca(4, { sum: 4, count: 1, pinned: true }).commit())
  })

  it('13. ocena 0 i ocena 6 w recenzji nie pozwalaja podbic agregatu', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(0, { sum: 8, count: 3 }).commit())
    await assertFails(partiaPodbijajaca(6, { sum: 14, count: 3 }).commit())
    await assertFails(partiaTworzaca(0, { sum: 0, count: 1 }).commit())
  })

  it('13b. kontrola: skrajne oceny 1 i 5 przechodza', async () => {
    await seedAgregat(8, 2)
    await assertSucceeds(partiaPodbijajaca(1, { sum: 9, count: 3 }).commit())
    await seed(env, async (db) => { await deleteDoc(doc(db, 'reviews/psych_recenzent')) })
    await assertSucceeds(partiaPodbijajaca(5, { sum: 14, count: 4 }).commit())
  })

  it('14. sum i count, ktore nie sa liczbami calkowitymi, sa odrzucone', async () => {
    await seedAgregat(8, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 12.5, count: 3 }).commit())
    await assertFails(partiaPodbijajaca(4, { sum: 12, count: 3.5 }).commit())
    await assertFails(partiaPodbijajaca(4, { sum: '12', count: '3' }).commit())
    await assertFails(partiaTworzaca(4.5, { sum: 4.5, count: 1 }).commit())
  })

  it('14b. sum bedace ulamkiem w istniejacym agregacie nie przechodzi, nawet gdy arytmetyka sie zgadza (kontrola typu)', async () => {
    await seedAgregat(8.5, 2)
    await assertFails(partiaPodbijajaca(4, { sum: 12.5, count: 3 }).commit())
  })

  it('recenzent nie moze zapisac agregatu o sobie samym', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'completions/recenzent_recenzent'), znacznik('recenzent', 'recenzent'))
    })
    const db = authedDb(env, 'recenzent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/recenzent_recenzent'), recenzja('recenzent', 'recenzent', 5))
    b.set(doc(db, 'ratings/recenzent'), { sum: 5, count: 1 })
    await assertFails(b.commit())
  })

  it('recenzja wystawiona przez kogos innego nie uprawnia piszacego do agregatu', async () => {
    await seedAgregat(8, 2)
    const db = authedDb(env, 'obcy')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psych_recenzent'), recenzja('psych', 'recenzent', 4))
    b.update(doc(db, 'ratings/psych'), { sum: 12, count: 3 })
    await assertFails(b.commit())
  })
})

describe('ratings — oceniany', () => {
  it('9. oceniany nie podbija wlasnego agregatu', async () => {
    await seedAgregat(8, 2)
    await seed(env, async (db) => {
      await setDoc(doc(db, 'completions/psych_psych'), znacznik('psych', 'psych'))
    })
    const db = authedDb(env, 'psych')
    await assertFails(updateDoc(doc(db, 'ratings/psych'), { sum: 500, count: 100 }))
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psych_psych'), recenzja('psych', 'psych', 5))
    b.update(doc(db, 'ratings/psych'), { sum: 13, count: 3 })
    await assertFails(b.commit())
  })

  it('9b. oceniany nie tworzy wlasnego agregatu', async () => {
    const db = authedDb(env, 'psych')
    await assertFails(setDoc(doc(db, 'ratings/psych'), { sum: 500, count: 100 }))
  })

  it('10. oceniany nie usuwa wlasnego agregatu', async () => {
    await seedAgregat(8, 2)
    const db = authedDb(env, 'psych')
    await assertFails(deleteDoc(doc(db, 'ratings/psych')))
  })

  it('10b. recenzent nie usuwa agregatu', async () => {
    await seedAgregat(8, 2)
    const db = authedDb(env, 'recenzent')
    await assertFails(deleteDoc(doc(db, 'ratings/psych')))
  })

  it('11. oceniany usuwa i odtwarza swoj dokument users — agregat zostaje nietkniety', async () => {
    await seedAgregat(45, 10)
    const db = authedDb(env, 'psych')
    await assertSucceeds(deleteDoc(doc(db, 'users/psych')))
    await assertSucceeds(setDoc(doc(db, 'users/psych'), {
      id: 'psych', role: 'PSYCHOLOGIST', isVerified: false, isAdmin: false,
      firstName: 'Ala',
    }))
    const snap = await assertSucceeds(getDoc(doc(db, 'ratings/psych')))
    expect(snap.data()).toEqual({ sum: 45, count: 10 })
  })

  it('11b. po odtworzeniu profilu agregat nadal nie daje sie wyzerowac', async () => {
    await seedAgregat(45, 10)
    const db = authedDb(env, 'psych')
    await assertSucceeds(deleteDoc(doc(db, 'users/psych')))
    await assertSucceeds(setDoc(doc(db, 'users/psych'), {
      id: 'psych', role: 'PSYCHOLOGIST', isVerified: false, isAdmin: false,
    }))
    await assertFails(setDoc(doc(db, 'ratings/psych'), { sum: 0, count: 0 }))
    await assertFails(deleteDoc(doc(db, 'ratings/psych')))
  })
})

describe('ratings — przypadki przeniesione z users.test.js', () => {
  async function seedPsycholog(id, sum, count) {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/' + id, {
        id, role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0, firstName: 'Ala',
      })
      await setDoc(doc(db, 'ratings/' + id), { sum, count })
      await setDoc(doc(db, 'completions/' + id + '_pacjent'), znacznik(id, 'pacjent'))
    })
  }

  function reviewBatch(id, rating, agregat) {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/' + id + '_pacjent'), {
      reviewId: id + '_pacjent', psychologistId: id, reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating, comment: 'x', timestamp: 1,
    })
    b.update(doc(db, 'ratings/' + id), agregat)
    return b
  }

  it('recenzja wymaga odbytej wizyty — konto bez historii nie podbija oceny', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'ratings/psycholog5'), { sum: 0, count: 0 })
    })
    const obcy = authedDb(env, 'nieznajomy')
    const b = writeBatch(obcy)
    b.set(doc(obcy, 'reviews/psycholog5_nieznajomy'), {
      reviewId: 'psycholog5_nieznajomy', psychologistId: 'psycholog5',
      reviewerId: 'nieznajomy', reviewerName: 'Nieznajomy',
      rating: 5, comment: '', timestamp: 1,
    })
    b.update(doc(obcy, 'ratings/psycholog5'), { sum: 5, count: 1 })
    await assertFails(b.commit())
  })

  it('ta sama recenzja ze znacznikiem odbytej wizyty przechodzi — kontrola ataku z konta bez historii', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'ratings/psycholog5'), { sum: 0, count: 0 })
      await setDoc(doc(db, 'completions/psycholog5_nieznajomy'), znacznik('psycholog5', 'nieznajomy'))
    })
    const obcy = authedDb(env, 'nieznajomy')
    const b = writeBatch(obcy)
    b.set(doc(obcy, 'reviews/psycholog5_nieznajomy'), {
      reviewId: 'psycholog5_nieznajomy', psychologistId: 'psycholog5',
      reviewerId: 'nieznajomy', reviewerName: 'Nieznajomy',
      rating: 5, comment: '', timestamp: 1,
    })
    b.update(doc(obcy, 'ratings/psycholog5'), { sum: 5, count: 1 })
    await assertSucceeds(b.commit())
  })

  it('pojedyncza recenzja nie przesuwa sredniej dowolnie — wartosc musi wynikac z historii', async () => {
    await seedPsycholog('psycholog6', 9998, 9998)
    await assertFails(reviewBatch('psycholog6', 5, { sum: 5 * 9999, count: 9999 }).commit())
  })

  it('recenzja podnosi sum o ocene i count o jeden', async () => {
    await seedPsycholog('ps_ok', 8, 2)
    await assertSucceeds(reviewBatch('ps_ok', 4, { sum: 12, count: 3 }).commit())
  })

  it('sum podniesione o wiecej niz ocena w recenzji jest odrzucone', async () => {
    await seedPsycholog('ps_zawyzona', 8, 2)
    await assertFails(reviewBatch('ps_zawyzona', 4, { sum: 13, count: 3 }).commit())
  })

  it('sum podniesione o mniej niz ocena w recenzji jest odrzucone', async () => {
    await seedPsycholog('ps_zanizona', 8, 2)
    await assertFails(reviewBatch('ps_zanizona', 4, { sum: 11, count: 3 }).commit())
  })

  it('sum podniesione bez recenzji jest odrzucone', async () => {
    await seedPsycholog('ps_bez', 8, 2)
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'ratings/ps_bez'), { sum: 13 }))
  })

  it('sum i count podniesione bez recenzji jest odrzucone', async () => {
    await seedPsycholog('ps_bez2', 8, 2)
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'ratings/ps_bez2'), { sum: 13, count: 3 }))
  })

  it('count podniesione o dwa jest odrzucone', async () => {
    await seedPsycholog('ps_licznik', 8, 2)
    await assertFails(reviewBatch('ps_licznik', 4, { sum: 12, count: 4 }).commit())
  })

  it('ocena powyzej piatki w recenzji jest odrzucona mimo spojnej sumy', async () => {
    await seedPsycholog('ps_szostka', 8, 2)
    await assertFails(reviewBatch('ps_szostka', 6, { sum: 14, count: 3 }).commit())
  })

  it('ocena ponizej jedynki w recenzji jest odrzucona mimo spojnej sumy', async () => {
    await seedPsycholog('ps_zero', 8, 2)
    await assertFails(reviewBatch('ps_zero', 0, { sum: 8, count: 3 }).commit())
  })

  it('krancowe oceny 1 i 5 przechodza', async () => {
    await seedPsycholog('ps_jeden', 8, 2)
    await assertSucceeds(reviewBatch('ps_jeden', 1, { sum: 9, count: 3 }).commit())
    await seedPsycholog('ps_piec', 8, 2)
    await assertSucceeds(reviewBatch('ps_piec', 5, { sum: 13, count: 3 }).commit())
  })

  it('wlasciciel nie moze podniesc sobie sum', async () => {
    await seedPsycholog('ps_wlasciciel', 8, 2)
    const db = authedDb(env, 'ps_wlasciciel')
    await assertFails(updateDoc(doc(db, 'ratings/ps_wlasciciel'), { sum: 500 }))
  })

  it('wlasciciel nie moze podniesc sobie samego count', async () => {
    await seedPsycholog('ps_wlasciciel2', 8, 2)
    const db = authedDb(env, 'ps_wlasciciel2')
    await assertFails(updateDoc(doc(db, 'ratings/ps_wlasciciel2'), { count: 100 }))
  })

  it('ocena niecalkowita w recenzji jest odrzucona mimo spojnej sumy', async () => {
    await seedPsycholog('ps_ulamek', 8, 2)
    await assertFails(reviewBatch('ps_ulamek', 4.5, { sum: 12.5, count: 3 }).commit())
  })
})

describe('reviews — zakres oceny przy tworzeniu', () => {
  function bezPola() {
    const r = recenzja('psych', 'recenzent', 5)
    delete r.rating
    return r
  }

  it('rating 0, 6, 100, tekst "5", ulamek i brak pola — odmowa', async () => {
    const db = authedDb(env, 'recenzent')
    const ref = doc(db, 'reviews/psych_recenzent')
    await assertFails(setDoc(ref, recenzja('psych', 'recenzent', 0)))
    await assertFails(setDoc(ref, recenzja('psych', 'recenzent', 6)))
    await assertFails(setDoc(ref, recenzja('psych', 'recenzent', 100)))
    await assertFails(setDoc(ref, recenzja('psych', 'recenzent', '5')))
    await assertFails(setDoc(ref, recenzja('psych', 'recenzent', 4.5)))
    await assertFails(setDoc(ref, bezPola()))
  })

  it('rating 1 i 5 — przechodzi', async () => {
    const db = authedDb(env, 'recenzent')
    const ref = doc(db, 'reviews/psych_recenzent')
    await assertSucceeds(setDoc(ref, recenzja('psych', 'recenzent', 1)))
    await seed(env, async (s) => { await deleteDoc(doc(s, 'reviews/psych_recenzent')) })
    await assertSucceeds(setDoc(ref, recenzja('psych', 'recenzent', 5)))
  })
})

describe('reviews — zaliczonej oceny nie wolno zmienic', () => {
  it('ocena 5 zaliczona do agregatu; zmiana na 1 odmowa; zmiana samej tresci przechodzi; agregat bez zmian', async () => {
    const db = authedDb(env, 'recenzent')
    await assertSucceeds(partiaTworzaca(5, { sum: 5, count: 1 }).commit())

    const ref = doc(db, 'reviews/psych_recenzent')
    await assertFails(updateDoc(ref, { rating: 1 }))
    await assertFails(partiaPodbijajaca(1, { sum: 6, count: 2 }).commit())
    expect((await getDoc(doc(db, 'ratings/psych'))).data()).toEqual({ sum: 5, count: 1 })
    expect((await getDoc(ref)).data().rating).toBe(5)

    await assertSucceeds(updateDoc(ref, { comment: 'poprawiona tresc' }))
    const po = (await getDoc(ref)).data()
    expect(po.comment).toBe('poprawiona tresc')
    expect(po.rating).toBe(5)
    expect((await getDoc(doc(db, 'ratings/psych'))).data()).toEqual({ sum: 5, count: 1 })
  })
})

describe('ratings — zapis przez transakcje (tak jak robi to klient)', () => {
  function oceniajTransakcja(rating, policzAgregat) {
    const db = authedDb(env, 'recenzent')
    return runTransaction(db, async (tx) => {
      const ref = doc(db, 'ratings/psych')
      const snap = await tx.get(ref)
      const biezacy = snap.exists() ? snap.data() : { sum: 0, count: 0 }
      tx.set(doc(db, 'reviews/psych_recenzent'), recenzja('psych', 'recenzent', rating))
      tx.set(ref, policzAgregat(biezacy, rating), { merge: true })
    })
  }

  const wlasciwy = (biezacy, rating) => ({ sum: biezacy.sum + rating, count: biezacy.count + 1 })

  it('pierwsza ocena przez transakcje tworzy agregat', async () => {
    await assertSucceeds(oceniajTransakcja(4, wlasciwy))
    const po = await getDoc(doc(authedDb(env, 'recenzent'), 'ratings/psych'))
    expect(po.data()).toEqual({ sum: 4, count: 1 })
  })

  it('kolejna ocena przez transakcje podbija istniejacy agregat', async () => {
    await seedAgregat(8, 2)
    await assertSucceeds(oceniajTransakcja(4, wlasciwy))
    const po = await getDoc(doc(authedDb(env, 'recenzent'), 'ratings/psych'))
    expect(po.data()).toEqual({ sum: 12, count: 3 })
  })

  it('transakcja liczaca od pustego agregatu przy istniejacym dostaje odmowe (skutek pomylonej nazwy kolekcji w odczycie)', async () => {
    await seedAgregat(8, 2)
    await assertFails(oceniajTransakcja(4, (_, rating) => ({ sum: rating, count: 1 })))
  })
})
