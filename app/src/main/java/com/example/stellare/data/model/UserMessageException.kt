package com.example.stellare.data.model

class UserMessageException(message: String) : IllegalStateException(message)

/**
 * Odczyt dokumentu użytkownika nie dał rozstrzygnięcia: albo nasłuch nie dostał jeszcze
 * odpowiedzi serwera, albo dokument jest, ale nie daje się odwzorować. Wyjątek **nie niesie
 * tekstu dla użytkownika** — ten jest jeden i należy do warstwy interfejsu
 * ([com.example.stellare.ui.UserFacingError.PROFILE_READ_FAILED]). Warstwa danych nie powtarza
 * zdań, które czyta człowiek, bo dwa egzemplarze tego samego zdania rozjeżdżają się przy
 * pierwszej korekcie.
 */
class ProfileNotConfirmedException : IllegalStateException("profil nie potwierdzony przez serwer")
