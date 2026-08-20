package com.example.stellare.ui.navigation

sealed class Screen {
    object Onboarding : Screen()
    object Registration : Screen()
    object Login : Screen()
    object MainDashboard : Screen()
    data class PsychologistDetail(val psychologistId: String) : Screen()
    data class ChatRoom(val chatId: String) : Screen()
    object ChatList : Screen()
    object MyRatings : Screen()
    object MyClients : Screen()
    object ProfileEdit : Screen()
    object FAQ : Screen()
    object Calendar : Screen()
    object Wallet : Screen()
}
