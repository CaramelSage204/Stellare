package com.example.stellare.psychologistdetail

import com.example.stellare.ui.psychologistdetail.priceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceLabelIntTest {

    @Test
    fun `stawka calkowita ma ten sam napis co jej odpowiednik zmiennoprzecinkowy`() {
        assertEquals("120 WKT / sesja", priceLabel(120))
        assertEquals("przeciazenie Int i Double maja dawac to samo", priceLabel(120.0), priceLabel(120))
    }

    @Test
    fun `stawka zero`() {
        assertEquals("0 WKT / sesja", priceLabel(0))
    }
}
