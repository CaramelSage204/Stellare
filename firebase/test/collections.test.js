import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest'
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing'
import { deleteDoc, deleteField, doc, getDoc, setDoc, updateDoc, writeBatch } from 'firebase/firestore'
import { authedDb, odrzucone, seed, setupEnv, unauthedDb , zalozUzytkownika } from './helpers.js'

let env

beforeAll(async () => { env = await setupEnv() })
afterAll(async () => { await env.cleanup() })
beforeEach(async () => {
  await env.clearFirestore()
  await seed(env, async (db) => {
    await zalozUzytkownika(db, 'users/psycholog', {
      id: 'psycholog', role: 'PSYCHOLOGIST', isVerified: true, isAdmin: false,
      coinsBalance: 0, pricePerSession: 80,
    })
    await zalozUzytkownika(db, 'users/pacjent', {
      id: 'pacjent', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 100,
    })
    // pricePerSession dopisane po Etapie 3, Zadaniu 1: reguła posts wiąże
    // priceCoins ogłoszenia z pricePerSession autora (cenaZgodnaZProfilem);
    // bez tego pola aktualizacja ogłoszenia intruza konczyla się bledem
    // ewaluacji, a nie zamierzonym zachowaniem, ktore te testy sprawdzaja.
    await zalozUzytkownika(db, 'users/intruz', {
      id: 'intruz', role: 'PATIENT', isVerified: false, isAdmin: false, coinsBalance: 0,
      pricePerSession: 80,
    })
    await setDoc(doc(db, 'admins/szef'), {})
    await setDoc(doc(db, 'completions/psycholog_pacjent'), {
      psychologistId: 'psycholog', patientId: 'pacjent', appointmentId: 'wizyta1', completedAt: 1,
    })
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
  it('psycholog prowadzacy czat tworzy notatke', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'notes/n1'), {
      noteId: 'n1', chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'praca domowa',
    }))
  })

  it('pacjent nie tworzy notatki, nawet z wlasnym authorId', async () => {
    const db = authedDb(env, 'pacjent')
    await assertFails(setDoc(doc(db, 'notes/np'), {
      noteId: 'np', chatId: 'czat1', authorId: 'pacjent', title: 'Dziennik', content: 'moje',
    }))
  })

  it('osoba spoza czatu nie tworzy notatki', async () => {
    const db = authedDb(env, 'intruz')
    await assertFails(setDoc(doc(db, 'notes/ni'), {
      noteId: 'ni', chatId: 'czat1', authorId: 'intruz', title: 'x', content: 'y',
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

  it('notatka sprzed dodania autora jest czytelna dla psychologa prowadzacego czat', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraNotatka'), {
        noteId: 'staraNotatka', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(getDoc(doc(db, 'notes/staraNotatka')))
  })

  it('pacjent nie odczyta notatki sprzed dodania autora', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/staraNotatka2'), {
        noteId: 'staraNotatka2', chatId: 'czat1',
        title: 'Sesja 0', content: 'sprzed migracji', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'notes/staraNotatka2')))
  })

  it('pacjent nie odczyta notatki z authorId psychologa', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/zAutorem'), {
        noteId: 'zAutorem', chatId: 'czat1', authorId: 'psycholog',
        title: 'Sesja 1', content: 'obserwacje', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(getDoc(doc(db, 'notes/zAutorem')))
  })

  it('notatki pacjenta sprzed zamkniecia create nie czyta juz nawet jej autor', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/wlasnaPacjenta'), {
        noteId: 'wlasnaPacjenta', chatId: 'czat1', authorId: 'pacjent',
        title: 'Dziennik', content: 'moje', timestamp: 1,
      })
    })
    await assertFails(getDoc(doc(authedDb(env, 'pacjent'), 'notes/wlasnaPacjenta')))
    await assertSucceeds(getDoc(doc(authedDb(env, 'psycholog'), 'notes/wlasnaPacjenta')))
  })

  it('notatki pacjenta sprzed zamkniecia create nie poprawi ani nie skasuje jej autor', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/wlasnaPacjenta2'), {
        noteId: 'wlasnaPacjenta2', chatId: 'czat1', authorId: 'pacjent',
        title: 'Dziennik', content: 'moje', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertFails(updateDoc(doc(db, 'notes/wlasnaPacjenta2'), { content: 'zmiana' }))
    await assertFails(deleteDoc(doc(db, 'notes/wlasnaPacjenta2')))
  })

  it('psycholog prowadzacy poprawi i skasuje notatke pacjenta sprzed zamkniecia create', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/wlasnaPacjenta3'), {
        noteId: 'wlasnaPacjenta3', chatId: 'czat1', authorId: 'pacjent',
        title: 'Dziennik', content: 'moje', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/wlasnaPacjenta3'), { content: 'zmiana' }))
    await assertSucceeds(deleteDoc(doc(db, 'notes/wlasnaPacjenta3')))
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
    b.set(doc(db, 'ratings/psycholog'), { sum: 4, count: 1 })
    await assertSucceeds(b.commit())
  })

  it('edycja wlasnej recenzji nie podbija oceny po raz drugi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), {
        reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
      })
      await setDoc(doc(s, 'ratings/psycholog'), { sum: 4, count: 1 })
    })
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.update(doc(db, 'reviews/psycholog_pacjent'), { rating: 5, comment: 'poprawiona' })
    b.update(doc(db, 'ratings/psycholog'), { sum: 9, count: 2 })
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

  describe('edycja istniejacej recenzji', () => {
    const recenzjaAutora = {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
    }

    beforeEach(async () => {
      await seed(env, async (s) => {
        await setDoc(doc(s, 'reviews/psycholog_pacjent'), recenzjaAutora)
        await setDoc(doc(s, 'ratings/psycholog'), { sum: 4, count: 1 })
      })
    })

    it('zapis czesciowy tresci (update) przechodzi i ocena zostaje', async () => {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'nowa tresc' }))
    })

    it('zapis z merge samej tresci przechodzi, bo dokument wynikowy dalej ma ocene', async () => {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(setDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'nowa tresc' }, { merge: true }))
    })

    it('nadpisanie calego dokumentu bez oceny dostaje odmowe', async () => {
      const db = authedDb(env, 'pacjent')
      const { rating, ...bezOceny } = recenzjaAutora
      await assertFails(setDoc(doc(db, 'reviews/psycholog_pacjent'), { ...bezOceny, comment: 'nowa tresc' }))
    })

    it('nadpisanie calego dokumentu z ta sama ocena przechodzi (kontrola: odmowa wyzej dotyczy braku oceny)', async () => {
      const db = authedDb(env, 'pacjent')
      await assertSucceeds(setDoc(doc(db, 'reviews/psycholog_pacjent'), { ...recenzjaAutora, comment: 'nowa tresc' }))
    })

    it('zmiana oceny w edycji dostaje odmowe, w gore i w dol', async () => {
      const db = authedDb(env, 'pacjent')
      await assertFails(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { rating: 5 }))
      await assertFails(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { rating: 1 }))
      await assertFails(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { rating: 5, comment: 'nowa tresc' }))
    })

    it('usuniecie oceny z recenzji przez deleteField dostaje odmowe', async () => {
      const db = authedDb(env, 'pacjent')
      await assertFails(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { rating: deleteField() }))
    })

    it('cudza recenzja nie daje sie edytowac', async () => {
      const db = authedDb(env, 'intruz')
      await assertFails(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'wandal' }))
    })
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
    await seed(env, async (s) => {
      await setDoc(doc(s, 'ratings/psycholog'), { sum: 0, count: 0 })
    })
    const db = authedDb(env, 'intruz')
    await assertFails(updateDoc(doc(db, 'ratings/psycholog'), {
      sum: 1, count: 1,
    }))
  })

  it('nie da sie podbic licznika ocen o wiecej niz jeden', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'x', timestamp: 1,
    })
    b.set(doc(db, 'ratings/psycholog'), { sum: 5, count: 500 })
    await assertFails(b.commit())
  })

  it('nie da sie wystawic oceny spoza skali', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 99, comment: 'x', timestamp: 1,
    })
    b.set(doc(db, 'ratings/psycholog'), { sum: 99, count: 1 })
    await assertFails(b.commit())
  })

  it('przy okazji oceny nie da sie przemycic zmiany salda', async () => {
    const db = authedDb(env, 'pacjent')
    const b = writeBatch(db)
    b.set(doc(db, 'reviews/psycholog_pacjent'), {
      reviewId: 'psycholog_pacjent', psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 5, comment: 'x', timestamp: 1,
    })
    b.set(doc(db, 'ratings/psycholog'), { sum: 5, count: 1 })
    b.update(doc(db, 'users/psycholog/private/contact'), { coinsBalance: 999999 })
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

// Zadanie 12 — identyfikator dokumentu jedyną tożsamością czatu i wiadomości.
// Ta sama klasa błędu, którą zadanie 11 zmierzyło na ogłoszeniach (R1): pole
// identyfikatora pisze klient, a nic nie wiązało go z identyfikatorem dokumentu.
// Kontrole pozytywne stoją wyżej w tym pliku i nie są tu powtarzane:
// „legalny czat we dwoje zakladany przez strone przechodzi" (tworzenie czatu),
// „uczestnik moze zaktualizowac ostatnia wiadomosc" (edycja czatu),
// „uczestnik wysyła wiadomość jako on sam" (tworzenie wiadomości) — wszystkie
// trzy zapisują pole identyfikatora równe identyfikatorowi dokumentu.
describe('czaty i wiadomości — pole identyfikatora musi być identyfikatorem dokumentu (zadanie 12)', () => {
  it('czat z polem chatId innym niż identyfikator dokumentu jest odrzucany', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'dwa czaty twierdzą, że są tym samym czatem: lista rozmów dostaje powtórzony klucz,'
        + ' a wysłanie wiadomości aktualizuje podstawiony dokument zamiast prawdziwej rozmowy',
      setDoc(doc(db, 'chats/prawdziwyIdentyfikator'), {
        chatId: 'czat1', psychologistId: 'psycholog', patientId: 'pacjent',
        participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
      })
    )
  })

  it('edycja czatu, którego pole chatId kłamie, jest odrzucana — nawet gdy nie dotyczy tego pola', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'chats/czatKlamliwy'), {
        chatId: 'czat1', psychologistId: 'psycholog', patientId: 'pacjent',
        participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
      })
    })
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'czat podający się za inny czat zostaje w kolekcji na stałe i dalej podstawia się'
        + ' ekranowi rozmowy zamiast prawdziwego dokumentu',
      updateDoc(doc(db, 'chats/czatKlamliwy'), { lastMessage: 'podstawione', lastMessageTime: 5 })
    )
  })

  it('wiadomość z polem messageId innym niż identyfikator dokumentu jest odrzucana', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'dwie wiadomości twierdzą, że są tą samą wiadomością: ekran rozmowy dostaje powtórzony'
        + ' klucz listy, a LazyColumn rzuca wyjątkiem przy powtórzonym kluczu',
      setDoc(doc(db, 'messages/prawdziwyIdentyfikator'), {
        messageId: 'podmieniony', chatId: 'czat1', senderId: 'pacjent',
        text: 'podszycie', timestamp: Date.now(),
      })
    )
  })
})

// Zadanie 12, runda poprawek 1 — notatki i recenzje.
// Notatki leżą na ścieżce usuwania konta: deleteNote() celuje w notes/<pole>, tak samo
// krok DeletionStep.DeleteNote, więc jedna rozjechana notatka przerywa usuwanie konta
// w połowie — objaw zmierzony w zadaniu 11 (R1) na ogłoszeniach.
// Recenzje: identyfikator dokumentu był już wiązany z parą psycholog_recenzent, ale samo
// pole reviewId było niczym nieograniczone.
describe('notatki — pole noteId musi być identyfikatorem dokumentu (zadanie 12, runda 1)', () => {
  const notatka = (noteId) => ({
    noteId, chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'tresc', timestamp: 1,
  })

  it('notatka z polem noteId innym niż identyfikator dokumentu jest odrzucana', async () => {
    const db = authedDb(env, 'psycholog')
    await odrzucone(
      'dwie notatki twierdzą, że są tą samą notatką: usunięcie notatki celuje w notes/podmieniony,'
        + ' a krok usuwania konta przerywa się w połowie na dokumencie, którego nie ma',
      setDoc(doc(db, 'notes/prawdziwyIdentyfikator'), notatka('podmieniony'))
    )
  })

  it('notatka z polem noteId równym identyfikatorowi dokumentu przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'notes/zgodnyIdentyfikator'), notatka('zgodnyIdentyfikator')))
  })

  it('edycja notatki, której pole noteId kłamie, jest odrzucana — choć edycja tego pola nie rusza', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/klamliwaNotatka'), notatka('n1'))
    })
    const db = authedDb(env, 'psycholog')
    await odrzucone(
      'notatka podająca się za inną notatkę zostaje w kolekcji na stałe i dalej blokuje usunięcie konta',
      updateDoc(doc(db, 'notes/klamliwaNotatka'), { content: 'uzupelniona tresc' })
    )
  })

  it('edycja notatki o zgodnym polu noteId nadal przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/zgodnaNotatka'), notatka('zgodnaNotatka'))
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/zgodnaNotatka'), { content: 'uzupelniona tresc' }))
  })
})

describe('recenzje — pole reviewId musi być identyfikatorem dokumentu (zadanie 12, runda 1)', () => {
  const recenzja = (reviewId) => ({
    reviewId, psychologistId: 'psycholog', reviewerId: 'pacjent',
    reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
  })

  it('recenzja z polem reviewId innym niż identyfikator dokumentu jest odrzucana', async () => {
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'dwie recenzje twierdzą, że są tą samą recenzją — każdy konsument reviewId bierze pole'
        + ' pisane przez klienta za tożsamość obiektu',
      setDoc(doc(db, 'reviews/psycholog_pacjent'), recenzja('podmieniony'))
    )
  })

  it('recenzja z polem reviewId równym identyfikatorowi dokumentu przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'reviews/psycholog_pacjent'), recenzja('psycholog_pacjent')))
  })

  it('edycja recenzji, której pole reviewId kłamie, jest odrzucana — choć edycja tego pola nie rusza', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), recenzja('podmieniony'))
    })
    const db = authedDb(env, 'pacjent')
    await odrzucone(
      'recenzja podająca się za inną recenzję zostaje w kolekcji na stałe i dalej daje się edytować',
      updateDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'poprawiona tresc' })
    )
  })

  it('edycja recenzji o zgodnym polu reviewId nadal przechodzi', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), recenzja('psycholog_pacjent'))
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'poprawiona tresc' }))
  })
})

// Zadanie 12, runda poprawek 2 — pole BRAKUJĄCE nie kłamie, więc przechodzi.
// Pełne uzasadnienie przy tej samej parze testów w posts.test.js i w komentarzu reguły
// (funkcja tozsamoscZIdentyfikatoraDokumentu w match /posts). Kontrolą negatywną dla każdej
// pary są testy „pole … kłamie → odmowa" stojące wyżej w tym pliku.
describe('czaty, wiadomości, notatki i recenzje — brak pola identyfikatora nie blokuje zapisu (zadanie 12, runda 2)', () => {
  it('czat BEZ pola chatId przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'chats/bezPolaChatId'), {
      psychologistId: 'psycholog', patientId: 'pacjent',
      participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
    }))
  })

  it('edycja czatu BEZ pola chatId przechodzi — wariant ścisły czynił go nieedytowalnym na zawsze', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'chats/czatBezPola'), {
        psychologistId: 'psycholog', patientId: 'pacjent',
        participants: ['psycholog', 'pacjent'], lastMessage: '', lastMessageTime: 0,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'chats/czatBezPola'), { lastMessage: 'nowa', lastMessageTime: 7 }))
  })

  it('wiadomość BEZ pola messageId przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'messages/bezPolaMessageId'), {
      chatId: 'czat1', senderId: 'pacjent', text: 'dzien dobry', timestamp: Date.now(),
    }))
  })

  it('notatka BEZ pola noteId przechodzi', async () => {
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(setDoc(doc(db, 'notes/bezPolaNoteId'), {
      chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'tresc', timestamp: 1,
    }))
  })

  it('edycja notatki BEZ pola noteId przechodzi — wariant ścisły czynił ją nieedytowalną na zawsze', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'notes/notatkaBezPola'), {
        chatId: 'czat1', authorId: 'psycholog', title: 'Sesja 1', content: 'tresc', timestamp: 1,
      })
    })
    const db = authedDb(env, 'psycholog')
    await assertSucceeds(updateDoc(doc(db, 'notes/notatkaBezPola'), { content: 'uzupelniona tresc' }))
  })

  it('recenzja BEZ pola reviewId przechodzi', async () => {
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(setDoc(doc(db, 'reviews/psycholog_pacjent'), {
      psychologistId: 'psycholog', reviewerId: 'pacjent',
      reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
    }))
  })

  it('edycja recenzji BEZ pola reviewId przechodzi — wariant ścisły czynił ją nieedytowalną na zawsze', async () => {
    await seed(env, async (s) => {
      await setDoc(doc(s, 'reviews/psycholog_pacjent'), {
        psychologistId: 'psycholog', reviewerId: 'pacjent',
        reviewerName: 'Bogdan', rating: 4, comment: 'pierwsza', timestamp: 1,
      })
    })
    const db = authedDb(env, 'pacjent')
    await assertSucceeds(updateDoc(doc(db, 'reviews/psycholog_pacjent'), { comment: 'poprawiona tresc' }))
  })
})
