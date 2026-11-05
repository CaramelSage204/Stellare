import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteDoc, doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv, unauthedDb , zalozUzytkownika } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 100,
    })
    await zalozUzytkownika(db, 'users/intruz', {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'appointments/wizyta1'), wizyta('wizyta1', 'pacjent', 'BOOKED'))
  })
})

function wizyta(appointmentId, patientId, status, psychologistId = 'psycholog') {
  return {
    appointmentId, psychologistId, patientId, date: '2026-10-01', time: '10:00',
    notes: '', status, priceCoins: 80,
  }
}

function znacznik(overrides = {}) {
  return {
    psychologistId: 'psycholog', patientId: 'pacjent', appointmentId: 'wizyta1',
    completedAt: 1, ...overrides,
  }
}

function zakonczWizyte(db, appointmentId, markerId, marker) {
  const b = writeBatch(db)
  b.update(doc(db, 'appointments/' + appointmentId), { status: 'COMPLETED' })
  if (markerId) b.set(doc(db, 'completions/' + markerId), marker)
  return b
}

function seedZnacznik(id = 'psycholog_pacjent', data = znacznik()) {
  return seed(env, (db) => setDoc(doc(db, 'completions/' + id), data))
}

function recenzja(db, ocena = 5) {
  const b = writeBatch(db)
  b.set(doc(db, 'reviews/psycholog_pacjent'), {
    reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
    reviewerName: 'Bogdan', rating: ocena, comment: '', timestamp: 1,
  })
  b.set(doc(db, 'ratings/psycholog'), { sum: ocena, count: 1 })
  return b
}

describe('znacznik odbytej wizyty — tworzenie', () => {
  it('psycholog oznaczajacy wizyte jako odbyta tworzy znacznik', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('pacjent oznaczajacy wizyte jako odbyta tez tworzy znacznik (swiadoma decyzja)', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('znacznik bez zmiany statusu wizyty jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'completions/psycholog_pacjent'), znacznik()))
  })

  it('znacznik z wizyta zmieniona na inny status niz COMPLETED jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/wizyta1'), { status: 'CANCELLED', cancelledBy: 'psycholog' })
    b.set(doc(db, 'completions/psycholog_pacjent'), znacznik())
    await assertFails(b.commit())
  })

  it('znacznik wskazujacy inna wizyte niz ta, ktora sie konczy, jest odrzucony', async () => {
    await seed(env, (s) => setDoc(doc(s, 'appointments/wizyta2'), wizyta('wizyta2', 'pacjent', 'BOOKED')))
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik({ appointmentId: 'wizyta2' })).commit())
  })

  it('znacznik wizyty juz odbytej jest odrzucony', async () => {
    await seed(env, (s) => setDoc(doc(s, 'appointments/wizyta1'), wizyta('wizyta1', 'pacjent', 'COMPLETED')))
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('znacznik wolnego terminu jest odrzucony', async () => {
    await seed(env, (s) => setDoc(doc(s, 'appointments/wolny'), wizyta('wolny', null, 'FREE')))
    const db = authedDb(env, 'psycholog')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/wolny'), { status: 'COMPLETED' })
    b.set(doc(db, 'completions/psycholog_pacjent'), znacznik({ appointmentId: 'wolny' }))
    await assertFails(b.commit())
  })

  it('identyfikator o zamienionej kolejnosci par jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'pacjent_psycholog', znacznik()).commit())
  })

  it('identyfikator bez czesci pacjenta jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog', znacznik()).commit())
  })

  it('identyfikator bez czesci psychologa jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'pacjent', znacznik()).commit())
  })

  it('identyfikator z cudzym pacjentem jest odrzucony mimo zgodnych pol wizyty', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_intruz', znacznik()).commit())
  })

  it('pole patientId niezgodne z wizyta jest odrzucone mimo zgodnego identyfikatora', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_intruz', znacznik({ patientId: 'intruz' })).commit())
  })

  it('pole psychologistId niezgodne z wizyta jest odrzucone mimo zgodnego identyfikatora', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'inny_pacjent', znacznik({ psychologistId: 'inny' })).commit())
  })

  it('znacznik z dodatkowym polem jest odrzucony', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik({ bonus: 1 })).commit())
  })

  it('znacznik tworzony przez osobe trzecia jest odrzucony', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('niezalogowany nie tworzy znacznika', async () => {
    const db = unauthedDb(env)
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })
})

describe('znacznik odbytej wizyty — nie przed terminem wizyty', () => {
  const TRZY_DNI = 3 * 24 * 60 * 60 * 1000

  function zTerminem(startsAtMillis) {
    return seed(env, (s) => setDoc(doc(s, 'appointments/wizyta1'), { ...wizyta('wizyta1', 'pacjent', 'BOOKED'), startsAtMillis }))
  }

  it('znacznik wizyty zaplanowanej za trzy dni jest odrzucony', async () => {
    await zTerminem(Date.now() + TRZY_DNI)
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('znacznik wizyty zaczynajacej sie za minute jest odrzucony', async () => {
    await zTerminem(Date.now() + 60 * 1000)
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('znacznik wizyty, ktora sie zaczela, jest przyjety', async () => {
    await zTerminem(Date.now() - 60 * 1000)
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('znacznik wizyty sprzed trzech dni jest przyjety', async () => {
    await zTerminem(Date.now() - TRZY_DNI)
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('pacjent tez nie wytworzy znacznika przed terminem', async () => {
    await zTerminem(Date.now() + TRZY_DNI)
    const db = authedDb(env, 'pacjent')
    await assertFails(zakonczWizyte(db, 'wizyta1', 'psycholog_pacjent', znacznik()).commit())
  })

  it('wczesne oznaczenie wizyty jako odbytej bez znacznika tez jest odrzucone', async () => {
    await zTerminem(Date.now() + TRZY_DNI)
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta1').commit())
  })

  it('wizyta bez znacznika po terminie moze zostac oznaczona jako odbyta', async () => {
    await zTerminem(Date.now() - 60 * 1000)
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(zakonczWizyte(db, 'wizyta1').commit())
  })

  it('odwolanie wizyty z przyszlosci nadal dziala', async () => {
    await zTerminem(Date.now() + TRZY_DNI)
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'appointments/wizyta1'), { status: 'CANCELLED', cancelledBy: 'psycholog' }))
  })
})

describe('znacznik odbytej wizyty — niezmiennosc i odczyt', () => {
  it('znacznika nie da sie zmienic', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'completions/psycholog_pacjent'), { completedAt: 2 }))
  })

  it('znacznika nie moze zmienic takze pacjent', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'completions/psycholog_pacjent'), { completedAt: 2 }))
  })

  it('znacznika nie da sie skasowac', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'psycholog')
    await assertFails(deleteDoc(doc(db, 'completions/psycholog_pacjent')))
  })

  it('znacznika nie moze skasowac takze pacjent', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'pacjent')
    await assertFails(deleteDoc(doc(db, 'completions/psycholog_pacjent')))
  })

  it('zalogowany czyta znacznik', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'intruz')
    await assertSucceeds(getDoc(doc(db, 'completions/psycholog_pacjent')))
  })

  it('niezalogowany nie czyta znacznika', async () => {
    await seedZnacznik()
    const db = unauthedDb(env)
    await assertFails(getDoc(doc(db, 'completions/psycholog_pacjent')))
  })
})

describe('druga wizyta tej samej pary', () => {
  beforeEach(async () => {
    await seedZnacznik()
    await seed(env, (s) => setDoc(doc(s, 'appointments/wizyta2'), wizyta('wizyta2', 'pacjent', 'BOOKED')))
  })

  it('partia ze statusem i ponownym znacznikiem jest odrzucona w calosci', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(zakonczWizyte(db, 'wizyta2', 'psycholog_pacjent', znacznik({ appointmentId: 'wizyta2' })).commit())
  })

  it('sama zmiana statusu bez znacznika przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(zakonczWizyte(db, 'wizyta2').commit())
  })
})

describe('recenzja wymaga znacznika odbytej wizyty', () => {
  it('recenzja bez znacznika jest odrzucona', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(recenzja(db).commit())
  })

  it('sama recenzja (bez zmiany oceny) bez znacznika jest odrzucona', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: '', timestamp: 1,
    }))
  })

  it('recenzja ze znacznikiem przechodzi', async () => {
    await seedZnacznik()
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(recenzja(db).commit())
  })

  it('znacznik innej pary nie uprawnia do recenzji', async () => {
    await seedZnacznik('psycholog_pacjent')
    const db = authedDb(env, 'intruz')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_intruz'), {
      reviewId: 'psycholog_intruz', psychologistId: 'psycholog', reviewerId: 'intruz',
      reviewerName: 'Intruz', rating: 5, comment: '', timestamp: 1,
    })
    b.set(doc(db, 'ratings/psycholog'), { sum: 5, count: 1 })
    await assertFails(b.commit())
  })

  it('znacznik u innego psychologa nie uprawnia do recenzji tego psychologa', async () => {
    await seedZnacznik('inny_pacjent', znacznik({ psychologistId: 'inny' }))
    const db = authedDb(env, 'pacjent')
    await assertFails(recenzja(db).commit())
  })
})
