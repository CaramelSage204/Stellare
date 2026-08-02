package com.example.ui.navigation

sealed class Screen {
    object Onboarding : Screen()
    object Registration : Screen()
    object Login : Screen()
    object MainDashboard : Screen()
    data class PsychologistDetail(val psychologistId: Int) : Screen()
    data class ChatRoom(val chatId: Int) : Screen()
    object ChatList : Screen()
    object MyRatings : Screen()
    object MyClients : Screen()
    object ProfileEdit : Screen()
    object FAQ : Screen()
    object Calendar : Screen()
    object Wallet : Screen()
}
