package com.example.stellare.ui.shell

fun profilePhoneLine(phone: String?): String? =
    phone?.trim()?.takeIf { it.isNotEmpty() }?.let { "Tel. $it" }
