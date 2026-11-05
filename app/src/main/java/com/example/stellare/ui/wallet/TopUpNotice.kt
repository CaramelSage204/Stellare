package com.example.stellare.ui.wallet

object TopUpNotice {
    const val NO_PAYMENT = "Ta wersja przyznaje monety bez pobierania płatności."
    const val PACK_VALUE_LABEL = "wartość"

    fun packValueDescription(pack: CoinPack): String = "$PACK_VALUE_LABEL ${pack.price}"
}
