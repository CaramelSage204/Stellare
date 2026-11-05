import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertSucceeds } from '@firebase/rules-unit-testing'
import {
  collection, collectionGroup, deleteDoc, deleteField, doc, getDoc, getDocs, setDoc, updateDoc, writeBatch,
} from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv, sumaMonetWSystemie, unauthedDb, zalozUzytkownika } from './helpers.js'

const here = path.dirname(fileURLToPath(import.meta.url))
let env

const CENA = 80
const GODZINA = 60 * 60 * 1000
const NUMER = '+48501234567'
const PRYWATNY = (uid) => `users/${uid}/private/contact`

const sumaMonet = async () => {
  const suma = await sumaMonetWSystemie(env, { collectionGroup, collection, getDocs })
  if (suma === 0) throw new Error('sumaMonet widzi zero monet - zly zrodlowy zbior dokumentow')
  return suma
}

const depozyt = (id, over = {}) => ({
  appointmentId: id, patientId: 'pacjent', psychologistId: 'psycholog', amount: CENA, status: 'HELD', ...over,
})

const wizyta = (id, over = {}) => ({
  appointmentId: id, psychologistId: 'psycholog', patientId: 'pacjent', date: '2026-10-01', time: '10:00',
  notes: '', status: 'COMPLETED', priceCoins: CENA, startsAtMillis: Date.now() - 2 * GODZINA, ...over,
})

const partia = (aktor, ops) => {
  const db = authedDb(env, aktor)
  const b = writeBatch(db)
  ops(b, db)
  return b
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

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, age: 41, gender: 'Kobieta',
      phone: '', email: 'psycholog@example.com', coinsBalance: 0, pricePerSession: CENA,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, age: 30, gender: 'Mezczyzna',
      phone: '', email: 'pacjent@example.com', coinsBalance: 500,
    })
    await zalozUzytkownika(db, 'users/obcy', {
      id: 'obcy', role: 'PATIENT', isVerified: false, isAdmin: false,
      phone: '', email: 'obcy@example.com', coinsBalance: 500,
    })
    await setDoc(doc(db, 'admins/szef'), {})
  })
})

describe('dokument prywatny — odczyt', () => {
  it('1. wlasciciel czyta swoj dokument prywatny', async () => {
    const snap = await assertSucceeds(getDoc(doc(authedDb(env, 'pacjent'), PRYWATNY('pacjent'))))
    expect(snap.data()).toEqual({ phone: '', email: 'pacjent@example.com', coinsBalance: 500 })
  })

  it('2. obcy zalogowany NIE czyta cudzego numeru telefonu, e-maila ani salda', async () => {
    await odrzucone('2. obcy zalogowany NIE czyta cudzego numeru telefonu, e-maila ani salda', getDoc(doc(authedDb(env, 'obcy'), PRYWATNY('pacjent'))))
  })

  it('2b. obcy czyta profil publiczny, ale nie znajduje w nim telefonu, e-maila ani salda', async () => {
    const snap = await assertSucceeds(getDoc(doc(authedDb(env, 'obcy'), 'users/pacjent')))
    const dane = snap.data()
    expect(dane.age, 'wiek ma zostac publiczny').toBe(30)
    expect(dane.gender, 'plec ma zostac publiczna').toBe('Mezczyzna')
    expect(Object.keys(dane), 'profil publiczny wydaje obcemu telefon').not.toContain('phone')
    expect(Object.keys(dane), 'profil publiczny wydaje obcemu e-mail').not.toContain('email')
    expect(Object.keys(dane), 'profil publiczny wydaje obcemu saldo').not.toContain('coinsBalance')
  })

  it('2c. zapytanie o cala kolekcje private jest zamkniete nawet dla wlasciciela (regula obejmuje tylko dokument contact)', async () => {
    await odrzucone('2c. zapytanie o kolekcje private przeszlo', getDocs(collection(authedDb(env, 'obcy'), 'users/pacjent/private')))
    await odrzucone('2c. wlasciciel zapytaniem listuje kolekcje private', getDocs(collection(authedDb(env, 'pacjent'), 'users/pacjent/private')))
  })

  it('2e. odczyt po sciezce w obie strony: pacjent nie czyta dokumentu psychologa, psycholog nie czyta dokumentu pacjenta', async () => {
    await odrzucone('2e. pacjent odczytal dane kontaktowe psychologa', getDoc(doc(authedDb(env, 'pacjent'), PRYWATNY('psycholog'))))
    await odrzucone('2e. psycholog odczytal dane kontaktowe pacjenta', getDoc(doc(authedDb(env, 'psycholog'), PRYWATNY('pacjent'))))
  })

  it('2d. grupa kolekcji private jest zamknieta dla obcego (pilnuje tylko ksztaltu match, NIE warunku odczytu - warunek pilnuja 2, 2b, 2e)', async () => {
    await odrzucone('2d. obcy wylistowal dokumenty prywatne grupa kolekcji', getDocs(collectionGroup(authedDb(env, 'obcy'), 'private')))
  })

  it('3. niezalogowany nie czyta dokumentu prywatnego', async () => {
    await odrzucone('3. niezalogowany nie czyta dokumentu prywatnego', getDoc(doc(unauthedDb(env), PRYWATNY('pacjent'))))
  })

  it('4. administrator czyta dokument prywatny', async () => {
    await assertSucceeds(getDoc(doc(authedDb(env, 'szef'), PRYWATNY('pacjent'))))
  })

  it('4b. administrator nie zapisuje w dokumencie prywatnym: ani salda, ani danych kontaktowych', async () => {
    const db = authedDb(env, 'szef')
    await odrzucone('4b. administrator zmienil saldo w cudzym dokumencie prywatnym', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 900 }))
    await odrzucone('4b. administrator zmienil e-mail w cudzym dokumencie prywatnym', updateDoc(doc(db, PRYWATNY('pacjent')), { email: 'poprawiony@example.com' }))
    await odrzucone('4b. administrator zalozyl cudzy dokument prywatny', setDoc(doc(db, PRYWATNY('nowy')), { coinsBalance: 0 }))
    await odrzucone('4b. administrator usunal cudzy dokument prywatny', deleteDoc(doc(db, PRYWATNY('pacjent'))))
  })
})

describe('dokument prywatny — zapis', () => {
  it('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', async () => {
    const db = authedDb(env, 'obcy')
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', updateDoc(doc(db, PRYWATNY('pacjent')), { phone: NUMER }))
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', updateDoc(doc(db, PRYWATNY('pacjent')), { email: 'przejety@example.com' }))
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 0 }))
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 900, escrowSettlement: 'w1' }))
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', setDoc(doc(db, PRYWATNY('pacjent')), { phone: '', email: '', coinsBalance: 500 }))
    await odrzucone('5. obcy nie zapisuje w cudzym dokumencie prywatnym poza sciezka rozliczenia depozytu', deleteDoc(doc(db, PRYWATNY('pacjent'))))
  })

  it('5b. obcy nie zaklada dokumentu prywatnego cudzemu kontu', async () => {
    await odrzucone('5b. obcy nie zaklada dokumentu prywatnego cudzemu kontu', setDoc(doc(authedDb(env, 'obcy'), PRYWATNY('nowy')), { phone: '', email: 'x@example.com', coinsBalance: 0 }))
  })

  it('6. wlasciciel nie ustawia salda z palca: ponad limit doladowania odmowa, w limicie i w dol przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone('6. wlasciciel nie ustawia salda z palca: ponad limit doladowania odmowa, w limicie i w dol przechodzi', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 500 + 1001 }))
    await odrzucone('6. wlasciciel nie ustawia salda z palca: ponad limit doladowania odmowa, w limicie i w dol przechodzi', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: -1 }))
    await odrzucone('6. wlasciciel nie ustawia salda z palca: ponad limit doladowania odmowa, w limicie i w dol przechodzi', updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 99.5 }))
    await assertSucceeds(updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 500 + 1000 }))
    await assertSucceeds(updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 10 }))
  })

  it('6b. dokument prywatny nie przyjmuje obcych pol', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone('6b. dokument prywatny nie przyjmuje obcych pol', updateDoc(doc(db, PRYWATNY('pacjent')), { role: 'PSYCHOLOGIST' }))
    await odrzucone('6b. dokument prywatny nie przyjmuje obcych pol', updateDoc(doc(db, PRYWATNY('pacjent')), { isAdmin: true }))
    await odrzucone('6b. dokument prywatny nie przyjmuje obcych pol', setDoc(doc(authedDb(env, 'nowy'), PRYWATNY('nowy')), { coinsBalance: 0, isAdmin: true }))
  })

  it('6c. zalozenie dokumentu prywatnego: saldo zero i bez deklaracji; saldo dodatnie odmowa', async () => {
    const db = authedDb(env, 'nowy')
    await odrzucone('6c. zalozenie dokumentu prywatnego: saldo zero i bez deklaracji; saldo dodatnie odmowa', setDoc(doc(db, PRYWATNY('nowy')), { phone: '', email: 'n@example.com', coinsBalance: 5000 }))
    await odrzucone('6c. zalozenie dokumentu prywatnego: saldo zero i bez deklaracji; saldo dodatnie odmowa', setDoc(doc(db, PRYWATNY('nowy')), { phone: '', email: 'n@example.com', coinsBalance: 0, escrowSettlement: 'w1' }))
    await odrzucone('6c. dokument prywatny bez coinsBalance zostawia konto na stale bez portfela', setDoc(doc(db, PRYWATNY('nowy')), { phone: '', email: 'n@example.com' }))
    await assertSucceeds(setDoc(doc(db, PRYWATNY('nowy')), { phone: '', email: 'n@example.com', coinsBalance: 0 }))
  })

  it('6d. skasowanie i odtworzenie dokumentu nie wskrzesza salda — odtworzenie tylko z zerem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(deleteDoc(doc(db, PRYWATNY('pacjent'))))
    await odrzucone('6d. skasowanie i odtworzenie dokumentu nie wskrzesza salda — odtworzenie tylko z zerem', setDoc(doc(db, PRYWATNY('pacjent')), { phone: '', email: 'p@example.com', coinsBalance: 500 }))
    await assertSucceeds(setDoc(doc(db, PRYWATNY('pacjent')), { phone: '', email: 'p@example.com', coinsBalance: 0 }))
  })

  it('7. niepusty phone musi zgadzac sie z numerem w tokenie (zapis, set i zalozenie)', async () => {
    const bezNumeru = authedDb(env, 'pacjent')
    const zNumerem = env.authenticatedContext('pacjent', { phone_number: NUMER }).firestore()
    const innyNumer = env.authenticatedContext('pacjent', { phone_number: '+48600700800' }).firestore()
    await odrzucone('7. niepusty phone musi zgadzac sie z numerem w tokenie (zapis, set i zalozenie)', updateDoc(doc(bezNumeru, PRYWATNY('pacjent')), { phone: NUMER }))
    await odrzucone('7. niepusty phone musi zgadzac sie z numerem w tokenie (zapis, set i zalozenie)', updateDoc(doc(innyNumer, PRYWATNY('pacjent')), { phone: NUMER }))
    await odrzucone('7. niepusty phone musi zgadzac sie z numerem w tokenie (zapis, set i zalozenie)', setDoc(doc(authedDb(env, 'nowy'), PRYWATNY('nowy')), { phone: NUMER, email: '', coinsBalance: 0 }))
    await assertSucceeds(updateDoc(doc(zNumerem, PRYWATNY('pacjent')), { phone: NUMER }))
  })

  it('8. wyczyszczenie potwierdzonego numeru przechodzi tylko, gdy token numeru nie ma', async () => {
    await seed(env, async (db) => { await updateDoc(doc(db, PRYWATNY('pacjent')), { phone: NUMER }) })
    const zNumerem = env.authenticatedContext('pacjent', { phone_number: NUMER }).firestore()
    await odrzucone('8. wyczyszczenie potwierdzonego numeru przechodzi tylko, gdy token numeru nie ma', updateDoc(doc(zNumerem, PRYWATNY('pacjent')), { phone: '' }))
    await odrzucone('8. wyczyszczenie potwierdzonego numeru przechodzi tylko, gdy token numeru nie ma', updateDoc(doc(zNumerem, PRYWATNY('pacjent')), { phone: deleteField() }))
    await assertSucceeds(updateDoc(doc(authedDb(env, 'pacjent'), PRYWATNY('pacjent')), { phone: '' }))
  })
})

describe('profil publiczny odrzuca pola prywatne', () => {
  const baza = { id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false, firstName: 'Jan' }

  it('9. zalozenie profilu publicznego z phone, email albo coinsBalance — odmowa', async () => {
    const db = authedDb(env, 'nowy')
    await odrzucone('9. zalozenie profilu publicznego z phone, email albo coinsBalance — odmowa', setDoc(doc(db, 'users/nowy'), { ...baza, phone: '' }))
    await odrzucone('9. zalozenie profilu publicznego z phone, email albo coinsBalance — odmowa', setDoc(doc(db, 'users/nowy'), { ...baza, email: 'jan@example.com' }))
    await odrzucone('9. zalozenie profilu publicznego z phone, email albo coinsBalance — odmowa', setDoc(doc(db, 'users/nowy'), { ...baza, coinsBalance: 0 }))
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), baza))
  })

  it('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', updateDoc(doc(db, 'users/pacjent'), { phone: '' }))
    await odrzucone('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', updateDoc(doc(db, 'users/pacjent'), { email: 'jan@example.com' }))
    await odrzucone('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 0 }))
    await odrzucone('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 99999 }))
    await odrzucone('9b. edycja profilu publicznego dopisujaca phone, email albo coinsBalance — odmowa', setDoc(doc(db, 'users/pacjent'), { id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, phone: NUMER }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { firstName: 'Janusz' }))
  })

  it('9c. administrator tez nie dopisuje pola prywatnego do profilu publicznego', async () => {
    const db = authedDb(env, 'szef')
    await odrzucone('9c. administrator tez nie dopisuje pola prywatnego do profilu publicznego', updateDoc(doc(db, 'users/pacjent'), { phone: NUMER }))
    await odrzucone('9c. administrator tez nie dopisuje pola prywatnego do profilu publicznego', updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 99999 }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { isVerified: true }))
  })

  it('9a. klucze wysylane przez klienta (UserProfileFields.NAMES) tworza profil, a nazwy spoza listy dozwolonej nie', async () => {
    const zrodlo = fs.readFileSync(path.join(here, '..', '..', 'app/src/main/java/com/example/stellare/data/remote/UserProfileFields.kt'), 'utf8')
    const lista = zrodlo.slice(zrodlo.indexOf('NAMES'), zrodlo.indexOf(')', zrodlo.indexOf('NAMES')))
    const nazwy = [...lista.matchAll(/"([A-Za-z]+)"/g)].map((m) => m[1])
    expect(nazwy.length, 'nie odczytano kluczy z UserProfileFields.kt').toBeGreaterThan(8)
    const publiczne = nazwy.filter((k) => !['phone', 'email', 'coinsBalance'].includes(k))
    const wartosci = {
      id: 'nowy', firstName: 'Jan', lastName: 'N', age: 30, gender: 'M', role: 'PATIENT', isVerified: false,
      isAdmin: false, qualifications: '', specializations: '', pricePerSession: 0, bio: '', customPrices: '',
      profileImageUri: null,
    }
    const doc1 = {}
    for (const k of publiczne) {
      expect(Object.hasOwn(wartosci, k), 'klient wysyla klucz "' + k + '", ktorego regula profilu publicznego nie zna - zaktualizuj liste dozwolona w firestore.rules').toBe(true)
      doc1[k] = wartosci[k]
    }
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), doc1))
    for (const obcy of [{ contact: { phone: NUMER } }, { phoneNumber: NUMER }, { mail: 'a@b.pl' }, { monety: 500 }]) {
      await odrzucone('9a. pole spoza listy dozwolonej w profilu publicznym: ' + Object.keys(obcy)[0],
        setDoc(doc(authedDb(env, 'inny'), 'users/inny'), { ...doc1, id: 'inny', ...obcy }))
    }
  })

  it('9d. profil sprzed przeniesienia (jeszcze z polami prywatnymi) przechodzi edycje dopiero po ich usunieciu', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/stary'), {
        id: 'stary', role: 'PATIENT', isVerified: false, isAdmin: false, firstName: 'Stary',
        phone: '', email: 'stary@example.com', coinsBalance: 0,
      })
    })
    const db = authedDb(env, 'stary')
    await odrzucone('9d. profil sprzed przeniesienia (jeszcze z polami prywatnymi) przechodzi edycje dopiero po ich usunieciu', updateDoc(doc(db, 'users/stary'), { firstName: 'Nowy' }))
    await assertSucceeds(updateDoc(doc(db, 'users/stary'), {
      firstName: 'Nowy', phone: deleteField(), email: deleteField(), coinsBalance: deleteField(),
    }))
  })

  it('9e. obcy nie edytuje cudzego profilu publicznego', async () => {
    await odrzucone('9e. obcy nie edytuje cudzego profilu publicznego', updateDoc(doc(authedDb(env, 'obcy'), 'users/pacjent'), { firstName: 'Podmieniony' }))
    await odrzucone('9e. obcy nie edytuje cudzego profilu publicznego', updateDoc(doc(authedDb(env, 'obcy'), 'users/pacjent'), { phone: NUMER }))
  })
})

describe('rozliczenie depozytu na dokumencie prywatnym', () => {
  async function seedWyplata(id = 'w1') {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/' + id), wizyta(id))
      await setDoc(doc(db, 'escrows/' + id), depozyt(id))
    })
  }

  const wyplata = (b, db, id = 'w1', saldo = CENA, deklaracja = id) => {
    b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
    const pola = { coinsBalance: saldo }
    if (deklaracja !== null) pola.escrowSettlement = deklaracja
    b.update(doc(db, PRYWATNY('psycholog')), pola)
  }

  it('10. wyplata psychologowi przechodzi, gdy saldo i deklaracja leza w dokumencie prywatnym', async () => {
    await seedWyplata()
    await przechodzi('wyplata depozytu', partia('psycholog', (b, db) => wyplata(b, db)))
  })

  it('10b. bez deklaracji w dokumencie prywatnym wyplata jest odrzucona', async () => {
    await seedWyplata()
    await odparty('wzrost salda bez deklaracji', partia('psycholog', (b, db) => wyplata(b, db, 'w1', CENA, null)))
  })

  it('10c. DEKLARACJA W PROFILU PUBLICZNYM zamiast w dokumencie z saldem — odmowa (rozdzielenie salda i deklaracji)', async () => {
    await seedWyplata()
    await odparty('deklaracja w innym dokumencie niz saldo zrywa powiazanie dokument-dokument',
      partia('psycholog', (b, db) => {
        b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
        b.update(doc(db, 'users/psycholog'), { escrowSettlement: 'w1' })
        b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA })
      }))
  })

  it('10d. wyplata przy saldzie niezgodnym z kwota depozytu — odmowa', async () => {
    await seedWyplata()
    await odparty('wzrost salda wiekszy niz depozyt', partia('psycholog', (b, db) => wyplata(b, db, 'w1', CENA + 1)))
    await odparty('wzrost salda mniejszy niz depozyt', partia('psycholog', (b, db) => wyplata(b, db, 'w1', CENA - 1)))
  })

  it('10e. obcy nie rozlicza cudzego depozytu na swoim ani cudzym dokumencie prywatnym', async () => {
    await seedWyplata()
    await odparty('obcy zmienia depozyt i podnosi swoje saldo',
      partia('obcy', (b, db) => {
        b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
        b.update(doc(db, PRYWATNY('obcy')), { coinsBalance: 500 + CENA, escrowSettlement: 'w1' })
      }))
    await odparty('obcy zmienia depozyt i podnosi saldo psychologa',
      partia('obcy', (b, db) => wyplata(b, db)))
  })

  it('10f. dokument prywatny pacjenta nie przyjmuje wzrostu salda z wyplaty psychologa (zla strona)', async () => {
    await seedWyplata()
    await odparty('wyplata depozytu na konto pacjenta zamiast psychologa',
      partia('psycholog', (b, db) => {
        b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
        b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 500 + CENA, escrowSettlement: 'w1' })
      }))
  })

  it('10g. rezerwacja bez dokumentu prywatnego pacjenta — odmowa (nie ma salda do porownania)', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/bezsalda'), { id: 'bezsalda', role: 'PATIENT', isVerified: false, isAdmin: false })
      await setDoc(doc(db, 'appointments/r1'), wizyta('r1', { status: 'FREE', patientId: null, startsAtMillis: Date.now() + 72 * GODZINA }))
    })
    await odparty('depozyt zalozony pacjentowi, ktory nie ma dokumentu z saldem',
      partia('bezsalda', (b, db) => {
        b.update(doc(db, 'appointments/r1'), { status: 'BOOKED', patientId: 'bezsalda', notes: '' })
        b.set(doc(db, 'escrows/r1'), depozyt('r1', { patientId: 'bezsalda' }))
        b.set(doc(db, PRYWATNY('bezsalda')), { phone: '', email: '', coinsBalance: 0 })
      }))
  })
})

describe('partie wielodokumentowe na dokumentach prywatnych (suma monet w systemie)', () => {
  const CENA2 = 100
  const IDS = ['a1', 'a2']

  async function seedWolne(saldo = 500) {
    await seed(env, async (db) => {
      await updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: saldo })
      for (const id of IDS) {
        await setDoc(doc(db, 'appointments/' + id), wizyta(id, {
          status: 'FREE', patientId: null, priceCoins: CENA2, startsAtMillis: Date.now() + 72 * GODZINA,
        }))
      }
    })
  }

  async function seedRozliczalne() {
    await seed(env, async (db) => {
      await updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 })
      for (const id of IDS) {
        await setDoc(doc(db, 'appointments/' + id), wizyta(id, { priceCoins: CENA2 }))
        await setDoc(doc(db, 'escrows/' + id), depozyt(id, { amount: CENA2 }))
      }
    })
  }

  const rezerwuj = (b, db, ids) => {
    for (const id of ids) {
      b.update(doc(db, 'appointments/' + id), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      b.set(doc(db, 'escrows/' + id), depozyt(id, { amount: CENA2 }))
    }
  }

  const saldoPacjenta = (b, db, wartosc, deklaracja) => {
    const pola = { coinsBalance: wartosc }
    if (deklaracja !== undefined) pola.escrowSettlement = deklaracja
    b.update(doc(db, PRYWATNY('pacjent')), pola)
  }

  it('P1. dwa depozyty przy jednym spadku salda na dokumencie prywatnym — odmowa (bicie monet)', async () => {
    await seedWolne()
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('dwa depozyty i jeden spadek salda bija monety (deklaracja ' + deklaracja + ')',
        partia('pacjent', (b, db) => { rezerwuj(b, db, IDS); saldoPacjenta(b, db, 500 - CENA2, deklaracja) }))
    }
    await odparty('dwa depozyty, spadek salda bez deklaracji',
      partia('pacjent', (b, db) => { rezerwuj(b, db, IDS); saldoPacjenta(b, db, 500 - 2 * CENA2) }))
  })

  it('P1b. kazda rezerwacja we wlasnej partii z wlasna deklaracja przechodzi (kontrola, suma bez zmian)', async () => {
    await seedWolne()
    await przechodzi('pierwsza rezerwacja', partia('pacjent', (b, db) => { rezerwuj(b, db, ['a1']); saldoPacjenta(b, db, 400, 'a1') }))
    await przechodzi('druga rezerwacja', partia('pacjent', (b, db) => { rezerwuj(b, db, ['a2']); saldoPacjenta(b, db, 300, 'a2') }))
  })

  it('P2. dwa depozyty rozliczane jednym wzrostem salda psychologa — odmowa (niszczenie monet)', async () => {
    await seedRozliczalne()
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('rozliczenie dwoch depozytow jednym wzrostem salda (deklaracja ' + deklaracja + ')',
        partia('psycholog', (b, db) => {
          for (const id of IDS) b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
          b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA2, escrowSettlement: deklaracja })
        }))
    }
  })

  it('P2b. dwa depozyty zwracane jednym wzrostem salda pacjenta — odmowa (niszczenie monet)', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 })
      for (const id of IDS) {
        await setDoc(doc(db, 'appointments/' + id), wizyta(id, { priceCoins: CENA2, status: 'CANCELLED', cancelledBy: 'psycholog' }))
        await setDoc(doc(db, 'escrows/' + id), depozyt(id, { amount: CENA2 }))
      }
    })
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('zwrot dwoch depozytow jednym wzrostem salda (deklaracja ' + deklaracja + ')',
        partia('psycholog', (b, db) => {
          for (const id of IDS) b.update(doc(db, 'escrows/' + id), { status: 'REFUNDED' })
          b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 + CENA2, escrowSettlement: deklaracja })
        }))
    }
  })

  it('P3. wyplata a1 i zwrot a2: ruch salda tylko po jednej stronie albo ta sama deklaracja po obu — odmowa', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 })
      await setDoc(doc(db, 'appointments/a1'), wizyta('a1', { priceCoins: CENA2 }))
      await setDoc(doc(db, 'escrows/a1'), depozyt('a1', { amount: CENA2 }))
      await setDoc(doc(db, 'appointments/a2'), wizyta('a2', { priceCoins: CENA2, status: 'CANCELLED', cancelledBy: 'psycholog' }))
      await setDoc(doc(db, 'escrows/a2'), depozyt('a2', { amount: CENA2 }))
    })
    const oba = (b, db) => {
      b.update(doc(db, 'escrows/a1'), { status: 'RELEASED' })
      b.update(doc(db, 'escrows/a2'), { status: 'REFUNDED' })
    }
    await odparty('wyplata a1 i zwrot a2, wzrost tylko u psychologa',
      partia('psycholog', (b, db) => { oba(b, db); b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA2, escrowSettlement: 'a1' }) }))
    await odparty('wyplata a1 i zwrot a2, wzrost tylko u pacjenta',
      partia('psycholog', (b, db) => { oba(b, db); b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 + CENA2, escrowSettlement: 'a2' }) }))
    await odparty('wyplata a1 i zwrot a2, obie strony z deklaracja tego samego depozytu',
      partia('psycholog', (b, db) => {
        oba(b, db)
        b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA2, escrowSettlement: 'a1' })
        b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 + CENA2, escrowSettlement: 'a1' })
      }))
    await przechodzi('kontrola: obie strony, kazda ze swoja deklaracja i swoim wzrostem',
      partia('psycholog', (b, db) => {
        oba(b, db)
        b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA2, escrowSettlement: 'a1' })
        b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 + CENA2, escrowSettlement: 'a2' })
      }))
  })

  it('P4. powstanie depozytu a3 razem ze zwrotem a1 w jednej partii przy saldzie netto bez zmiany — odmowa', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300 })
      await setDoc(doc(db, 'appointments/a1'), wizyta('a1', { priceCoins: CENA2, status: 'CANCELLED', cancelledBy: 'psycholog' }))
      await setDoc(doc(db, 'escrows/a1'), depozyt('a1', { amount: CENA2 }))
      await setDoc(doc(db, 'appointments/a3'), wizyta('a3', {
        status: 'FREE', patientId: null, priceCoins: CENA2, startsAtMillis: Date.now() + 72 * GODZINA,
      }))
    })
    for (const deklaracja of ['a1', 'a3']) {
      await odparty('zwrot a1 (+100) i rezerwacja a3 (-100) skladaja sie na saldo bez zmiany, deklaracja ' + deklaracja,
        partia('pacjent', (b, db) => {
          b.update(doc(db, 'escrows/a1'), { status: 'REFUNDED' })
          b.update(doc(db, 'appointments/a3'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
          b.set(doc(db, 'escrows/a3'), depozyt('a3', { amount: CENA2 }))
          b.update(doc(db, PRYWATNY('pacjent')), { coinsBalance: 300, escrowSettlement: deklaracja })
        }))
    }
  })

  it('P5. doladowanie razem z rozliczeniem w jednym zapisie salda — odmowa (kwota wzrostu nie rowna sie depozytowi)', async () => {
    await seedRozliczalne()
    await odparty('wyplata 100 plus doladowanie 900 jednym wzrostem salda',
      partia('psycholog', (b, db) => {
        b.update(doc(db, 'escrows/a1'), { status: 'RELEASED' })
        b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: CENA2 + 900, escrowSettlement: 'a1' })
      }))
    await przechodzi('doladowanie w osobnej partii nie rusza depozytow (suma rosnie o doladowanie)',
      partia('psycholog', (b, db) => { b.update(doc(db, PRYWATNY('psycholog')), { coinsBalance: 900 }) }), 900)
  })

  it('P6. wlasne saldo pacjenta w dol z deklaracja nie rozlicza cudzego depozytu (dobrowolne zniszczenie)', async () => {
    await seedRozliczalne()
    await przechodzi('dobrowolne zniszczenie wlasnych monet',
      partia('pacjent', (b, db) => { saldoPacjenta(b, db, 250, 'a1') }), -50)
  })
})
