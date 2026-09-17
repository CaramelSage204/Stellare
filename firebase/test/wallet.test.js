import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
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
    await setDoc(doc(db, 'users/ofiara'), {
      id: 'ofiara', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'users/slup1'), {
      id: 'slup1', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'users/slup2'), {
      id: 'slup2', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'users/slup3'), {
      id: 'slup3', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'users/psy_x'), {
      id: 'psy_x', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'users/x'), {
      id: 'x', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, coinsBalance: 0,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 80,
    })
    await setDoc(doc(db, 'appointments/termin2'), {
      appointmentId: 'termin2', psychologistId: 'psycholog', patientId: null,
      date: '2026-10-02', time: '11:00', notes: '', status: 'FREE', priceCoins: 80,
    })
  })
})

describe('portfel — zachowanie monet', () => {
  it('nie pozwala dopisać monet obcej osobie bez pokrycia', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { coinsBalance: 999999 }))
  })

  it('nie pozwala doładować sobie ponad limit jednorazowy', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 500 + 1001 }))
  })

  it('pozwala doładować sobie w granicach limitu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 500 + 1000 }))
  })

  it('przepuszcza rezerwację, gdy monety się bilansują', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: 'pierwsza sesja' })
    batch.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 80, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 80 })
    batch.set(doc(db, 'wallet_transactions/pacjent_t1'), {
      transactionId: 'pacjent_t1', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Opłata za wizytę', timestamp: Date.now(),
    })
    batch.set(doc(db, 'wallet_transactions/pacjent_termin1'), {
      transactionId: 'pacjent_termin1', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wpływ za wizytę', timestamp: Date.now(),
    })
    await assertSucceeds(batch.commit())
  })

  it('odrzuca rezerwację, w której psycholog dostaje więcej, niż zapłacił pacjent', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    batch.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 80 })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 5000 })
    await assertFails(batch.commit())
  })

  it('odrzuca dopisanie monet psychologowi bez ruchu po stronie pacjenta', async () => {
    const db = authedDb(env, 'pacjent')
    const batch = writeBatch(db)
    batch.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    batch.update(doc(db, 'users/psycholog'), { coinsBalance: 0 + 80 })
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
        date: '2026-10-09', time: '12:00', notes: '', status: 'FREE', priceCoins: 80,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'appointments/wlasny'), {
      status: 'BOOKED', patientId: 'psycholog', notes: '',
    }))
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
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 80, lastTransferTo: 'slup1', lastTransferAmount: 80 })
    b.update(doc(db, 'users/slup1'), { coinsBalance: 80 })
    b.update(doc(db, 'users/slup2'), { coinsBalance: 80 })
    b.update(doc(db, 'users/slup3'), { coinsBalance: 80 })
    await assertFails(b.commit())
  })

  it('nie pozwala zaplacic wiecej, niz sie ma — brak debetu', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 500 - 1000000, lastTransferTo: 'slup1', lastTransferAmount: 1000000 })
    b.update(doc(db, 'users/slup1'), { coinsBalance: 1000000 })
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
    b.update(doc(db, 'users/ofiara'), { coinsBalance: 500 - 80 })
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 500 + 80 })
    await assertFails(b.commit())
  })

  it('nie pozwala zaplacic z konta bez dokumentu uzytkownika', async () => {
    const db = authedDb(env, 'duch')
    await assertFails(updateDoc(doc(db, 'users/slup1'), { coinsBalance: 80 }))
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
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    b.update(doc(db, 'users/psycholog'), { coinsBalance: 80 })
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
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 1000 }))
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
    await assertFails(updateDoc(doc(db, 'users/pacjent'), {
      lastTransferTo: 'ofiara', lastTransferAmount: -99999,
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
      await setDoc(doc(s, 'users/pacjent'), {
        id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
        coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80,
      })
      await setDoc(doc(s, 'users/psycholog'), {
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
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    b.update(doc(db, 'users/psycholog'), { coinsBalance: 80 })
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
    await assertFails(updateDoc(doc(napastnik, 'users/pacjent'), {
      lastTransferTo: 'psycholog', lastTransferAmount: 80,
    }))
    await assertFails(setDoc(doc(napastnik, 'wallet_transactions/termin2_psycholog'), {
      transactionId: 'termin2_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin2', amount: 80, type: 'BOOKING_EARNING',
      title: 'Zasiedzenie', timestamp: Date.now(),
    }))

    const db = authedDb(env, 'ofiara')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/termin2'), { status: 'BOOKED', patientId: 'ofiara', notes: '' })
    b.update(doc(db, 'users/ofiara'), { coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    b.update(doc(db, 'users/psycholog'), { coinsBalance: 80 })
    b.set(doc(db, 'wallet_transactions/ofiara_termin2_platnosc'), {
      transactionId: 'ofiara_termin2_platnosc', userId: 'ofiara', counterpartyId: 'psycholog',
      appointmentId: 'termin2', amount: -80, type: 'BOOKING_PAYMENT',
      title: 'Oplata za wizyte', timestamp: Date.now(),
    })
    b.set(doc(db, 'wallet_transactions/ofiara_termin2'), {
      transactionId: 'ofiara_termin2', userId: 'psycholog', counterpartyId: 'ofiara',
      appointmentId: 'termin2', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wplyw za wizyte', timestamp: Date.now(),
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
      await setDoc(doc(s, 'users/pacjent'), {
        id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false,
        coinsBalance: 420, lastTransferTo: 'psy_x', lastTransferAmount: 80,
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
    await assertFails(updateDoc(doc(db, 'users/pacjent'), {
      lastTransferTo: 'psycholog', lastTransferAmount: 80,
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
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'slup1', lastTransferAmount: 80 })
    b.update(doc(db, 'users/slup1'), { coinsBalance: 80 })
    await assertSucceeds(b.commit())
    await assertFails(updateDoc(doc(db, 'users/slup1'), { coinsBalance: 160 }))
  })

  it('rozgalezienie i debet nadal odparte', async () => {
    const db = authedDb(env, 'pacjent')
    const b1 = writeBatch(db)
    b1.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'slup1', lastTransferAmount: 80 })
    b1.update(doc(db, 'users/slup1'), { coinsBalance: 80 })
    b1.update(doc(db, 'users/ofiara'), { coinsBalance: 580 })
    await assertFails(b1.commit())

    const b2 = writeBatch(db)
    b2.update(doc(db, 'users/pacjent'), { coinsBalance: -500, lastTransferTo: 'slup1', lastTransferAmount: 1000 })
    b2.update(doc(db, 'users/slup1'), { coinsBalance: 1000 })
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
        })
      }
    })
    const psy = authedDb(env, 'psycholog')
    const pac = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(psy, 'appointments/zajety'), { status: 'COMPLETED' }))
    await assertSucceeds(updateDoc(doc(pac, 'appointments/zajety2'), { status: 'FREE', patientId: null, notes: '' }))
    await assertSucceeds(updateDoc(doc(psy, 'appointments/zajety3'), { status: 'FREE', patientId: null, notes: '' }))
  })

  // ETAP 5: regula wallet_transactions musi zyskac kierunek zwrotu — psycholog
  // zapisujacy wpis REFUND do ksiegi pacjenta, wiazany z terminem, ktorego jest
  // psychologiem. Przelew monet juz dziala, brakuje wylacznie wpisu w ksiedze.
  it.skip('pelny zwrot inicjowany przez psychologa przechodzi — id sie nie zderza', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog'), {
        id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 80, pricePerSession: 80,
      })
      await setDoc(doc(db, 'appointments/termin1'), {
        appointmentId: 'termin1', psychologistId: 'psycholog', patientId: 'pacjent',
        date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 80,
      })
      await setDoc(doc(db, 'wallet_transactions/termin1_psycholog'), {
        transactionId: 'termin1_psycholog', userId: 'psycholog', counterpartyId: 'pacjent',
        appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING', title: 'Wplyw', timestamp: 1,
      })
    })
    const psy = authedDb(env, 'psycholog')
    const b = writeBatch(psy)
    b.update(doc(psy, 'appointments/termin1'), { status: 'FREE', patientId: null, notes: '' })
    b.update(doc(psy, 'users/psycholog'), { coinsBalance: 0, lastTransferTo: 'pacjent', lastTransferAmount: 80 })
    b.update(doc(psy, 'users/pacjent'), { coinsBalance: 580 })
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
  it.skip('drugi (czesciowy) zwrot dla tej samej wizyty przechodzi', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog'), {
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
    b.update(doc(psy, 'users/psycholog'), { coinsBalance: 40, lastTransferTo: 'pacjent', lastTransferAmount: 40 })
    b.update(doc(psy, 'users/pacjent'), { coinsBalance: 540 })
    b.set(doc(psy, 'wallet_transactions/zwrot_rata2'), {
      transactionId: 'zwrot_rata2', userId: 'pacjent', counterpartyId: 'psycholog',
      appointmentId: 'termin1', amount: 40, type: 'REFUND', title: 'Rata 2', timestamp: 1,
    })
    await assertSucceeds(b.commit())
  })

  // ETAP 5: ten test NIE dotyczy reguly ksiegi — pada na receivesPaidTransfer().
  // Zada, zeby pacjent mogl OBNIZYC SALDO PSYCHOLOGA, co jest wprost zakazane przez
  // rdzeniowy inwariant „obcemu mozna tylko dodac monety". NIE zazieleniac przez
  // rozluznienie receivesPaidTransfer() — otworzyloby to sciaganie monet z cudzych kont.
  // Zwrot musi inicjowac psycholog (jak test powyzej) albo serwer.
  it.skip('zwrot inicjowany przez pacjenta przechodzi', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'users/psycholog'), {
        id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 80, pricePerSession: 80,
      })
      await setDoc(doc(db, 'appointments/termin1'), {
        appointmentId: 'termin1', psychologistId: 'psycholog', patientId: 'pacjent',
        date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 80,
      })
    })
    const pac = authedDb(env, 'pacjent')
    const b = writeBatch(pac)
    b.update(doc(pac, 'appointments/termin1'), { status: 'FREE', patientId: null, notes: '' })
    b.update(doc(pac, 'users/psycholog'), { coinsBalance: 0 })
    b.update(doc(pac, 'users/pacjent'), { coinsBalance: 580 })
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
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420 })
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
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { coinsBalance: 1000 }))
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
    b.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    b.update(doc(db, 'users/psycholog'), { coinsBalance: 80 })
    b.set(doc(db, 'wallet_transactions/nowy_wplyw'), {
      transactionId: 'nowy_wplyw', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 80, type: 'BOOKING_EARNING',
      title: 'Wplyw z drugiej rezerwacji', timestamp: 2,
    })
    await assertSucceeds(b.commit())
  })

  // ŚWIADOME OGRANICZENIE (ruling R17, zawężone przez R19). Kto w TYM SAMYM zatwierdzeniu
  // realnie rezerwuje wolny termin (FREE→BOOKED), może przy okazji dopisać psychologowi
  // nadmiarowe wpisy o DODATNIEJ kwocie. Kosztuje to napastnika jedną realną rezerwację
  // na serię i nie da się tego powtórzyć w kolejnych commitach.
  // Saldo pozostaje szczelne — zmienia się wyłącznie wyświetlana historia.
  //
  // Trzy próby szczelnego zamknięcia tego (deterministyczne id, wiązanie z deltą salda,
  // wiązanie z zapamiętaną deklaracją przelewu) zepsuły kolejno: doładowanie portfela,
  // ponowną rezerwację zwolnionego terminu i wszystkie zapisy do księgi z klienta.
  // Szczelne zamknięcie wymaga serwera. NIE „naprawiać" bez przeczytania task-4-report.md.
  it('ZNANE OGRANICZENIE: rezerwujacy moze dopisac nadmiarowe wpisy w ksiedze psychologa', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent', notes: '' })
    b.update(doc(db, 'users/pacjent'), { coinsBalance: 420, lastTransferTo: 'psycholog', lastTransferAmount: 80 })
    b.update(doc(db, 'users/psycholog'), { coinsBalance: 80 })
    b.set(doc(db, 'wallet_transactions/nadmiar1'), {
      transactionId: 'nadmiar1', userId: 'psycholog', counterpartyId: 'pacjent',
      appointmentId: 'termin1', amount: 99999, type: 'BOOKING_EARNING',
      title: 'Wpis nadmiarowy', timestamp: 1,
    })
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
// lastTransferTo oraz brakująca walidacja pola type w księdze operacji.
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

  it('wyzerowanie deklaracji przelewu przechodzi bez zmiany salda', async () => {
    await seed(env, async (s) => {
      await updateDoc(doc(s, 'users/pacjent'), {
        lastTransferTo: 'psycholog', lastTransferAmount: 80,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), {
      lastTransferTo: null, lastTransferAmount: 0,
    }))
  })
})
