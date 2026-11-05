package com.example.stellare.ui.calendar.components

object VisibleMonth {

    private const val MONTHS_IN_YEAR = 12

    fun key(year: Int, monthZeroBased: Int): Int = year * MONTHS_IN_YEAR + monthZeroBased

    fun year(key: Int): Int = Math.floorDiv(key, MONTHS_IN_YEAR)

    fun monthZeroBased(key: Int): Int = Math.floorMod(key, MONTHS_IN_YEAR)

    fun initial(selectedDate: String?, todayYear: Int, todayMonthZeroBased: Int): Int {
        val parts = selectedDate?.split("-")
        val year = parts?.getOrNull(0)?.toIntOrNull()
        val month = parts?.getOrNull(1)?.toIntOrNull()
        val valid = parts?.size == 3 && year != null && month != null && month in 1..MONTHS_IN_YEAR
        return if (valid) key(year!!, month!! - 1) else key(todayYear, todayMonthZeroBased)
    }
}
