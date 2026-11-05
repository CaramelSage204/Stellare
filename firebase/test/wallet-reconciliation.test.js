import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import {
  collection, doc, documentId, getDoc, getDocs, increment, limit, query, where, writeBatch,
} from 'firebase/firestore'
import { authedDb, seed, setupEnv, zalozUzytkownika } from './helpers.js'

// Uzgadnianie po bledzie zapisu (zadanie 8). Klient sprawdza, czy doladowanie doszlo, zapytaniem
// o wpis ksiegi pod kluczem idempotencji. Te testy dowodza trzech rzeczy, od ktorych zalezy cala
// sciezka: (1) odczyt po sciezce brakujacego wpisu jest ODMAWIANY (nie "brak"), wiec klient musi
// pytac zapytaniem; (2) zapytanie ograniczone do userId przechodzi takze przy pustym wyniku;
// (3) ponowny zapis pod tym samym kluczem jest odrzucany przez niezmienna ksiege, wiec monety
// nie podwajaja sie. Transport jak w produkcji: writeBatch dla zapisu, getDocs dla sprawdzenia.

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0,
    })
  })
})

const KLUCZ = 'topup_klucz-1'

const wpis = (id, userId, amount) => ({
  transactionId: id, userId, counterpartyId: null, appointmentId: null,
  amount, type: 'TOPUP', title: 'Doladowanie portfela', timestamp: Date.now(),
})

const doladuj = (db, uid, klucz, kwota) => {
  const b = writeBatch(db)
  b.update(doc(db, 'users/' + uid + '/private/contact'), { coinsBalance: increment(kwota) })
  b.set(doc(db, 'wallet_transactions/' + klucz), wpis(klucz, uid, kwota))
  return b.commit()
}

const sprawdz = (db, uid, klucz) => getDocs(query(
  collection(db, 'wallet_transactions'),
  where('userId', '==', uid),
  where('transactionId', '==', klucz),
  limit(1),
))

const sprawdzPoIdentyfikatorzeDokumentu = (db, uid, klucz) => getDocs(query(
  collection(db, 'wallet_transactions'),
  where('userId', '==', uid),
  where(documentId(), '==', klucz),
  limit(1),
))

const saldo = async (uid) => {
  let wartosc
  await seed(env, async (db) => {
    const snap = await getDoc(doc(db, 'users/' + uid + '/private/contact'))
    expect(snap.exists(), 'przyrzad: dokument prywatny ' + uid + ' musi istniec').toBe(true)
    wartosc = snap.data().coinsBalance
  })
  return wartosc
}

describe('doladowanie — uzgadnianie po bledzie zapisu', () => {
  it('odczyt po sciezce brakujacego wpisu jest odmawiany, a nie zwraca "brak" — dlatego klient pyta zapytaniem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'wallet_transactions/' + KLUCZ)))
  })

  it('zapytanie z documentId() o brakujacy wpis tez jest odmawiane — dlatego klient filtruje po polu transactionId', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(sprawdzPoIdentyfikatorzeDokumentu(db, 'pacjent', KLUCZ))
  })

  it('zapytanie po polu transactionId o brakujacy wpis wlasnego uzytkownika przechodzi i zwraca pusty wynik', async () => {
    const db = authedDb(env, 'pacjent')
    const snap = await assertSucceeds(sprawdz(db, 'pacjent', KLUCZ))
    expect(snap.empty).toBe(true)
  })

  it('po doladowaniu zapytanie widzi dokladnie jeden wpis pod kluczem, a saldo wzroslo o kwote', async () => {
    const db = authedDb(env, 'pacjent')
    expect(await saldo('pacjent')).toBe(500)

    await assertSucceeds(doladuj(db, 'pacjent', KLUCZ, 50))

    const snap = await assertSucceeds(sprawdz(db, 'pacjent', KLUCZ))
    expect(snap.size).toBe(1)
    expect(snap.docs[0].id).toBe(KLUCZ)
    expect(await saldo('pacjent')).toBe(550)
  })

  it('zapytanie nie widzi cudzego wpisu o tym samym identyfikatorze', async () => {
    await seed(env, async (db) => {
      const b = writeBatch(db)
      b.set(doc(db, 'wallet_transactions/' + KLUCZ), wpis(KLUCZ, 'psycholog', 10))
      await b.commit()
    })
    const db = authedDb(env, 'pacjent')
    const snap = await assertSucceeds(sprawdz(db, 'pacjent', KLUCZ))
    expect(snap.empty).toBe(true)
  })

  it('zapytanie o wpis cudzego uzytkownika jest odmawiane', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(sprawdz(db, 'psycholog', KLUCZ))
  })

  it('ponowny zapis pod tym samym kluczem jest odrzucony w calosci i nie podwaja monet', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(doladuj(db, 'pacjent', KLUCZ, 50))
    expect(await saldo('pacjent')).toBe(550)

    await assertFails(doladuj(db, 'pacjent', KLUCZ, 50))

    expect(await saldo('pacjent')).toBe(550)
    const snap = await sprawdz(db, 'pacjent', KLUCZ)
    expect(snap.size).toBe(1)
  })

  it('dwa doladowania pod roznymi kluczami sa dwoma doladowaniami', async () => {
    const db = authedDb(env, 'pacjent')

    await assertSucceeds(doladuj(db, 'pacjent', 'topup_a', 50))
    await assertSucceeds(doladuj(db, 'pacjent', 'topup_b', 50))

    expect(await saldo('pacjent')).toBe(600)
    expect((await sprawdz(db, 'pacjent', 'topup_a')).size).toBe(1)
    expect((await sprawdz(db, 'pacjent', 'topup_b')).size).toBe(1)
  })

  it('wpis o transactionId innym niz identyfikator dokumentu jest odrzucany — pole moze zastepowac identyfikator w zapytaniu', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: increment(50) })
    b.set(doc(db, 'wallet_transactions/' + KLUCZ), wpis('inny_klucz', 'pacjent', 50))
    await assertFails(b.commit())
    expect(await saldo('pacjent')).toBe(500)
  })

  it('klucz zajety cudzym wpisem blokuje zapis, ale nie zmienia salda', async () => {
    await seed(env, async (db) => {
      const b = writeBatch(db)
      b.set(doc(db, 'wallet_transactions/' + KLUCZ), wpis(KLUCZ, 'psycholog', 10))
      await b.commit()
    })
    const db = authedDb(env, 'pacjent')

    await assertFails(doladuj(db, 'pacjent', KLUCZ, 50))

    expect(await saldo('pacjent')).toBe(500)
  })
})

describe('doladowanie — indeks dla zapytania sprawdzajacego', () => {
  it('firestore.indexes.json deklaruje indeks wallet_transactions po userId i transactionId', () => {
    const here = path.dirname(fileURLToPath(import.meta.url))
    const indexes = JSON.parse(fs.readFileSync(path.join(here, '..', 'firestore.indexes.json'), 'utf8')).indexes
    const wallet = indexes.filter((i) => i.collectionGroup === 'wallet_transactions')
    expect(wallet.length, 'przyrzad: musi byc co najmniej indeks listy wpisow').toBeGreaterThanOrEqual(1)
    const znaleziony = wallet.some((i) => i.queryScope === 'COLLECTION'
      && i.fields.length === 2
      && i.fields[0].fieldPath === 'userId'
      && i.fields[1].fieldPath === 'transactionId')
    expect(znaleziony, 'brak indeksu userId+transactionId: uzgadnianie doladowania moze padac w produkcji dokladnie wtedy, gdy jest potrzebne').toBe(true)
  })
})
