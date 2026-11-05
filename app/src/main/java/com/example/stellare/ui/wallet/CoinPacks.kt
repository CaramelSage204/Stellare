package com.example.stellare.ui.wallet

val CoinPacks: List<CoinPack> = listOf(
    CoinPack("Pakiet Mały", 50, "50,00 PLN", "Idealny na start i przetestowanie wizyty"),
    CoinPack("Pakiet Standardowy", 100, "100,00 PLN", "Najbardziej popularny pakiet"),
    CoinPack("Pakiet Profesjonalny", 250, "250,00 PLN", "Najlepsza wartość i wygoda")
)

data class CoinPack(
    val name: String,
    val coins: Int,
    val price: String,
    val description: String
)
