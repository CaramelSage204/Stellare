import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteField, doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv , zalozUzytkownika } from './helpers.js'

let env
const DZIEN = 24 * 60 * 60 * 1000
const ZA_TYDZIEN = Date.now() + 7 * DZIEN

const depozyt = (over = {}) => ({
  appointmentId: 'termin1', patientId: 'pacjent', psychologistId: 'psycholog',
  amount: 80, status: 'HELD', ...over,
})

const rezerwujTermin = (b, db, uid = 'pacjent', id = 'termin1') => {
  b.update(doc(db, 'appointments/' + id), { status: 'BOOKED', patientId: uid, notes: '' })
  b.set(doc(db, 'escrows/' + id), depozyt({ appointmentId: id, patientId: uid }))
  b.update(doc(db, 'users/' + uid + '/private/contact'), { coinsBalance: 500 - 80, escrowSettlement: id })
}

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await zalozUzytkownika(db, 'users/ofiara', {
      id: 'ofiara', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await zalozUzytkownika(db, 'users/slup1', {
      id: 'slup1', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await zalozUzytkownika(db, 'users/slup2', {
      id: 'slup2', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await zalozUzytkownika(db, 'users/slup3', {
      id: 'slup3', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await zalozUzytkownika(db, 'users/psy_x', {
      id: 'psy_x', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0,
    })
    await zalozUzytkownika(db, 'users/x', {
      id: 'x', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      startsAtMillis: ZA_TYDZIEN, date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 80,
    })
    await setDoc(doc(db, 'appointments/termin2'), {
      appointmentId: 'termin2', psychologistId: 'psycholog', patientId: null,
      startsAtMillis: ZA_TYDZIEN, date: '2026-10-02', time: '11:00', notes: '', status: 'FREE', priceCoins: 80,
    })
  })
})

describe('portfel — zachowanie monet', () => {
  it('nie pozwala dopisać monet obcej osobie bez pokrycia', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 999999 }))
  })

  it('nie pozwala doładować sobie ponad limit jednorazowy', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 + 1001 }))
  })

  it('pozwala doładować sobie w granicach limitu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 + 1000 }))
  })

  it('przepuszcza rezerwację, gdy monety się bilansują', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: 'pierwsza sesja' })
    batch.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 - 80, escrowSettlement: 'termin1' })
    batch.set(doc(db, 'escrows/termin1'), depozyt())
    batch.set(doc(db, 'wallet_transactions/pacjent_t1'), {
      transactionId: 'pacjent_t1', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Opłata za wizytę', timestamp: Date.now(),
    })
    await assertSucceeds(batch.commit())
  })

  it('odrzuca rezerwację, w której psycholog dostaje więcej, niż zapłacił pacjent', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    rezerwujTermin(batch, db)
    batch.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 0 + 5000 })
    await assertFails(batch.commit())
  })

  it('odrzuca dopisanie monet psychologowi bez ruchu po stronie pacjenta', async () => {
    await seed(env, async (s) => {
      await updateDoc(doc(s, 'appointments/termin1'), { priceCoins: 0 })
    })
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    batch.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 0 + 80 })
    await assertFails(batch.commit())
  })

  it('księga operacji jest niezmienna', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'wallet_transactions/t1'), {
        transactionId: 't1', userId: 'pacjent', counterpartyId: 'psycholog',
        appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
        title: 'Opłata za wizytę', timestamp: Date.now(),
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'wallet_transactions/t1'), { amount: 0 }))
  })

  it('nie pozwala czytać cudzych operacji', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'wallet_transactions/t9'), {
        transactionId: 't9', userId: 'psycholog', counterpartyId: null,
        appointmentId: null, amount: 100, type: 'TOPUP',
        title: 'Doładowanie', timestamp: Date.now(),
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'wallet_transactions/t9')))
  })

  it('nie pozwala zarezerwowac wlasnego terminu — blokuje bicie monety', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/wlasny'), {
        appointmentId: 'wlasny', psychologistId: 'psycholog', patientId: null,
        startsAtMillis: ZA_TYDZIEN, date: '2026-10-09', time: '12:00', notes: '', status: 'FREE', priceCoins: 80,
      })
      await updateDoc(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 500 })
    })
    const db = authedDb(env, 'psycholog')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/wlasny'), { status: 'BOOKED', patientId: 'psycholog', notes: '' })
    b.set(doc(db, 'escrows/wlasny'), depozyt({ appointmentId: 'wlasny', patientId: 'psycholog' }))
    b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 500 - 80, escrowSettlement: 'wlasny' })
    await assertFails(b.commit())
  })

  it('pacjent nie moze wystawic terminu we wlasnym imieniu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'appointments/podrobka'), {
      appointmentId: 'podrobka', psychologistId: 'pacjent', patientId: null,
      date: '2026-10-10', time: '13:00', notes: '', status: 'FREE', priceCoins: 80,
    }))
  })

  it('nie pozwala rozgalezic przelewu na wielu odbiorcow w jednym batchu', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 - 80 })
    b.update(doc(db, 'users/slup1/private/contact'), { coinsBalance: 80 })
    b.update(doc(db, 'users/slup2/private/contact'), { coinsBalance: 80 })
    b.update(doc(db, 'users/slup3/private/contact'), { coinsBalance: 80 })
    await assertFails(b.commit())
  })

  it('nie pozwala zaplacic wiecej, niz sie ma — brak debetu', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 - 1000000 })
    b.update(doc(db, 'users/slup1/private/contact'), { coinsBalance: 1000000 })
    await assertFails(b.commit())
  })

  it('nie pozwala dopisac wpisu do cudzej ksiegi jako kontrahent', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/podrobka'), {
      transactionId: 'podrobka', userId: 'ofiara', counterpartyId: 'pacjent',
      appointmentId: null, amount: -99999, type: 'BOOKING_PAYMENT',
      title: 'Wymyslona oplata', timestamp: Date.now(),
    }))
  })

  it('nie pozwala zabrac monet obcej osobie', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/ofiara/private/contact'), { coinsBalance: 500 - 80 })
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 500 + 80 })
    await assertFails(b.commit())
  })

  it('nie pozwala zaplacic z konta bez dokumentu uzytkownika', async () => {
    const db = authedDb(env, 'duch')
    await assertFails(updateDoc(doc(db, 'users/slup1/private/contact'), { coinsBalance: 80 }))
  })

  it('nie pozwala oznaczyc wolnego terminu jako odbytego z dowolnym pacjentem', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'COMPLETED', patientId: 'ofiara',
    }))
  })

  it('nie pozwala dopisac zerowego wpisu do cudzej ksiegi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/smiec'), {
      transactionId: 'smiec', userId: 'ofiara', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 0, type: 'BOOKING_PAYMENT',
      title: 'Oplata za wizyte ktorej nie bylo', timestamp: Date.now(),
    }))
  })

  it('nie pozwala zwielokrotnic wpisow o realnej kwocie w cudzej ksiedze', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    rezerwujTermin(b, db)
    for (let i = 0; i < 5; i++) {
      b.set(doc(db, 'wallet_transactions/dup' + i), {
        transactionId: 'dup' + i, userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
        title: 'Wplyw ' + i, timestamp: Date.now(),
      })
    }
    await assertFails(b.commit())
  })

  it('nie pozwala oznaczyc jako odbyty terminu, ktory nie byl zarezerwowany', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'COMPLETED', patientId: null,
    }))
  })

  it('pozwala zapisac doladowanie jako osobna operacje, tak jak robi to aplikacja', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 1000 }))
    await assertSucceeds(setDoc(doc(db, 'wallet_transactions/pacjent_top2'), {
      transactionId: 'pacjent_top2', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: 500, type: 'TOPUP',
      title: 'Doladowanie', timestamp: Date.now(),
    }))
  })
})

// ---------------------------------------------------------------------------
// ATAKI RUNDY 3 — zamykają N3 (deklaracja bez ruchu salda, zwielokrotnienie
// wpisów w cudzej księdze) i podatność z rundy 2 (zasiedzanie identyfikatora
// blokujące legalne rezerwacje). Każdy test padał na regułach sprzed tej rundy.
// ---------------------------------------------------------------------------

describe('ksiega operacji — ataki rundy 3', () => {
  it('A1: zmyslona deklaracja przelewu wpisuje dowolna kwote do cudzej ksiegi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent/private/contact'), {
      escrowSettlement: 'zmyslona', coinsBalance: 500 + 99999,
    }))
    await assertFails(setDoc(doc(db, 'wallet_transactions/zmyslona_ofiara'), {
      transactionId: 'zmyslona_ofiara', userId: 'ofiara', counterpartyId: 'pacjent',
      appointmentId: 'zmyslona', amount: -99999, type: 'BOOKING_PAYMENT',
      title: 'Obciazenie, ktorego nie bylo', timestamp: Date.now(),
    }))
  })

  // Stan po PRAWDZIWYM przelewie zasiewany przez seed(): po R17 wpis w cudzej ksiedze
  // wymaga rezerwacji terminu w tym samym zatwierdzeniu, wiec krok przygotowawczy nie
  // jest juz legalna sciezka. Asercja atakujaca jest ostatnia i faktycznie sie wykonuje.
  it('A2: deklaracja po PRAWDZIWYM przelewie zyje dalej — wpisy widmo bez ruchu salda', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/pacjent', {
        id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
        coinsBalance: 420,
      })
      await zalozUzytkownika(s, 'users/psycholog', {
        id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 80, pricePerSession: 80,
      })
      await setDoc(doc(s, 'wallet_transactions/pacjent_termin1'), {
        transactionId: 'pacjent_termin1', userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
        title: 'Wplyw za wizyte', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/widmo1_psycholog'), {
      transactionId: 'widmo1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'widmo1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wplyw z niczego', timestamp: 1,
    }))
  })

  it('A3: jedna oplata, wiele wpisow w cudzej ksiedze — wystarczy zmieniac appointmentId', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    rezerwujTermin(b, db)
    for (let i = 0; i < 5; i++) {
      b.set(doc(db, 'wallet_transactions/wizyta' + i + '_psycholog'), {
        transactionId: 'wizyta' + i + '_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'wizyta' + i, amount: 80, type: 'BOOKING_EARNING',
        title: 'Wplyw ' + i, timestamp: Date.now(),
      })
    }
    await assertFails(b.commit())
  })

  it('A4: zasiedzenie identyfikatora blokuje pozniejsza legalna rezerwacje', async () => {
    const napastnik = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(napastnik, 'users/pacjent/private/contact'), {
      escrowSettlement: 'termin2', coinsBalance: 500 + 1500,
    }))
    await assertFails(setDoc(doc(napastnik, 'escrows/termin2'), depozyt({
      appointmentId: 'termin2', patientId: 'pacjent',
    })))
    await assertFails(setDoc(doc(napastnik, 'wallet_transactions/termin2_psycholog'), {
      transactionId: 'termin2_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin2', amount: 80, type: 'BOOKING_EARNING',
      title: 'Zasiedzenie', timestamp: Date.now(),
    }))

    const db = authedDb(env, 'ofiara')
    const b = writeBatch(db)
    rezerwujTermin(b, db, 'ofiara', 'termin2')
    b.set(doc(db, 'wallet_transactions/ofiara_termin2_platnosc'), {
      transactionId: 'ofiara_termin2_platnosc', userId: 'ofiara', counterpartyId: 'psycholog',
      appointmentId: 'termin2', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Oplata za wizyte', timestamp: Date.now(),
    })
    await assertSucceeds(b.commit())
  })

  // A6 (zasiedzenie identyfikatora dokumentu) usuniety w rundzie 5: po R17 reguly nie
  // sprawdzaja identyfikatorow, klient nadaje automatyczne, wiec zajecie dowolnego id
  // niczego nie blokuje — atak zniknal razem z mechanizmem, ktory go umozliwial.

  // Zywa deklaracja przelewu do psy_x zasiewana przez seed() — napastnik zdobyl ja
  // prawdziwym przelewem. Legalny "ogon" testu (wpis w ksiedze uzytkownika x bez
  // rezerwacji terminu) usuniety w rundzie 5: po R17 to nie jest juz legalna sciezka,
  // a jego padniecie ukrywalo fakt, ze asercja atakujaca dalej dziala.
  it('A5: kolizja sklejonego id — wpis dla psy_x zajmuje id wpisu dla x', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/pacjent', {
        id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
        coinsBalance: 420,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/t1_psy_x'), {
      transactionId: 't1_psy_x', userId: 'psy_x', counterpartyId: 'pacjent',
      appointmentId: 't1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wpis dla psy_x', timestamp: 1,
    }))
  })
})

// ---------------------------------------------------------------------------
// KONTROLE RUNDY 3 — pilnują, że ochrona i legalne ścieżki nadal działają.
// ---------------------------------------------------------------------------

describe('ksiega operacji — kontrole rundy 3', () => {
  it('nie da sie nadpisac istniejacego wpisu w ksiedze', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'wallet_transactions/termin1_psycholog'), {
        transactionId: 'termin1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
        title: 'Wplyw', timestamp: Date.now(),
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent/private/contact'), {
      escrowSettlement: 'termin1', coinsBalance: 500 + 1500,
    }))
    await assertFails(setDoc(doc(db, 'wallet_transactions/termin1_psycholog'), {
      transactionId: 'termin1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Podmiana', timestamp: Date.now(),
    }))
  })

  it('kwota ulamkowa, zero i tekst nie przechodza', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/ulamek'), {
      transactionId: 'ulamek', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: 0.5, type: 'TOPUP', title: 'x', timestamp: Date.now(),
    }))
    await assertFails(setDoc(doc(db, 'wallet_transactions/zerodbl'), {
      transactionId: 'zerodbl', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: 0.0, type: 'TOPUP', title: 'x', timestamp: Date.now(),
    }))
    await assertFails(setDoc(doc(db, 'wallet_transactions/tekst'), {
      transactionId: 'tekst', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: '80', type: 'TOPUP', title: 'x', timestamp: Date.now(),
    }))
  })

  it('stala deklaracja nie pozwala powtorzyc samego ruchu salda', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', {
        id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 0, pricePerSession: 1500,
      })
      await setDoc(doc(db, 'appointments/odbyta'), {
        appointmentId: 'odbyta', psychologistId: 'psycholog', patientId: 'pacjent',
        date: '2026-10-01', time: '10:00', notes: '', status: 'COMPLETED', priceCoins: 1500,
        startsAtMillis: Date.now() - 2 * 60 * 60 * 1000,
      })
      await setDoc(doc(db, 'escrows/odbyta'), depozyt({ appointmentId: 'odbyta', amount: 1500 }))
    })
    const db = authedDb(env, 'psycholog')
    const b = writeBatch(db)
    b.update(doc(db, 'escrows/odbyta'), { status: 'RELEASED' })
    b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 1500, escrowSettlement: 'odbyta' })
    await assertSucceeds(b.commit())
    await assertFails(updateDoc(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 3000 }))
  })

  it('rozgalezienie i debet nadal odparte', async () => {
    const db = authedDb(env, 'pacjent')
    const b1 = writeBatch(db)
    b1.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 420 })
    b1.update(doc(db, 'users/slup1/private/contact'), { coinsBalance: 80 })
    b1.update(doc(db, 'users/ofiara/private/contact'), { coinsBalance: 580 })
    await assertFails(b1.commit())

    const b2 = writeBatch(db)
    b2.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: -500 })
    b2.update(doc(db, 'users/slup1/private/contact'), { coinsBalance: 1000 })
    await assertFails(b2.commit())
  })

  it('wlasna ksiega — wpis wskazujacy ofiare nie trafia do jej historii', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'wallet_transactions/pacjent_wlasny_falsz'), {
      transactionId: 'pacjent_wlasny_falsz', userId: 'pacjent', counterpartyId: 'ofiara',
      appointmentId: 'nieistnieje', amount: -500, type: 'BOOKING_PAYMENT',
      title: 'Rzekoma oplata na rzecz ofiary', timestamp: Date.now(),
    }))
    const dbOfiara = authedDb(env, 'ofiara')
    await assertFails(getDoc(doc(dbOfiara, 'wallet_transactions/pacjent_wlasny_falsz')))
  })

  it('legalne przejscia terminu po poprawce N4', async () => {
    await seed(env, async (db) => {
      for (const id of ['zajety', 'zajety2', 'zajety3']) {
        await setDoc(doc(db, 'appointments/' + id), {
          appointmentId: id, psychologistId: 'psycholog', patientId: 'pacjent',
          date: '2026-10-03', time: '12:00', notes: '', status: 'BOOKED', priceCoins: 80,
          startsAtMillis: id === 'zajety' ? Date.now() - 60 * 60 * 1000 : Date.now() + 3 * 24 * 60 * 60 * 1000,
        })
      }
    })
    const psy = authedDb(env, 'psycholog')
    const pac = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(psy, 'appointments/zajety'), { status: 'COMPLETED' }))
    await assertSucceeds(updateDoc(doc(pac, 'appointments/zajety2'), { status: 'FREE', patientId: null, notes: '' }))
    await assertSucceeds(updateDoc(doc(psy, 'appointments/zajety3'), { status: 'FREE', patientId: null, notes: '' }))
  })

  // Zwrot inicjowany przez psychologa: depozyt przechodzi na REFUNDED, saldo pacjenta
  // rosnie o amount, a wpis REFUND w ksiedze pacjenta dowodzi tego przestawienia.
  it('pelny zwrot inicjowany przez psychologa przechodzi — id sie nie zderza', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/termin1'), {
        appointmentId: 'termin1', psychologistId: 'psycholog', patientId: 'pacjent',
        date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 80,
        startsAtMillis: Date.now() + 3 * 24 * 60 * 60 * 1000,
      })
      await setDoc(doc(db, 'escrows/termin1'), depozyt())
    })
    const psy = authedDb(env, 'psycholog')
    const b = writeBatch(psy)
    b.update(doc(psy, 'appointments/termin1'), { status: 'CANCELLED', cancelledBy: 'psycholog' })
    b.update(doc(psy, 'escrows/termin1'), { status: 'REFUNDED' })
    b.update(doc(psy, 'users/pacjent/private/contact'), { coinsBalance: 580, escrowSettlement: 'termin1' })
    b.set(doc(psy, 'wallet_transactions/psycholog_termin1_zwrot'), {
      transactionId: 'psycholog_termin1_zwrot', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: -80, type: 'REFUND', title: 'Zwrot', timestamp: 1,
    })
    b.set(doc(psy, 'wallet_transactions/psycholog_termin1'), {
      transactionId: 'psycholog_termin1', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: 80, type: 'REFUND', title: 'Zwrot', timestamp: 1,
    })
    await assertSucceeds(b.commit())
  })

  // ETAP 5: regula wallet_transactions musi zyskac kierunek zwrotu — psycholog
  // zapisujacy wpis REFUND do ksiegi pacjenta, wiazany z terminem, ktorego jest
  // psychologiem. Przelew monet juz dziala, brakuje wylacznie wpisu w ksiedze.
  // POMINIETY SWIADOMIE (decyzja kontrolera, 2026-10-08): zwroty czesciowe NIE sa czescia Etapu 5.
  // Plan wymaga kwoty rownej priceCoins i jednego zwrotu na wizyte; czesciowe wymagalyby sledzenia
  // sumy zwroconej, czyli nowego pola i niezmiennika. Test zostaje jako gotowa specyfikacja.
  it.skip('drugi (czesciowy) zwrot dla tej samej wizyty przechodzi', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog', {
        id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 80, pricePerSession: 80,
      })
      await setDoc(doc(db, 'wallet_transactions/zwrot_rata1'), {
        transactionId: 'zwrot_rata1', userId: 'pacjent', counterpartyId: 'psycholog',
        appointmentId: 'termin1', amount: 40, type: 'REFUND', title: 'Rata 1', timestamp: 1,
      })
    })
    const psy = authedDb(env, 'psycholog')
    const b = writeBatch(psy)
    b.update(doc(psy, 'users/pacjent/private/contact'), { coinsBalance: 540 })
    b.set(doc(psy, 'wallet_transactions/zwrot_rata2'), {
      transactionId: 'zwrot_rata2', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: 40, type: 'REFUND', title: 'Rata 2', timestamp: 1,
    })
    await assertSucceeds(b.commit())
  })

  // Sedno depozytu: pacjent odwolujacy ponad 24 h przed terminem odzyskuje monety SAM,
  // w jednej partii, bez udzialu psychologa (wczesniej zostawial slad i czekal na rozliczenie).
  it('zwrot inicjowany przez pacjenta przechodzi', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/pacjent', {
        id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 420,
      })
      await setDoc(doc(db, 'appointments/termin1'), {
        appointmentId: 'termin1', psychologistId: 'psycholog', patientId: 'pacjent',
        startsAtMillis: ZA_TYDZIEN, date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 80,
      })
      await setDoc(doc(db, 'escrows/termin1'), depozyt())
    })
    const pac = authedDb(env, 'pacjent')
    const b = writeBatch(pac)
    b.update(doc(pac, 'appointments/termin1'), { status: 'CANCELLED', cancelledBy: 'pacjent' })
    b.update(doc(pac, 'escrows/termin1'), { status: 'REFUNDED' })
    b.update(doc(pac, 'users/pacjent/private/contact'), { coinsBalance: 500, escrowSettlement: 'termin1' })
    await assertSucceeds(b.commit())
  })
})

// ---------------------------------------------------------------------------
// RUNDA 4 — reguła proporcjonalna do stawki. Dwa ataki, które reguła ma odpierać,
// i dwie funkcje zepsute przez rundy 1-3, które przywracamy.
// ---------------------------------------------------------------------------

describe('ksiega operacji — runda 4', () => {
  it('nie pozwala dopisac wplywu psychologowi, u ktorego sie nie rezerwuje', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 420 })
    b.set(doc(db, 'wallet_transactions/fikcja'), {
      transactionId: 'fikcja', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'nieistniejaca', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wplyw ktorego nie bylo', timestamp: 1,
    })
    await assertFails(b.commit())
  })

  it('nie pozwala dopisac wpisu do ksiegi osoby trzeciej', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/obcy'), {
      transactionId: 'obcy', userId: 'ofiara', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Nie moja ksiega', timestamp: 1,
    }))
  })

  it('PRZYWROCONE: klient zapisuje wpis z automatycznym identyfikatorem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { coinsBalance: 1000 }))
    await assertSucceeds(setDoc(doc(db, 'wallet_transactions/kQ7x9ZaBcDeF'), {
      transactionId: 'kQ7x9ZaBcDeF', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: 500, type: 'TOPUP',
      title: 'Doladowanie portfela', timestamp: 1,
    }))
  })

  it('PRZYWROCONE: ponowna rezerwacja zwolnionego terminu', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'wallet_transactions/stary_wpis'), {
        transactionId: 'stary_wpis', userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
        title: 'Wplyw z pierwszej rezerwacji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    rezerwujTermin(b, db)
    b.set(doc(db, 'wallet_transactions/nowa_platnosc'), {
      transactionId: 'nowa_platnosc', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Oplata z drugiej rezerwacji', timestamp: 2,
    })
    await assertSucceeds(b.commit())
  })

  // ŚWIADOME OGRANICZENIE (ruling R17, zawężone przez R19). Kto w TYM SAMYM zatwierdzeniu
  // wyplaca depozyt psychologowi po pozdnym odwolaniu, moze przy okazji dopisac mu nadmiarowe
  // wpisy w ksiedze. Od depozytu kwota wpisu musi byc rowna amount, wiec nie da sie juz wpisac
  // dowolnej sumy - zostaje wylacznie powielenie wpisu o kwocie amount. Saldo pozostaje
  // szczelne: zmienia sie wylacznie wyswietlana historia. Szczelne zamkniecie wymaga serwera.
  it('ZNANE OGRANICZENIE: wyplacajacy moze dopisac powielone wpisy w ksiedze psychologa', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'appointments/termin1'), {
        appointmentId: 'termin1', psychologistId: 'psycholog', patientId: 'pacjent',
        date: '2026-10-01', time: '10:00', notes: '', status: 'CANCELLED', cancelledBy: 'pacjent', priceCoins: 80,
        startsAtMillis: Date.now() + 2 * 60 * 60 * 1000,
      })
      await setDoc(doc(s, 'escrows/termin1'), depozyt())
    })
    const db = authedDb(env, 'pacjent')
    const wpisPsychologa = (id, amount) => ({
      transactionId: id, userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount, type: 'BOOKING_EARNING', title: 'Wpis', timestamp: 1,
    })
    const nadmiar = writeBatch(db)
    nadmiar.update(doc(db, 'escrows/termin1'), { status: 'RELEASED' })
    nadmiar.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 80, escrowSettlement: 'termin1' })
    nadmiar.set(doc(db, 'wallet_transactions/nadmiar1'), wpisPsychologa('nadmiar1', 99999))
    await assertFails(nadmiar.commit())

    const b = writeBatch(db)
    b.update(doc(db, 'escrows/termin1'), { status: 'RELEASED' })
    b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 80, escrowSettlement: 'termin1' })
    b.set(doc(db, 'wallet_transactions/nadmiar1'), wpisPsychologa('nadmiar1', 80))
    b.set(doc(db, 'wallet_transactions/nadmiar2'), wpisPsychologa('nadmiar2', 80))
    await assertSucceeds(b.commit())
  })
})

describe('ksiega operacji — K1: getAfter bez rezerwacji', () => {
  // Termin zarezerwowany DAWNO, napastnik jest jego pacjentem i nic w nim nie zmienia.
  // getAfter() na dokumencie nietknietym w commicie zwraca stan biezacy, wiec warunek
  // "w tym samym zatwierdzeniu faktycznie rezerwuje wizyte" nie byl egzekwowany.
  const dawnaRezerwacja = async (env) => seed(env, async (s) => {
    await setDoc(doc(s, 'appointments/stary'), {
      appointmentId: 'stary', psychologistId: 'psycholog', patientId: 'pacjent',
      date: '2026-09-01', time: '09:00', notes: '', status: 'BOOKED', priceCoins: 80,
    })
  })

  it('K1a: osobny commit, ZERO rezerwacji, ZERO ruchu salda — wpisy widmo', async () => {
    await dawnaRezerwacja(env)
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    for (let i = 0; i < 20; i++) {
      b.set(doc(db, 'wallet_transactions/widmo' + i), {
        transactionId: 'widmo' + i, userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'stary', amount: 99999, type: 'BOOKING_EARNING',
        title: 'Wplyw ktorego nie bylo ' + i, timestamp: 1,
      })
    }
    await assertFails(b.commit())
  })

  it('K1b: ujemna kwota w cudzej ksiedze — falszywe obciazenie psychologa', async () => {
    await dawnaRezerwacja(env)
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/falszywe_obciazenie'), {
      transactionId: 'falszywe_obciazenie', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'stary', amount: -99999, type: 'BOOKING_PAYMENT',
      title: 'Obciazenie, ktorego nie bylo', timestamp: 1,
    }))
  })
})

// ---------------------------------------------------------------------------
// RUNDA DODATKOWA (przy okazji Zadania 6) — martwa gałąź sentinela
// deklaracja rozliczenia oraz brakująca walidacja pola type w księdze operacji.
// ---------------------------------------------------------------------------

describe('portfel — sentinel przelewu i walidacja typu operacji', () => {
  it('nie pozwala zapisac wpisu o nieznanym typie operacji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'wallet_transactions/zly_typ'), {
      transactionId: 'zly_typ', userId: 'pacjent', counterpartyId: null,
      appointmentId: null, amount: 100, type: 'GARBAGE',
      title: 'Wysypie ekran portfela', timestamp: 1,
    }))
  })

  it('przepuszcza wszystkie znane typy operacji', async () => {
    for (const typ of ['TOPUP', 'BOOKING_PAYMENT', 'BOOKING_EARNING', 'REFUND']) {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(setDoc(doc(db, 'wallet_transactions/ok_' + typ), {
        transactionId: 'ok_' + typ, userId: 'pacjent', counterpartyId: null,
        appointmentId: null, amount: 100, type: typ,
        title: 'Operacja ' + typ, timestamp: 1,
      }))
    }
  })

  it('wyzerowanie deklaracji rozliczenia przechodzi bez zmiany salda', async () => {
    await seed(env, async (s) => {
      await updateDoc(doc(s, 'users/pacjent/private/contact'), { escrowSettlement: 'termin1' })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: deleteField() }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent/private/contact'), { escrowSettlement: null }))
  })
})

describe('portfel — typy przelewu i wyplaty', () => {
  for (const typ of ['TRANSFER_OUT', 'WITHDRAWAL']) {
    it('przepuszcza wlasny wpis typu ' + typ, async () => {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(setDoc(doc(db, 'wallet_transactions/nowy_' + typ), {
        transactionId: 'nowy_' + typ, userId: 'pacjent', counterpartyId: null,
        appointmentId: null, amount: -50, type: typ,
        title: 'Operacja ' + typ, timestamp: 1,
      }))
    })
  }
})
