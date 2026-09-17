import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { initializeTestEnvironment } from '@firebase/rules-unit-testing'

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
