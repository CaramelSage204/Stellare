package com.example.stellare.ui.profile

data class CustomPrices(
    val entries: List<Pair<String, Int>>,
    val unreadable: List<String>
) {
    fun format(): String =
        (entries.map { "${it.first} - ${it.second} WKT" } + unreadable).joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = ";"

        fun parse(raw: String?): CustomPrices {
            val entries = mutableListOf<Pair<String, Int>>()
            val unreadable = mutableListOf<String>()
            raw.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
                val parsed = parseEntry(part)
                if (parsed != null) entries += parsed else unreadable += part
            }
            return CustomPrices(entries, unreadable)
        }

        private fun parseEntry(part: String): Pair<String, Int>? {
            val cut = part.lastIndexOf('-')
            if (cut <= 0) return null
            val service = part.substring(0, cut).trim()
            val price = part.substring(cut + 1).replace("PLN", "").replace("WKT", "").trim().toIntOrNull()
            return if (service.isNotEmpty() && price != null) service to price else null
        }
    }
}
