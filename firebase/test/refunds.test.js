import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv , zalozUzytkownika } from './helpers.js'

let env

const GODZINA = 60 * 60 * 1000
const DZIEN = 24 * GODZINA
const MARGINES = 60 * 1000

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => { await env.clearFirestore() })

const seedWizyta = (startsAtMillis, extra = {}) => seed(env, async (db) => {
  await zalozUzytkownika(db, 'users/psycholog', {
    id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
    isAdmin: false, coinsBalance: 160, pricePerSession: 80,
  })
  await zalozUzytkownika(db, 'users/pacjent', {
    id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
  })
  await zalozUzytkownika(db, 'users/ofiara', {
    id: 'ofiara', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
  })
  const wizyta = {
    appointmentId: 'w1', psychologistId: 'psycholog', patientId: 'pacjent',
    date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 80, ...extra,
  }
  if (startsAtMillis !== undefined) wizyta.startsAtMillis = startsAtMillis
  await setDoc(doc(db, 'appointments/w1'), wizyta)
  await setDoc(doc(db, 'escrows/w1'), {
    appointmentId: 'w1', patientId: 'pacjent', psychologistId: 'psycholog', amount: 80, status: 'HELD',
  })
})

const wpis = (id, over) => ({
  transactionId: id, userId: 'pacjent', counterpartyId: 'psycholog',
  appointmentId: 'w1', amount: 80, type: 'REFUND', title: 'Zwrot', timestamp: 1, ...over,
})

function zwrotPsychologa({ status = 'CANCELLED', wpisPacjenta = {}, ruchWizyty = true, suffix = '' } = {}) {
  const db = authedDb(env, 'psycholog')
  const b = writeBatch(db)
  if (ruchWizyty) {
    b.update(doc(db, 'appointments/w1'),
      status === 'FREE' ? { status, patientId: null, notes: '' }
        : status === 'CANCELLED' ? { status, cancelledBy: 'psycholog' } : { status })
  }
  b.update(doc(db, 'escrows/w1'), { status: 'REFUNDED' })
  b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 580, escrowSettlement: 'w1' })
  b.set(doc(db, 'wallet_transactions/psycholog_w1_zwrot' + suffix), wpis('psycholog_w1_zwrot' + suffix, {
    userId: 'psycholog', counterpartyId: 'pacjent', amount: -80,
  }))
  b.set(doc(db, 'wallet_transactions/psycholog_w1' + suffix), wpis('psycholog_w1' + suffix, {
    userId: 'pacjent', counterpartyId: 'psycholog', amount: 80, ...wpisPacjenta,
  }))
  return b
}

function zwrotPacjenta({ pozno = false } = {}) {
  const db = authedDb(env, 'pacjent')
  const b = writeBatch(db)
  b.baza = db
  b.update(doc(db, 'appointments/w1'), { status: 'CANCELLED', cancelledBy: 'pacjent' })
  if (pozno) {
    b.update(doc(db, 'escrows/w1'), { status: 'RELEASED' })
    b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 240, escrowSettlement: 'w1' })
    b.set(doc(db, 'wallet_transactions/pacjent_w1_wplyw'), wpis('pacjent_w1_wplyw', {
      userId: 'psycholog', counterpartyId: 'pacjent', amount: 80, type: 'BOOKING_EARNING',
    }))
  } else {
    b.update(doc(db, 'escrows/w1'), { status: 'REFUNDED' })
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 580, escrowSettlement: 'w1' })
    b.set(doc(db, 'wallet_transactions/pacjent_w1_zwrot'), wpis('pacjent_w1_zwrot', {
      userId: 'pacjent', counterpartyId: 'psycholog', amount: 80,
    }))
  }
  return b
}

function odwolaniePacjenta({ pozno = false, wpisPsychologa = {}, bezOperacji = false } = {}) {
  let b
  if (bezOperacji) {
    const db = authedDb(env, 'pacjent')
    b = writeBatch(db)
    b.baza = db
  } else {
    b = zwrotPacjenta({ pozno })
  }
  b.set(doc(b.baza, 'wallet_transactions/pacjent_w1'), wpis('pacjent_w1', {
    userId: 'psycholog', counterpartyId: 'pacjent', amount: -80, ...wpisPsychologa,
  }))
  return b
}

describe('zwroty - pacjent nie ma sciezki do wpisu REFUND w ksiedze psychologa', () => {
  const proby = [
    ['ponad 24 h przed terminem, kwota -cena', 3 * DZIEN, false, { amount: -80 }],
    ['ponizej 24 h przed terminem (wyplata), kwota -cena', 2 * GODZINA, true, { amount: -80 }],
    ['tuz powyzej progu 24 h, kwota -cena', DZIEN + MARGINES, false, { amount: -80 }],
    ['kwota dodatnia +cena', 3 * DZIEN, false, { amount: 80 }],
    ['kwota inna niz cena', 3 * DZIEN, false, { amount: -79 }],
    ['kwota dowolna dodatnia', 3 * DZIEN, false, { amount: 1 }],
    ['inny typ wpisu niz REFUND', 3 * DZIEN, false, { type: 'BOOKING_PAYMENT' }],
    ['wpis w ksiedze osoby trzeciej', 3 * DZIEN, false, { userId: 'ofiara', amount: -80 }],
    ['counterpartyId nie wskazuje piszacego', 3 * DZIEN, false, { counterpartyId: 'ofiara', amount: -80 }],
  ]

  it('kontrola izolacji: ta sama partia zwrotu pacjenta bez wpisu u psychologa przechodzi', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertSucceeds(zwrotPacjenta().commit())
  })

  it('kontrola izolacji: ta sama partia wyplaty po pozdnym odwolaniu bez cudzego wpisu przechodzi', async () => {
    await seedWizyta(Date.now() + 2 * GODZINA)
    await assertSucceeds(zwrotPacjenta({ pozno: true }).commit())
  })

  for (const [opis, doTerminu, pozno, nadpisanie] of proby) {
    it('operacja pacjenta z wpisem w ksiedze psychologa odrzucona: ' + opis, async () => {
      await seedWizyta(Date.now() + doTerminu)
      await assertFails(odwolaniePacjenta({ pozno, wpisPsychologa: nadpisanie }).commit())
    })
  }

  it('wpis w ksiedze psychologa bez zadnego ruchu depozytu - odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(odwolaniePacjenta({ bezOperacji: true }).commit())
  })

  it('wpis w ksiedze psychologa przy zwolnieniu terminu do FREE - odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/w1'), { status: 'FREE', patientId: null, notes: '' })
    b.set(doc(db, 'wallet_transactions/pacjent_w1'), wpis('pacjent_w1', {
      userId: 'psycholog', counterpartyId: 'pacjent', amount: -80,
    }))
    await assertFails(b.commit())
  })
})

describe('zwroty — psycholog odwoluje', () => {
  it('ponizej 24 h przed terminem — zwrot przechodzi', async () => {
    await seedWizyta(Date.now() + 2 * GODZINA)
    await assertSucceeds(zwrotPsychologa().commit())
  })

  it('ponad 24 h przed terminem — zwrot przechodzi', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertSucceeds(zwrotPsychologa().commit())
  })

  it('termin juz minal — odwolanie ze zwrotem przez psychologa przechodzi (zwrot za wizyte, ktora sie nie odbyla)', async () => {
    await seedWizyta(Date.now() - 2 * GODZINA)
    await assertSucceeds(zwrotPsychologa().commit())
  })

  it('termin bez startsAtMillis — psycholog moze odwolac ze zwrotem (inaczej nikt by nie mogl)', async () => {
    await seedWizyta(undefined)
    await assertSucceeds(zwrotPsychologa().commit())
  })

  it('kwota zwrotu mniejsza od priceCoins — odrzucona', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { amount: 79 } }).commit())
  })

  it('kwota zwrotu wieksza od priceCoins — odrzucona', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { amount: 81 } }).commit())
  })

  it('kwota ujemna w ksiedze pacjenta (kierunek odwrotny) — odrzucona', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { amount: -80 } }).commit())
  })

  it('zwrot bez przejscia wizyty na CANCELLED — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ ruchWizyty: false }).commit())
  })

  it('przejscie na FREE zamiast CANCELLED — zwrot odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ status: 'FREE' }).commit())
  })

  it('przejscie na COMPLETED zamiast CANCELLED — zwrot odrzucony', async () => {
    await seedWizyta(Date.now() - 2 * GODZINA)
    await assertFails(zwrotPsychologa({ status: 'COMPLETED' }).commit())
  })

  it('wpis w ksiedze osoby, ktora nie jest pacjentem wizyty — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { userId: 'ofiara' } }).commit())
  })

  it('inny typ wpisu niz REFUND — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { type: 'BOOKING_EARNING' } }).commit())
  })

  it('counterpartyId nie wskazuje piszacego — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertFails(zwrotPsychologa({ wpisPacjenta: { counterpartyId: 'ofiara' } }).commit())
  })

  it('drugi zwrot dla tej samej wizyty — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertSucceeds(zwrotPsychologa().commit())
    await assertFails(zwrotPsychologa({ suffix: '_drugi' }).commit())
  })

  it('zwrot psychologa po zwrocie, ktory pacjent wykonal sam — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertSucceeds(zwrotPacjenta().commit())
    await assertFails(zwrotPsychologa({ suffix: '_po_pacjencie' }).commit())
  })

  it('drugi wpis pacjenta bez nowego odwolania — odrzucony', async () => {
    await seedWizyta(Date.now() + 3 * DZIEN)
    await assertSucceeds(zwrotPsychologa().commit())
    const db = authedDb(env, 'psycholog')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 660, escrowSettlement: 'w1' })
    b.set(doc(db, 'wallet_transactions/powtorka'), wpis('powtorka', {
      userId: 'pacjent', counterpartyId: 'psycholog', amount: 80,
    }))
    await assertFails(b.commit())
  })
})
