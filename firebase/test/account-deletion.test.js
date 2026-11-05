import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, where } from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv, unauthedDb , zalozUzytkownika } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })

const ogloszenie = (id, authorId) => ({
  id, authorId, type: 'OFFERING_THERAPY', title: 'Terapia', body: 'Opis', tags: [],
  priceCoins: 80, createdAt: 1, isActive: true,
  authorSnapshot: { firstName: 'Ala', isVerified: true },
})

beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/zostajacy', {
      id: 'zostajacy', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 100,
    })
    await zalozUzytkownika(db, 'users/odchodzacy', {
      id: 'odchodzacy', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 50, pricePerSession: 80,
    })
    await setDoc(doc(db, 'chats/czatOdchodzacego'), {
      chatId: 'czatOdchodzacego', psychologistId: 'odchodzacy', patientId: 'pacjent',
      participants: ['odchodzacy', 'pacjent'], lastMessage: '', lastMessageTime: 0,
    })
    await setDoc(doc(db, 'notes/notatkaOdchodzacego'), {
      noteId: 'notatkaOdchodzacego', chatId: 'czatOdchodzacego', authorId: 'odchodzacy',
      title: 'Sesja', content: 'obserwacje', timestamp: 1,
    })
    await setDoc(doc(db, 'posts/ogloszenieOdchodzacego'), ogloszenie('ogloszenieOdchodzacego', 'odchodzacy'))
    await setDoc(doc(db, 'posts/ogloszenieZostajacego'), ogloszenie('ogloszenieZostajacego', 'zostajacy'))
    await setDoc(doc(db, 'wallet_transactions/wpis1'), {
      transactionId: 'wpis1', userId: 'odchodzacy', amount: 50, type: 'TOPUP',
      title: 'Doładowanie', timestamp: 1,
    })
    await setDoc(doc(db, 'reviews/odchodzacy_pacjent'), {
      reviewId: 'odchodzacy_pacjent', psychologistId: 'odchodzacy', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'dobrze', timestamp: 1,
    })
    await setDoc(doc(db, 'reviews/zostajacy_odchodzacy'), {
      reviewId: 'zostajacy_odchodzacy', psychologistId: 'zostajacy', reviewerId: 'odchodzacy',
      reviewerName: 'Ala', rating: 4, comment: 'ok', timestamp: 1,
    })
    await setDoc(doc(db, 'appointments/wizytaZajeta'), {
      appointmentId: 'wizytaZajeta', psychologistId: 'odchodzacy', patientId: 'pacjent', status: 'BOOKED',
      date: '2030-01-01', time: '10:00', startsAtMillis: 1893492000000, priceCoins: 80,
    })
  })
})

async function usunProfil(uid) {
  await seed(env, async (s) => { await deleteDoc(doc(s, `users/${uid}`)) })
}

describe('usuniecie konta - dokument uzytkownika', () => {
  it('wlasciciel usuwa swoj dokument uzytkownika', async () => {
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'users/odchodzacy')))
  })

  it('obcy nie usuwa cudzego dokumentu uzytkownika', async () => {
    await assertFails(deleteDoc(doc(authedDb(env, 'zostajacy'), 'users/odchodzacy')))
  })

  it('niezalogowany nie usuwa dokumentu uzytkownika', async () => {
    await assertFails(deleteDoc(doc(unauthedDb(env), 'users/odchodzacy')))
  })
})

describe('usuniecie konta - dokument prywatny', () => {
  const PRYWATNY = (uid) => `users/${uid}/private/contact`

  it('wlasciciel usuwa swoj dokument prywatny (telefon, e-mail, saldo)', async () => {
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), PRYWATNY('odchodzacy'))))
    let snap
    await seed(env, async (s) => { snap = await getDoc(doc(s, PRYWATNY('odchodzacy'))) })
    expect(snap.exists(), 'dokument prywatny z telefonem i e-mailem przetrwal usuniecie konta').toBe(false)
  })

  it('obcy, niezalogowany i administrator nie usuwaja cudzego dokumentu prywatnego', async () => {
    await seed(env, async (s) => { await setDoc(doc(s, 'admins/szef'), {}) })
    await odrzucone('obcy usunal cudzy dokument prywatny', deleteDoc(doc(authedDb(env, 'zostajacy'), PRYWATNY('odchodzacy'))))
    await odrzucone('niezalogowany usunal cudzy dokument prywatny', deleteDoc(doc(unauthedDb(env), PRYWATNY('odchodzacy'))))
    await odrzucone('administrator usunal cudzy dokument prywatny', deleteDoc(doc(authedDb(env, 'szef'), PRYWATNY('odchodzacy'))))
  })

  it('kolejnosc: dokument prywatny schodzi PRZED publicznym, a kazdy z nich usuwa sie niezaleznie od drugiego', async () => {
    const db = authedDb(env, 'odchodzacy')
    await assertSucceeds(deleteDoc(doc(db, PRYWATNY('odchodzacy'))))
    await assertSucceeds(deleteDoc(doc(db, 'users/odchodzacy')))
  })

  it('usuniecie dokumentu publicznego nie zostawia dokumentu prywatnego dostepnego dla obcych', async () => {
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'users/odchodzacy')))
    await odrzucone('obcy odczytal telefon, e-mail i saldo po usunieciu profilu publicznego', getDoc(doc(authedDb(env, 'zostajacy'), PRYWATNY('odchodzacy'))))
  })
})

describe('usuniecie konta - ogloszenia', () => {
  it('autor, ktory usunal swoj profil, usuwa swoje ogloszenie', async () => {
    await usunProfil('odchodzacy')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'posts/ogloszenieOdchodzacego')))
  })

  it('autor, ktory usunal swoj profil, nadal znajduje swoje ogloszenia zapytaniem po authorId', async () => {
    await usunProfil('odchodzacy')
    const db = authedDb(env, 'odchodzacy')
    await assertSucceeds(getDocs(query(collection(db, 'posts'), where('authorId', '==', 'odchodzacy'))))
  })

  it('autor z istniejacym profilem nadal nie kasuje ogloszenia - tylko dezaktywacja', async () => {
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'posts/ogloszenieOdchodzacego')))
  })

  it('uzytkownik bez profilu nie kasuje cudzego ogloszenia', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'posts/ogloszenieZostajacego')))
  })

  it('niezalogowany nie kasuje ogloszenia', async () => {
    await assertFails(deleteDoc(doc(unauthedDb(env), 'posts/ogloszenieOdchodzacego')))
  })
})

describe('usuniecie konta - notatki', () => {
  it('psycholog prowadzacy czat usuwa swoja notatke takze po usunieciu profilu', async () => {
    await usunProfil('odchodzacy')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'notes/notatkaOdchodzacego')))
  })

  it('psycholog po usunieciu profilu nadal znajduje notatki czatu zapytaniem po chatId', async () => {
    await usunProfil('odchodzacy')
    const db = authedDb(env, 'odchodzacy')
    await assertSucceeds(getDocs(query(collection(db, 'notes'), where('chatId', '==', 'czatOdchodzacego'))))
  })

  it('pacjent czatu nie usuwa notatki psychologa', async () => {
    await assertFails(deleteDoc(doc(authedDb(env, 'pacjent'), 'notes/notatkaOdchodzacego')))
  })

  it('obcy psycholog nie usuwa notatki z cudzego czatu', async () => {
    await assertFails(deleteDoc(doc(authedDb(env, 'zostajacy'), 'notes/notatkaOdchodzacego')))
  })
})

describe('usuniecie konta - to, co zostaje, nadal jest nieusuwalne', () => {
  it('wpis ksiegi portfela jest nieusuwalny dla wlasciciela z profilem', async () => {
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'wallet_transactions/wpis1')))
  })

  it('wpis ksiegi portfela jest nieusuwalny dla wlasciciela po usunieciu profilu', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'wallet_transactions/wpis1')))
  })

  it('opinia wystawiona przez odchodzacego jest nieusuwalna po usunieciu profilu', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'reviews/zostajacy_odchodzacy')))
  })

  it('opinia o odchodzacym psychologu jest nieusuwalna dla jej autora', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'pacjent'), 'reviews/odchodzacy_pacjent')))
  })

  it('zarezerwowana wizyta nie znika po usunieciu profilu psychologa', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'appointments/wizytaZajeta')))
  })

  it('czat odchodzacego jest nieusuwalny po usunieciu profilu', async () => {
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'chats/czatOdchodzacego')))
  })
})

async function zasilZgloszenie(uid, status) {
  await seed(env, async (s) => {
    await setDoc(doc(s, `verificationRequests/${uid}`), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status, submittedAt: 1,
      reviewedBy: status === 'REJECTED' ? 'szef' : '', reviewedAt: status === 'REJECTED' ? 2 : 0,
      reviewNote: status === 'REJECTED' ? 'nieczytelny skan' : '',
    })
  })
}

describe('usuniecie konta - zgloszenie weryfikacyjne ze skanem dokumentu', () => {
  it('wlasciciel z istniejacym profilem nie usuwa swojego odrzuconego zgloszenia - slad rozpatrzenia', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'verificationRequests/odchodzacy')))
  })

  it('wlasciciel z istniejacym profilem nie usuwa tez zgloszenia oczekujacego', async () => {
    await zasilZgloszenie('odchodzacy', 'PENDING')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'verificationRequests/odchodzacy')))
  })

  it('wlasciciel bez profilu usuwa swoje zgloszenie ze skanem', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await usunProfil('odchodzacy')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'verificationRequests/odchodzacy')))
  })

  it('wlasciciel bez profilu usuwa tez zgloszenie zatwierdzone', async () => {
    await zasilZgloszenie('odchodzacy', 'APPROVED')
    await usunProfil('odchodzacy')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'verificationRequests/odchodzacy')))
  })

  it('obcy z profilem nie usuwa cudzego zgloszenia', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await assertFails(deleteDoc(doc(authedDb(env, 'zostajacy'), 'verificationRequests/odchodzacy')))
  })

  it('obcy bez profilu nie usuwa cudzego zgloszenia', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await usunProfil('zostajacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'zostajacy'), 'verificationRequests/odchodzacy')))
  })

  it('obcy bez profilu nie usuwa zgloszenia, gdy wlasciciel nadal ma profil', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await usunProfil('pacjent')
    await assertFails(deleteDoc(doc(authedDb(env, 'pacjent'), 'verificationRequests/odchodzacy')))
  })

  it('niezalogowany nie usuwa zgloszenia', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(unauthedDb(env), 'verificationRequests/odchodzacy')))
  })

  it('administrator usuwa zgloszenie jak dotad, takze gdy wlasciciel ma profil', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await seed(env, async (s) => { await setDoc(doc(s, 'admins/szef'), {}) })
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'szef'), 'verificationRequests/odchodzacy')))
  })

  it('wlasciciel bez profilu nadal czyta swoje zgloszenie, wiec aplikacja moze sprawdzic, czy je ma', async () => {
    await zasilZgloszenie('odchodzacy', 'REJECTED')
    await usunProfil('odchodzacy')
    await assertSucceeds(getDoc(doc(authedDb(env, 'odchodzacy'), 'verificationRequests/odchodzacy')))
  })
})

const termin = (id, status, patientId = null) => ({
  id, appointmentId: id, psychologistId: 'odchodzacy', patientId, status,
  date: '2030-01-02', time: '11:00', startsAtMillis: 1893578400000, priceCoins: 80,
})

async function zasilTermin(id, status, patientId = null) {
  await seed(env, async (s) => { await setDoc(doc(s, `appointments/${id}`), termin(id, status, patientId)) })
}

describe('usuniecie konta - wolne terminy psychologa (bez zmiany regul)', () => {
  it('psycholog z profilem usuwa swoj wolny termin', async () => {
    await zasilTermin('wolny1', 'FREE')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'appointments/wolny1')))
  })

  it('psycholog bez profilu nadal usuwa swoj wolny termin', async () => {
    await zasilTermin('wolny1', 'FREE')
    await usunProfil('odchodzacy')
    await assertSucceeds(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'appointments/wolny1')))
  })

  it('psycholog bez profilu znajduje swoje wolne terminy zapytaniem po psychologistId i statusie', async () => {
    await zasilTermin('wolny1', 'FREE')
    await usunProfil('odchodzacy')
    const db = authedDb(env, 'odchodzacy')
    await assertSucceeds(getDocs(query(
      collection(db, 'appointments'),
      where('psychologistId', '==', 'odchodzacy'),
      where('status', '==', 'FREE')
    )))
  })

  it('obcy nie usuwa cudzego wolnego terminu', async () => {
    await zasilTermin('wolny1', 'FREE')
    await assertFails(deleteDoc(doc(authedDb(env, 'zostajacy'), 'appointments/wolny1')))
  })

  it('zarezerwowany termin nie znika dla psychologa bez profilu', async () => {
    await zasilTermin('zajety1', 'BOOKED', 'pacjent')
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'appointments/zajety1')))
  })

  it('odbyta wizyta nie znika dla psychologa bez profilu', async () => {
    await zasilTermin('odbyta1', 'COMPLETED', 'pacjent')
    await usunProfil('odchodzacy')
    await assertFails(deleteDoc(doc(authedDb(env, 'odchodzacy'), 'appointments/odbyta1')))
  })

  it('pacjent nie usuwa wolnego terminu psychologa', async () => {
    await zasilTermin('wolny1', 'FREE')
    await assertFails(deleteDoc(doc(authedDb(env, 'pacjent'), 'appointments/wolny1')))
  })
})
