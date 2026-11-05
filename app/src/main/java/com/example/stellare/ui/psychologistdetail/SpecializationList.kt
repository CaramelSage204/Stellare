package com.example.stellare.ui.psychologistdetail

fun parseSpecializations(raw: String): List<String> =
    raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
