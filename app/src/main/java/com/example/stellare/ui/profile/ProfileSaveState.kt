package com.example.stellare.ui.profile

sealed interface ProfileSaveState {
    data object Idle : ProfileSaveState
    data object InProgress : ProfileSaveState
    data object Success : ProfileSaveState
    data class Error(val message: String) : ProfileSaveState

    /**
     * Zapis, który nie odpowiedział w limicie czasu. Firestore przyjmuje go lokalnie i wysyła,
     * gdy wróci połączenie — zmierzone: po 120 s bez sieci zapis nadal trwał, a po jej powrocie
     * wylądował na serwerze. Dlatego to nie jest [Error]: "nie udało się" byłoby nieprawdą.
     */
    data class Uncertain(val message: String) : ProfileSaveState
}