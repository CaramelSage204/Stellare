package com.example.stellare.data.remote

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.ProfileNotConfirmedException
import com.example.stellare.data.model.UserModel

/**
 * Firestore podaje "serwer potwierdza, że dokumentu nie ma" i "pamięć podręczna nic o nim nie wie"
 * jako tę samą migawkę z `exists() == false`. Odróżnia je dopiero `isFromCache`, i to w czasie,
 * nie w pojedynczej migawce — dlatego potwierdzenie serwera trzyma nasłuch: raz potwierdzona
 * nieobecność zostaje nieobecnością także w migawkach z pamięci podręcznej, bo inaczej utrata
 * sieci w trakcie wypełniania formularza wyrzucałaby użytkownika z tego formularza.
 *
 * Bez tego rozróżnienia pierwszy start bez sieci i z czystą pamięcią podręczną kończy się
 * zaproszeniem do założenia profilu, a założony profil nadpisuje prawdziwy po powrocie sieci.
 *
 * Istniejący dokument, którego nie udało się odwzorować na [UserModel], też nie jest
 * nieobecnością: o takim profilu wiadomo, że jest, więc nie wolno go zaproponować do nadpisania.
 *
 * Nasłuch zakłada się z [com.google.firebase.firestore.MetadataChanges.INCLUDE], bo przejście
 * tej samej nieobecności z pamięci podręcznej na potwierdzenie serwera nie zmienia danych
 * dokumentu — przy domyślnym EXCLUDE nie przyszłaby żadna migawka i [Absent] byłby nieosiągalny
 * do ponownego założenia nasłuchu.
 */
class UserDocumentWatch {

    private var serverAnswered = false

    fun read(user: UserModel?, exists: Boolean, fromCache: Boolean, error: Throwable?): CurrentUserRead {
        if (error != null) return CurrentUserRead.Unavailable(error)
        if (!fromCache) serverAnswered = true
        return when {
            user != null -> CurrentUserRead.Loaded(user)
            !exists && serverAnswered -> CurrentUserRead.Absent
            else -> CurrentUserRead.Unavailable(ProfileNotConfirmedException())
        }
    }
}
