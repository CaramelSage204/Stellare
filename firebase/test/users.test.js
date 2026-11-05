import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteField, doc, setDoc, updateDoc } from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv , zalozUzytkownika } from './helpers.js'

let env

function znacznik(psychologistId, patientId) {
  return { psychologistId, patientId, appointmentId: 'wizyta_' + psychologistId, completedAt: 1 }
}

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: false,
      isAdmin: false, coinsBalance: 0, firstName: 'Ala', pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false,
      isAdmin: false, coinsBalance: 500, firstName: 'Bogdan',
    })
    await setDoc(doc(db, 'admins/szef'), {})
  })
})

describe('users — przywileje', () => {
  it('nie pozwala samemu ustawić isVerified', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { isVerified: true }))
  })

  it('nie pozwala samemu zmienić roli', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { role: 'PSYCHOLOGIST' }))
  })

  it('nie pozwala samemu zostać administratorem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { isAdmin: true }))
  })

  it('pozwala edytować własne dane opisowe', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'users/psycholog'), { firstName: 'Alicja', bio: 'nowy opis' }))
  })

  it('nie pozwala edytować cudzego profilu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { firstName: 'Zmieniony' }))
  })

  it('pozwala administratorowi zatwierdzić weryfikację', async () => {
    const db = authedDb(env, 'szef')
    await assertSucceeds(updateDoc(doc(db, 'users/psycholog'), { isVerified: true }))
  })

  it('zakłada konto tylko niezweryfikowane i z zerowym saldem', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: true, isAdmin: false,
    }))
    await assertFails(setDoc(doc(db, 'users/nowy/private/contact'), { coinsBalance: 10000 }))
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false,
    }))
  })

  it('nie pozwala nikomu pisać do kolekcji admins', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'admins/pacjent'), {}))
  })

  it('nie pozwala zalozyc konta z polami oceny nawet zerowymi', async () => {
    const db = authedDb(env, 'nowy')
    const baza = { id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false }
    await assertFails(setDoc(doc(db, 'users/nowy'), { ...baza, ratingSum: 0, ratingCount: 0 }))
    await assertFails(setDoc(doc(db, 'users/nowy'), { ...baza, ratingSum: 0 }))
    await assertFails(setDoc(doc(db, 'users/nowy'), { ...baza, ratingCount: 0 }))
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), baza))
  })

  it('wlasciciel edytuje caly dokument (jak realny zapis .set) zachowujac ocene bez zmian', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/psycholog3', {
        id: 'psycholog3', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
        coinsBalance: 0, firstName: 'Ala', ratingSum: 45, ratingCount: 10,
      })
    })
    const db = authedDb(env, 'psycholog3')
    await assertSucceeds(setDoc(doc(db, 'users/psycholog3'), {
      id: 'psycholog3', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      firstName: 'Alicja Zaktualizowana', ratingSum: 45, ratingCount: 10,
    }))
  })

  it('nie pozwala zalozyc konta z nieznana rola', async () => {
    const db = authedDb(env, 'nowy')
    await assertFails(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'ADMINISTRATOR', isVerified: false, isAdmin: false,
    }))
  })

  it('pozwala zalozyc konto z kazda ze znanych rol', async () => {
    for (const rola of ['PATIENT', 'PSYCHOLOGIST', 'PSYCHOLOGY_STUDENT']) {
      const uid = 'nowy_' + rola
      const db = authedDb(env, uid)
      await assertSucceeds(setDoc(doc(db, 'users/' + uid), {
        id: uid, role: rola, isVerified: false, isAdmin: false,
      }))
    }
  })

  it('konto zalozone przed dodaniem pola isAdmin daje sie dalej edytowac', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/stareKonto', {
        id: 'stareKonto', role: 'PATIENT', isVerified: false, coinsBalance: 0,
        firstName: 'Stary', ratingSum: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto')
    await assertSucceeds(updateDoc(doc(db, 'users/stareKonto'), {
      firstName: 'Zaktualizowany', isAdmin: false,
    }))
  })

  it('konto bez pola isAdmin nadal nie moze go podniesc na true', async () => {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/stareKonto2', {
        id: 'stareKonto2', role: 'PATIENT', isVerified: false, coinsBalance: 0,
        firstName: 'Stary', ratingSum: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto2')
    await assertFails(updateDoc(doc(db, 'users/stareKonto2'), { isAdmin: true }))
  })

  it('rejestracja z dokumentem tak, jak serializuje go Android SDK po poprawce', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), {
      id: 'nowy', role: 'PATIENT', isVerified: false, isAdmin: false,
      firstName: 'Jan', lastName: 'Nowak', age: 30, gender: 'Mezczyzna',
      qualifications: '', specializations: '',
      pricePerSession: 0, bio: '', customPrices: '',
      profileImageUri: null,
    }))
  })

  it('weryfikacji nie da sie ustawic z klienta; stara nazwa pola jest martwym kluczem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { verified: true }))
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { isVerified: true }))
  })

  it('konto sprzed poprawki serializacji daje sie dalej edytowac', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/stareKonto3', {
        id: 'stareKonto3', role: 'PATIENT', verified: false, admin: false,
        coinsBalance: 0, firstName: 'Stary', ratingSum: 0, ratingCount: 0,
      })
    })
    const db = authedDb(env, 'stareKonto3')
    await assertSucceeds(updateDoc(doc(db, 'users/stareKonto3'), {
      firstName: 'Zaktualizowany', isVerified: false, isAdmin: false,
    }))
  })

  it('obcy nie zapisuje pol oceny w cudzym profilu, bo obcy nie ma zadnej galezi update na profilu publicznym (saldo rozliczane jest w users/{uid}/private/contact)', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), { ratingSum: 5, ratingCount: 1 }))
  })

  // Agregat oceny zyje w ratings/{psychologistId} — testy w ratings.test.js.
})

describe('ocena jako suma calkowita', () => {
  async function seedPsycholog(id, ratingSum, ratingCount) {
    await seed(env, async (db) => {
      await zalozUzytkownika(db, 'users/' + id, {
        id, role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
        coinsBalance: 0, firstName: 'Ala', ratingSum, ratingCount,
      })
      await setDoc(doc(db, 'completions/' + id + '_pacjent'), znacznik(id, 'pacjent'))
    })
  }

  it('wlasciciel moze zapisac caly dokument zachowujac ratingSum bez zmian', async () => {
    await seedPsycholog('ps_zapis', 8, 2)
    const db = authedDb(env, 'ps_zapis')
    await assertSucceeds(setDoc(doc(db, 'users/ps_zapis'), {
      id: 'ps_zapis', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      firstName: 'Zmieniona', ratingSum: 8, ratingCount: 2,
    }))
  })

  it('wlasciciel nie dopisuje ratingSum ani ratingCount do profilu bez tych pol', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { ratingSum: 0 }))
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { ratingCount: 0 }))
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { firstName: 'X', ratingSum: 5, ratingCount: 1 }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { firstName: 'X' }))
  })

  it('wlasciciel nie zmienia istniejacego ratingSum w profilu', async () => {
    await seedPsycholog('ps_stary', 8, 2)
    const db = authedDb(env, 'ps_stary')
    await assertFails(updateDoc(doc(db, 'users/ps_stary'), { ratingSum: 0, ratingCount: 0 }))
    await assertSucceeds(updateDoc(doc(db, 'users/ps_stary'), { firstName: 'Nowe imie' }))
  })

  const staryProfil = (id, ratingSum, ratingCount) => ({
    id, role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, firstName: 'Ala', ratingSum, ratingCount,
  })
  const profilBezOceny = (id) => ({
    id, role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false, firstName: 'Alicja',
  })

  it('wlasciciel starego profilu zapisuje caly dokument bez pol oceny (tak jak robi to klient po zdjeciu pol z UserModel)', async () => {
    await seedPsycholog('ps_usuwa', 45, 10)
    const db = authedDb(env, 'ps_usuwa')
    await assertSucceeds(setDoc(doc(db, 'users/ps_usuwa'), profilBezOceny('ps_usuwa')))
  })

  it('wlasciciel usuwa pola oceny przez deleteField', async () => {
    await seedPsycholog('ps_delete', 45, 10)
    const db = authedDb(env, 'ps_delete')
    await assertSucceeds(updateDoc(doc(db, 'users/ps_delete'), { ratingSum: deleteField(), ratingCount: deleteField() }))
  })

  it('usuniecie tylko jednego pola oceny tez przechodzi, a drugie zostaje chronione', async () => {
    await seedPsycholog('ps_jedno', 45, 10)
    const db = authedDb(env, 'ps_jedno')
    await assertSucceeds(setDoc(doc(db, 'users/ps_jedno'), { ...profilBezOceny('ps_jedno'), ratingCount: 10 }))
    await assertFails(setDoc(doc(db, 'users/ps_jedno'), { ...profilBezOceny('ps_jedno'), ratingCount: 1 }))
  })

  it('usuniecie jednego pola nie pozwala przy tym zmienic drugiego', async () => {
    await seedPsycholog('ps_mieszany', 45, 10)
    const db = authedDb(env, 'ps_mieszany')
    await assertFails(setDoc(doc(db, 'users/ps_mieszany'), { ...profilBezOceny('ps_mieszany'), ratingSum: 5 }))
    await assertFails(setDoc(doc(db, 'users/ps_mieszany'), { ...profilBezOceny('ps_mieszany'), ratingCount: 1 }))
  })

  it('pole oceny usuniete z profilu nie wraca: ani zero, ani poprzednia wartosc', async () => {
    await seedPsycholog('ps_wrot', 45, 10)
    const db = authedDb(env, 'ps_wrot')
    await assertSucceeds(setDoc(doc(db, 'users/ps_wrot'), profilBezOceny('ps_wrot')))
    await assertFails(updateDoc(doc(db, 'users/ps_wrot'), { ratingSum: 45, ratingCount: 10 }))
    await assertFails(updateDoc(doc(db, 'users/ps_wrot'), { ratingSum: 0 }))
    await assertFails(setDoc(doc(db, 'users/ps_wrot'), { ...profilBezOceny('ps_wrot'), ratingCount: 0 }))
  })

  it('zmiana wartosci oceny w starym profilu nadal odmowa przy zapisie calego dokumentu', async () => {
    await seedPsycholog('ps_zmiana', 45, 10)
    const db = authedDb(env, 'ps_zmiana')
    await assertFails(setDoc(doc(db, 'users/ps_zmiana'), staryProfil('ps_zmiana', 50, 10)))
    await assertFails(setDoc(doc(db, 'users/ps_zmiana'), staryProfil('ps_zmiana', 45, 0)))
    await assertSucceeds(setDoc(doc(db, 'users/ps_zmiana'), staryProfil('ps_zmiana', 45, 10)))
  })

  it('administrator tez moze zapisac stary profil bez pol oceny, ale nie zmienic ich wartosci', async () => {
    await seedPsycholog('ps_admin', 45, 10)
    const db = authedDb(env, 'szef')
    await assertFails(updateDoc(doc(db, 'users/ps_admin'), { ratingSum: 1 }))
    await assertSucceeds(updateDoc(doc(db, 'users/ps_admin'), { ratingSum: deleteField(), ratingCount: deleteField(), isVerified: false }))
  })

  it('administrator nie dopisuje ratingSum ani ratingCount do profilu', async () => {
    const db = authedDb(env, 'szef')
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { ratingSum: 5 }))
    await assertFails(updateDoc(doc(db, 'users/pacjent'), { isVerified: true, ratingCount: 1 }))
    await assertSucceeds(updateDoc(doc(db, 'users/pacjent'), { isVerified: true }))
  })
})

// Zadanie 12, runda poprawek 1 — najszersze wejście tej klasy błędu.
// UserModel.id pochodził z pola dokumentu, a getCurrentUser().id steruje authorId
// ogłoszenia, patientId rezerwacji, reviewerId opinii i userId wpisu księgi. Rozjechane
// pole zamienia publikację, rezerwację i wystawienie opinii w odmowę reguł z komunikatem
// „nie udało się", bez żadnej drogi naprawy od wewnątrz aplikacji.
// Obie gałęzie `allow update` mają własną parę testów: reguły są alternatywą, więc
// zdjęcie warunku z JEDNEJ gałęzi wystarczy, by zapis przeszedł tą gałęzią.
describe('users — pole id musi być identyfikatorem dokumentu (zadanie 12, runda 1)', () => {
  const profil = (id, extra = {}) => ({
    id, role: 'PATIENT', isVerified: false, isAdmin: false, firstName: 'Bogdan', ...extra,
  })

  it('zalozenie profilu z polem id innym niz identyfikator dokumentu jest odrzucane', async () => {
    const db = authedDb(env, 'nowy')
    await odrzucone(
      'profil twierdzi, ze jest profilem innego uzytkownika: getCurrentUser().id podstawia obce uid'
        + ' do authorId ogloszenia, patientId rezerwacji i reviewerId opinii, a kazdy taki zapis'
        + ' dostaje odmowe regul z komunikatem "nie udalo sie"',
      setDoc(doc(db, 'users/nowy'), profil('psycholog'))
    )
  })

  it('zalozenie profilu z polem id rownym identyfikatorowi dokumentu przechodzi', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), profil('nowy')))
  })

  it('edycja wlasnego profilu, ktorego pole id klamie, jest odrzucana — choc edycja tego pola nie rusza', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/klamliwy', profil('psycholog', { coinsBalance: 0 }))
    })
    const db = authedDb(env, 'klamliwy')
    await odrzucone(
      'profil podajacy sie za cudzy zostaje na stale i dalej podstawia obce uid do kazdego zapisu'
        + ' wykonywanego w imieniu wlasciciela',
      updateDoc(doc(db, 'users/klamliwy'), { firstName: 'Zmieniony' })
    )
  })

  it('edycja wlasnego profilu o zgodnym polu id nadal przechodzi', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/zgodny', profil('zgodny', { coinsBalance: 0 }))
    })
    const db = authedDb(env, 'zgodny')
    await assertSucceeds(updateDoc(doc(db, 'users/zgodny'), { firstName: 'Zmieniony' }))
  })

  it('zatwierdzenie weryfikacji przez administratora na profilu z klamliwym polem id jest odrzucane', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/klamliwyPsych', profil('psycholog', {
        role: 'PSYCHOLOGIST', coinsBalance: 0, pricePerSession: 80,
      }))
    })
    const db = authedDb(env, 'szef')
    await odrzucone(
      'administrator nadaje odznake "Zweryfikowany" profilowi, ktory twierdzi, ze jest innym profilem'
        + ' — odznaka trafia na dokument, ktorego tozsamosc wskazuje kogos innego',
      updateDoc(doc(db, 'users/klamliwyPsych'), { isVerified: true })
    )
  })

  it('zatwierdzenie weryfikacji przez administratora na profilu o zgodnym polu id nadal przechodzi', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/zgodnyPsych', profil('zgodnyPsych', {
        role: 'PSYCHOLOGIST', coinsBalance: 0, pricePerSession: 80,
      }))
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(updateDoc(doc(db, 'users/zgodnyPsych'), { isVerified: true }))
  })
})

// Zadanie 12, runda poprawek 2 — pole BRAKUJĄCE nie kłamie, więc przechodzi.
// Jedna para na każde wejście reguły, które dostało warunek: tworzenie oraz obie gałęzie
// `allow update` (administrator i właściciel). Trzeci test jest tu najważniejszy: to on
// odpowiada szkodzie, która rozstrzygnęła wybór wariantu — profil zapisany spoza aplikacji
// bez pola `id` był profilem, KTÓREGO ADMINISTRATOR NIGDY NIE ZWERYFIKUJE, bo
// verificationApproval() pisze tylko isVerified i pola `id` nie wysyła.
// Kontrolą negatywną są trzy testy „pole id kłamie → odmowa" stojące wyżej.
describe('users — brak pola id nie blokuje zapisu (zadanie 12, runda 2)', () => {
  const profilBezPola = (extra = {}) => ({
    role: 'PATIENT', isVerified: false, isAdmin: false, firstName: 'Bogdan', ...extra,
  })

  it('zalozenie profilu BEZ pola id przechodzi', async () => {
    const db = authedDb(env, 'nowy')
    await assertSucceeds(setDoc(doc(db, 'users/nowy'), profilBezPola()))
  })

  it('edycja wlasnego profilu BEZ pola id przechodzi — wariant scisly czynil go nieedytowalnym na zawsze', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/bezPola', profilBezPola({ coinsBalance: 0 }))
    })
    const db = authedDb(env, 'bezPola')
    await assertSucceeds(updateDoc(doc(db, 'users/bezPola'), { firstName: 'Zmieniony' }))
  })

  it('zatwierdzenie weryfikacji przez administratora na profilu BEZ pola id przechodzi', async () => {
    await seed(env, async (s) => {
      await zalozUzytkownika(s, 'users/psychBezPola', profilBezPola({
        role: 'PSYCHOLOGIST', coinsBalance: 0, pricePerSession: 80,
      }))
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(updateDoc(doc(db, 'users/psychBezPola'), { isVerified: true }))
  })
})
