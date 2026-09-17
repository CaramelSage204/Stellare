import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore'
import { authedDb, seed, setupEnv } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: false,
      isAdmin: false, coinsBalance: 0, pricePerSession: 80,
    })
    await setDoc(doc(db, 'users/student'), {
      id: 'student', role: 'PSYCHOLOGY_STUDENT', isVerified: false,
      isAdmin: false, coinsBalance: 0, pricePerSession: 0,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false,
      isAdmin: false, coinsBalance: 500, pricePerSession: 0,
    })
    await setDoc(doc(db, 'users/obcy'), {
      id: 'obcy', role: 'PSYCHOLOGIST', isVerified: true,
      isAdmin: false, coinsBalance: 0, pricePerSession: 60,
    })
    await setDoc(doc(db, 'admins/szef'), {})
  })
})

describe('weryfikacja kwalifikacji — reguły verificationRequests', () => {
  it('1. psycholog tworzy własne zgłoszenie ze statusem PENDING — przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
    }))
  })

  it('2. tworzenie zgłoszenia pod cudzym identyfikatorem jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/student'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
    }))
  })

  it('3. tworzenie zgłoszenia od razu ze statusem APPROVED jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'APPROVED', submittedAt: Date.now(),
    }))
  })

  it('4. pacjent tworzy zgłoszenie — odrzucone', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'verificationRequests/pacjent'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
    }))
  })

  it('5. student psychologii tworzy zgłoszenie — przechodzi', async () => {
    const db = authedDb(env, 'student')
    await assertSucceeds(setDoc(doc(db, 'verificationRequests/student'), {
      docImageBase64: 'YWJj', docType: 'INDEX', status: 'PENDING', submittedAt: Date.now(),
    }))
  })

  it('6. zgłoszenie z dopisanym reviewedBy jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      reviewedBy: 'psycholog',
    }))
  })

  it('7. zgłoszenie z dopisanym polem spoza modelu jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      pinned: true,
    }))
  })

  it('8. submittedAt w przyszłości o godzinę jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING',
      submittedAt: Date.now() + 60 * 60 * 1000,
    }))
  })

  it('9. obcy czyta cudze zgłoszenie — odrzucone', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'obcy')
    await assertFails(getDoc(doc(db, 'verificationRequests/psycholog')))
  })

  it('10. administrator czyta cudze zgłoszenie — przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(getDoc(doc(db, 'verificationRequests/psycholog')))
  })

  it('11. zgłaszający czyta własne zgłoszenie — przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(getDoc(doc(db, 'verificationRequests/psycholog')))
  })

  it('12. zgłaszający sam ustawia sobie status = APPROVED jest odrzucane', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'verificationRequests/psycholog'), { status: 'APPROVED' }))
  })

  it('13. zgłaszający po odrzuceniu wysyła ponownie (REJECTED → PENDING) — przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'REJECTED', submittedAt: 1,
        reviewedBy: 'szef', reviewedAt: 2, reviewNote: 'Nieczytelny skan',
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'bmV3', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      reviewedBy: '', reviewNote: '',
    }))
  })

  it('14. administrator zatwierdza zgłoszenie — przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'APPROVED', reviewedBy: 'szef', reviewedAt: Date.now(), reviewNote: '',
    }))
  })

  it('15. obcy usuwa zgłoszenie — odrzucone', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      })
    })
    const db = authedDb(env, 'obcy')
    await assertFails(deleteDoc(doc(db, 'verificationRequests/psycholog')))
  })

  // Dopisany po mutacji z Kroku 5: usunięcie dolnej granicy okna czasu
  // (submittedAt > now - 300000) nie zapalało żadnego z testów 1-15 — test 8
  // sprawdza tylko górną granicę (data w przyszłości). Bez tego testu dolna
  // granica byłaby deklarowana jako pokryta, a w rzeczywistości nie była.
  it('16. submittedAt sprzed 6 minut jest odrzucane', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'PENDING',
      submittedAt: Date.now() - 6 * 60 * 1000,
    }))
  })

  // Domknięcie E4-R3 (Zadanie 2): ścieżka wznowienia (REJECTED -> PENDING)
  // do tej pory nie miała listy dozwolonych pól — dało się dopisać dowolne
  // nieprzewidziane pole, ta sama dziura co przy tworzeniu, tylko innym
  // wejściem. tylkoDozwolonePola() jest teraz też na tej gałęzi allow update.
  it('17. wznowienie z dodatkowym polem spoza listy jest odrzucane', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'YWJj', docType: 'DIPLOMA', status: 'REJECTED', submittedAt: 1,
        reviewedBy: 'szef', reviewedAt: 2, reviewNote: 'Nieczytelny skan',
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'bmV3', docType: 'DIPLOMA', status: 'PENDING', submittedAt: Date.now(),
      reviewedBy: '', reviewedAt: 0, reviewNote: '', pinned: true,
    }))
  })
})
