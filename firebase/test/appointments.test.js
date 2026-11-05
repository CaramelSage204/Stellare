import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { doc, setDoc, updateDoc, deleteDoc } from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv , zalozUzytkownika } from './helpers.js'

const DZIEN = 24 * 60 * 60 * 1000
const JUTRO = Date.now() + DZIEN
const ZA_TYDZIEN = Date.now() + 7 * DZIEN

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    for (const [uid, cena] of [['psychologUjemny', -1], ['psychologDarmowy', 0], ['psychologUlamek', 80.5]]) {
      await setDoc(doc(db, `users/${uid}`), {
        id: uid, role: 'PSYCHOLOGIST', isVerified: true,
        isAdmin: false, coinsBalance: 0, pricePerSession: cena,
      })
    }
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await zalozUzytkownika(db, 'users/intruz', {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 500,
    })
    await setDoc(doc(db, 'appointments/termin1'), {
      appointmentId: 'termin1', psychologistId: 'psycholog', patientId: null,
      startsAtMillis: JUTRO, date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 0,
    })
  })
})

const wolnyTermin = {
  appointmentId: 'nowy', psychologistId: 'psycholog', patientId: null,
  startsAtMillis: ZA_TYDZIEN, date: '2026-10-02', time: '11:00', notes: '', status: 'FREE', priceCoins: 80,
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
        startsAtMillis: ZA_TYDZIEN, date: '2026-10-05', time: '09:00', notes: '', status: 'FREE',
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
  // Dzis blokuje to amount > 0 w depozycie (escrows), ale to ochrona przypadkowa,
  // niezwiazana z cena. priceCoins >= 0 wiaze to wprost przy zrodle.
  it('nie da się wystawić terminu z ceną ujemną', async () => {
    const db = authedDb(env, 'psychologUjemny')
    await assertFails(setDoc(doc(db, 'appointments/cenaUjemna'), {
      ...wolnyTermin, appointmentId: 'cenaUjemna', psychologistId: 'psychologUjemny', priceCoins: -1,
    }))
  })

  it('można wystawić darmowy termin (priceCoins = 0)', async () => {
    const db = authedDb(env, 'psychologDarmowy')
    await assertSucceeds(setDoc(doc(db, 'appointments/cenaZero'), {
      ...wolnyTermin, appointmentId: 'cenaZero', psychologistId: 'psychologDarmowy', priceCoins: 0,
    }))
  })

  // Zadanie 4 Etapu 5 — startsAtMillis jako jedyne zrodlo prawdy o terminie.
  it('nie da się wystawić terminu w przeszłości', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/przeszly'), {
      ...wolnyTermin, appointmentId: 'przeszly', startsAtMillis: Date.now() - 60 * 1000,
    }))
  })

  it('nie da się wystawić terminu z początkiem w odległej przeszłości', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/epoka'), {
      ...wolnyTermin, appointmentId: 'epoka', startsAtMillis: 1,
    }))
  })

  it('można wystawić termin z początkiem w przyszłości', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'appointments/przyszly'), {
      ...wolnyTermin, appointmentId: 'przyszly', startsAtMillis: Date.now() + 2 * 60 * 60 * 1000,
    }))
  })

  it('nie da się wystawić terminu bez startsAtMillis', async () => {
    const db = authedDb(env, 'psycholog')
    const { startsAtMillis, ...bezPola } = wolnyTermin
    await assertFails(setDoc(doc(db, 'appointments/bezPola'), { ...bezPola, appointmentId: 'bezPola' }))
  })

  it('nie da się wystawić terminu ze startsAtMillis równym null', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/pusty'), {
      ...wolnyTermin, appointmentId: 'pusty', startsAtMillis: null,
    }))
  })

  it('nie da się wystawić terminu ze startsAtMillis jako tekstem', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/tekst'), {
      ...wolnyTermin, appointmentId: 'tekst', startsAtMillis: String(ZA_TYDZIEN),
    }))
  })

  it('nie da się wystawić terminu ze startsAtMillis jako liczbą niecałkowitą', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'appointments/ulamek'), {
      ...wolnyTermin, appointmentId: 'ulamek', startsAtMillis: ZA_TYDZIEN + 0.5,
    }))
  })

  it('pacjent nie może zmienić startsAtMillis przy rezerwacji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'pacjent', startsAtMillis: ZA_TYDZIEN,
    }))
  })

  it('pacjent nie może przesunąć startsAtMillis w przeszłość przy rezerwacji', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'pacjent', startsAtMillis: 1,
    }))
  })

  it('rezerwacja bez ruszania startsAtMillis przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/termin1'), {
      status: 'BOOKED', patientId: 'pacjent', notes: 'x',
    }))
  })

  const seedTermin = (id, pola) => seed(env, async (db) => {
    await setDoc(doc(db, 'appointments/' + id), {
      appointmentId: id, psychologistId: 'psycholog', patientId: null,
      date: '2026-10-05', time: '09:00', notes: '', status: 'FREE', priceCoins: 0, ...pola,
    })
  })
  const rezerwuj = (id) => updateDoc(doc(authedDb(env, 'pacjent'), 'appointments/' + id), {
    status: 'BOOKED', patientId: 'pacjent', notes: '',
  })

  it('E5-R20: nie da się zarezerwować terminu, który już minął', async () => {
    await seedTermin('minal', { startsAtMillis: Date.now() - 60 * 60 * 1000 })
    await assertFails(rezerwuj('minal'))
  })

  it('E5-R20: nie da się zarezerwować terminu bez startsAtMillis', async () => {
    await seedTermin('bezpola', {})
    await assertFails(rezerwuj('bezpola'))
  })

  it('E5-R20: nie da się zarezerwować terminu ze startsAtMillis równym zero', async () => {
    await seedTermin('zero', { startsAtMillis: 0 })
    await assertFails(rezerwuj('zero'))
  })

  it('E5-R20: termin za minutę da się jeszcze zarezerwować', async () => {
    await seedTermin('zaminute', { startsAtMillis: Date.now() + 60 * 1000 })
    await assertSucceeds(rezerwuj('zaminute'))
  })

  it('pacjent nie może dopisać startsAtMillis do terminu sprzed etapu', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'appointments/stary'), {
        appointmentId: 'stary', psychologistId: 'psycholog', patientId: null,
        date: '2026-10-05', time: '09:00', notes: '', status: 'FREE',
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'appointments/stary'), {
      status: 'BOOKED', patientId: 'pacjent', startsAtMillis: ZA_TYDZIEN,
    }))
  })

  describe('odwołanie i zakończenie', () => {
    beforeEach(async () => {
      await seed(env, async (db) => {
        await updateDoc(doc(db, 'appointments/termin1'), { status: 'BOOKED', patientId: 'pacjent' })
      })
    })

    it('pacjent odwołuje rezerwację bez ruszania startsAtMillis', async () => {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(updateDoc(doc(db, 'appointments/termin1'), { status: 'CANCELLED', cancelledBy: 'pacjent' }))
    })

    it('pacjent nie może zmienić startsAtMillis przy odwołaniu', async () => {
      const db = authedDb(env, 'pacjent')
      await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
        status: 'CANCELLED', cancelledBy: 'pacjent', startsAtMillis: Date.now() + 365 * DZIEN,
      }))
    })

    it('psycholog nie może zmienić startsAtMillis przy zakończeniu', async () => {
      const db = authedDb(env, 'psycholog')
      await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
        status: 'COMPLETED', startsAtMillis: 1,
      }))
    })

    it('psycholog nie może zmienić startsAtMillis zwalniając termin do FREE', async () => {
      const db = authedDb(env, 'psycholog')
      await assertFails(updateDoc(doc(db, 'appointments/termin1'), {
        status: 'FREE', patientId: null, startsAtMillis: ZA_TYDZIEN,
      }))
    })
  })

  describe('cena związana z profilem psychologa', () => {
    it('cena równa pricePerSession z profilu przechodzi', async () => {
      const db = authedDb(env, 'psycholog')
      await assertSucceeds(setDoc(doc(db, 'appointments/cena80'), {
        ...wolnyTermin, appointmentId: 'cena80', priceCoins: 80,
      }))
    })

    it('cena wyższa niż w profilu jest odrzucona', async () => {
      const db = authedDb(env, 'psycholog')
      await assertFails(setDoc(doc(db, 'appointments/cena81'), {
        ...wolnyTermin, appointmentId: 'cena81', priceCoins: 81,
      }))
    })

    it('cena niższa niż w profilu jest odrzucona', async () => {
      const db = authedDb(env, 'psycholog')
      await assertFails(setDoc(doc(db, 'appointments/cena79'), {
        ...wolnyTermin, appointmentId: 'cena79', priceCoins: 79,
      }))
    })

    it('cena zero przy płatnym profilu jest odrzucona', async () => {
      const db = authedDb(env, 'psycholog')
      await assertFails(setDoc(doc(db, 'appointments/cena0'), {
        ...wolnyTermin, appointmentId: 'cena0', priceCoins: 0,
      }))
    })

    it('int() obcina pricePerSession: 80.5 daje 80', async () => {
      const db = authedDb(env, 'psychologUlamek')
      await assertSucceeds(setDoc(doc(db, 'appointments/ulamek80'), {
        ...wolnyTermin, appointmentId: 'ulamek80', psychologistId: 'psychologUlamek', priceCoins: 80,
      }))
    })

    it('int() nie zaokrągla w górę: 81 przy 80.5 jest odrzucone', async () => {
      const db = authedDb(env, 'psychologUlamek')
      await assertFails(setDoc(doc(db, 'appointments/ulamek81'), {
        ...wolnyTermin, appointmentId: 'ulamek81', psychologistId: 'psychologUlamek', priceCoins: 81,
      }))
    })
  })
})

// Zadanie 12, runda poprawek 1 — pole appointmentId musi być identyfikatorem dokumentu.
// Druga połowa awarii z zadania 11 (R1), w gorszym miejscu niż ogłoszenia: wartość
// odczytana z pola sterowała rezerwacją, odwołaniem, zakończeniem, kasowaniem terminu
// i krokiem usuwania konta (DeletionStep.DeleteFreeSlot). Rozjechany dokument daje
// wolny termin nie do zarezerwowania i nie do skasowania oraz usuwanie konta
// przerwane w połowie — bez żadnej drogi naprawy z aplikacji.
describe('wizyty — pole appointmentId musi być identyfikatorem dokumentu (zadanie 12, runda 1)', () => {
  it('wystawienie terminu z polem appointmentId innym niż identyfikator dokumentu jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await odrzucone(
      'dwa terminy twierdzą, że są tą samą wizytą: rezerwacja i kasowanie celują w appointments/podmieniony,'
        + ' czyli w dokument, którego nie ma — wolny termin przepada bez możliwości naprawy z aplikacji',
      setDoc(doc(db, 'appointments/prawdziwyIdentyfikator'), { ...wolnyTermin, appointmentId: 'podmieniony' })
    )
  })

  it('wystawienie terminu z polem appointmentId równym identyfikatorowi dokumentu przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(
      setDoc(doc(db, 'appointments/zgodnyIdentyfikator'), { ...wolnyTermin, appointmentId: 'zgodnyIdentyfikator' })
    )
  })

  const wolnyZasiany = (nazwa, appointmentId) => seed(env, async (s) => {
    await setDoc(doc(s, `appointments/${nazwa}`), {
      appointmentId, psychologistId: 'psycholog', patientId: null,
      startsAtMillis: JUTRO, date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 0,
    })
  })

  const zajetyZasiany = (nazwa, appointmentId) => seed(env, async (s) => {
    await setDoc(doc(s, `appointments/${nazwa}`), {
      appointmentId, psychologistId: 'psycholog', patientId: 'pacjent',
      startsAtMillis: JUTRO, date: '2026-10-01', time: '10:00', notes: '', status: 'BOOKED', priceCoins: 0,
    })
  })

  it('rezerwacja terminu, którego pole appointmentId kłamie, jest odrzucana — choć rezerwacja tego pola nie rusza', async () => {
    await wolnyZasiany('klamliwyWolny', 'termin1')
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'termin podający się za inny termin daje się zarezerwować, więc pacjent płaci za wizytę,'
        + ' której depozyt i rozliczenie wskazują inny dokument',
      updateDoc(doc(db, 'appointments/klamliwyWolny'), { status: 'BOOKED', patientId: 'pacjent' })
    )
  })

  it('rezerwacja terminu o zgodnym polu appointmentId nadal przechodzi', async () => {
    await wolnyZasiany('zgodnyWolny', 'zgodnyWolny')
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/zgodnyWolny'), { status: 'BOOKED', patientId: 'pacjent' }))
  })

  it('odwołanie wizyty, której pole appointmentId kłamie, jest odrzucane — choć odwołanie tego pola nie rusza', async () => {
    await zajetyZasiany('klamliwyZajety', 'termin1')
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'wizyta podająca się za inną wizytę zostaje w kolekcji na stałe i dalej kieruje odwołanie,'
        + ' zakończenie i krok usuwania konta w dokument, którego nie ma',
      updateDoc(doc(db, 'appointments/klamliwyZajety'), { status: 'CANCELLED', cancelledBy: 'pacjent' })
    )
  })

  it('odwołanie wizyty o zgodnym polu appointmentId nadal przechodzi', async () => {
    await zajetyZasiany('zgodnyZajety', 'zgodnyZajety')
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/zgodnyZajety'), { status: 'CANCELLED', cancelledBy: 'pacjent' }))
  })
})

// Zadanie 12, runda poprawek 2 — pole BRAKUJĄCE nie kłamie, więc przechodzi.
// Jedna para na każde wejście reguły, które dostało warunek: tworzenie oraz obie gałęzie
// `allow update` (rezerwacja i odwołanie/zakończenie). Kontrolą negatywną są trzy testy
// „pole appointmentId kłamie → odmowa" stojące wyżej. Pełne uzasadnienie w komentarzu reguły.
describe('wizyty — brak pola appointmentId nie blokuje zapisu (zadanie 12, runda 2)', () => {
  const { appointmentId: _bezZnaczenia, ...wolnyBezPola } = wolnyTermin

  const zasiejBezPola = (nazwa, nadpisania) => seed(env, async (s) => {
    await setDoc(doc(s, `appointments/${nazwa}`), {
      psychologistId: 'psycholog', patientId: null,
      startsAtMillis: JUTRO, date: '2026-10-01', time: '10:00', notes: '', status: 'FREE', priceCoins: 0,
      ...nadpisania,
    })
  })

  it('wystawienie terminu BEZ pola appointmentId przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'appointments/bezPolaAppointmentId'), wolnyBezPola))
  })

  it('rezerwacja terminu BEZ pola appointmentId przechodzi — wariant ścisły czynił go nie do zarezerwowania na zawsze', async () => {
    await zasiejBezPola('wolnyBezPola')
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/wolnyBezPola'), { status: 'BOOKED', patientId: 'pacjent' }))
  })

  it('odwołanie wizyty BEZ pola appointmentId przechodzi — wariant ścisły czynił ją nie do odwołania na zawsze', async () => {
    await zasiejBezPola('zajetyBezPola', { status: 'BOOKED', patientId: 'pacjent' })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'appointments/zajetyBezPola'), { status: 'CANCELLED', cancelledBy: 'pacjent' }))
  })
})
