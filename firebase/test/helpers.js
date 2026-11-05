import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { assertFails, initializeTestEnvironment } from '@firebase/rules-unit-testing'
import { doc, setDoc } from 'firebase/firestore'

const here = path.dirname(fileURLToPath(import.meta.url))
const rulesPath = path.join(here, '..', 'firestore.rules')

export async function setupEnv() {
  return initializeTestEnvironment({
    projectId: 'stellare-test',
    firestore: {
      rules: fs.readFileSync(rulesPath, 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  })
}

export function authedDb(env, uid) {
  return env.authenticatedContext(uid).firestore()
}

export function unauthedDb(env) {
  return env.unauthenticatedContext().firestore()
}

export function seed(env, fn) {
  return env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()))
}

export const POLA_PRYWATNE = ['phone', 'email', 'coinsBalance']

export function rozdzielProfil(dane) {
  const publiczne = {}
  const prywatne = {}
  for (const [k, v] of Object.entries(dane)) {
    if (POLA_PRYWATNE.includes(k)) prywatne[k] = v
    else publiczne[k] = v
  }
  return { publiczne, prywatne }
}

export async function zalozUzytkownika(db, sciezka, dane) {
  const { publiczne, prywatne } = rozdzielProfil(dane)
  await setDoc(doc(db, sciezka), publiczne)
  await setDoc(doc(db, sciezka + '/private/contact'), { coinsBalance: 0, ...prywatne })
}

export async function sumaMonetWSystemie(env, { collectionGroup, collection, getDocs }) {
  let suma = 0
  await seed(env, async (db) => {
    for (const d of (await getDocs(collection(db, 'users'))).docs) {
      if (Object.hasOwn(d.data(), 'coinsBalance')) {
        throw new Error('sumaMonet: saldo zostalo w publicznym users/' + d.id + ' - pomiar czesciowy')
      }
    }
    for (const d of (await getDocs(collectionGroup(db, 'private'))).docs) suma += d.data().coinsBalance ?? 0
    for (const d of (await getDocs(collection(db, 'escrows'))).docs) {
      if (d.data().status === 'HELD') suma += d.data().amount
    }
  })
  return suma
}

export async function odrzucone(szkoda, zapis) {
  try {
    await assertFails(zapis)
  } catch (e) {
    throw new Error('REGULY PRZEPUSCILY ATAK: ' + szkoda)
  }
}
