package com.example.stellare.ui

import com.example.stellare.data.model.CurrentUserRead
import com.example.stellare.data.model.UserModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.runningFold

object CurrentUserReads {

    /**
     * Nieudany odczyt nie kasuje ostatniego znanego profilu — ekrany, które na nim stoją,
     * zgasłyby przy chwilowej awarii, a o samej awarii mówi [failureMessage] osobnym komunikatem.
     * Nieobecność i wylogowanie kasują, bo wtedy nie ma czego pokazywać; i to one, nie awaria,
     * przestawiają nawigację.
     */
    fun kept(previous: UserModel?, read: CurrentUserRead): UserModel? = when (read) {
        is CurrentUserRead.Loaded -> read.user
        is CurrentUserRead.Unavailable -> previous
        CurrentUserRead.Absent, CurrentUserRead.SignedOut -> null
    }

    fun failureMessage(read: CurrentUserRead): String? = when (read) {
        is CurrentUserRead.Unavailable ->
            UserFacingError.message(read.error, UserFacingError.PROFILE_READ_FAILED)
        is CurrentUserRead.Loaded, CurrentUserRead.Absent, CurrentUserRead.SignedOut -> null
    }
}

fun Flow<CurrentUserRead>.lastKnownUser(): Flow<UserModel?> =
    runningFold<CurrentUserRead, UserModel?>(null, CurrentUserReads::kept).distinctUntilChanged()
