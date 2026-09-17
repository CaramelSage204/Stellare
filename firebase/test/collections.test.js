import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteDoc, doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, seed, setupEnv, unauthedDb } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await setDoc(doc(db, 'users/psycholog'), {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, rating: 5.0, ratingCount: 0, pricePerSession: 80,
    })
    await setDoc(doc(db, 'users/pacjent'), {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 100,
    })
    // pricePerSession dopisane po Etapie 3, Zadaniu 1: reguła posts wiąże
    // priceCoins ogłoszenia z pricePerSession autora (cenaZgodnaZProfilem);
    // bez tego pola aktualizacja ogłoszenia intruza konczyla się bledem
    // ewaluacji, a nie zamierzonym zachowaniem, ktore te testy sprawdzaja.
    await setDoc(doc(db, 'users/intruz'), {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      pricePerSession: 80,
    })
    await setDoc(doc(db, 'admins/szef'), {})
    await setDoc(doc(db, 'chats/czat1'), {
      chatId: 'czat1', psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
    })
  })
})

describe('czaty i wiadomości', () => {
  it('uczestnik czyta swój czat', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(getDoc(doc(db, 'chats/czat1')))
  })

  it('osoba spoza czatu go nie czyta', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'chats/czat1')))
  })

  it('uczestnik wysyła wiadomość jako on sam', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'messages/w1'), {
      messageId: 'w1', chatId: 'czat1', senderId: 'pacjent', text: 'dzień dobry', timestamp: Date.now(),
    }))
  })

  it('nie da się podszyć pod nadawcę', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'messages/w2'), {
      messageId: 'w2', chatId: 'czat1', senderId: 'psycholog', text: 'podszycie', timestamp: Date.now(),
    }))
  })

  it('obcy nie wyśle wiadomości do cudzego czatu', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'messages/w3'), {
      messageId: 'w3', chatId: 'czat1', senderId: 'intruz', text: 'wtręt', timestamp: Date.now(),
    }))
  })

  it('uczestnik nie moze dopisac intruza do listy uczestnikow czatu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'chats/czat1'), {
      participants: ['psycholog', 'pacjent', 'intruz'],
    }))
  })

  it('uczestnik nie moze usunac drugiej strony z czatu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'chats/czat1'), {
      participants: ['pacjent'],
    }))
  })

  it('uczestnik moze zaktualizowac ostatnia wiadomosc', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'chats/czat1'), {
      lastMessage: 'nowa wiadomosc', lastMessageTime: 123,
    }))
  })

  it('aktualizacja czatu zmienia wylacznie ostatnia wiadomosc', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'chats/czat1'), {
      lastMessage: 'nowa', lastMessageTime: 99,
    }))
  })

  it('nie da sie utworzyc czatu z dwiema obcymi osobami', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'chats/podszyty'), {
      chatId: 'podszyty', psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'pacjent', 'intruz'], lastMessage: '', lastMessageTime: 0,
    }))
  })

  it('nie da sie utworzyc czatu opisanego cudza para psycholog-pacjent', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'chats/podszyty2'), {
      chatId: 'podszyty2', psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'intruz'], lastMessage: '', lastMessageTime: 0,
    }))
  })

  it('legalny czat we dwoje zakladany przez strone przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'chats/nowy'), {
      chatId: 'nowy', psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'pacjent'], lastMessage: 'Rozpoczeto nowy czat.', lastMessageTime: 1,
    }))
  })
})

describe('notatki do sesji', () => {
  it('uczestnik czatu zapisuje notatkę', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'notes/n1'), {
      noteId: 'n1', chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'praca domowa',
    }))
  })

  it('obcy nie czyta cudzych notatek', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'notes/n1'), {
        noteId: 'n1', chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'praca domowa',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'notes/n1')))
  })

  it('nie da sie przeniesc notatki do cudzego czatu', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'chats/czatIntruza'), {
        chatId: 'czatIntruza', psychologistId: 'psycholog', patientId: 'intruz',
        participants: ['psycholog', 'intruz'], lastMessage: '', lastMessageTime: 0,
      })
      await setDoc(doc(s, 'notes/notatkaIntruza'), {
        noteId: 'notatkaIntruza', chatId: 'czatIntruza', title: 'Wstrzyk', content: 'tresc', timestamp: 1,
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'notes/notatkaIntruza'), { chatId: 'czat1' }))
  })

  it('uczestnik moze poprawic tresc notatki we wlasnym czacie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/n2'), {
        noteId: 'n2', chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'stara', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/n2'), { content: 'poprawiona' }))
  })

  it('psycholog odczyta i skasuje wlasna notatke', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(getDoc(doc(db, 'notes/klinicznaNotatka')))
    await assertSucceeds(deleteDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('notatka sprzed dodania autora pozostaje czytelna dla uczestnikow', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraNotatka'), {
        noteId: 'staraNotatka', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(getDoc(doc(db, 'notes/staraNotatka')))
  })

  it('psycholog moze skasowac notatke sprzed migracji we wlasnym czacie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraBezAutora'), {
        noteId: 'staraBezAutora', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(deleteDoc(doc(db, 'notes/staraBezAutora')))
  })

  it('pacjent nie skasuje notatki sprzed migracji', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraBezAutora2'), {
        noteId: 'staraBezAutora2', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(deleteDoc(doc(db, 'notes/staraBezAutora2')))
  })

  it('nie da sie przepisac cudzej notatki na siebie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/cudza'), {
        noteId: 'cudza', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'notes/cudza'), { authorId: 'pacjent' }))
  })

  it('autor nie przepisze wlasnej notatki na kogos innego', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/mojaNotatka'), {
        noteId: 'mojaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'notes/mojaNotatka'), { authorId: 'pacjent' }))
  })

  it('autor moze poprawic tresc wlasnej notatki', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/mojaNotatka2'), {
        noteId: 'mojaNotatka2', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'stara tresc', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/mojaNotatka2'), { content: 'poprawiona tresc' }))
  })

  it('pacjent nie odczyta notatki psychologa o sobie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('pacjent nie skasuje notatki psychologa o sobie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klinicznaNotatka'), {
        noteId: 'klinicznaNotatka', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje kliniczne', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(deleteDoc(doc(db, 'notes/klinicznaNotatka')))
  })

  it('psycholog moze poprawic tresc notatki sprzed migracji we wlasnym czacie', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraDoEdycji'), {
        noteId: 'staraDoEdycji', chatId: 'czat1', title: 'Sesja 0',
        content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/staraDoEdycji'), { content: 'uzupelniona tresc' }))
  })

  it('autor nie przeniesie wlasnej notatki do innego czatu', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'chats/czatDrugi'), {
        chatId: 'czatDrugi', psychologistId: 'psycholog', patientId: 'intruz',
        participants: ['psycholog', 'intruz'], lastMessage: '', lastMessageTime: 0,
      })
      await setDoc(doc(s, 'notes/mojaNotatka3'), {
        noteId: 'mojaNotatka3', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'tresc', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'notes/mojaNotatka3'), { chatId: 'czatDrugi' }))
  })

  it('nie da sie utworzyc notatki podpisanej cudzym autorem', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'notes/podszyta'), {
      noteId: 'podszyta', chatId: 'czat1', authorId: 'psycholog',
      title: 'Podszycie', content: 'falszywy wpis',
    }))
  })
})

describe('recenzje', () => {
  it('zalogowany wystawia recenzję jako on sam', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'polecam',
    }))
  })

  it('nie da się wystawić recenzji w cudzym imieniu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'reviews/psycholog_intruz'), {
      reviewId: 'psycholog_intruz', psychologistId: 'psycholog', reviewerId: 'intruz',
      reviewerName: 'Intruz', rating: 1, comment: 'fałszywka',
    }))
  })

  it('recenzje są publiczne dla zalogowanych', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'reviews/psycholog_pacjent'), {
        reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 5, comment: 'polecam',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertSucceeds(getDoc(doc(db, 'reviews/psycholog_pacjent')))
  })

  it('recenzja wraz z podbiciem oceny w jednym zatwierdzeniu przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 4, comment: 'polecam', timestamp: 1,
    })
    b.update(doc(db, 'users/psycholog'), { rating: 4.5, ratingCount: 1 })
    await assertSucceeds(b.commit())
  })

  it('edycja wlasnej recenzji nie podbija oceny po raz drugi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), {
        reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
      })
      await updateDoc(doc(s, 'users/psycholog'), { rating: 4.0, ratingCount: 1 })
    })
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'reviews/psycholog_pacjent'), { rating: 5, comment: 'poprawiona' })
    b.update(doc(db, 'users/psycholog'), { rating: 4.5, ratingCount: 2 })
    await assertFails(b.commit())
  })

  it('autor moze poprawic tresc wlasnej recenzji bez ruszania oceny', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), {
        reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'reviews/psycholog_pacjent'), {
      comment: 'poprawiona tresc',
    }))
  })

  it('nie da sie skasowac wlasnej recenzji', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), {
        reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 5, comment: 'pierwsza', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(deleteDoc(doc(db, 'reviews/psycholog_pacjent')))
  })

  it('obcy nie moze zmienic oceny psychologa bez wystawienia recenzji', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'users/psycholog'), {
      rating: 1, ratingCount: 1,
    }))
  })

  it('nie da sie podbic licznika ocen o wiecej niz jeden', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'x', timestamp: 1,
    })
    b.update(doc(db, 'users/psycholog'), { rating: 5, ratingCount: 500 })
    await assertFails(b.commit())
  })

  it('nie da sie wystawic oceny spoza skali', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'x', timestamp: 1,
    })
    b.update(doc(db, 'users/psycholog'), { rating: 99, ratingCount: 1 })
    await assertFails(b.commit())
  })

  it('przy okazji oceny nie da sie przemycic zmiany salda', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'x', timestamp: 1,
    })
    b.update(doc(db, 'users/psycholog'), { rating: 5, ratingCount: 1, coinsBalance: 999999 })
    await assertFails(b.commit())
  })
})

describe('zgłoszenia weryfikacyjne', () => {
  it('psycholog składa własne zgłoszenie jako oczekujące', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
      submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
    }))
  })

  it('nie da się złożyć zgłoszenia od razu zatwierdzonego', async () => {
    const db = authedDb(env, 'psycholog')
    await assertFails(setDoc(doc(db, 'verificationRequests/psycholog'), {
      docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'APPROVED',
      submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
    }))
  })

  it('obcy nie czyta cudzego dokumentu', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
        submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'verificationRequests/psycholog')))
  })

  it('administrator czyta i rozstrzyga', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
        submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
      })
    })
    const db = authedDb(env, 'szef')
    await assertSucceeds(getDoc(doc(db, 'verificationRequests/psycholog')))
    await assertSucceeds(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'REJECTED', reviewedBy: 'szef', reviewedAt: Date.now(), reviewNote: 'nieczytelny skan',
    }))
  })

  it('nie da się samodzielnie zatwierdzić własnego zgłoszenia', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'PENDING',
        submittedAt: Date.now(), reviewedBy: '', reviewedAt: 0, reviewNote: '',
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'APPROVED', reviewedBy: 'psycholog', reviewedAt: Date.now(), reviewNote: '',
    }))
  })

  it('nie da się zmodyfikować zgłoszenia już rozpatrzonego', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'APPROVED',
        submittedAt: Date.now(), reviewedBy: 'szef', reviewedAt: Date.now(), reviewNote: 'ok',
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'PENDING',
    }))
  })

  it('wznowienie odrzuconego zgloszenia czysci slad rozstrzygniecia', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'verificationRequests/psycholog'), {
        docImageBase64: 'AAAA', docType: 'DYPLOM', status: 'REJECTED',
        submittedAt: 1, reviewedBy: 'szef', reviewedAt: 2, reviewNote: 'nieczytelny skan',
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertFails(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'PENDING', reviewedBy: 'ktos inny', reviewNote: 'zmyslona notatka',
    }))
    await assertSucceeds(updateDoc(doc(db, 'verificationRequests/psycholog'), {
      status: 'PENDING', reviewedBy: '', reviewNote: '',
    }))
  })
})

describe('ulubione', () => {
  it('właściciel dodaje własny wpis ulubionych', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'favorites/pacjent_psycholog'), {
      userId: 'pacjent', psychologistId: 'psycholog',
    }))
  })

  it('obcy nie czyta cudzych ulubionych', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'favorites/pacjent_psycholog'), {
        userId: 'pacjent', psychologistId: 'psycholog',
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(getDoc(doc(db, 'favorites/pacjent_psycholog')))
  })

  it('obcy nie tworzy wpisu w cudzym imieniu', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'favorites/pacjent_psycholog'), {
      userId: 'pacjent', psychologistId: 'psycholog',
    }))
  })

  it('nie da sie przepisac wlasnego ulubionego na cudze konto', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'favorites/pacjent_psycholog'), {
        userId: 'pacjent', psychologistId: 'psycholog',
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'favorites/pacjent_psycholog'), { userId: 'intruz' }))
  })

  it('obcy nie zajmie identyfikatora ulubionych ofiary', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'favorites/pacjent_psycholog'), {
      userId: 'intruz', psychologistId: 'psycholog',
    }))
  })

  it('wlasciciel dalej doda i usunie swoj wpis ulubionych', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'favorites/pacjent_psycholog'), {
      userId: 'pacjent', psychologistId: 'psycholog',
    }))
    await assertSucceeds(deleteDoc(doc(db, 'favorites/pacjent_psycholog')))
  })

  it('nie da sie utworzyc wpisu ulubionych o niezgodnym identyfikatorze', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'favorites/cokolwiek'), {
      userId: 'pacjent', psychologistId: 'psycholog',
    }))
  })
})

describe('ogłoszenia', () => {
  it('autor publikuje własne ogłoszenie', async () => {
    const db = authedDb(env, 'psycholog')
    // authorSnapshot.isVerified musi zgadzać się z users/psycholog (isVerified: true) —
    // reguła posts to wymusza (snapshotPrawdziwy), pusty snapshot już nie wystarcza.
    await assertSucceeds(setDoc(doc(db, 'posts/p1'), {
      id: 'p1', authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Terapia poznawczo-behawioralna', body: 'Zapraszam', tags: ['LEK'],
      priceCoins: 80, createdAt: Date.now(), isActive: true,
      authorSnapshot: { isVerified: true },
    }))
  })

  it('nie da się opublikować ogłoszenia w cudzym imieniu', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'posts/p2'), {
      id: 'p2', authorId: 'psycholog', type: 'OFFERING_THERAPY',
      title: 'Podszycie', body: '', tags: [], priceCoins: 0,
      createdAt: Date.now(), isActive: true, authorSnapshot: {},
    }))
  })

  it('obcy nie edytuje cudzego ogłoszenia', async () => {
    await seed(env, async (db) => {
      await setDoc(doc(db, 'posts/p1'), {
        id: 'p1', authorId: 'psycholog', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: '', tags: [], priceCoins: 80,
        createdAt: Date.now(), isActive: true, authorSnapshot: {},
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'posts/p1'), { title: 'Przejęte' }))
  })

  it('nie da sie przepisac wlasnego ogloszenia na cudze konto', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/mojePost'), {
        id: 'mojePost', authorId: 'intruz', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: '', tags: [], priceCoins: 80,
        createdAt: 1, isActive: true, authorSnapshot: {},
      })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'posts/mojePost'), { authorId: 'psycholog' }))
  })

  it('autor moze poprawic tresc wlasnego ogloszenia', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'posts/mojePost'), {
        id: 'mojePost', authorId: 'intruz', type: 'OFFERING_THERAPY',
        title: 'Terapia', body: '', tags: [], priceCoins: 80,
        createdAt: 1, isActive: true, authorSnapshot: {},
      })
    })
    const db = authedDb(env, 'intruz')
    await assertSucceeds(updateDoc(doc(db, 'posts/mojePost'), { title: 'Nowy tytul' }))
  })
})

describe('blokada domyślna', () => {
  it('niezalogowany nie czyta niczego', async () => {
    const db = unauthedDb(env)
    await assertFails(getDoc(doc(db, 'users/psycholog')))
  })

  it('nieznana kolekcja jest zablokowana', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'cokolwiek/x'), { a: 1 }))
  })
})
