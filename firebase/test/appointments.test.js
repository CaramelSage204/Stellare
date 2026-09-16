import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'
import { authedDb, seed, setupEnv } from './helpers.js'

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
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'users/intruz'), {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 80,
    })
  })
})

const wolnyTermin = {
  appointmentId: 'nowy', psychologistId: 'psycholog', patientId: null,
  date: '2026-10-02', time: '11:00', notes: '', status: 'FREE', priceCoins: 80,
}

describe('appointments — cykl życia', () => {
  it('psycholog może wystawić własny wolny termin', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'appointments/nowy'), wolnyTermin))
  })

  it('pacjent nie może wystawić terminu w imieniu psychologa', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'appointments/nowy'), wolnyTermin))
  })

  it('psycholog nie może wystawić terminu od razu zarezerwowanego', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/nowy'), {
      ...wolnyTermin, status: 'BOOKED', patientId: 'pacjent',
    }))
  })

  it('pacjent nie może zarezerwować terminu na kogoś innego', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'intruz',
    }))
  })

  it('pacjent nie może podmienić daty przy rezerwacji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'pacjent', date: '2026-12-24',
    }))
  })

  it('nie da się zarezerwować terminu już zajętego', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent' })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'intruz',
    }))
  })

  it('psycholog może usunąć własny wolny termin', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(deleteDoc(doc(db, 'appointments/termin1')))
  })

  it('psycholog nie może usunąć terminu już zarezerwowanego', async () => {
    await seed(env, async (db) => {
      await updateDoc(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent' })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(deleteDoc(doc(db, 'appointments/termin1')))
  })

  it('obca osoba nie może dotknąć cudzego terminu', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), { notes: 'cokolwiek' }))
  })

  it('rezerwuje termin zapisany przed etapem, bez pola priceCoins', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/stary'), {
        appointmentId: 'stary', psychologistId: 'psycholog', patientId: null,
        date: '2026-10-05', time: '09:00', notes: '', status: 'FREE',
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/stary'), {
      status: 'BOOKED', patientId: 'pacjent', notes: '',
    }))
  })

  // Runda poprawek 3 — ujemna cena zamieniona z przypadku w zamiar: przy
  // rezerwacji terminu z ujemnym priceCoins prawdziwy kod rezerwacji
  // (bookAppointmentAtomically) rzeczywiscie probuje zwiekszyc saldo pacjenta.
  // Dzis blokuja to niezwiazane z cena inwarianty kwota>0/amount>0 w
  // users/wallet_transactions, ale Etap 5 ma przerobic receivesPaidTransfer()
  // pod zwroty — nie mozna liczyc, ze tamten refaktor pamieta o tej ukrytej
  // zaleznosci. priceCoins >= 0 wiaze to wprost przy zrodle.
  it('nie da się wystawić terminu z ceną ujemną', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/cenaUjemna'), {
      ...wolnyTermin, appointmentId: 'cenaUjemna', priceCoins: -1,
    }))
  })

  it('można wystawić darmowy termin (priceCoins = 0)', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'appointments/cenaZero'), {
      ...wolnyTermin, appointmentId: 'cenaZero', priceCoins: 0,
    }))
  })
})
