package com.example.stellare.appointments

import com.example.stellare.data.model.AppointmentModel
import com.example.stellare.data.model.CompletionEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionEligibilityTest {

    private val start = 1_000_000L

    private fun visit(startsAt: Long) = AppointmentModel(startsAtMillis = startsAt, status = "BOOKED")

    @Test
    fun visitInTheFutureCannotBeCompleted() {
        val result = CompletionEligibility.of(visit(start), start - 1)
        assertFalse(result.isAllowed)
        assertEquals(CompletionEligibility.NotYet(start), result)
    }

    @Test
    fun visitThreeDaysAheadCannotBeCompleted() {
        val threeDays = 3 * 24 * 60 * 60 * 1000L
        assertFalse(CompletionEligibility.of(visit(start + threeDays), start).isAllowed)
    }

    @Test
    fun visitCanBeCompletedExactlyAtStart() {
        assertTrue(CompletionEligibility.of(visit(start), start).isAllowed)
    }

    @Test
    fun visitCanBeCompletedAfterStart() {
        assertTrue(CompletionEligibility.of(visit(start), start + 3_600_000).isAllowed)
    }

    @Test
    fun legacyVisitWithoutStartTimeMatchesRuleAndIsAllowed() {
        assertTrue(CompletionEligibility.of(visit(0L), start).isAllowed)
    }
}
