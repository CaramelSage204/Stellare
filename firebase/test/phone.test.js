import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteField, doc, setDoc, updateDoc } from 'firebase/firestore'
import { seed, setupEnv } from './helpers.js'

let env

const NUMER = '+48501234567'
const INNY_NUMER = '+48600700800'

function zNumerem(uid, numer) {
  return env.authenticatedContext(uid, { phone_number: numer }).firestore()
}

function bezNumeru(uid) {
  return env.authenticatedContext(uid).firestore()
}

async function odrzucone(opis, zapis) {
  try {
    await assertFails(zapis)
  } catch (e) {
    throw new Error('reguly powinny odrzucic: ' + opis + ', a zapis przeszedl')
  }
}

async function przyjete(opis, zapis) {
  try {
    await assertSucceeds(zapis)
  } catch (e) {
    throw new Error('reguly powinny przyjac: ' + opis + ', a zapis odrzucono: ' + String(e.message).split(String.fromCharCode(10))[0])
  }
}

function dokument(uid, phone) {
  return {
    coinsBalance: 0, email: uid + '@example.com', phone,
  }
}

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/jan/private/contact'), dokument('jan', ''))
    await setDoc(doc(db, 'users/stary/private/contact'), dokument('stary', '500600700'))
    await setDoc(doc(db, 'users/ewa/private/contact'), dokument('ewa', ''))
  })
})

describe('users — numer telefonu potwierdzony w tokenie', () => {
  it('1. wlasciciel z numerem w tokenie zapisuje dokladnie ten numer w profilu', async () => {
    await przyjete('numer z tokenu zapisany w profilu przez wlasciciela', updateDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), { phone: NUMER }))
  })

  it('2. wlasciciel BEZ numeru w tokenie nie zapisze zadnego numeru w profilu', async () => {
    await odrzucone('numer w profilu przy tokenie bez phone_number', updateDoc(doc(bezNumeru('jan'), 'users/jan/private/contact'), { phone: NUMER }))
  })

  it('3. numer w tokenie inny niz zapisywany jest odrzucony', async () => {
    await odrzucone('numer w profilu inny niz phone_number z tokenu', updateDoc(doc(zNumerem('jan', INNY_NUMER), 'users/jan/private/contact'), { phone: NUMER }))
  })

  it('4. zapis calego dokumentu (set) z numerem z tokenu przechodzi', async () => {
    await przyjete('zapis calego dokumentu z numerem z tokenu', setDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), dokument('jan', NUMER)))
  })

  it('5. zapis calego dokumentu (set) z numerem bez tokenu z numerem jest odrzucony', async () => {
    await odrzucone('zapis calego dokumentu z numerem przy tokenie bez phone_number', setDoc(doc(bezNumeru('jan'), 'users/jan/private/contact'), dokument('jan', NUMER)))
  })

  it('6. zalozenie konta z numerem wymaga tego numeru w tokenie', async () => {
    await odrzucone('zalozenie konta z numerem przy tokenie bez phone_number', setDoc(doc(bezNumeru('nowy'), 'users/nowy/private/contact'), dokument('nowy', NUMER)))
    await przyjete('zalozenie konta z numerem z tokenu', setDoc(doc(zNumerem('nowy', NUMER), 'users/nowy/private/contact'), dokument('nowy', NUMER)))
  })

  it('7. zalozenie konta bez numeru dalej przechodzi bez tokenu z numerem', async () => {
    await przyjete('zalozenie konta bez numeru przy tokenie bez phone_number', setDoc(doc(bezNumeru('nowy'), 'users/nowy/private/contact'), dokument('nowy', '')))
  })

  it('8. wlasciciel moze wyczyscic numer z profilu bez tokenu z numerem', async () => {
    await przyjete('wyczyszczenie dawnego numeru przez wlasciciela', updateDoc(doc(bezNumeru('stary'), 'users/stary/private/contact'), { phone: '' }))
  })

  it('9. dawny numer bez potwierdzenia zostaje, gdy wlasciciel edytuje inne pole', async () => {
    await przyjete('edycja innego pola przy dawnym numerze bez zmiany', updateDoc(doc(bezNumeru('stary'), 'users/stary/private/contact'), { email: 'nowy@example.com' }))
  })

  it('10. dawny numer bez potwierdzenia nie zmieni sie na inny niepotwierdzony', async () => {
    await odrzucone('zmiana dawnego numeru na inny niepotwierdzony', updateDoc(doc(bezNumeru('stary'), 'users/stary/private/contact'), { phone: '111222333' }))
  })

  it('11. numer nietekstowy w profilu jest odrzucony nawet przy numerze w tokenie', async () => {
    await odrzucone('numer zapisany jako liczba zamiast tekstu', updateDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), { phone: 48501234567 }))
  })

  it('12. usuniecie pola phone z profilu nie wymaga tokenu z numerem', async () => {
    await przyjete('usuniecie pola phone przez wlasciciela', updateDoc(doc(bezNumeru('stary'), 'users/stary/private/contact'), { phone: deleteField() }))
  })

  it('13. cudzy numer z tokenu nie pozwala zmienic telefonu w cudzym profilu', async () => {
    await odrzucone('zmiana telefonu w cudzym profilu', updateDoc(doc(zNumerem('ewa', NUMER), 'users/jan/private/contact'), { phone: NUMER }))
  })

  it('14. administrator nie zapisuje numeru w dokumencie prywatnym (runda poprawek 1: zapis administratora usuniety)', async () => {
    await seed(env, async (db) => { await setDoc(doc(db, 'admins/szef'), {}) })
    await odrzucone('administrator zmienil numer telefonu w cudzym dokumencie prywatnym', updateDoc(doc(bezNumeru('szef'), 'users/jan/private/contact'), { phone: INNY_NUMER }))
  })

  it('15. wlasciciel z potwierdzonym numerem w tokenie nie wyczysci numeru zapisem phone = pusty', async () => {
    await seed(env, async (db) => { await setDoc(doc(db, 'users/jan/private/contact'), dokument('jan', NUMER)) })
    await odrzucone('wyczyszczenie numeru przez posiadacza potwierdzonego numeru', updateDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), { phone: '' }))
  })

  it('16. posiadacz potwierdzonego numeru nie straci go przez zapis calego dokumentu ze starego modelu', async () => {
    await seed(env, async (db) => { await setDoc(doc(db, 'users/jan/private/contact'), dokument('jan', NUMER)) })
    await odrzucone('zapis calego dokumentu z pustym phone przy tokenie z numerem', setDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), dokument('jan', '')))
  })

  it('17. posiadacz potwierdzonego numeru nie usunie pola phone', async () => {
    await seed(env, async (db) => { await setDoc(doc(db, 'users/jan/private/contact'), dokument('jan', NUMER)) })
    await odrzucone('usuniecie pola phone przez posiadacza potwierdzonego numeru', updateDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), { phone: deleteField() }))
  })

  it('18. posiadacz potwierdzonego numeru edytuje inne pola bez ruszania numeru', async () => {
    await seed(env, async (db) => { await setDoc(doc(db, 'users/jan/private/contact'), dokument('jan', NUMER)) })
    await przyjete('edycja imienia przy zachowanym numerze', updateDoc(doc(zNumerem('jan', NUMER), 'users/jan/private/contact'), { email: 'janusz@example.com' }))
  })

  it('19. zalozenie konta z numerem innym niz phone_number w tokenie jest odrzucone', async () => {
    await odrzucone('zalozenie konta z numerem roznym od numeru w tokenie', setDoc(doc(zNumerem('nowy', INNY_NUMER), 'users/nowy/private/contact'), dokument('nowy', NUMER)))
  })
})
