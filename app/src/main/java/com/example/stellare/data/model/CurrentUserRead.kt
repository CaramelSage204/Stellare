package com.example.stellare.data.model

/**
 * [Absent] i [Unavailable] muszą zostać osobne, bo tylko [Absent] wolno zamienić na zaproszenie
 * do założenia profilu. Zalogowany psycholog z pustą pamięcią podręczną i bez sieci dostawał
 * ekran "Uzupełnij profil", a wypełniony formularz nadpisałby jego prawdziwy profil po powrocie
 * sieci: nieudany odczyt był rysowany jako "nie masz profilu".
 *
 * [SignedOut] to brak zalogowanego konta — jedyna wartość, która wolno odesłać na ekran powitalny.
 */
sealed interface CurrentUserRead {
    data class Loaded(val user: UserModel) : CurrentUserRead
    data object SignedOut : CurrentUserRead
    data object Absent : CurrentUserRead
    data class Unavailable(val error: Throwable) : CurrentUserRead
}
