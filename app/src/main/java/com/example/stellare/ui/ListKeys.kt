package com.example.stellare.ui

object ListKeys {

    fun unique(ids: List<String>): List<String> {
        val used = HashSet<String>(ids.size * 2)
        return ids.map { id ->
            var candidate = id
            var suffix = 1
            while (!used.add(candidate)) {
                candidate = "$id#$suffix"
                suffix++
            }
            candidate
        }
    }
}
