import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import {
  collection, collectionGroup, deleteDoc, deleteField, doc, getDocs, setDoc, updateDoc, writeBatch,
} from 'firebase/firestore'
import { authedDb, seed, setupEnv, sumaMonetWSystemie, zalozUzytkownika } from './helpers.js'

let env

const GODZINA = 60 * 60 * 1000
const DZIEN = 24 * GODZINA
const MARGINES = 60 * 1000
const CENA = 80
const DUZA_CENA = 1500

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => { await env.clearFirestore() })

const uzytkownik = (id, role, coinsBalance, extra = {}) => ({
  id, role, isVerified: role !== 'PATIENT', isAdmin: false, coinsBalance, ...extra,
})

const wizyta = (over = {}) => {
  const w = {
    appointmentId: 'w1', psychologistId: 'psycholog', patientId: 'pacjent',
    date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: CENA,
    startsAtMillis: Date.now() + 3 * DZIEN, ...over,
  }
  for (const k of Object.keys(w)) if (w[k] === undefined) delete w[k]
  return w
}

const depozyt = (over = {}) => ({
  appointmentId: 'w1', patientId: 'pacjent', psychologistId: 'psycholog',
  amount: CENA, status: 'HELD', ...over,
})

const wpis = (id, over = {}) => ({
  transactionId: id, userId: 'pacjent', counterpartyId: 'psycholog', appointmentId: 'w1',
  amount: -CENA, type: 'BOOKING_PAYMENT', title: 'Operacja', timestamp: 1, ...over,
})

async function seedStan({
  wizyta: wizytaOver = {}, depozyt: depozytOver = {}, bezDepozytu = false,
  saldoPacjenta = 500 - CENA, saldoPsychologa = 0, cena = CENA,
} = {}) {
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', saldoPsychologa, { pricePerSession: cena }))
    await zalozUzytkownika(db, 'users/pacjent', uzytkownik('pacjent', 'PATIENT', saldoPacjenta))
    await zalozUzytkownika(db, 'users/ofiara', uzytkownik('ofiara', 'PATIENT', 500))
    await zalozUzytkownika(db, 'users/psy_x', uzytkownik('psy_x', 'PSYCHOLOGIST', 0, { pricePerSession: cena }))
    const cancelledBy = wizytaOver.status === 'CANCELLED' && !('cancelledBy' in wizytaOver)
      ? { cancelledBy: 'pacjent' } : {}
    await setDoc(doc(db, 'appointments/w1'), wizyta({ priceCoins: cena, ...cancelledBy, ...wizytaOver }))
    if (!bezDepozytu) {
      await setDoc(doc(db, 'escrows/w1'), depozyt({ amount: cena, ...depozytOver }))
    }
  })
}

async function seedWolna({ cena = CENA, saldo = 500, startsAtMillis = Date.now() + 3 * DZIEN } = {}) {
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', 0, { pricePerSession: cena }))
    await zalozUzytkownika(db, 'users/pacjent', uzytkownik('pacjent', 'PATIENT', saldo))
    await zalozUzytkownika(db, 'users/ofiara', uzytkownik('ofiara', 'PATIENT', 500))
    await setDoc(doc(db, 'appointments/w1'), wizyta({
      status: 'FREE', patientId: null, priceCoins: cena, startsAtMillis,
    }))
  })
}

async function sumaMonet() {
  const suma = await sumaMonetWSystemie(env, { collectionGroup, collection, getDocs })
  if (suma === 0) throw new Error('sumaMonet widzi zero monet - zly zrodlowy zbior dokumentow')
  return suma
}

async function odparty(szkoda, partia) {
  const przed = await sumaMonet()
  let blad = null
  try { await partia.commit() } catch (e) { blad = e }
  const po = await sumaMonet()
  expect(blad, 'ATAK PRZESZEDL PRZEZ REGULY: ' + szkoda).not.toBeNull()
  expect(blad?.code, 'odmowa nie z regul (' + blad?.message + '), wiec nie dowodzi obrony: ' + szkoda)
    .toBe('permission-denied')
  expect(po, szkoda + ' — suma sald + depozytow HELD zmienila sie z ' + przed + ' na ' + po).toBe(przed)
}

async function przechodzi(opis, partia, zmianaSumy = 0) {
  const przed = await sumaMonet()
  await assertSucceeds(partia.commit())
  const po = await sumaMonet()
  expect(po - przed, opis + ' — monety powstaly z niczego albo przepadly (suma sald + HELD)').toBe(zmianaSumy)
}

function partia(aktor, ops) {
  const db = authedDb(env, aktor)
  const b = writeBatch(db)
  ops(b, db)
  b.baza = db
  return b
}

const saldo = (b, db, uid, coinsBalance, deklaracja) => {
  const pola = { coinsBalance }
  if (deklaracja !== undefined) pola.escrowSettlement = deklaracja
  b.update(doc(db, 'users/' + uid + '/private/contact'), pola)
}

const ma = (o, k, domyslnie) => (Object.hasOwn(o, k) ? o[k] : domyslnie)

const status = (b, db, nowy) => b.update(doc(db, 'escrows/w1'), { status: nowy })

const rezerwacja = (aktor, {
  depozytNadpis = {}, saldoPo = 500 - CENA, bezDepozytu = false, bezSalda = false,
  ruchWizyty = { status: 'BOOKED', patientId: aktor, notes: '' },
} = {}) => partia(aktor, (b, db) => {
  if (ruchWizyty) b.update(doc(db, 'appointments/w1'), ruchWizyty)
  if (!bezDepozytu) b.set(doc(db, 'escrows/w1'), depozyt({ patientId: aktor, ...depozytNadpis }))
  saldo(b, db, aktor, bezSalda ? 500 : saldoPo, 'w1')
  b.set(doc(db, 'wallet_transactions/' + aktor + '_w1_platnosc'), wpis(aktor + '_w1_platnosc', { userId: aktor }))
})

const wyplataPsychologa = (over = {}) => partia('psycholog', (b, db) => {
  if (over.ruchWizyty) b.update(doc(db, 'appointments/w1'), over.ruchWizyty)
  status(b, db, over.status ?? 'RELEASED')
  saldo(b, db, 'psycholog', over.bezSalda ? 0 : (over.saldo ?? CENA), ma(over, 'deklaracja', 'w1'))
  b.set(doc(db, 'wallet_transactions/psycholog_w1_wplyw'), wpis('psycholog_w1_wplyw', {
    userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: 'BOOKING_EARNING',
  }))
})

const wyplataPacjenta = (aktor = 'pacjent', over = {}) => partia(aktor, (b, db) => {
  if (over.ruchWizyty) b.update(doc(db, 'appointments/w1'), over.ruchWizyty)
  status(b, db, 'RELEASED')
  if (!over.bezSalda) saldo(b, db, 'psycholog', over.saldo ?? CENA, ma(over, 'deklaracja', 'w1'))
  if (!over.bezWpisu) {
    b.set(doc(db, 'wallet_transactions/pacjent_w1_wplyw'), wpis('pacjent_w1_wplyw', {
      userId: 'psycholog', counterpartyId: aktor, amount: CENA, type: 'BOOKING_EARNING',
      ...(over.wpis ?? {}),
    }))
  }
})

const zwrotPacjenta = (over = {}) => partia('pacjent', (b, db) => {
  if (over.ruchWizyty) b.update(doc(db, 'appointments/w1'), over.ruchWizyty)
  status(b, db, over.status ?? 'REFUNDED')
  saldo(b, db, 'pacjent', over.bezSalda ? 500 - CENA : (over.saldo ?? 500), ma(over, 'deklaracja', 'w1'))
  b.set(doc(db, 'wallet_transactions/pacjent_w1_zwrot'), wpis('pacjent_w1_zwrot', {
    userId: 'pacjent', counterpartyId: 'psycholog', amount: CENA, type: 'REFUND',
  }))
})

const zwrotPsychologa = (over = {}) => partia(over.aktor ?? 'psycholog', (b, db) => {
  if (over.ruchWizyty) b.update(doc(db, 'appointments/w1'), over.ruchWizyty)
  status(b, db, over.status ?? 'REFUNDED')
  if (!over.bezSalda) saldo(b, db, over.komu ?? 'pacjent', over.saldo ?? 500, ma(over, 'deklaracja', 'w1'))
  if (!over.bezWpisu) {
    b.set(doc(db, 'wallet_transactions/psycholog_w1_zwrot'), wpis('psycholog_w1_zwrot', {
      userId: 'pacjent', counterpartyId: 'psycholog', amount: CENA, type: 'REFUND',
      ...(over.wpis ?? {}),
    }))
  }
})

describe('depozyt — powstanie', () => {
  beforeEach(async () => { await seedWolna() })

  it('1: pacjent rezerwuje — depozyt HELD, wizyta BOOKED, saldo pacjenta maleje o amount', async () => {
    await przechodzi('rezerwacja z depozytem', rezerwacja('pacjent'))
  })

  it('2: depozyt bez zmniejszenia salda pacjenta to bicie monet', async () => {
    await odparty('depozyt 80 powstal, a saldo pacjenta nie spadlo — bicie monet',
      rezerwacja('pacjent', { bezSalda: true }))
  })

  it('2b: saldo zmniejszone o mniej niz amount to bicie monet', async () => {
    await odparty('depozyt 80 przy spadku salda o 30 — bicie monet',
      rezerwacja('pacjent', { saldoPo: 470 }))
  })

  it('3: amount rozny od priceCoins wizyty — odmowa', async () => {
    await odparty('depozyt 70 za wizyte za 80 — psycholog dostalby mniej niz cena',
      rezerwacja('pacjent', { depozytNadpis: { amount: 70 }, saldoPo: 430 }))
  })

  it('4: amount ujemne — odmowa (rezerwacja przybliza monety zamiast je pobierac)', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/w1'), { priceCoins: -CENA })
    })
    await odparty('depozyt -80 zwiekszylby saldo pacjenta przy rezerwacji — bicie monet',
      rezerwacja('pacjent', { depozytNadpis: { amount: -CENA }, saldoPo: 500 + CENA }))
  })

  it('4b: amount zero — odmowa', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/w1'), { priceCoins: 0 })
    })
    await odparty('depozyt 0 — pusty depozyt blokuje identyfikator wizyty',
      rezerwacja('pacjent', { depozytNadpis: { amount: 0 }, saldoPo: 500 }))
  })

  it('4c: amount ulamkowy — odmowa (osiagalna tylko przez calkowite saldo: is int to obrona w glab)', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/w1'), { priceCoins: 80.5 })
    })
    await odparty('depozyt 80.5 — ulamkowe monety rozjezdzaja sie z saldem calkowitym',
      rezerwacja('pacjent', { depozytNadpis: { amount: 80.5 }, saldoPo: 419.5 }))
  })

  it('rezerwacja na kredyt: saldo pacjenta nie moze zejsc ponizej zera', async () => {
    await seedWolna({ saldo: 20 })
    await odparty('pacjent z saldem 20 rezerwuje wizyte za 80 i konczy z saldem -60 — usluga bez pokrycia',
      rezerwacja('pacjent', { saldoPo: 20 - CENA }))
  })

  it('W2: poprawna pojedyncza rezerwacja BEZ deklaracji rozliczenia — odmowa', async () => {
    const b = partia('pacjent', (bb, db) => {
      bb.update(doc(db, 'appointments/w1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      bb.set(doc(db, 'escrows/w1'), depozyt())
      saldo(bb, db, 'pacjent', 500 - CENA)
    })
    await odparty('platna rezerwacja bez deklaracji rozliczenia nie przejdzie — klient musi zapisac deklaracje w tej samej transakcji (zapis calego profilu .set ja zgubi i kazda platna rezerwacja padnie w produkcji)', b)
  })

  it('5: depozyt na wizyte, ktora nie przechodzi FREE -> BOOKED (jest juz BOOKED) — odmowa', async () => {
    await seedStan({ bezDepozytu: true, saldoPacjenta: 500 })
    await odparty('depozyt zalozony na wizyte zarezerwowana wczesniej — pobranie oplaty za nic',
      partia('pacjent', (b, db) => {
        b.set(doc(db, 'escrows/w1'), depozyt())
        saldo(b, db, 'pacjent', 500 - CENA, 'w1')
      }))
  })

  it('6: depozyt zakladany przez kogos, kto nie zostaje pacjentem wizyty — odmowa', async () => {
    await odparty('obca osoba zaklada depozyt na cudza rezerwacje i zwrot trafilby do niewlasciwego konta',
      rezerwacja('ofiara', { depozytNadpis: { patientId: 'pacjent' } }))
  })

  it('7: psychologistId w depozycie rozny od tego w wizycie — odmowa', async () => {
    await odparty('depozyt wskazuje innego psychologa — wyplata trafilaby do niewlasciwej osoby',
      rezerwacja('pacjent', { depozytNadpis: { psychologistId: 'psy_x' } }))
  })

  it('8: pole poza dozwolonym zestawem kluczy — odmowa', async () => {
    await odparty('depozyt z dodatkowym polem — schowek na dane w nieusuwalnym dokumencie',
      rezerwacja('pacjent', { depozytNadpis: { refundPending: false } }))
  })

  it('9: status inny niz HELD przy tworzeniu — odmowa', async () => {
    await odparty('depozyt zalozony od razu jako RELEASED — monety zniknelyby z salda i z depozytow',
      rezerwacja('pacjent', { depozytNadpis: { status: 'RELEASED' } }))
    await odparty('depozyt zalozony od razu jako REFUNDED — oplata wrocilaby bez zwrotu salda',
      rezerwacja('pacjent', { depozytNadpis: { status: 'REFUNDED' } }))
  })

  it('9b: appointmentId w depozycie inny niz identyfikator dokumentu — odmowa', async () => {
    await odparty('depozyt z cudzym appointmentId — podszycie pod inna wizyte',
      rezerwacja('pacjent', { depozytNadpis: { appointmentId: 'w2' } }))
  })

  it('9c: depozyt zalozony bez rezerwacji wizyty (wizyta zostaje FREE) — odmowa', async () => {
    await odparty('depozyt na wolnym terminie — oplata pobrana, a termin nadal wolny',
      rezerwacja('pacjent', { ruchWizyty: null }))
  })

  it('9d: napastnik nie moze zasiedziec identyfikatora depozytu przed legalna rezerwacja', async () => {
    await odparty('depozyt zalozony na wolnym terminie blokuje przyszla rezerwacje (identyfikator zajety)',
      partia('ofiara', (b, db) => {
        b.set(doc(db, 'escrows/w1'), depozyt({ patientId: 'ofiara' }))
        saldo(b, db, 'ofiara', 500 - CENA, 'w1')
      }))
    await przechodzi('legalna rezerwacja po nieudanej probie zasiedzenia', rezerwacja('pacjent'))
  })
})

describe('depozyt — wyplata psychologowi', () => {
  it('10: psycholog przestawia HELD -> RELEASED po odbytej wizycie, jego saldo rosnie o amount', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await przechodzi('wyplata po odbytej wizycie', wyplataPsychologa())
  })

  it('10b: wyplata w tej samej partii, w ktorej psycholog oznacza wizyte jako odbyta', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - 2 * GODZINA } })
    await przechodzi('oznaczenie wizyty i wyplata w jednej partii',
      wyplataPsychologa({ ruchWizyty: { status: 'COMPLETED' } }))
  })

  it('11: psycholog przestawia HELD -> RELEASED przy wizycie BOOKED — odmowa', async () => {
    await seedStan()
    await odparty('psycholog zabiera oplate za wizyte, ktora sie jeszcze nie odbyla',
      wyplataPsychologa())
  })

  it('11b: psycholog nie wyplaca sobie depozytu przy wizycie CANCELLED', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('psycholog sam odwoluje wizyte i zatrzymuje oplate pacjenta',
      wyplataPsychologa())
  })

  it('12: RELEASED bez wzrostu salda psychologa — odmowa (monety przestaja istniec)', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('depozyt RELEASED bez wzrostu salda psychologa niszczy monety',
      wyplataPsychologa({ bezSalda: true }))
  })

  it('13: RELEASED ze wzrostem salda psychologa o wiecej niz amount — odmowa', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('psycholog zyskuje 90 z depozytu 80 — bicie monet',
      wyplataPsychologa({ saldo: 90 }))
  })

  it('13b: RELEASED ze wzrostem salda psychologa o mniej niz amount — odmowa', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('depozyt 80 rozliczony na 50 — 30 monet znika',
      wyplataPsychologa({ saldo: 50 }))
  })

  it('14: pacjent wyplaca HELD -> RELEASED po odwolaniu pozniej niz 24 h przed terminem', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await przechodzi('pozne odwolanie: oplata nalezy sie psychologowi', wyplataPacjenta())
  })

  it('14b: pozne odwolanie i wyplata w jednej partii', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 2 * GODZINA } })
    await przechodzi('odwolanie pacjenta <24 h i wyplata w jednej partii',
      wyplataPacjenta('pacjent', { ruchWizyty: { status: 'CANCELLED', cancelledBy: 'pacjent' } }))
  })

  it('15: pacjent wyplaca psychologowi przy odwolaniu WCZESNIEJ niz 24 h przed terminem — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('pacjent oddaje psychologowi oplate za wizyte odwolana z duzym wyprzedzeniem — jemu nalezy sie zwrot',
      wyplataPacjenta())
  })

  it('15b: pacjent nie wyplaca psychologowi przy wizycie BOOKED', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('pacjent oddaje psychologowi oplate, a wizyta nadal stoi w kalendarzu',
      wyplataPacjenta())
  })

  it('16: obcy przestawia HELD -> RELEASED — odmowa', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('obca osoba wyplaca cudzy depozyt psychologowi',
      wyplataPacjenta('ofiara', { bezWpisu: true }))
  })

  it('17: RELEASED -> REFUNDED (drugie rozliczenie tego samego depozytu) — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN },
      depozyt: { status: 'RELEASED' }, saldoPacjenta: 420, saldoPsychologa: CENA,
    })
    await odparty('depozyt wyplacony psychologowi zwrocony tez pacjentowi — podwojne rozliczenie to bicie monet',
      zwrotPacjenta())
  })

  it('17b: ponowne rozliczenie rozliczonego depozytu przez deklaracje salda (depozyt nietkniety w partii) — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      depozyt: { status: 'RELEASED' }, cena: DUZA_CENA, saldoPsychologa: DUZA_CENA, saldoPacjenta: 0,
    })
    await odparty('psycholog drugi raz pobiera ten sam depozyt przez stara deklaracje — bicie monet',
      partia('psycholog', (b, db) => saldo(b, db, 'psycholog', 2 * DUZA_CENA, 'w1')))
  })

  it('18: RELEASED -> HELD — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      depozyt: { status: 'RELEASED' }, saldoPsychologa: CENA, saldoPacjenta: 420,
    })
    await odparty('wyplacony depozyt wraca do HELD — monety liczone podwojnie',
      partia('psycholog', (b, db) => status(b, db, 'HELD')))
  })

  it('18b: REFUNDED -> RELEASED — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      depozyt: { status: 'REFUNDED' }, saldoPacjenta: 500,
    })
    await odparty('zwrocony depozyt wyplacony tez psychologowi — podwojne rozliczenie to bicie monet',
      wyplataPsychologa())
  })
})

describe('depozyt — zwrot pacjentowi', () => {
  it('19: pacjent odzyskuje monety SAM, gdy odwolal ponad 24 h przed terminem', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await przechodzi('zwrot pacjenta bez udzialu psychologa', zwrotPacjenta())
  })

  it('19b: odwolanie i zwrot pacjenta w jednej partii', async () => {
    await seedStan()
    await przechodzi('odwolanie >24 h i zwrot w jednej partii',
      zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'pacjent' } }))
  })

  it('20: zwrot pacjenta ponizej 24 h przed terminem — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('pacjent odwolujacy tuz przed terminem odzyskuje oplate, ktora nalezy sie psychologowi',
      zwrotPacjenta())
  })

  it('21: psycholog zwraca pacjentowi niezaleznie od okna czasowego', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await przechodzi('zwrot przez psychologa', zwrotPsychologa())
  })

  it('22: REFUNDED bez wzrostu salda pacjenta to zniszczenie monet — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('depozyt REFUNDED bez wzrostu salda pacjenta niszczy monety',
      zwrotPacjenta({ bezSalda: true }))
    await odparty('depozyt REFUNDED przez psychologa bez wzrostu salda pacjenta niszczy monety',
      zwrotPsychologa({ bezSalda: true, bezWpisu: true }))
  })

  it('22b: zwrot ze wzrostem salda pacjenta o mniej niz amount — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('depozyt 80 zwrocony jako 50 — 30 monet znika',
      zwrotPacjenta({ saldo: 470 }))
  })

  it('22c: zwrot ze wzrostem salda pacjenta o wiecej niz amount (doladowanie bez deklaracji) — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('pacjent zyskuje 90 z depozytu 80 — bicie monet',
      zwrotPacjenta({ saldo: 510 }))
  })

  it('23: REFUNDED ze wzrostem salda psychologa zamiast pacjenta — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('zwrot idzie na konto psychologa — pacjent traci oplate i zwrot',
      zwrotPsychologa({ komu: 'psycholog', saldo: CENA, bezWpisu: true }))
  })

  it('23b: to samo bez deklaracji (wzrost w limicie doladowania) — odmowa z samej reguly depozytu', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('zwrot idzie na konto psychologa przez doladowanie — pacjent traci oplate i zwrot',
      zwrotPsychologa({ komu: 'psycholog', saldo: CENA, deklaracja: undefined, bezWpisu: true }))
  })

  it('24: REFUNDED przy wizycie BOOKED — odmowa', async () => {
    await seedStan()
    await odparty('pacjent odzyskuje oplate, a wizyta nadal sie odbedzie',
      zwrotPacjenta())
    await odparty('psycholog zwraca oplate, a wizyta nadal stoi jako zarezerwowana',
      zwrotPsychologa())
  })

  it('24b: REFUNDED przy wizycie COMPLETED — odmowa', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('zwrot za wizyte, ktora sie odbyla',
      zwrotPsychologa())
  })

  it('25: obcy przestawia HELD -> REFUNDED — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('obca osoba zwraca cudzy depozyt',
      zwrotPsychologa({ aktor: 'ofiara', bezWpisu: true }))
  })

  it('25b: psycholog innej wizyty (psy_x) nie zwraca cudzego depozytu', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('inny psycholog zwraca depozyt cudzej wizyty',
      zwrotPsychologa({ aktor: 'psy_x', bezWpisu: true }))
  })
})

describe('depozyt — niezmiennik i trwalosc', () => {
  it('26: nikt nie usuwa depozytu — pacjent, psycholog ani obcy', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    const przed = await sumaMonet()
    for (const uid of ['pacjent', 'psycholog', 'ofiara']) {
      await assertFails(deleteDoc(doc(authedDb(env, uid), 'escrows/w1')))
    }
    expect(await sumaMonet(), 'depozyt usuniety — monety w nim znikaja').toBe(przed)
  })

  it('26b: nikt nie usuwa rozliczonego depozytu', async () => {
    await seedStan({ depozyt: { status: 'RELEASED' }, saldoPsychologa: CENA, saldoPacjenta: 420 })
    for (const uid of ['pacjent', 'psycholog']) {
      await assertFails(deleteDoc(doc(authedDb(env, uid), 'escrows/w1')))
    }
  })

  it('26c: depozyt nie zmienia kwoty ani stron', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('wyplata ze zmiana kwoty depozytu',
      partia('psycholog', (b, db) => {
        b.update(doc(db, 'escrows/w1'), { status: 'RELEASED', amount: 10 })
        saldo(b, db, 'psycholog', CENA, 'w1')
      }))
    await odparty('wyplata ze zmiana odbiorcy depozytu',
      partia('psycholog', (b, db) => {
        b.update(doc(db, 'escrows/w1'), { status: 'RELEASED', psychologistId: 'psy_x' })
        saldo(b, db, 'psycholog', CENA, 'w1')
      }))
  })

  it('27: deklaracja wskazujaca depozyt, ktory NIE zmienia sie w partii — odmowa (obcy zapis)', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('pacjent dopisuje psychologowi monety deklaracja nietknietego depozytu — bicie monet',
      partia('pacjent', (b, db) => saldo(b, db, 'psycholog', CENA, 'w1')))
  })

  it('27b: deklaracja nietknietego depozytu, wlasne saldo ponad limit doladowania — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      cena: DUZA_CENA, saldoPacjenta: 0,
    })
    await odparty('psycholog podnosi sobie saldo o 1500 deklaracja depozytu, ktorego nie rozlicza — bicie monet',
      partia('psycholog', (b, db) => saldo(b, db, 'psycholog', DUZA_CENA, 'w1')))
  })

  it('27c: deklaracja nieistniejacego depozytu — odmowa', async () => {
    await seedStan({ cena: DUZA_CENA })
    await odparty('wlasne saldo o 1500 w gore deklaracja depozytu, ktory nie istnieje — bicie monet',
      partia('ofiara', (b, db) => saldo(b, db, 'ofiara', 500 + DUZA_CENA, 'nie_ma_takiego')))
  })

  it('27d: pusta i nietekstowa deklaracja nie otwiera salda', async () => {
    await seedStan({ cena: DUZA_CENA })
    await odparty('wzrost salda o 1500 z pusta deklaracja — bicie monet',
      partia('ofiara', (b, db) => saldo(b, db, 'ofiara', 500 + DUZA_CENA, '')))
    await odparty('wzrost salda o 1500 z deklaracja liczbowa — bicie monet',
      partia('ofiara', (b, db) => saldo(b, db, 'ofiara', 500 + DUZA_CENA, 7)))
  })

  it('28: deklaracja wskazujaca depozyt nalezacy do kogo innego — odmowa', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA }, cena: DUZA_CENA, saldoPacjenta: 0 })
    await odparty('depozyt rozliczony raz, a monety wplynely takze na konto osoby trzeciej — bicie monet',
      partia('psycholog', (b, db) => {
        status(b, db, 'RELEASED')
        saldo(b, db, 'psycholog', DUZA_CENA, 'w1')
        saldo(b, db, 'ofiara', 500 + DUZA_CENA, 'w1')
      }))
  })

  it('28b: pacjent odzyskujacy depozyt nie dopisuje tego samego depozytu psychologowi', async () => {
    await seedStan({
      wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN },
      cena: DUZA_CENA, saldoPacjenta: 0,
    })
    await odparty('depozyt zwrocony pacjentowi i jednoczesnie wyplacony psychologowi — bicie monet',
      partia('pacjent', (b, db) => {
        status(b, db, 'REFUNDED')
        saldo(b, db, 'pacjent', DUZA_CENA, 'w1')
        saldo(b, db, 'psycholog', DUZA_CENA, 'w1')
      }))
  })

  it('28c: psycholog nie rozlicza cudzego depozytu na swoje konto', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA }, cena: DUZA_CENA, saldoPacjenta: 0 })
    await odparty('psy_x pobiera depozyt nalezacy do innego psychologa — kradziez monet',
      partia('psy_x', (b, db) => {
        status(b, db, 'RELEASED')
        saldo(b, db, 'psy_x', DUZA_CENA, 'w1')
      }))
  })

  it('29: doladowanie ponad limit nadal odrzucane, w limicie nadal przechodzi', async () => {
    await seedStan()
    await odparty('doladowanie 1001 ponad limit jednorazowy — bicie monet',
      partia('ofiara', (b, db) => saldo(b, db, 'ofiara', 500 + 1001)))
    await przechodzi('doladowanie 1000 w granicach limitu',
      partia('ofiara', (b, db) => saldo(b, db, 'ofiara', 500 + 1000)), 1000)
  })

  it('29b: doladowanie obcego konta nadal odrzucane', async () => {
    await seedStan()
    await odparty('obca osoba dopisuje cudze saldo bez pokrycia — bicie monet',
      partia('ofiara', (b, db) => saldo(b, db, 'pacjent', 500)))
  })

  it('saldo ulamkowe nie przechodzi nawet przy zmniejszeniu', async () => {
    await seedStan()
    await odparty('saldo 419.5 rozjezdza sie z calkowitymi kwotami depozytow',
      partia('pacjent', (b, db) => saldo(b, db, 'pacjent', 419.5)))
  })

  it('obcy rozliczajacy depozyt nie zmienia przy okazji innych pol cudzego profilu', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('pacjent wyplacajac depozyt podmienia psychologowi adres e-mail',
      partia('pacjent', (b, db) => {
        status(b, db, 'RELEASED')
        b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: CENA, escrowSettlement: 'w1', email: 'przejety@example.com' })
      }))
    await odparty('pacjent wyplacajac depozyt nadpisuje psychologowi numer telefonu',
      partia('pacjent', (b, db) => {
        status(b, db, 'RELEASED')
        b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: CENA, escrowSettlement: 'w1', phone: '+48000000000' })
      }))
  })

  it('30: wlasne saldo wolno zmniejszac bez deklaracji', async () => {
    await seedStan()
    await przechodzi('wydanie monet z wlasnego salda', partia('pacjent', (b, db) => saldo(b, db, 'pacjent', 400)), -20)
  })

  it('30b: zmniejszenie salda z deklaracja rozliczanego depozytu tez przechodzi', async () => {
    await seedStan()
    await assertSucceeds(updateDoc(doc(authedDb(env, 'pacjent'), 'users/pacjent/private/contact'), {
      coinsBalance: 400, escrowSettlement: 'w1',
    }))
  })

  it('rozliczenie czesciowe dwa razy: psycholog nie wyciaga depozytu w ratach', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      cena: DUZA_CENA, saldoPacjenta: 0,
    })
    await odparty('rata 1 z 2: depozyt 1500 rozliczony kwota 1000',
      partia('psycholog', (b, db) => {
        status(b, db, 'RELEASED')
        saldo(b, db, 'psycholog', 1000, 'w1')
      }))
    await odparty('rata 2 z 2: dobranie reszty deklaracja tego samego depozytu',
      partia('psycholog', (b, db) => saldo(b, db, 'psycholog', 1500, 'w1')))
    await odparty('depozyt 1500 rozliczony kwota 500 i dobranie reszty',
      partia('psycholog', (b, db) => {
        status(b, db, 'RELEASED')
        saldo(b, db, 'psycholog', 500, 'w1')
      }))
  })
})

describe('profil bez deklaracji escrowSettlement (zapis modelu nadpisuje caly dokument)', () => {
  const profil = (over = {}) => ({
    id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
    firstName: 'Jan', lastName: 'Nowak', bio: 'bio', ...over,
  })

  it('zwykly zapis profilu bez deklaracji i bez zmiany salda PRZECHODZI', async () => {
    await seedStan()
    await assertSucceeds(updateDoc(doc(authedDb(env, 'pacjent'), 'users/pacjent'), { bio: 'nowe bio' }))
  })

  it('pelne nadpisanie profilu (.set) bez deklaracji PRZECHODZI, takze po starej deklaracji', async () => {
    await seedStan()
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: 'w1' })
    })
    await assertSucceeds(setDoc(doc(authedDb(env, 'pacjent'), 'users/pacjent'), profil()))
    await assertSucceeds(setDoc(doc(authedDb(env, 'pacjent'), 'users/pacjent/private/contact'), { coinsBalance: 420 }))
  })

  it('usuniecie starej deklaracji (deleteField i null) przechodzi', async () => {
    await seedStan()
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: 'w1' })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: deleteField() }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: null }))
  })

  it('zapis profilu psychologa z saldem w dol i bez deklaracji przechodzi', async () => {
    await seedStan({ saldoPsychologa: 100 })
    await assertSucceeds(setDoc(doc(authedDb(env, 'psycholog'), 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      pricePerSession: 80,
    }))
    await assertSucceeds(setDoc(doc(authedDb(env, 'psycholog'), 'users/psycholog/private/contact'), { coinsBalance: 50 }))
  })

  it('stara deklaracja nie pozwala po rozliczeniu zwiekszyc salda drugi raz', async () => {
    await seedStan({
      wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA },
      cena: DUZA_CENA, saldoPacjenta: 0,
    })
    await assertSucceeds(partia('psycholog', (b, db) => {
      status(b, db, 'RELEASED')
      saldo(b, db, 'psycholog', DUZA_CENA, 'w1')
    }).commit())
    await odparty('psycholog powtarza wzrost salda ze stara deklaracja — bicie monet',
      partia('psycholog', (b, db) => saldo(b, db, 'psycholog', 2 * DUZA_CENA)))
    await assertSucceeds(updateDoc(doc(authedDb(env, 'psycholog'), 'users/psycholog'), { bio: 'x' }))
  })
})

describe('ksiega operacji a depozyt', () => {
  it('wpis BOOKING_EARNING w ksiedze psychologa przy rezerwacji — odmowa (naleznosc powstaje przy wyplacie)', async () => {
    await seedWolna()
    const b = rezerwacja('pacjent')
    const db = b.baza
    b.set(doc(db, 'wallet_transactions/pacjent_w1_wplyw'), wpis('pacjent_w1_wplyw', {
      userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: 'BOOKING_EARNING',
    }))
    await odparty('psycholog dostaje w historii wplyw, ktorego nie ma — depozyt jeszcze nie zostal wyplacony', b)
  })

  it('wplyw w ksiedze psychologa po pozdnym odwolaniu — kwota rozna od amount odrzucona', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('wplyw 99999 w historii psychologa przy depozycie 80',
      wyplataPacjenta('pacjent', { wpis: { amount: 99999 } }))
  })

  it('wplyw w ksiedze osoby trzeciej zamiast psychologa — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('wplyw w historii osoby trzeciej',
      wyplataPacjenta('pacjent', { wpis: { userId: 'ofiara' } }))
  })

  it('wplyw z counterpartyId niewskazujacym piszacego — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('wplyw podpisany cudzym counterpartyId',
      wyplataPacjenta('pacjent', { wpis: { counterpartyId: 'ofiara' } }))
  })

  it('wplyw w ksiedze psychologa bez przestawienia depozytu (depozyt HELD nietkniety) — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('wpisy widmo w historii psychologa bez wyplaty',
      partia('pacjent', (b, db) => {
        b.set(doc(db, 'wallet_transactions/widmo'), wpis('widmo', {
          userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: 'BOOKING_EARNING',
        }))
      }))
  })

  it('K1: wplyw w ksiedze psychologa po wyplacie, depozyt RELEASED nietkniety w partii — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA },
      depozyt: { status: 'RELEASED' }, saldoPsychologa: CENA, saldoPacjenta: 420,
    })
    await odparty('wpisy widmo po rozliczeniu: getAfter na nietknietym depozycie widzi stan biezacy',
      partia('pacjent', (b, db) => {
        for (let i = 0; i < 5; i++) {
          b.set(doc(db, 'wallet_transactions/widmo' + i), wpis('widmo' + i, {
            userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: 'BOOKING_EARNING',
          }))
        }
      }))
  })

  it('wpis REFUND w ksiedze pacjenta po zwrocie, depozyt REFUNDED nietkniety w partii — odmowa', async () => {
    await seedStan({
      wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN },
      depozyt: { status: 'REFUNDED' }, saldoPacjenta: 500,
    })
    await odparty('wpisy widmo zwrotu po rozliczeniu: getAfter na nietknietym depozycie widzi stan biezacy',
      partia('psycholog', (b, db) => {
        for (let i = 0; i < 3; i++) {
          b.set(doc(db, 'wallet_transactions/widmo_zwrot' + i), wpis('widmo_zwrot' + i, {
            userId: 'pacjent', counterpartyId: 'psycholog', amount: CENA, type: 'REFUND',
          }))
        }
      }))
  })

  it('wyplata depozytu nie pozwala dopisac psychologowi wpisu innego typu niz BOOKING_EARNING', async () => {
    for (const typ of ['BOOKING_PAYMENT', 'TOPUP', 'WITHDRAWAL']) {
      await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
      const b = wyplataPacjenta()
      b.set(doc(b.baza, 'wallet_transactions/obce_' + typ), wpis('obce_' + typ, {
        userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: typ,
      }))
      await odparty('falszywy wpis typu ' + typ + ' w historii psychologa', b)
    }
  })

  it('pacjent nie ma sciezki do wpisu REFUND w cudzej ksiedze', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('pacjent zapisuje psychologowi falszywy wpis REFUND',
      partia('pacjent', (b, db) => {
        status(b, db, 'REFUNDED')
        saldo(b, db, 'pacjent', 500, 'w1')
        b.set(doc(db, 'wallet_transactions/falsz'), wpis('falsz', {
          userId: 'psycholog', counterpartyId: 'pacjent', amount: -CENA, type: 'REFUND',
        }))
      }))
  })

  it('wpis BOOKING_PAYMENT w cudzej ksiedze — odmowa', async () => {
    await seedWolna()
    const b = rezerwacja('pacjent')
    const db = b.baza
    b.set(doc(db, 'wallet_transactions/obciazenie'), wpis('obciazenie', {
      userId: 'ofiara', counterpartyId: 'pacjent', amount: -CENA, type: 'BOOKING_PAYMENT',
    }))
    await odparty('falszywe obciazenie cudzej historii oplata za wizyte', b)
  })

  it('zwrot psychologa: typ inny niz REFUND w ksiedze pacjenta — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('zwrot zapisany w historii pacjenta jako wplyw',
      zwrotPsychologa({ wpis: { type: 'BOOKING_EARNING' } }))
  })

  it('znane ograniczenie: pacjent wyplacajacy depozyt moze zdublowac wplyw o kwocie rownej amount', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    const b = wyplataPacjenta()
    const db = b.baza
    b.set(doc(db, 'wallet_transactions/dublet'), wpis('dublet', {
      userId: 'psycholog', counterpartyId: 'pacjent', amount: CENA, type: 'BOOKING_EARNING',
    }))
    await przechodzi('wyplata ze zdublowanym wpisem w historii (saldo szczelne)', b)
  })
})

describe('wizyta z depozytem nie wraca do FREE', () => {
  it('psycholog nie zwalnia terminu do FREE, gdy depozyt jest HELD', async () => {
    await seedStan()
    await odparty('zwolniony termin zostawia depozyt w HELD na zawsze — monety zablokowane',
      partia('psycholog', (b, db) => b.update(doc(db, 'appointments/w1'), { status: 'FREE', patientId: null, notes: '' })))
  })

  it('pacjent nie zwalnia terminu do FREE, gdy depozyt jest HELD', async () => {
    await seedStan()
    await odparty('zwolniony termin zostawia depozyt w HELD na zawsze — monety zablokowane',
      partia('pacjent', (b, db) => b.update(doc(db, 'appointments/w1'), { status: 'FREE', patientId: null, notes: '' })))
  })

  it('nie zwalnia terminu do FREE rowniez po rozliczeniu depozytu', async () => {
    await seedStan({
      wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN },
      depozyt: { status: 'REFUNDED' }, saldoPacjenta: 500,
    })
    await odparty('ponowna rezerwacja musialaby zalozyc depozyt pod zajetym identyfikatorem',
      partia('psycholog', (b, db) => b.update(doc(db, 'appointments/w1'), { status: 'FREE', patientId: null, notes: '' })))
  })

  it('wizyta sprzed depozytow (bez dokumentu escrows) nadal da sie zwolnic', async () => {
    await seedStan({ bezDepozytu: true })
    await assertSucceeds(updateDoc(doc(authedDb(env, 'psycholog'), 'appointments/w1'), {
      status: 'FREE', patientId: null, notes: '',
    }))
  })
})

describe('kto odwolal wizyte (cancelledBy)', () => {
  const GODZ2 = () => Date.now() + 2 * GODZINA
  const zwolnij = (uid, ruch) => updateDoc(doc(authedDb(env, uid), 'appointments/w1'), ruch)

  it('NAGLOWKOWY: psycholog odwoluje 2 h przed terminem, pacjent SAM odzyskuje monety', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await assertSucceeds(zwolnij('psycholog', { status: 'CANCELLED', cancelledBy: 'psycholog' }))
    await przechodzi('zwrot pacjenta po odwolaniu przez psychologa, bez udzialu psychologa', zwrotPacjenta())
  })

  it('pacjent odwolujacy 2 h przed terminem NIE odzyskuje monet (bez zmian)', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await odparty('pacjent odwolal pozno i odbiera oplate, ktora nalezy sie psychologowi',
      zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'pacjent' } }))
  })

  it('psycholog odwoluje i sam zwraca — przechodzi (bez zmian)', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await przechodzi('odwolanie i zwrot psychologa w jednej partii',
      zwrotPsychologa({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'psycholog' } }))
  })

  it('pacjent podrabia odwolujacego: wpisuje psychologa przy WLASNYM odwolaniu — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await odparty('pacjent udaje, ze wizyte odwolal psycholog, i odbiera oplate za pozne odwolanie',
      zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'psycholog' } }))
  })

  it('samo podrobienie pola przy odwolaniu pacjenta jest odrzucone przez regule wizyty', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await assertFails(zwolnij('pacjent', { status: 'CANCELLED', cancelledBy: 'psycholog' }))
  })

  it('odwolanie bez pola cancelledBy jest odrzucone', async () => {
    await seedStan({ wizyta: { startsAtMillis: GODZ2() } })
    await assertFails(zwolnij('pacjent', { status: 'CANCELLED' }))
    await assertFails(zwolnij('psycholog', { status: 'CANCELLED' }))
  })

  it('podmiana pola po odwolaniu (CANCELLED -> CANCELLED) — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: 'pacjent', startsAtMillis: GODZ2() } })
    await odparty('pacjent po swoim odwolaniu przepisuje je na psychologa i odbiera oplate',
      zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'psycholog' } }))
    await assertFails(zwolnij('pacjent', { cancelledBy: 'psycholog' }))
    await assertFails(zwolnij('psycholog', { cancelledBy: 'psycholog' }))
  })

  it('ten sam zapis CANCELLED -> CANCELLED bez zmiany pola przechodzi', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: 'pacjent', startsAtMillis: GODZ2() } })
    await assertSucceeds(zwolnij('pacjent', { notes: 'x' }))
  })

  it('pole nie powstaje poza odwolaniem: FREE, COMPLETED i rezerwacja z planta sa odrzucone', async () => {
    await seedStan({ bezDepozytu: true, wizyta: { startsAtMillis: Date.now() - GODZINA } })
    await assertFails(zwolnij('psycholog', { status: 'COMPLETED', cancelledBy: 'psycholog' }))
    await assertSucceeds(zwolnij('psycholog', { status: 'COMPLETED' }))
  })

  it('powrot do FREE czysci pole; zostawienie go jest odrzucone', async () => {
    await seedStan({ bezDepozytu: true, wizyta: { status: 'CANCELLED', cancelledBy: 'psycholog' } })
    await assertFails(zwolnij('psycholog', { status: 'FREE', patientId: null, notes: '' }))
    await assertFails(zwolnij('psycholog', { status: 'FREE', patientId: null, notes: '', cancelledBy: 'psycholog' }))
    await assertSucceeds(zwolnij('psycholog', { status: 'FREE', patientId: null, notes: '', cancelledBy: deleteField() }))
  })

  it('rezerwacja nie przenosi podrzuconego pola cancelledBy', async () => {
    await seedWolna({ cena: 0 })
    await assertFails(updateDoc(doc(authedDb(env, 'pacjent'), 'appointments/w1'), {
      status: 'BOOKED', patientId: 'pacjent', notes: '', cancelledBy: 'psycholog',
    }))
  })

  it('zwrot pacjenta, gdy odwolanie jest sprzed pola (brak cancelledBy) i < 24 h — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: undefined, startsAtMillis: GODZ2() } })
    await odparty('odwolanie bez wiadomego odwolujacego nie upowaznia pacjenta do zwrotu tuz przed terminem',
      zwrotPacjenta())
  })

  it('pacjent nie wyplaca psychologowi monet po odwolaniu PRZEZ PSYCHOLOGA — odmowa', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: 'psycholog', startsAtMillis: GODZ2() } })
    await odparty('pacjent oddaje psychologowi pieniadze za wizyte, ktora odwolal psycholog',
      wyplataPacjenta())
  })

  it('pacjent nie wyplaca psychologowi monet, gdy odwolania nie ma w danych (brak cancelledBy)', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: undefined, startsAtMillis: GODZ2() } })
    await odparty('wyplata pacjenta bez dowodu, ze to on odwolal',
      wyplataPacjenta())
  })
})

describe('rezerwacja platnego terminu wymaga depozytu', () => {
  const rezerwujSamTermin = (uid = 'pacjent') => partia(uid, (b, db) => {
    b.update(doc(db, 'appointments/w1'), { status: 'BOOKED', patientId: uid, notes: '' })
  })

  it('NAGLOWKOWY: zajecie platnego terminu bez zaplaty — odmowa', async () => {
    await seedWolna()
    await odparty('platny termin zajety za darmo — psycholog traci cene wizyty',
      rezerwujSamTermin())
  })

  it('platny termin z depozytem przechodzi (sciezka szczesliwa)', async () => {
    await seedWolna()
    await przechodzi('rezerwacja platnego terminu z depozytem', rezerwacja('pacjent'))
  })

  it('termin DARMOWY bez depozytu przechodzi (wymog jest warunkowy)', async () => {
    await seedWolna({ cena: 0 })
    await assertSucceeds(rezerwujSamTermin().commit())
  })

  it('depozyt wskazujacy INNA wizyte nie upowaznia do rezerwacji', async () => {
    await seedWolna()
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/w2'), wizyta({ appointmentId: 'w2', status: 'FREE', patientId: null }))
    })
    const b = partia('pacjent', (bb, db) => {
      bb.update(doc(db, 'appointments/w1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      bb.update(doc(db, 'appointments/w2'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      bb.set(doc(db, 'escrows/w2'), depozyt({ appointmentId: 'w2' }))
      saldo(bb, db, 'pacjent', 500 - CENA, 'w2')
    })
    await odparty('dwie wizyty zajete, a zaplacona jedna — druga za darmo', b)
  })

  it('depozyt istniejacy PRZED partia nie upowaznia do rezerwacji', async () => {
    await seedWolna()
    await seed(env, async (db) => {
      await setDoc(doc(db, 'escrows/w1'), depozyt())
    })
    await odparty('stary depozyt (cudzy albo zalegly) udaje zaplate za nowa rezerwacje',
      rezerwujSamTermin())
  })

  it('termin zapisany przed etapem (bez priceCoins) rezerwuje sie bez depozytu', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/pacjent', uzytkownik('pacjent', 'PATIENT', 500))
      await setDoc(doc(db, 'appointments/w1'), wizyta({ status: 'FREE', patientId: null, priceCoins: undefined }))
    })
    await assertSucceeds(rezerwujSamTermin().commit())
  })
})

describe('partie z wieloma depozytami (ta sama wartosc naduzywana przez kilka dokumentow)', () => {
  const IDS = ['a1', 'a2']
  const CENA2 = 100

  async function seedWiele({ wolne = false, starty = {}, saldoPacjenta = 300 } = {}) {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', 0, { pricePerSession: CENA2 }))
      await zalozUzytkownika(db, 'users/pacjent', uzytkownik('pacjent', 'PATIENT', saldoPacjenta))
      await zalozUzytkownika(db, 'users/ofiara', uzytkownik('ofiara', 'PATIENT', 500))
      for (const id of IDS) {
        const w = wolne
          ? { status: 'FREE', patientId: null, startsAtMillis: Date.now() + 3 * DZIEN }
          : { status: 'CANCELLED', cancelledBy: 'pacjent', startsAtMillis: starty[id] ?? Date.now() + 2 * GODZINA }
        await setDoc(doc(db, 'appointments/' + id), wizyta({ appointmentId: id, priceCoins: CENA2, ...w }))
        if (!wolne) {
          await setDoc(doc(db, 'escrows/' + id), depozyt({ appointmentId: id, amount: CENA2 }))
        }
      }
    })
  }

  const ksiegiRezerwacji = (b, db, ids) => {
    for (const id of ids) {
      b.update(doc(db, 'appointments/' + id), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
      b.set(doc(db, 'escrows/' + id), depozyt({ appointmentId: id, amount: CENA2 }))
    }
  }

  it('Z1: dwa depozyty w jednej partii przy jednym spadku salda — odmowa (bicie monet)', async () => {
    await seedWiele({ wolne: true, saldoPacjenta: 500 })
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('partia z dwoma depozytami i jednym spadkiem salda bije monety (deklaracja ' + deklaracja + ')',
        partia('pacjent', (b, db) => {
          ksiegiRezerwacji(b, db, IDS)
          saldo(b, db, 'pacjent', 500 - CENA2, deklaracja)
        }))
    }
  })

  it('Z1b: dwa depozyty bez deklaracji i z podwojnym spadkiem salda — odmowa', async () => {
    await seedWiele({ wolne: true, saldoPacjenta: 500 })
    await odparty('partia z dwoma depozytami bez deklaracji bije monety',
      partia('pacjent', (b, db) => {
        ksiegiRezerwacji(b, db, IDS)
        saldo(b, db, 'pacjent', 500 - 2 * CENA2)
      }))
  })

  it('Z1c: kazda rezerwacja we WLASNEJ partii z wlasna deklaracja przechodzi', async () => {
    await seedWiele({ wolne: true, saldoPacjenta: 500 })
    await przechodzi('pierwsza rezerwacja', partia('pacjent', (b, db) => {
      ksiegiRezerwacji(b, db, ['a1'])
      saldo(b, db, 'pacjent', 400, 'a1')
    }))
    await przechodzi('druga rezerwacja', partia('pacjent', (b, db) => {
      ksiegiRezerwacji(b, db, ['a2'])
      saldo(b, db, 'pacjent', 300, 'a2')
    }))
  })

  it('Z2: dwa depozyty rozliczane w jednej partii przy jednym wzroscie salda psychologa — odmowa (niszczenie monet)', async () => {
    await seedWiele()
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('rozliczenie dwoch depozytow jednym wzrostem salda niszczy monety (deklaracja ' + deklaracja + ')',
        partia('pacjent', (b, db) => {
          for (const id of IDS) b.update(doc(db, 'escrows/' + id), { status: 'RELEASED' })
          saldo(b, db, 'psycholog', CENA2, deklaracja)
        }))
    }
  })

  it('Z2b: dwa depozyty zwracane w jednej partii przy jednym wzroscie salda pacjenta — odmowa (niszczenie monet)', async () => {
    await seedWiele({ starty: { a1: Date.now() + 3 * DZIEN, a2: Date.now() + 3 * DZIEN } })
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('zwrot dwoch depozytow jednym wzrostem salda niszczy monety (deklaracja ' + deklaracja + ')',
        partia('pacjent', (b, db) => {
          for (const id of IDS) b.update(doc(db, 'escrows/' + id), { status: 'REFUNDED' })
          saldo(b, db, 'pacjent', 300 + CENA2, deklaracja)
        }))
    }
  })

  it('Z2c: wariant krzyzowy — jeden depozyt wyplacony, drugi zwrocony, ruch salda tylko po jednej stronie — odmowa', async () => {
    await seedWiele({ starty: { a1: Date.now() + 2 * GODZINA, a2: Date.now() + 3 * DZIEN } })
    await odparty('wyplata a1 i zwrot a2 przy samym wzroscie salda psychologa niszczy monety',
      partia('pacjent', (b, db) => {
        status(b, db, 'RELEASED')
        b.update(doc(db, 'escrows/a2'), { status: 'REFUNDED' })
        saldo(b, db, 'psycholog', CENA2, 'a1')
      }))
    await odparty('wyplata a1 i zwrot a2 przy samym wzroscie salda pacjenta niszczy monety',
      partia('pacjent', (b, db) => {
        b.update(doc(db, 'escrows/a1'), { status: 'RELEASED' })
        b.update(doc(db, 'escrows/a2'), { status: 'REFUNDED' })
        saldo(b, db, 'pacjent', 300 + CENA2, 'a2')
      }))
  })

  it('Z2d: wariant krzyzowy — obie strony podnosza saldo deklaracja tego samego depozytu — odmowa', async () => {
    await seedWiele({ starty: { a1: Date.now() + 2 * GODZINA, a2: Date.now() + 3 * DZIEN } })
    await odparty('dwa wzrosty salda z jednego depozytu i niezgodna deklaracja drugiego rozliczenia',
      partia('pacjent', (b, db) => {
        b.update(doc(db, 'escrows/a1'), { status: 'RELEASED' })
        b.update(doc(db, 'escrows/a2'), { status: 'REFUNDED' })
        saldo(b, db, 'psycholog', CENA2, 'a1')
        saldo(b, db, 'pacjent', 300 + CENA2, 'a1')
      }))
  })

  it('Z2e: kontrola — wyplata a1 i zwrot a2 z DWOMA deklaracjami i DWOMA wzrostami salda przechodzi', async () => {
    await seedWiele({ starty: { a1: Date.now() + 2 * GODZINA, a2: Date.now() + 3 * DZIEN } })
    await przechodzi('wyplata a1 i zwrot a2 w jednej partii, kazde ze swoim ruchem salda',
      partia('pacjent', (b, db) => {
        b.update(doc(db, 'escrows/a1'), { status: 'RELEASED' })
        b.update(doc(db, 'escrows/a2'), { status: 'REFUNDED' })
        saldo(b, db, 'psycholog', CENA2, 'a1')
        saldo(b, db, 'pacjent', 300 + CENA2, 'a2')
      }))
  })
})

describe('zwrot po 14 dniach od terminu wizyty, ktora nadal jest BOOKED', () => {
  const OKNO = 14 * DZIEN

  it('pacjent odzyskuje monety SAM po 14 dniach (14 dni + minuta po terminie)', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await przechodzi('zwrot po niestawieniu sie psychologa', zwrotPacjenta())
  })

  it('14 dni minus minuta po terminie — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO + MARGINES } })
    await odparty('pacjent odbiera oplate za wizyte przed uplywem 14 dni od terminu',
      zwrotPacjenta())
  })

  it('godzine po terminie — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - GODZINA } })
    await odparty('pacjent odbiera oplate godzine po terminie, zanim psycholog zdazyl oznaczyc wizyte',
      zwrotPacjenta())
  })

  it('przed terminem wizyta BOOKED nie daje zwrotu pacjentowi — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('pacjent odbiera oplate za wizyte, ktora jeszcze sie nie odbyla',
      zwrotPacjenta())
  })

  it('wizyta juz COMPLETED — odmowa (kto pierwszy, ten rozliczy)', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - OKNO - MARGINES } })
    await odparty('pacjent odbiera oplate za wizyte oznaczona jako odbyta',
      zwrotPacjenta())
  })

  it('brak startsAtMillis — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: undefined } })
    await odparty('zwrot po oknie bez znanego terminu', zwrotPacjenta())
  })

  it('obcy i psycholog nie korzystaja ze sciezki pacjenta po 14 dniach', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await odparty('obca osoba zwraca depozyt po oknie',
      zwrotPsychologa({ aktor: 'ofiara', bezWpisu: true }))
  })

  it('brak podwojnego rozliczenia: po zwrocie pacjenta psycholog nie wyplaci depozytu', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await przechodzi('zwrot po 14 dniach', zwrotPacjenta())
    await odparty('psycholog oznacza wizyte i probuje wyplacic depozyt, ktory juz wrocil do pacjenta',
      wyplataPsychologa({ ruchWizyty: { status: 'COMPLETED' } }))
  })

  it('brak podwojnego rozliczenia: po wyplacie psychologa pacjent nie odbierze depozytu', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await przechodzi('wyplata psychologa', wyplataPsychologa({ ruchWizyty: { status: 'COMPLETED' } }))
    await odparty('pacjent odbiera depozyt, ktory juz trafil do psychologa',
      zwrotPacjenta())
  })

  it('zwrot po 14 dniach z deklaracja, ale bez wzrostu salda pacjenta — odmowa (niszczy monety)', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await odparty('depozyt REFUNDED po 14 dniach bez wzrostu salda niszczy monety',
      partia('pacjent', (b, db) => {
        status(b, db, 'REFUNDED')
        saldo(b, db, 'pacjent', 500 - CENA, 'w1')
      }))
  })

  it('zwrot po 14 dniach: dwa depozyty w jednej partii przy jednym wzroscie salda — odmowa (niszczenie monet)', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', 0, { pricePerSession: 100 }))
      await zalozUzytkownika(db, 'users/pacjent', uzytkownik('pacjent', 'PATIENT', 300))
      for (const id of ['a1', 'a2']) {
        await setDoc(doc(db, 'appointments/' + id), wizyta({
          appointmentId: id, priceCoins: 100, startsAtMillis: Date.now() - OKNO - MARGINES,
        }))
        await setDoc(doc(db, 'escrows/' + id), depozyt({ appointmentId: id, amount: 100 }))
      }
    })
    for (const deklaracja of ['a1', 'a2']) {
      await odparty('zwrot po oknie dwoch depozytow jednym wzrostem salda niszczy monety (deklaracja ' + deklaracja + ')',
        partia('pacjent', (b, db) => {
          for (const id of ['a1', 'a2']) b.update(doc(db, 'escrows/' + id), { status: 'REFUNDED' })
          saldo(b, db, 'pacjent', 400, deklaracja)
        }))
    }
  })

  it('zwrot po 14 dniach razem z oznaczeniem wizyty jako odbytej przez pacjenta — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await odparty('pacjent odbiera oplate i sam oznacza wizyte jako odbyta',
      zwrotPacjenta({ ruchWizyty: { status: 'COMPLETED' } }))
  })

  it('zwrot po 14 dniach bez deklaracji lub bez wzrostu salda — odmowa', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - OKNO - MARGINES } })
    await odparty('zwrot po oknie bez wzrostu salda niszczy monety', zwrotPacjenta({ bezSalda: true }))
  })
})

describe('cancelledBy - uzupelnienia rundy 2', () => {
  it('Z5: termin nie moze powstac z polem cancelledBy', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', 0, { pricePerSession: CENA }))
    })
    const nowy = {
      appointmentId: 'nowy', psychologistId: 'psycholog', patientId: null,
      startsAtMillis: Date.now() + 3 * DZIEN, date: '2026-10-02', time: '11:00',
      notes: '', status: 'FREE', priceCoins: CENA,
    }
    await assertFails(setDoc(doc(authedDb(env, 'psycholog'), 'appointments/nowy'), { ...nowy, cancelledBy: 'psycholog' }))
    await assertSucceeds(setDoc(doc(authedDb(env, 'psycholog'), 'appointments/nowy'), nowy))
  })

  it('Z6: zwrot ponad 24 h dla odwolania sprzed pola (brak cancelledBy) PRZECHODZI', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', cancelledBy: undefined, startsAtMillis: Date.now() + 3 * DZIEN } })
    await przechodzi('zwrot starego odwolania ponad 24 h przed terminem', zwrotPacjenta())
  })
})

describe('okno 24 h — przypadki przeniesione z refund-pending.test.js', () => {
  it('pacjent odwoluje ponad 24 h przed terminem i w tej samej partii odzyskuje monety', async () => {
    await seedStan()
    await przechodzi('odwolanie + zwrot pacjenta', zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'pacjent' } }))
  })

  it('kontrola izolacji: samo CANCELLED przechodzi nawet o 2 h przed terminem', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 2 * GODZINA } })
    await assertSucceeds(updateDoc(doc(authedDb(env, 'pacjent'), 'appointments/w1'), { status: 'CANCELLED', cancelledBy: 'pacjent' }))
  })

  it('zwrot pacjenta ponizej 24 h przed terminem w partii z odwolaniem — odrzucony', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 2 * GODZINA } })
    await odparty('zwrot pacjenta tuz przed terminem — oplata nalezy sie psychologowi',
      zwrotPacjenta({ ruchWizyty: { status: 'CANCELLED', cancelledBy: 'pacjent' } }))
  })

  it('zwrot tuz ponizej progu (24 h minus minuta) — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + DZIEN - MARGINES } })
    await odparty('zwrot pacjenta minute przed progiem 24 h', zwrotPacjenta())
  })

  it('zwrot tuz powyzej progu (24 h plus minuta) — przechodzi', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + DZIEN + MARGINES } })
    await przechodzi('zwrot pacjenta minute po progu 24 h', zwrotPacjenta())
  })

  it('E5-R19: zwrot pacjenta na terminie bez startsAtMillis — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: undefined } })
    await odparty('zwrot pacjenta bez znanego terminu — okna 24 h nie da sie sprawdzic',
      zwrotPacjenta())
  })

  it('zwrot przy wizycie COMPLETED zamiast CANCELLED — odrzucony (takze dla psychologa)', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - 2 * GODZINA } })
    await odparty('zwrot za odbyta wizyte', zwrotPsychologa())
  })

  // Izolacja: partia pada tez na zapisie wizyty na FREE (bezPowrotuDoFreeZDepozytem). Dowod dla
  // klauzuli 'zwrot wymaga wizyty CANCELLED' jest w testach 24 i 24b (wizyta BOOKED/COMPLETED).
  it('zwrot przy zwolnieniu terminu do FREE zamiast CANCELLED — odrzucony', async () => {
    await seedStan()
    await odparty('zwrot z jednoczesnym zwolnieniem terminu',
      zwrotPacjenta({ ruchWizyty: { status: 'FREE', patientId: null, notes: '' } }))
  })

  it('kontrola: termin da sie wystawic bez zadnego sladu zwrotu', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', uzytkownik('psycholog', 'PSYCHOLOGIST', 0, { pricePerSession: CENA }))
    })
    await assertSucceeds(setDoc(doc(authedDb(env, 'psycholog'), 'appointments/nowy'), {
      appointmentId: 'nowy', psychologistId: 'psycholog', patientId: null,
      startsAtMillis: Date.now() + 3 * DZIEN, date: '2026-10-02', time: '11:00',
      notes: '', status: 'FREE', priceCoins: CENA,
    }))
  })

  it('kontrola: sam ruch FREE -> BOOKED darmowego terminu przechodzi na poziomie reguly wizyty', async () => {
    await seedWolna({ cena: 0 })
    await assertSucceeds(updateDoc(doc(authedDb(env, 'pacjent'), 'appointments/w1'), {
      status: 'BOOKED', patientId: 'pacjent', notes: '',
    }))
  })

  it('wizyta CANCELLED z depozytem HELD nie przechodzi na FREE', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED' } })
    await odparty('odwolana wizyta zwolniona do FREE zostawia depozyt HELD bez wizyty',
      partia('psycholog', (b, db) => b.update(doc(db, 'appointments/w1'), { status: 'FREE', patientId: null, notes: '' })))
  })

  it('psycholog zwraca pacjentowi po odwolaniu ponad 24 h przed terminem: saldo i wpis w ksiedze', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await przechodzi('zwrot psychologa', zwrotPsychologa())
  })

  it('zwrot psychologa przy bliskim terminie tez przechodzi — okno dotyczy tylko pacjenta', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 2 * GODZINA } })
    await przechodzi('zwrot psychologa tuz przed terminem', zwrotPsychologa())
  })

  it('wpis zwrotu w ksiedze kogos innego niz pacjent — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('wpis zwrotu w ksiedze osoby trzeciej',
      zwrotPsychologa({ wpis: { userId: 'ofiara' } }))
  })

  it('wpis zwrotu o kwocie innej niz priceCoins — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('wpis zwrotu 79 przy cenie 80', zwrotPsychologa({ wpis: { amount: 79 } }))
  })

  it('wpis zwrotu na wizycie CANCELLED, gdy depozyt nie zmienia sie w partii — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('wpis zwrotu bez zwrotu monet',
      partia('psycholog', (b, db) => {
        b.set(doc(db, 'wallet_transactions/zwrot_bez_ruchu'), wpis('zwrot_bez_ruchu', {
          userId: 'pacjent', counterpartyId: 'psycholog', amount: CENA, type: 'REFUND',
        }))
      }))
  })

  it('drugi zwrot tej samej wizyty — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await przechodzi('pierwszy zwrot', zwrotPsychologa())
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'users/psycholog/private/contact'), { coinsBalance: CENA })
    })
    await odparty('drugi zwrot tego samego depozytu — bicie monet',
      partia('psycholog', (b, db) => {
        status(b, db, 'REFUNDED')
        saldo(b, db, 'pacjent', 580, 'w1')
        b.set(doc(db, 'wallet_transactions/drugi_zwrot'), wpis('drugi_zwrot', {
          userId: 'pacjent', counterpartyId: 'psycholog', amount: CENA, type: 'REFUND',
        }))
      }))
  })

  // Izolacja: partia pada tez na zapisie wizyty na FREE (bezPowrotuDoFreeZDepozytem). Dowod dla
  // klauzul wpisu zwrotu (kierunek, uzytkownik, kwota) jest w 'pacjent nie ma sciezki do wpisu REFUND'
  // i w testach zwrot psychologa z 'zwroty - psycholog odwoluje' w refunds.test.js.
  it('zwrot, ktory przy okazji zwalnia termin do FREE — odrzucony', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() + 3 * DZIEN } })
    await odparty('zwrot przez psychologa z jednoczesnym zwolnieniem terminu',
      zwrotPsychologa({ ruchWizyty: { status: 'FREE', patientId: null, notes: '' } }))
  })

  it('zwrot po terminie wizyty (psycholog odwolal wizyte, ktora sie nie odbyla) — przechodzi', async () => {
    await seedStan({ wizyta: { status: 'CANCELLED', startsAtMillis: Date.now() - 2 * DZIEN } })
    await przechodzi('zwrot za wizyte po terminie', zwrotPsychologa())
  })
})

describe('odwolanie wizyty, ktora sie juz zaczela — zalezy od tego, kto zapisuje (przeniesione)', () => {
  const odwolaj = (uid, ruch = { status: 'CANCELLED', cancelledBy: uid }) =>
    updateDoc(doc(authedDb(env, uid), 'appointments/w1'), ruch)

  it('pacjent odwoluje przed terminem — przechodzi', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() + 2 * GODZINA } })
    await assertSucceeds(odwolaj('pacjent'))
  })

  it('pacjent odwoluje po terminie — odrzucone', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - GODZINA } })
    await assertFails(odwolaj('pacjent'))
  })

  it('psycholog odwoluje po terminie — przechodzi (okno nie dotyczy psychologa)', async () => {
    await seedStan({ wizyta: { startsAtMillis: Date.now() - GODZINA } })
    await assertSucceeds(odwolaj('psycholog'))
  })

  it('psycholog zwalnia termin do FREE po terminie — przechodzi (wizyta sprzed depozytow)', async () => {
    await seedStan({ bezDepozytu: true, wizyta: { startsAtMillis: Date.now() - GODZINA } })
    await assertSucceeds(odwolaj('psycholog', { status: 'FREE', patientId: null, notes: '' }))
  })

  it('psycholog odwoluje wizyte bez startsAtMillis — przechodzi', async () => {
    await seedStan({ wizyta: { startsAtMillis: undefined } })
    await assertSucceeds(odwolaj('psycholog'))
  })

  it('pacjent nie odwola wizyty bez startsAtMillis — odrzucone', async () => {
    await seedStan({ wizyta: { startsAtMillis: undefined } })
    await assertFails(odwolaj('pacjent'))
  })

  it('zakonczonej wizyty (COMPLETED) nie da sie odwolac — odrzucone', async () => {
    await seedStan({ wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - GODZINA } })
    await assertFails(odwolaj('psycholog'))
  })

  it('zakonczonej wizyty (COMPLETED) nie da sie zwolnic do FREE — odrzucone', async () => {
    await seedStan({ bezDepozytu: true, wizyta: { status: 'COMPLETED', startsAtMillis: Date.now() - GODZINA } })
    await assertFails(odwolaj('psycholog', { status: 'FREE', patientId: null, notes: '' }))
  })
})
