import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertSucceeds } from '@firebase/rules-unit-testing'
import {
  collection, collectionGroup, deleteDoc, deleteField, doc, getDoc, getDocs, increment, setDoc, writeBatch,
} from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv, sumaMonetWSystemie, zalozUzytkownika } from './helpers.js'

// Kształty zapisów, które wysyła klient Kotlin po przeniesieniu telefonu, e-maila i salda do
// users/{uid}/private/contact: scalający zapis profilu publicznego z deleteField(), zatwierdzenie
// weryfikacji z deleteField(), dokument prywatny z jawnym coinsBalance: 0 oraz saldo zmieniane
// WYŁĄCZNIE przez increment() (strona rozliczająca nie może odczytać dokumentu kontrahenta).
// Testy z increment() powtarzają też ataki partii wielodokumentowych, bo przyrost jest tą samą
// wartością, którą kilka depozytów może próbować wykorzystać naraz.

let env

const CENA = 80
const GODZINA = 60 * 60 * 1000
const DZIEN = 24 * GODZINA
const PRYWATNY = (uid) => `users/${uid}/private/contact`

const PROFIL = {
  id: 'nowy', firstName: 'Jan', lastName: 'Kowalski', age: 30, gender: 'M', role: 'PATIENT', isVerified: false,
  isAdmin: false, qualifications: '', specializations: '', pricePerSession: 0, bio: '', customPrices: '',
  profileImageUri: null,
}
const CZYSZCZENIE = { phone: deleteField(), email: deleteField(), coinsBalance: deleteField() }

const odczytaj = async (sciezka) => {
  let dane
  await seed(env, async (db) => { dane = (await getDoc(doc(db, sciezka))).data() })
  return dane
}

const sumaMonet = async () => {
  const suma = await sumaMonetWSystemie(env, { collectionGroup, collection, getDocs })
  if (suma === 0) throw new Error('sumaMonet widzi zero monet - zly zrodlowy zbior dokumentow')
  return suma
}

async function odparty(szkoda, b) {
  const przed = await sumaMonet()
  let blad = null
  try { await b.commit() } catch (e) { blad = e }
  const po = await sumaMonet()
  expect(blad, 'ATAK PRZESZEDL PRZEZ REGULY: ' + szkoda).not.toBeNull()
  expect(blad?.code, 'odmowa nie z regul (' + blad?.message + '), wiec nie dowodzi obrony: ' + szkoda).toBe('permission-denied')
  expect(po, szkoda + ' — suma sald + depozytow HELD zmienila sie z ' + przed + ' na ' + po).toBe(przed)
}

async function przechodzi(opis, b, zmianaSumy = 0) {
  const przed = await sumaMonet()
  await assertSucceeds(b.commit())
  const po = await sumaMonet()
  expect(po - przed, opis + ' — monety powstaly z niczego albo przepadly').toBe(zmianaSumy)
}

const partia = (aktor, ops) => {
  const db = authedDb(env, aktor)
  const b = writeBatch(db)
  ops(b, db)
  return b
}

const wizyta = (id, over = {}) => ({
  appointmentId: id, psychologistId: 'psycholog', patientId: 'pacjent', date: '2026-10-01', time: '10:00',
  notes: '', status: 'BOOKED', priceCoins: CENA, startsAtMillis: Date.now() + 3 * DZIEN, ...over,
})

const depozyt = (id, over = {}) => ({
  appointmentId: id, patientId: 'pacjent', psychologistId: 'psycholog', amount: CENA, status: 'HELD', ...over,
})

const przyrost = (b, db, uid, kwota, deklaracja) => {
  const pola = { coinsBalance: increment(kwota) }
  if (deklaracja !== undefined) pola.escrowSettlement = deklaracja
  b.update(doc(db, PRYWATNY(uid)), pola)
}

async function seedDwaDepozyty({ startsAtMillis = Date.now() - 2 * GODZINA } = {}) {
  await seed(env, async (db) => {
    for (const id of ['w1', 'w2']) {
      await setDoc(doc(db, 'appointments/' + id), wizyta(id, { startsAtMillis }))
      await setDoc(doc(db, 'escrows/' + id), depozyt(id))
    }
  })
}

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, pricePerSession: CENA,
      phone: '', email: 'psycholog@example.com', coinsBalance: 50,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
      phone: '', email: 'pacjent@example.com', coinsBalance: 200,
    })
    await zalozUzytkownika(db, 'users/obcy', {
      id: 'obcy', role: 'PATIENT', isVerified: false, isAdmin: false,
      phone: '', email: 'obcy@example.com', coinsBalance: 200,
    })
    await setDoc(doc(db, 'admins/szef'), {})
  })
})

describe('zapis profilu publicznego: scalenie z deleteField()', () => {
  it('nowe konto: scalajacy zapis czternastu pol i czyszczenia tworzy profil bez pol prywatnych', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), { ...PROFIL, ...CZYSZCZENIE }, { merge: true }))
    const zapisany = await odczytaj('users/nowy')
    expect(Object.keys(zapisany ?? {}).sort()).toEqual(Object.keys(PROFIL).sort())
  })

  it('profil sprzed przeniesienia: ten sam zapis przechodzi i usuwa pola prywatne razem ze zmiana', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stary'), {
        ...PROFIL, id: 'stary', phone: '', email: 'stary@example.com', coinsBalance: 7, ratingSum: 9, ratingCount: 2,
      })
    })
    const db = authedDb(env, 'stary')
    await assertSucceeds(setDoc(doc(db, 'users/stary'), { ...PROFIL, id: 'stary', bio: 'nowe bio', ...CZYSZCZENIE }, { merge: true }))
    const po = await odczytaj('users/stary')
    expect(po.bio).toBe('nowe bio')
    for (const pole of ['phone', 'email', 'coinsBalance']) expect(Object.hasOwn(po, pole), 'pole ' + pole + ' zostalo w profilu publicznym').toBe(false)
    expect(po.ratingSum, 'scalanie nie moze kasowac pol, ktorych model nie zna').toBe(9)
  })

  it('profil sprzed przeniesienia: zapis BEZ czyszczenia jest odrzucany, wiec czyszczenie nie moze isc po zmianie', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stary'), { ...PROFIL, id: 'stary', phone: '', email: 'stary@example.com', coinsBalance: 7 })
    })
    await odrzucone('edycja starego profilu bez deleteField() zostawia pola prywatne w dokumencie czytanym przez kazdego',
      setDoc(doc(authedDb(env, 'stary'), 'users/stary'), { ...PROFIL, id: 'stary', bio: 'x' }, { merge: true }))
  })

  it('profil juz oczyszczony: ten sam zapis przechodzi bez wzgledu na kolejna edycje', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'users/pacjent'), { ...PROFIL, id: 'pacjent', bio: 'x', ...CZYSZCZENIE }, { merge: true }))
    await assertSucceeds(setDoc(doc(db, 'users/pacjent'), { ...PROFIL, id: 'pacjent', bio: 'y', ...CZYSZCZENIE }, { merge: true }))
  })
})

describe('zatwierdzenie weryfikacji przez administratora', () => {
  it('stary profil: update z isVerified i deleteField() przechodzi i czysci pola prywatne', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stary'), { ...PROFIL, id: 'stary', role: 'PSYCHOLOGIST', phone: '', email: 'a@b.pl', coinsBalance: 3 })
    })
    await assertSucceeds(partia('szef', (b, db) => b.update(doc(db, 'users/stary'), { isVerified: true, ...CZYSZCZENIE })).commit())
    const po = await odczytaj('users/stary')
    expect(po.isVerified).toBe(true)
    for (const pole of ['phone', 'email', 'coinsBalance']) expect(Object.hasOwn(po, pole)).toBe(false)
  })

  it('stary profil: update z samym isVerified jest odrzucany', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stary'), { ...PROFIL, id: 'stary', role: 'PSYCHOLOGIST', phone: '', email: 'a@b.pl', coinsBalance: 3 })
    })
    await odrzucone('zatwierdzenie bez czyszczenia zostawia pola prywatne w profilu publicznym',
      partia('szef', (b, db) => b.update(doc(db, 'users/stary'), { isVerified: true })).commit())
  })

  it('profil juz oczyszczony: update z isVerified i deleteField() tez przechodzi', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/nowy'), { ...PROFIL, role: 'PSYCHOLOGIST' })
    })
    await assertSucceeds(partia('szef', (b, db) => b.update(doc(db, 'users/nowy'), { isVerified: true, ...CZYSZCZENIE })).commit())
  })
})

describe('dokument prywatny zakladany przy rejestracji', () => {
  it('phone, email i coinsBalance: 0 podane jawnie — przechodzi', async () => {
    await assertSucceeds(setDoc(doc(authedDb(env, 'nowy'), PRYWATNY('nowy')), { phone: '', email: 'jan@example.com', coinsBalance: 0 }))
  })

  it('bez coinsBalance — odmowa (konto zostaloby na stale bez portfela)', async () => {
    await odrzucone('dokument prywatny bez jawnego salda zostawia konto bez portfela',
      setDoc(doc(authedDb(env, 'nowy'), PRYWATNY('nowy')), { phone: '', email: 'jan@example.com' }))
  })

  it('z saldem wiekszym od zera — odmowa', async () => {
    await odrzucone('rejestracja z saldem startowym to bicie monet',
      setDoc(doc(authedDb(env, 'nowy'), PRYWATNY('nowy')), { phone: '', email: 'jan@example.com', coinsBalance: 1 }))
  })
})

describe('saldo zmieniane wylacznie przez increment()', () => {
  it('rezerwacja: increment(-cena) z deklaracja przechodzi, suma sald i depozytow sie nie zmienia', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/w1'), wizyta('w1', { status: 'FREE', patientId: null }))
    })
    await przechodzi('rezerwacja przyrostem', partia('pacjent', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      b.set(doc(db, 'escrows/w1'), depozyt('w1'))
      przyrost(b, db, 'pacjent', -CENA, 'w1')
      b.set(doc(db, 'wallet_transactions/w1_pacjent'), {
        transactionId: 'w1_pacjent', userId: 'pacjent', counterpartyId: 'psycholog', appointmentId: 'w1',
        amount: -CENA, type: 'BOOKING_PAYMENT', title: 'Oplata', timestamp: 1,
      })
    }))
  })

  it('wyplata po odbytej wizycie: increment(+kwota) na wlasnym dokumencie psychologa przechodzi', async () => {
    await seedDwaDepozyty()
    await przechodzi('wyplata przyrostem', partia('psycholog', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'COMPLETED' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA, 'w1')
      b.set(doc(db, 'wallet_transactions/w1_psycholog'), {
        transactionId: 'w1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent', appointmentId: 'w1',
        amount: CENA, type: 'BOOKING_EARNING', title: 'Wplyw', timestamp: 1,
      })
    }))
  })

  it('pozne odwolanie przez pacjenta: increment(+kwota) na CUDZYM dokumencie psychologa przechodzi bez odczytu', async () => {
    await seedDwaDepozyty({ startsAtMillis: Date.now() + 2 * GODZINA })
    await przechodzi('wyplata inicjowana przez pacjenta', partia('pacjent', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'CANCELLED', cancelledBy: 'pacjent' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA, 'w1')
      b.set(doc(db, 'wallet_transactions/w1_psycholog'), {
        transactionId: 'w1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent', appointmentId: 'w1',
        amount: CENA, type: 'BOOKING_EARNING', title: 'Wplyw', timestamp: 1,
      })
    }))
  })

  it('odwolanie przez psychologa: increment(+kwota) na CUDZYM dokumencie pacjenta przechodzi bez odczytu', async () => {
    await seedDwaDepozyty({ startsAtMillis: Date.now() + 2 * GODZINA })
    await przechodzi('zwrot inicjowany przez psychologa', partia('psycholog', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'CANCELLED', cancelledBy: 'psycholog' })
      b.update(doc(db, 'escrows/w1'), { status: 'REFUNDED' })
      przyrost(b, db, 'pacjent', CENA, 'w1')
      b.set(doc(db, 'wallet_transactions/w1_refund'), {
        transactionId: 'w1_refund', userId: 'pacjent', counterpartyId: 'psycholog', appointmentId: 'w1',
        amount: CENA, type: 'REFUND', title: 'Zwrot', timestamp: 1,
      })
    }))
  })

  it('dwa depozyty rozliczone JEDNYM przyrostem o kwote jednego — odmowa, oba zostaja HELD', async () => {
    await seedDwaDepozyty()
    for (const deklaracja of ['w1', 'w2', undefined]) {
      await odparty('dwa depozyty po 80 zwolnione jednym przyrostem o 80 (deklaracja ' + deklaracja + ')', partia('psycholog', (b, db) => {
        for (const id of ['w1', 'w2']) {
          b.update(doc(db, 'appointments/' + id), { status: 'COMPLETED' })
          b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
        }
        przyrost(b, db, 'psycholog', CENA, deklaracja)
      }))
    }
  })

  it('dwa depozyty rozliczone podwojonym przyrostem z jedna deklaracja — odmowa', async () => {
    await seedDwaDepozyty()
    await odparty('przyrost o 160 z deklaracja jednego z dwoch depozytow', partia('psycholog', (b, db) => {
      for (const id of ['w1', 'w2']) {
        b.update(doc(db, 'appointments/' + id), { status: 'COMPLETED' })
        b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
      }
      przyrost(b, db, 'psycholog', 2 * CENA, 'w1')
    }))
  })

  it('dwa zwroty jednemu pacjentowi jednym przyrostem o kwote jednego — odmowa, oba depozyty zostaja HELD', async () => {
    await seedDwaDepozyty({ startsAtMillis: Date.now() + 2 * GODZINA })
    for (const deklaracja of ['w1', 'w2', undefined]) {
      await odparty('dwa zwroty po 80 jednemu pacjentowi jednym przyrostem o 80 (deklaracja ' + deklaracja + ')', partia('psycholog', (b, db) => {
        for (const id of ['w1', 'w2']) {
          b.update(doc(db, 'appointments/' + id), { status: 'CANCELLED', cancelledBy: 'psycholog' })
          b.update(doc(db, 'escrows/' + id), { status: 'REFUNDED' })
        }
        przyrost(b, db, 'pacjent', CENA, deklaracja)
      }))
    }
  })

  it('rozliczenie na nieistniejacym dokumencie prywatnym kontrahenta konczy sie permission-denied, nie not-found', async () => {
    await seedDwaDepozyty({ startsAtMillis: Date.now() + 2 * GODZINA })
    await seed(env, async (db) => { await deleteDoc(doc(db, PRYWATNY('psycholog'))) })
    await odparty('wyplata na dokument prywatny, ktorego nie ma (trwa usuwanie konta psychologa)', partia('pacjent', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'CANCELLED', cancelledBy: 'pacjent' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA, 'w1')
    }))
  })

  it('dwa osobne przyrosty na tym samym dokumencie w jednej partii z roznymi deklaracjami — odmowa', async () => {
    await seedDwaDepozyty()
    await odparty('dwa przyrosty po 80 na jednym dokumencie, kazdy z deklaracja innego depozytu', partia('psycholog', (b, db) => {
      for (const id of ['w1', 'w2']) {
        b.update(doc(db, 'appointments/' + id), { status: 'COMPLETED' })
        b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
        przyrost(b, db, 'psycholog', CENA, id)
      }
    }))
  })

  it('rozliczenie z przyrostem dla wspolnika z tego samego depozytu — odmowa', async () => {
    await seedDwaDepozyty()
    await odparty('wyplata plus 80 monet dla wspolnika z tego samego depozytu', partia('psycholog', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'COMPLETED' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA, 'w1')
      przyrost(b, db, 'obcy', CENA, 'w1')
    }))
  })

  it('dwie rezerwacje jednym przyrostem o minus cene — odmowa', async () => {
    await seed(env, async (db) => {
      for (const id of ['w1', 'w2']) await setDoc(doc(db, 'appointments/' + id), wizyta(id, { status: 'FREE', patientId: null }))
    })
    for (const deklaracja of ['w1', 'w2', undefined]) {
      await odparty('dwa depozyty po 80 za jeden spadek salda o 80 (deklaracja ' + deklaracja + ')', partia('pacjent', (b, db) => {
        for (const id of ['w1', 'w2']) {
          b.update(doc(db, 'appointments/' + id), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
          b.set(doc(db, 'escrows/' + id), depozyt(id))
        }
        przyrost(b, db, 'pacjent', -CENA, deklaracja)
      }))
    }
  })

  it('stary przyrost na deklaracji juz rozliczonego depozytu — obcy nie podniesie cudzego salda', async () => {
    await seedDwaDepozyty()
    await przechodzi('wyplata przyrostem', partia('psycholog', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'COMPLETED' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA, 'w1')
      b.set(doc(db, 'wallet_transactions/w1_psycholog'), {
        transactionId: 'w1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent', appointmentId: 'w1',
        amount: CENA, type: 'BOOKING_EARNING', title: 'Wplyw', timestamp: 1,
      })
    }))
    await odparty('powtorzony przyrost na starej deklaracji', partia('obcy', (b, db) => przyrost(b, db, 'psycholog', CENA, 'w1')))
  })

  it('doladowanie przyrostem do limitu przechodzi (jedyny wyjatek od stalosci sumy), ponad limit — odmowa', async () => {
    await przechodzi('doladowanie 1000', partia('pacjent', (b, db) => przyrost(b, db, 'pacjent', 1000)), 1000)
    await odparty('doladowanie 1001 jednym przyrostem', partia('pacjent', (b, db) => przyrost(b, db, 'pacjent', 1001)))
  })

  it('doladowanie nie przykrywa rozliczenia: wyplata plus doladowanie w jednym przyroscie — odmowa', async () => {
    await seedDwaDepozyty()
    await odparty('wyplata 80 z doladowaniem 500 w jednym przyroscie', partia('psycholog', (b, db) => {
      b.update(doc(db, 'appointments/w1'), { status: 'COMPLETED' })
      b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
      przyrost(b, db, 'psycholog', CENA + 500, 'w1')
    }))
  })

  it('dobrowolne zniszczenie wlasnych monet przyrostem ujemnym przechodzi, cudzych — nie', async () => {
    await przechodzi('zniszczenie 50 wlasnych monet', partia('pacjent', (b, db) => przyrost(b, db, 'pacjent', -50)), -50)
    await odparty('obcy niszczy cudze monety', partia('obcy', (b, db) => przyrost(b, db, 'pacjent', -50)))
  })

  it('obcy nie podnosi cudzego salda przyrostem bez rozliczenia depozytu', async () => {
    await odparty('przyrost cudzego salda bez depozytu', partia('obcy', (b, db) => przyrost(b, db, 'pacjent', 10, 'nieistniejacy')))
  })
})
