package com.example.stellare.ui.settings

import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.PolishPlural

data class KeptItem(val what: String, val why: String)

object AccountDeletionDisclosure {

    const val INTRO = "Tej operacji nie da się cofnąć. Poniżej dokładnie, co zniknie, a co zostanie."

    const val OTHER_DATA_REASON = "Aplikacja nie usuwa tych danych."

    const val BALANCE_LOADING = "Saldo portfela: trwa sprawdzanie. Jeśli masz monety, przepadną bez zwrotu."
    const val BALANCE_UNAVAILABLE = "Saldo portfela: nie udało się go sprawdzić. Jeśli masz monety, przepadną bez zwrotu."
    const val BALANCE_MISSING = "Saldo portfela: nie znaleziono portfela, więc nie ma monet, które mogłyby przepaść."

    fun removed(role: UserRole, coinsBalance: Int): List<String> = removed(role, BalanceState.Known(coinsBalance))

    fun removed(role: UserRole, balance: BalanceState): List<String> = buildList {
        add("Twój profil: imię, nazwisko, numer telefonu, e-mail, opis i zdjęcie.")
        when (balance) {
            BalanceState.Loading -> add(BALANCE_LOADING)
            BalanceState.Unavailable -> add(BALANCE_UNAVAILABLE)
            BalanceState.Missing -> add(BALANCE_MISSING)
            is BalanceState.Known -> if (balance.coins > 0) {
                add(
                    "Saldo portfela: ${balance.coins} ${PolishPlural.form(balance.coins, "moneta", "monety", "monet")} " +
                        "przepadnie bez zwrotu."
                )
            }
        }
        add("Wszystkie Twoje ogłoszenia.")
        if (role != UserRole.PATIENT) {
            add("Notatki terapeutyczne, które prowadzisz.")
            add("Twoje wolne terminy: znikną z listy, więc nikt nie zarezerwuje ich już u Ciebie.")
            add("Zgłoszenie weryfikacyjne wraz ze zdjęciem dokumentu, jeśli je składałeś lub składałaś.")
        }
        add("Konto logowania: nie zalogujesz się już na to konto.")
    }

    const val PATIENT_COINS_LOST = "Monety wpłacone za nie przepadną: po usunięciu konta nie odzyskasz ich już w żaden sposób."
    const val PSYCHOLOGIST_COINS_RETURN =
        "Monety, które pacjenci wpłacili za te wizyty, wrócą do nich po upływie okna zwrotu (14 dni po terminie)."

    private fun bookedVisitsCoins(role: UserRole): String =
        if (role == UserRole.PATIENT) PATIENT_COINS_LOST else PSYCHOLOGIST_COINS_RETURN

    fun kept(role: UserRole): List<KeptItem> = buildList {
        add(
            KeptItem(
                "Wizyty zarezerwowane, także na przyszłość, odbyte i odwołane. Rezerwacji nie anulujemy. " +
                    bookedVisitsCoins(role),
                "To także historia drugiej strony. Przy Twoim nazwisku zobaczy ona „inny użytkownik”."
            )
        )
        add(
            KeptItem(
                "Opinie, które wystawiłeś lub wystawiłaś.",
                "Reguły pozwalają zmienić oceny psychologa tylko razem z nową opinią, " +
                    "więc usunięcie opinii rozjechałoby jego ocenę."
            )
        )
        if (role != UserRole.PATIENT) {
            add(
                KeptItem(
                    "Opinie i oceny, które wystawili Ci pacjenci.",
                    "To wypowiedzi pacjentów, nie Twoje dane: po usunięciu konta oceny Twojej pracy nadal będą widoczne."
                )
            )
        }
        add(
            KeptItem(
                "Historia operacji w portfelu.",
                "Księga jest niezmienna: nikt, także Ty, nie może jej wpisów usunąć."
            )
        )
        if (role == UserRole.PATIENT) {
            add(
                KeptItem(
                    "Notatki, które psychologowie prowadzili o Tobie.",
                    "Należą do psychologa, nie do Ciebie."
                )
            )
        }
        add(KeptItem("Wiadomości i rozmowy w czatach.", OTHER_DATA_REASON))
        add(KeptItem("Znaczniki odbytych wizyt.", OTHER_DATA_REASON))
        add(KeptItem("Lista ulubionych psychologów.", OTHER_DATA_REASON))
    }
}
