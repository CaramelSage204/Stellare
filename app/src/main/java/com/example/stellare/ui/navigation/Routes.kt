package com.example.stellare.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTRATION = "registration"

    const val FEED = "feed"
    const val CALENDAR = "calendar"
    const val CHATS = "chats"
    const val WALLET = "wallet"

    const val PROFILE_EDIT = "profile/edit"
    const val MY_RATINGS = "ratings"
    const val MY_CLIENTS = "clients"
    const val FAQ = "faq"
    const val POST_COMPOSER = "posts/new"
    const val VERIFICATION = "verification"
    const val VERIFICATION_ADMIN = "verification/admin"

    private const val PSYCHOLOGIST_DETAIL_BASE = "psychologist"
    const val PSYCHOLOGIST_DETAIL = "$PSYCHOLOGIST_DETAIL_BASE/{psychologistId}"
    fun psychologistDetail(psychologistId: String) = "$PSYCHOLOGIST_DETAIL_BASE/$psychologistId"

    private const val CHAT_ROOM_BASE = "chat"
    const val CHAT_ROOM = "$CHAT_ROOM_BASE/{chatId}"
    fun chatRoom(chatId: String) = "$CHAT_ROOM_BASE/$chatId"

    val BOTTOM_BAR_ROUTES = listOf(FEED, CALENDAR, CHATS, WALLET)
}
