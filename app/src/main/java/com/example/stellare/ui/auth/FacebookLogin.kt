package com.example.stellare.ui.auth

object FacebookLogin {

    private val appIdPattern = Regex("""\d{5,}""")

    fun isAvailable(appId: String?): Boolean = appId?.trim()?.matches(appIdPattern) == true
}
